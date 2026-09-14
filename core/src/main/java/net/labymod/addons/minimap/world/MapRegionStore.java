package net.labymod.addons.minimap.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import net.labymod.api.util.logging.Logging;
import org.jetbrains.annotations.Nullable;

/**
 * Loads and saves the regions of one {@link MapWorldKey}. Disk access runs on the executor. Call
 * every method on the render thread.
 */
public final class MapRegionStore {

  private static final Logging LOGGER = Logging.getLogger();
  private static final int MAX_LOADED_REGIONS = 24;
  private static final int MAX_LOADED_LODS = 1024;
  private static final int MAX_LOADED_FINE_LODS = 160;
  private static final String REGION_DIRECTORY = "regions";
  // Overviews used to be half the size, the new directory keeps them from being read as corrupt
  private static final String LOD_DIRECTORY = "lod256";
  private static final String FILE_PREFIX = "r.";
  private static final String FILE_SUFFIX = ".bin";

  private final MapWorldKey key;
  private final Path directory;
  private final Executor executor;
  private final Queue<Runnable> completions = new ConcurrentLinkedQueue<>();
  private final Long2ObjectLinkedOpenHashMap<MapRegion> regions = new Long2ObjectLinkedOpenHashMap<>();
  private final Long2ObjectLinkedOpenHashMap<int[]> lods = new Long2ObjectLinkedOpenHashMap<>();
  private final Long2ObjectLinkedOpenHashMap<int[]> fineLods = new Long2ObjectLinkedOpenHashMap<>();
  private final LongSet stored = new LongOpenHashSet();
  private final LongSet loadingRegions = new LongOpenHashSet();
  private final LongSet loadingLods = new LongOpenHashSet();
  private final LongSet dirty = new LongOpenHashSet();
  private boolean indexed;

  public MapRegionStore(MapWorldKey key, Path root, Executor executor) {
    this.key = key;
    this.directory = key.directory(root);
    this.executor = executor;
    this.executor.execute(this::index);
  }

  public MapWorldKey key() {
    return this.key;
  }

  public boolean isIndexed() {
    return this.indexed;
  }

  public boolean isLoading(int x, int z) {
    return this.loadingRegions.contains(MapRegion.key(x, z));
  }

  public void processCompletions() {
    Runnable completion;
    while ((completion = this.completions.poll()) != null) {
      completion.run();
    }
  }

  /**
   * @return the region if it is loaded. A saved region that isn't loaded yet starts loading.
   */
  @Nullable
  public MapRegion getRegion(int x, int z) {
    long key = MapRegion.key(x, z);
    MapRegion region = this.regions.getAndMoveToLast(key);
    if (region == null && this.stored.contains(key)) {
      this.loadRegion(key);
    }

    return region;
  }

  /**
   * @return the region to write into, or {@code null} while it still has to be loaded from disk
   */
  @Nullable
  public MapRegion getOrCreateRegion(int x, int z) {
    if (!this.indexed) {
      return null;
    }

    long key = MapRegion.key(x, z);
    MapRegion region = this.regions.getAndMoveToLast(key);
    if (region != null) {
      return region;
    }

    if (this.stored.contains(key)) {
      this.loadRegion(key);
      return null;
    }

    region = new MapRegion(x, z);
    this.regions.putAndMoveToLast(key, region);
    this.stored.add(key);
    return region;
  }

  /**
   * Like {@link #getOrCreateRegion(int, int)} without creating anything.
   */
  public boolean isWritable(int x, int z) {
    if (!this.indexed) {
      return false;
    }

    long key = MapRegion.key(x, z);
    if (this.regions.containsKey(key)) {
      return true;
    }

    if (this.stored.contains(key)) {
      this.loadRegion(key);
      return false;
    }

    return !this.loadingRegions.contains(key);
  }

  public void markDirty(MapRegion region) {
    this.dirty.add(MapRegion.key(region.x(), region.z()));
  }

  /**
   * @return the {@value MapLod#SMALL_SIZE} pixel overview of a saved region, or {@code null} while
   *     it is loading
   */
  public int @Nullable [] getLod(int x, int z) {
    return this.getLod(this.lods, x, z);
  }

  /**
   * @return the {@value MapLod#SIZE} pixel overview of a saved region, or {@code null} while it is
   *     loading
   */
  public int @Nullable [] getFineLod(int x, int z) {
    return this.getLod(this.fineLods, x, z);
  }

  private int @Nullable [] getLod(Long2ObjectLinkedOpenHashMap<int[]> lods, int x, int z) {
    long key = MapRegion.key(x, z);
    int[] lod = lods.getAndMoveToLast(key);
    if (lod == null && this.stored.contains(key)) {
      this.loadLod(key);
    }

    return lod;
  }

  /**
   * Saves every changed region in the background and unloads the least recently used ones.
   */
  public void flush() {
    LongIterator iterator = this.dirty.iterator();
    while (iterator.hasNext()) {
      MapRegion region = this.regions.get(iterator.nextLong());
      if (region != null) {
        MapRegion snapshot = region.copy();
        this.executor.execute(() -> this.save(snapshot));
      }
    }

    this.dirty.clear();
    this.evictRegions();
  }

  /**
   * Unloads the least recently used regions that have no unsaved changes.
   */
  public void evictRegions() {
    int excess = this.regions.size() - MAX_LOADED_REGIONS;
    ObjectIterator<Long2ObjectMap.Entry<MapRegion>> iterator =
        this.regions.long2ObjectEntrySet().fastIterator();
    while (excess > 0 && iterator.hasNext()) {
      if (this.dirty.contains(iterator.next().getLongKey())) {
        continue;
      }

      iterator.remove();
      excess--;
    }
  }

  static Path regionFile(Path directory, int x, int z) {
    return directory.resolve(REGION_DIRECTORY).resolve(FILE_PREFIX + x + "." + z + FILE_SUFFIX);
  }

  static void writeAtomically(Path file, byte[] data) throws IOException {
    Files.createDirectories(file.getParent());
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    Files.write(temporary, data);
    try {
      Files.move(
          temporary, file,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE
      );
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void index() {
    LongSet found = new LongOpenHashSet();
    Path regionDirectory = this.directory.resolve(REGION_DIRECTORY);
    if (Files.isDirectory(regionDirectory)) {
      try (DirectoryStream<Path> files = Files.newDirectoryStream(
          regionDirectory,
          FILE_PREFIX + "*" + FILE_SUFFIX
      )) {
        for (Path file : files) {
          String name = file.getFileName().toString();
          String[] coordinates = name
              .substring(FILE_PREFIX.length(), name.length() - FILE_SUFFIX.length())
              .split("\\.");
          if (coordinates.length != 2) {
            continue;
          }

          try {
            found.add(MapRegion.key(
                Integer.parseInt(coordinates[0]),
                Integer.parseInt(coordinates[1])
            ));
          } catch (NumberFormatException ignored) {
          }
        }
      } catch (IOException exception) {
        LOGGER.error("Failed to index map regions in {}", regionDirectory, exception);
      }
    }

    this.completions.add(() -> {
      this.stored.addAll(found);
      this.indexed = true;
    });
  }

  private void loadRegion(long key) {
    if (!this.loadingRegions.add(key)) {
      return;
    }

    int x = MapRegion.keyX(key);
    int z = MapRegion.keyZ(key);
    Path file = regionFile(this.directory, x, z);
    this.executor.execute(() -> {
      MapRegion region = null;
      boolean missing = false;
      try {
        region = MapRegion.decode(x, z, Files.readAllBytes(file));
      } catch (NoSuchFileException exception) {
        missing = true;
      } catch (IOException exception) {
        LOGGER.warn("Failed to read map region {}", file, exception);
        missing = moveAside(file);
      }

      MapRegion loaded = region;
      boolean absent = missing;
      this.completions.add(() -> {
        if (loaded != null) {
          this.loadingRegions.remove(key);
          if (!this.regions.containsKey(key)) {
            this.regions.putAndMoveToLast(key, loaded);
          }
        } else if (absent) {
          this.loadingRegions.remove(key);
          this.stored.remove(key);
        }
        // Otherwise the file is unreadable but couldn't be moved aside. The region stays marked as
        // loading, so nothing overwrites it this session.
      });
    });
  }

  private void loadLod(long key) {
    if (!this.loadingLods.add(key)) {
      return;
    }

    int x = MapRegion.keyX(key);
    int z = MapRegion.keyZ(key);
    Path lodFile = this.lodFile(x, z);
    Path regionFile = regionFile(this.directory, x, z);
    this.executor.execute(() -> {
      int[] lod = null;
      if (Files.isRegularFile(lodFile)) {
        try {
          lod = MapLod.decode(Files.readAllBytes(lodFile));
        } catch (IOException exception) {
          LOGGER.warn("Failed to read map overview {}, rebuilding it", lodFile, exception);
        }
      }

      if (lod == null && Files.isRegularFile(regionFile)) {
        try {
          lod = MapLod.build(MapRegion.decode(x, z, Files.readAllBytes(regionFile)));
          writeAtomically(lodFile, MapLod.encode(lod));
        } catch (IOException exception) {
          LOGGER.warn("Failed to build map overview {}", lodFile, exception);
        }
      }

      // A missing overview stays marked as loading until the region is saved
      if (lod != null) {
        int[] loaded = lod;
        int[] small = MapLod.small(lod);
        this.completions.add(() -> {
          this.loadingLods.remove(key);
          // Keeping a loaded small overview spares the renderer from uploading the same pixels again
          this.putLod(key, loaded, this.lods.containsKey(key) ? null : small);
        });
      }
    });
  }

  private void save(MapRegion snapshot) {
    long key = MapRegion.key(snapshot.x(), snapshot.z());
    Path regionFile = regionFile(this.directory, snapshot.x(), snapshot.z());
    try {
      writeAtomically(regionFile, snapshot.encode());
      int[] lod = MapLod.build(snapshot);
      writeAtomically(this.lodFile(snapshot.x(), snapshot.z()), MapLod.encode(lod));
      int[] small = MapLod.small(lod);
      this.completions.add(() -> {
        this.loadingLods.remove(key);
        this.putLod(key, lod, small);
      });
    } catch (IOException exception) {
      LOGGER.error("Failed to save map region {}", regionFile, exception);
    }
  }

  private void putLod(long key, int[] fine, int @Nullable [] small) {
    this.fineLods.putAndMoveToLast(key, fine);
    while (this.fineLods.size() > MAX_LOADED_FINE_LODS) {
      this.fineLods.removeFirst();
    }

    if (small != null) {
      this.lods.putAndMoveToLast(key, small);
      while (this.lods.size() > MAX_LOADED_LODS) {
        this.lods.removeFirst();
      }
    }
  }

  /**
   * Renames an unreadable file so a new recording doesn't overwrite it.
   *
   * @return whether the rename worked
   */
  private static boolean moveAside(Path file) {
    try {
      Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()));
      return true;
    } catch (IOException exception) {
      LOGGER.error("Failed to move unreadable map region {} aside", file, exception);
      return false;
    }
  }

  private Path lodFile(int x, int z) {
    return this.directory.resolve(LOD_DIRECTORY).resolve(FILE_PREFIX + x + "." + z + FILE_SUFFIX);
  }
}
