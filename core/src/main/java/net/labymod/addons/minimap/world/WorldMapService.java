package net.labymod.addons.minimap.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.data.GameChunkData;
import net.labymod.addons.minimap.world.SubWorldMatcher.ChunkSample;
import net.labymod.api.Constants;
import net.labymod.api.Laby;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.network.server.ServerData;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.lifecycle.GameTickEvent;
import net.labymod.api.event.client.network.server.ServerDisconnectEvent;
import net.labymod.api.event.client.network.server.SubServerSwitchEvent;
import net.labymod.api.event.client.world.DimensionChangeEvent;
import net.labymod.api.event.client.world.WorldEnterEvent;
import net.labymod.api.event.client.world.WorldLeaveEvent;
import net.labymod.api.event.client.world.chunk.BlockUpdateEvent;
import net.labymod.api.event.client.world.chunk.ChunkEvent;
import net.labymod.api.event.client.world.chunk.ChunkEvent.Type;
import net.labymod.api.server.LocalWorld;
import net.labymod.api.util.io.LabyExecutors;
import net.labymod.api.util.logging.Logging;
import org.jetbrains.annotations.Nullable;

/**
 * Records the explored surface of every world and dimension to disk and serves it to the world
 * map.
 *
 * <p>Several worlds can share one server address and dimension. Before recording, the service
 * compares a few chunks around the player with every saved sub-world and continues the one that
 * matches. See {@link #chooseSubWorld}.
 */
public final class WorldMapService implements SurfaceRecorder.Sink {

  private static final Logging LOGGER = Logging.getLogger();
  private static final int FLUSH_INTERVAL_TICKS = 200;
  private static final int VIEWING_FLUSH_INTERVAL_TICKS = 40;
  private static final int KEY_CHECK_INTERVAL_TICKS = 20;
  private static final int MATCH_SAMPLES = 16;
  private static final int MATCH_TIMEOUT_TICKS = 200;
  private static final String DIMENSION_FILE = "dimension.id";
  private static final String NAME_FILE = "name";
  private static final String EXPORT_DIRECTORY = "screenshots";
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 5L;
  private static final Comparator<MapWorldKey> KEY_ORDER = Comparator
      .comparing(MapWorldKey::dimension)
      .thenComparingInt(MapWorldKey::subWorld);

  private final MinimapAddon addon;
  private final Path root;
  private final ExecutorService executor = LabyExecutors.newSingleThreadExecutor("Minimap-WorldMap-%d");
  private final SurfaceRecorder recorder = new SurfaceRecorder();
  private final Queue<Runnable> completions = new ConcurrentLinkedQueue<>();
  private final List<ChunkSample> samples = new ArrayList<>();
  private final Map<MapWorldKey, String> names = new HashMap<>();

  @Nullable
  private MapWorldKey resolvingKey;
  @Nullable
  private List<Integer> candidates;
  private boolean matching;
  private int resolveTicks;
  private int resolveGeneration;
  @Nullable
  private MapRegionStore activeStore;
  @Nullable
  private MapRegionStore viewStore;
  @Nullable
  private WorldMapWaypoints waypoints;
  private boolean viewing;
  private int ticks;

  public WorldMapService(MinimapAddon addon) {
    this.addon = addon;
    this.root = Laby.labyAPI().labyModLoader().getGameDirectory()
        .resolve(Constants.Files.LABYMOD_DIRECTORY)
        .resolve(Util.NAMESPACE)
        .resolve("worlds");
    Runtime.getRuntime().addShutdownHook(new Thread(this::awaitSaves, "Minimap-WorldMap-Shutdown"));
  }

  @Subscribe
  public void onWorldEnter(WorldEnterEvent event) {
    this.refreshKey(null, true);
  }

  @Subscribe
  public void onDimensionChange(DimensionChangeEvent event) {
    this.refreshKey(event.toDimension().toString(), false);
  }

  @Subscribe
  public void onSubServerSwitch(SubServerSwitchEvent event) {
    this.refreshKey(null, true);
  }

  @Subscribe
  public void onWorldLeave(WorldLeaveEvent event) {
    this.close();
  }

  @Subscribe
  public void onServerDisconnect(ServerDisconnectEvent event) {
    this.close();
  }

  @Subscribe
  public void onChunk(ChunkEvent event) {
    Chunk chunk = event.getChunk();
    if (event.getType() == Type.LOAD) {
      this.recorder.enqueue(chunk.getChunkX(), chunk.getChunkZ());
    } else if (event.getType() == Type.UNLOAD) {
      this.recorder.remove(chunk.getChunkX(), chunk.getChunkZ());
    }
  }

  @Subscribe
  public void onBlockUpdate(BlockUpdateEvent event) {
    Chunk chunk = event.getChunk();
    this.recorder.enqueueDelayed(chunk.getChunkX(), chunk.getChunkZ());
  }

  @Subscribe
  public void onTick(GameTickEvent event) {
    if (event.phase() != Phase.POST) {
      return;
    }

    this.processCompletions();
    Minecraft minecraft = Laby.labyAPI().minecraft();
    if (!minecraft.isIngame() || !this.addon.isMinimapAllowed()) {
      if (this.activeStore != null || this.resolvingKey != null) {
        this.closeActive();
      }
      return;
    }

    this.ticks++;
    if (this.ticks % KEY_CHECK_INTERVAL_TICKS == 0) {
      this.refreshKey(null, false);
    }

    if (this.resolvingKey != null) {
      this.tickResolving();
    }

    this.recorder.setBiomeBlend(this.addon.configuration().biomeBlend().get());
    this.recorder.tick(minecraft.clientWorld(), this);

    int flushInterval = this.viewing ? VIEWING_FLUSH_INTERVAL_TICKS : FLUSH_INTERVAL_TICKS;
    if (this.activeStore != null && this.ticks % flushInterval == 0) {
      this.activeStore.flush();
    }
  }

  @Override
  public boolean isReady(int chunkX, int chunkZ) {
    if (this.resolvingKey != null) {
      return this.samples.size() < MATCH_SAMPLES;
    }

    return this.activeStore != null && this.activeStore.isWritable(
        chunkX >> MapRegion.CHUNK_SHIFT,
        chunkZ >> MapRegion.CHUNK_SHIFT
    );
  }

  @Override
  public void accept(GameChunkData data, String[] biomes) {
    if (this.resolvingKey != null) {
      this.samples.add(ChunkSample.of(data));
      return;
    }

    MapRegion region = this.activeStore.getOrCreateRegion(
        data.getX() >> MapRegion.CHUNK_SHIFT,
        data.getZ() >> MapRegion.CHUNK_SHIFT
    );
    region.writeChunk(
        data.getX() & (MapRegion.CHUNKS - 1),
        data.getZ() & (MapRegion.CHUNKS - 1),
        data,
        biomes
    );
    this.activeStore.markDirty(region);
  }

  @Nullable
  public MapWorldKey activeKey() {
    return this.activeStore == null ? null : this.activeStore.key();
  }

  @Nullable
  public MapRegionStore activeStore() {
    return this.activeStore;
  }

  /**
   * @return the store of the recorded world if the key matches it, otherwise a read-only store
   */
  public MapRegionStore openView(MapWorldKey key) {
    if (this.activeStore != null && this.activeStore.key().equals(key)) {
      return this.activeStore;
    }

    if (this.viewStore == null || !this.viewStore.key().equals(key)) {
      this.viewStore = new MapRegionStore(key, this.root, this.executor);
    }

    return this.viewStore;
  }

  public void closeView() {
    this.viewStore = null;
  }

  /**
   * Saves every {@value #VIEWING_FLUSH_INTERVAL_TICKS} ticks while the world map is open, so its
   * overviews show new terrain.
   */
  public void setViewing(boolean viewing) {
    this.viewing = viewing;
  }

  /**
   * @return every saved dimension and sub-world of the key's world, including the key itself
   */
  public List<MapWorldKey> dimensions(MapWorldKey key) {
    List<MapWorldKey> keys = new ArrayList<>();
    Path worldDirectory = key.worldDirectory(this.root);
    if (Files.isDirectory(worldDirectory)) {
      try (DirectoryStream<Path> directories = Files.newDirectoryStream(
          worldDirectory,
          Files::isDirectory
      )) {
        for (Path dimensionDirectory : directories) {
          Path dimensionFile = dimensionDirectory.resolve(DIMENSION_FILE);
          if (!Files.isRegularFile(dimensionFile)) {
            continue;
          }

          String dimension = Files.readString(dimensionFile).trim();
          for (int subWorld : SubWorldMatcher.listSubWorlds(dimensionDirectory)) {
            keys.add(new MapWorldKey(key.multiplayer(), key.context(), dimension, subWorld));
          }
        }
      } catch (IOException exception) {
        LOGGER.warn("Failed to list map dimensions in {}", worldDirectory, exception);
      }
    }

    addMissing(keys, this.activeKey(), key);
    addMissing(keys, key, key);
    keys.sort(KEY_ORDER);
    return keys;
  }

  /**
   * @return when the player last recorded into the key's sub-world in epoch millis, 0 if unknown
   */
  public long lastUsed(MapWorldKey key) {
    return SubWorldMatcher.lastUsed(key.directory(this.root));
  }

  /**
   * @return the name the player gave the sub-world, {@code null} if none
   */
  @Nullable
  public String worldName(MapWorldKey key) {
    String name = this.names.get(key);
    if (name == null) {
      try {
        name = Files.readString(key.directory(this.root).resolve(NAME_FILE)).trim();
      } catch (IOException exception) {
        name = "";
      }

      this.names.put(key, name);
    }

    return name.isEmpty() ? null : name;
  }

  public void renameWorld(MapWorldKey key, String name) {
    this.names.put(key, name);
    Path file = key.directory(this.root).resolve(NAME_FILE);
    byte[] data = name.getBytes(StandardCharsets.UTF_8);
    this.executor.execute(() -> {
      try {
        if (name.isEmpty()) {
          Files.deleteIfExists(file);
        } else {
          MapRegionStore.writeAtomically(file, data);
        }
      } catch (IOException exception) {
        LOGGER.error("Failed to save map name {}", file, exception);
      }
    });
  }

  /**
   * Deletes everything saved for the sub-worlds. Recording starts over if the player is in one of
   * them.
   *
   * @param done runs on the render thread once the files are gone
   */
  public void deleteWorlds(List<MapWorldKey> keys, Runnable done) {
    boolean active = this.release(keys);
    List<Path> directories = new ArrayList<>();
    for (MapWorldKey key : keys) {
      directories.add(key.directory(this.root));
      this.names.remove(key);
    }

    this.executor.execute(() -> {
      for (Path directory : directories) {
        try {
          deleteRecursively(directory);
        } catch (IOException exception) {
          LOGGER.error("Failed to delete map {}", directory, exception);
        }
      }

      this.completions.add(done);
    });
    this.restartRecording(active);
  }

  /**
   * Moves the source sub-world into the target. Where both saved a chunk, the one used more
   * recently wins.
   *
   * @param done runs on the render thread once the source is gone
   */
  public void mergeWorlds(MapWorldKey source, MapWorldKey target, Runnable done) {
    boolean active = this.release(List.of(source, target));
    this.names.remove(source);
    Path sourceDirectory = source.directory(this.root);
    Path targetDirectory = target.directory(this.root);
    this.executor.execute(() -> {
      try {
        long sourceUse = SubWorldMatcher.lastUsed(sourceDirectory);
        long targetUse = SubWorldMatcher.lastUsed(targetDirectory);
        MapRegionStore.merge(sourceDirectory, targetDirectory, sourceUse > targetUse);
        if (sourceUse > targetUse) {
          MapRegionStore.writeAtomically(
              targetDirectory.resolve(SubWorldMatcher.LAST_USED_FILE),
              Long.toString(sourceUse).getBytes(StandardCharsets.UTF_8)
          );
        }

        deleteRecursively(sourceDirectory);
      } catch (IOException exception) {
        LOGGER.error("Failed to merge map {} into {}", sourceDirectory, targetDirectory, exception);
      }

      this.completions.add(done);
    });
    this.restartRecording(active);
  }

  /**
   * Saves a rectangle of the key's map as a PNG in the screenshots folder.
   *
   * @param blocksPerPixel how many blocks one pixel covers
   * @param done           gets the written file, or {@code null} if it failed. Runs on the render
   *                       thread.
   */
  public void exportImage(
      MapWorldKey key,
      MapMode mode,
      int biomeBlend,
      double minX, double minZ,
      double blocksPerPixel,
      int width, int height,
      Consumer<@Nullable Path> done
  ) {
    if (this.activeStore != null && this.activeStore.key().equals(key)) {
      this.activeStore.flush();
    }

    Path directory = key.directory(this.root);
    Path file = exportFile(Laby.labyAPI().labyModLoader().getGameDirectory().resolve(EXPORT_DIRECTORY));
    // Runs after the pending saves, then draws on its own thread so region loading keeps going
    this.executor.execute(() -> new Thread(() -> {
      Path written = file;
      try {
        MapImageExport.write(directory, mode, biomeBlend, minX, minZ, blocksPerPixel, width, height, file);
      } catch (IOException | RuntimeException exception) {
        LOGGER.error("Failed to export map image {}", file, exception);
        written = null;
      }

      Path result = written;
      this.completions.add(() -> done.accept(result));
    }, "Minimap-WorldMap-Export").start());
  }

  @Nullable
  public WorldMapWaypoints waypoints() {
    return this.waypoints;
  }

  public void setWaypoints(@Nullable WorldMapWaypoints waypoints) {
    this.waypoints = waypoints;
  }

  /**
   * Saves and forgets the current world. The next tick starts over if the player is still in one.
   */
  public void close() {
    this.closeActive();
    this.viewStore = null;
    this.recorder.reset();
  }

  private void processCompletions() {
    Runnable completion;
    while ((completion = this.completions.poll()) != null) {
      completion.run();
    }

    if (this.activeStore != null) {
      this.activeStore.processCompletions();
    }

    if (this.viewStore != null) {
      this.viewStore.processCompletions();
      // A viewed store never saves, so it never unloads regions through flush
      this.viewStore.evictRegions();
    }
  }

  /**
   * Runs on JVM shutdown. Leaving the world already queued the last saves. The disk thread is a
   * daemon, so the JVM would kill it before it writes them.
   */
  private void awaitSaves() {
    this.executor.shutdown();
    try {
      if (!this.executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        LOGGER.warn("Not all map regions were saved before shutdown");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Closes the stores of the keys before their files change.
   *
   * @return whether the recorded world was one of them
   */
  private boolean release(List<MapWorldKey> keys) {
    boolean active = this.activeStore != null && keys.contains(this.activeStore.key());
    if (active) {
      this.closeActive();
    }

    if (this.viewStore != null && keys.contains(this.viewStore.key())) {
      this.viewStore = null;
    }

    return active;
  }

  /**
   * Resolves the sub-world again. The disk thread runs it after the file changes, so it sees
   * their result.
   */
  private void restartRecording(boolean active) {
    if (active) {
      this.recorder.reset();
      this.refreshKey(null, true);
    }
  }

  private void refreshKey(@Nullable String dimension, boolean force) {
    if (!this.addon.isMinimapAllowed()) {
      return;
    }

    MapWorldKey key = this.currentKey(dimension);
    MapWorldKey current = this.resolvingKey != null ? this.resolvingKey : this.activeKey();
    boolean unchanged = key == null ? current == null : !force && key.sameDimension(current);
    if (unchanged) {
      return;
    }

    this.closeActive();
    if (key != null) {
      this.resolve(key);
    }
  }

  @Nullable
  private MapWorldKey currentKey(@Nullable String dimension) {
    Minecraft minecraft = Laby.labyAPI().minecraft();
    if (!minecraft.isIngame()) {
      return null;
    }

    String currentDimension = dimension == null
        ? minecraft.clientWorld().dimension().toString()
        : dimension;
    ServerData serverData = Laby.labyAPI().serverController().getCurrentServerData();
    if (serverData != null) {
      return new MapWorldKey(
          true,
          serverData.address().toString(),
          currentDimension,
          MapWorldKey.UNRESOLVED_SUB_WORLD
      );
    }

    LocalWorld localWorld = Laby.references().integratedServer().getLocalWorld();
    return localWorld == null ? null : new MapWorldKey(
        false,
        localWorld.folderName(),
        currentDimension,
        MapWorldKey.UNRESOLVED_SUB_WORLD
    );
  }

  private void resolve(MapWorldKey key) {
    int generation = ++this.resolveGeneration;
    this.resolvingKey = key;
    this.candidates = null;
    this.matching = false;
    this.resolveTicks = 0;
    this.samples.clear();
    this.recorder.reset();
    this.recorder.enqueueAll(Laby.labyAPI().minecraft().clientWorld());

    Path dimensionDirectory = key.dimensionDirectory(this.root);
    this.executor.execute(() -> {
      List<Integer> subWorlds = SubWorldMatcher.listSubWorlds(dimensionDirectory);
      this.completions.add(() -> {
        if (generation != this.resolveGeneration) {
          return;
        }

        if (subWorlds.isEmpty()) {
          this.activate(0);
        } else {
          this.candidates = subWorlds;
        }
      });
    });
  }

  private void tickResolving() {
    if (this.candidates == null || this.matching) {
      return;
    }

    this.resolveTicks++;
    if (this.samples.size() < MATCH_SAMPLES && this.resolveTicks < MATCH_TIMEOUT_TICKS) {
      return;
    }

    this.matching = true;
    int generation = this.resolveGeneration;
    MapWorldKey key = this.resolvingKey;
    List<Integer> candidates = this.candidates;
    List<ChunkSample> samples = new ArrayList<>(this.samples);
    this.executor.execute(() -> {
      int chosen = this.chooseSubWorld(key, candidates, samples);
      this.completions.add(() -> {
        if (generation == this.resolveGeneration) {
          this.activate(chosen);
        }
      });
    });
  }

  /**
   * Runs on the disk thread. A sub-world above {@link SubWorldMatcher#MATCH_THRESHOLD} wins.
   * Otherwise the player may stand where a sub-world has no data yet, so the most recently used
   * sub-world without overlap continues. A new sub-world starts only when every one of them
   * overlaps and disagrees.
   */
  private int chooseSubWorld(MapWorldKey key, List<Integer> candidates, List<ChunkSample> samples) {
    int best = -1;
    float bestMatch = SubWorldMatcher.NO_OVERLAP;
    int latestUnknown = -1;
    long latestUnknownUse = Long.MIN_VALUE;
    int highest = candidates.get(0);
    for (int subWorld : candidates) {
      Path directory = key.withSubWorld(subWorld).directory(this.root);
      float match = SubWorldMatcher.match(directory, samples);
      if (match > bestMatch) {
        bestMatch = match;
        best = subWorld;
      }

      if (match == SubWorldMatcher.NO_OVERLAP) {
        long lastUse = SubWorldMatcher.lastUsed(directory);
        if (lastUse > latestUnknownUse) {
          latestUnknownUse = lastUse;
          latestUnknown = subWorld;
        }
      }

      highest = Math.max(highest, subWorld);
    }

    if (bestMatch >= SubWorldMatcher.MATCH_THRESHOLD) {
      return best;
    }

    return latestUnknown != -1 ? latestUnknown : highest + 1;
  }

  private void activate(int subWorld) {
    MapWorldKey key = this.resolvingKey.withSubWorld(subWorld);
    this.resolvingKey = null;
    this.candidates = null;
    this.samples.clear();

    if (this.viewStore != null && this.viewStore.key().equals(key)) {
      this.activeStore = this.viewStore;
      this.viewStore = null;
    } else {
      this.activeStore = new MapRegionStore(key, this.root, this.executor);
    }

    Path directory = key.directory(this.root);
    Path dimensionFile = key.dimensionDirectory(this.root).resolve(DIMENSION_FILE);
    byte[] dimension = key.dimension().getBytes(StandardCharsets.UTF_8);
    this.executor.execute(() -> {
      try {
        MapRegionStore.writeAtomically(dimensionFile, dimension);
        SubWorldMatcher.markUsed(directory);
      } catch (IOException exception) {
        LOGGER.error("Failed to prepare map directory {}", directory, exception);
      }
    });

    // Chunks sampled while resolving were not written anywhere yet
    this.recorder.clear();
    this.recorder.enqueueAll(Laby.labyAPI().minecraft().clientWorld());
  }

  private void closeActive() {
    this.resolveGeneration++;
    this.resolvingKey = null;
    this.candidates = null;
    this.samples.clear();
    if (this.activeStore != null) {
      this.activeStore.flush();
      this.activeStore = null;
    }
  }

  private static Path exportFile(Path directory) {
    String name = "worldmap_" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
    Path file = directory.resolve(name + ".png");
    for (int index = 1; Files.exists(file); index++) {
      file = directory.resolve(name + "_" + index + ".png");
    }

    return file;
  }

  private static void deleteRecursively(Path directory) throws IOException {
    try {
      Files.walkFileTree(directory, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
          if (exception != null) {
            throw exception;
          }

          Files.delete(directory);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (NoSuchFileException ignored) {
    }
  }

  private static void addMissing(
      List<MapWorldKey> keys,
      @Nullable MapWorldKey candidate,
      MapWorldKey world
  ) {
    if (candidate != null && candidate.sameWorld(world) && !keys.contains(candidate)) {
      keys.add(candidate);
    }
  }
}
