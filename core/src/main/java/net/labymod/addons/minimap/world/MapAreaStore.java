package net.labymod.addons.minimap.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import net.labymod.api.util.GsonUtil;
import net.labymod.api.util.logging.Logging;
import org.jetbrains.annotations.Nullable;

/**
 * Loads and saves the marked areas of one {@link MapWorldKey}. Disk access runs on the executor.
 * Call every method on the render thread.
 */
public final class MapAreaStore {

  private static final Logging LOGGER = Logging.getLogger();
  private static final String FILE = "areas.json";

  private final Path file;
  private final Executor executor;
  private final Queue<Runnable> completions = new ConcurrentLinkedQueue<>();
  private final List<MapArea> areas = new ArrayList<>();
  private boolean loaded;
  private int revision;

  MapAreaStore(MapWorldKey key, Path root, Executor executor) {
    this.file = key.directory(root).resolve(FILE);
    this.executor = executor;
    this.executor.execute(this::load);
  }

  /**
   * @return every area in drawing order, empty while loading
   */
  public List<MapArea> areas() {
    return this.areas;
  }

  /**
   * @return a number that changes whenever areas load, get added, removed or {@link #save saved}
   */
  public int revision() {
    return this.revision;
  }

  @Nullable
  public MapArea get(String id) {
    for (MapArea area : this.areas) {
      if (area.id().equals(id)) {
        return area;
      }
    }

    return null;
  }

  public void add(MapArea area) {
    this.areas.add(area);
    this.save();
  }

  public void remove(MapArea area) {
    if (this.areas.remove(area)) {
      this.save();
    }
  }

  /**
   * Saves the current areas in the background. Call after changing one of them.
   */
  public void save() {
    this.revision++;
    // Saving before the file was read would overwrite it
    if (!this.loaded) {
      return;
    }

    byte[] data = GsonUtil.DEFAULT_GSON.toJson(this.areas.toArray(new MapArea[0]))
        .getBytes(StandardCharsets.UTF_8);
    this.executor.execute(() -> {
      try {
        MapRegionStore.writeAtomically(this.file, data);
      } catch (IOException exception) {
        LOGGER.error("Failed to save map areas {}", this.file, exception);
      }
    });
  }

  void processCompletions() {
    Runnable completion;
    while ((completion = this.completions.poll()) != null) {
      completion.run();
    }
  }

  private void load() {
    List<MapArea> loaded = new ArrayList<>();
    try {
      MapArea[] areas = GsonUtil.DEFAULT_GSON.fromJson(
          Files.readString(this.file, StandardCharsets.UTF_8),
          MapArea[].class
      );
      if (areas != null) {
        for (MapArea area : areas) {
          if (area != null && area.isValid()) {
            loaded.add(area);
          }
        }
      }
    } catch (NoSuchFileException ignored) {
    } catch (Exception exception) {
      LOGGER.warn("Failed to read map areas {}, moving it aside", this.file, exception);
      try {
        Files.move(this.file, this.file.resolveSibling(FILE + ".corrupt-" + System.currentTimeMillis()));
      } catch (IOException moveException) {
        LOGGER.error("Failed to move unreadable map areas {} aside", this.file, moveException);
        // Keeps the file from being overwritten this session
        return;
      }
    }

    this.completions.add(() -> {
      // Areas added while loading come after the saved ones
      this.areas.addAll(0, loaded);
      this.loaded = true;
      if (loaded.size() < this.areas.size()) {
        this.save();
      } else {
        this.revision++;
      }
    });
  }
}
