package net.labymod.addons.minimap.world;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.data.GameChunkData;
import net.labymod.addons.minimap.data.compilation.GameChunkCompiler;
import net.labymod.addons.minimap.data.compilation.RoofDetector;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.client.world.chunk.Chunk;

/**
 * Compiles the surface of loaded chunks for the saved map. Uses its own compiler so the minimap's
 * cave mode never ends up on disk.
 */
public final class SurfaceRecorder {

  private static final long BUILD_BUDGET_NANOS = 1_500_000L;
  private static final int BLOCK_UPDATE_DELAY_TICKS = 40;
  private static final int EMPTY_COLOR = 0xFF000000;

  private final GameChunkCompiler compiler = new GameChunkCompiler();
  private final RoofDetector roofDetector = new RoofDetector();
  private final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();
  private final LongSet delayed = new LongOpenHashSet();
  private final String[] biomes = new String[MapRegion.BIOME_CELLS * MapRegion.BIOME_CELLS];
  private int ticks;

  public void enqueue(int chunkX, int chunkZ) {
    this.queue.add(MapRegion.key(chunkX, chunkZ));
  }

  /**
   * Blocks change in bursts, so this records the chunk up to {@value #BLOCK_UPDATE_DELAY_TICKS}
   * ticks later.
   */
  public void enqueueDelayed(int chunkX, int chunkZ) {
    this.delayed.add(MapRegion.key(chunkX, chunkZ));
  }

  public void remove(int chunkX, int chunkZ) {
    long key = MapRegion.key(chunkX, chunkZ);
    this.queue.remove(key);
    this.delayed.remove(key);
  }

  public void clear() {
    this.queue.clear();
    this.delayed.clear();
  }

  /**
   * Clears the queue and detects the roof again. Call it when the dimension changes.
   */
  public void reset() {
    this.clear();
    this.roofDetector.reset();
  }

  public void enqueueAll(ClientWorld world) {
    for (Chunk chunk : world.getChunks()) {
      this.enqueue(chunk.getChunkX(), chunk.getChunkZ());
    }
  }

  public void tick(ClientWorld world, Sink sink) {
    if (++this.ticks % BLOCK_UPDATE_DELAY_TICKS == 0 && !this.delayed.isEmpty()) {
      this.queue.addAll(this.delayed);
      this.delayed.clear();
    }

    this.roofDetector.tick();
    this.compiler.setRoofed(this.roofDetector.isRoofed());

    long deadline = System.nanoTime() + BUILD_BUDGET_NANOS;
    int remaining = this.queue.size();
    while (remaining-- > 0) {
      long key = this.queue.removeFirstLong();
      int chunkX = MapRegion.keyX(key);
      int chunkZ = MapRegion.keyZ(key);
      Chunk chunk = world.getChunk(chunkX, chunkZ);
      if (chunk == null) {
        continue;
      }

      // Recording before the roof is known would save the wrong layer
      if (!this.roofDetector.isDecided()) {
        this.roofDetector.sample(chunk);
        this.queue.add(key);
        if (System.nanoTime() >= deadline) {
          return;
        }

        continue;
      }

      if (!sink.isReady(chunkX, chunkZ)) {
        this.queue.add(key);
        continue;
      }

      GameChunkData data = new GameChunkData(chunk);
      this.compiler.compile(data);
      if (this.isEmpty(data)) {
        this.delayed.add(key);
      } else {
        this.sampleBiomes(world, data);
        sink.accept(data, this.biomes);
      }

      if (System.nanoTime() >= deadline) {
        return;
      }
    }
  }

  /**
   * Chunks without any visible block haven't received their blocks yet and would overwrite the
   * saved map with black.
   */
  private boolean isEmpty(GameChunkData data) {
    for (int z = 0; z < ChunkData.CHUNK_SIZE; z++) {
      for (int x = 0; x < ChunkData.CHUNK_SIZE; x++) {
        if (data.getColor(x, z) != EMPTY_COLOR) {
          return false;
        }
      }
    }

    return true;
  }

  private void sampleBiomes(ClientWorld world, GameChunkData data) {
    int blockX = data.getX() << 4;
    int blockZ = data.getZ() << 4;
    for (int cellZ = 0; cellZ < MapRegion.BIOME_CELLS; cellZ++) {
      int z = cellZ * MapRegion.BIOME_CELL_SIZE + MapRegion.BIOME_CELL_SIZE / 2;
      for (int cellX = 0; cellX < MapRegion.BIOME_CELLS; cellX++) {
        int x = cellX * MapRegion.BIOME_CELL_SIZE + MapRegion.BIOME_CELL_SIZE / 2;
        this.biomes[cellZ * MapRegion.BIOME_CELLS + cellX] = world
            .biome(blockX + x, data.getHeight(x, z), blockZ + z)
            .toString();
      }
    }
  }

  public interface Sink {

    /**
     * @return {@code false} to keep the chunk queued, for example while its region is loading
     */
    boolean isReady(int chunkX, int chunkZ);

    void accept(GameChunkData data, String[] biomes);
  }
}
