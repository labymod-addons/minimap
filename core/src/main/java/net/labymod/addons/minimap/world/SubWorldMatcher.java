package net.labymod.addons.minimap.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.api.util.logging.Logging;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the saved sub-world whose terrain matches the chunks around the player. Runs on the disk
 * thread.
 */
final class SubWorldMatcher {

  static final float MATCH_THRESHOLD = 0.6F;
  static final float NO_OVERLAP = -1.0F;
  private static final Logging LOGGER = Logging.getLogger();
  static final String LAST_USED_FILE = "last-used";
  private static final int MAX_HEIGHT_DIFFERENCE = 1;

  private SubWorldMatcher() {
  }

  static List<Integer> listSubWorlds(Path dimensionDirectory) {
    List<Integer> subWorlds = new ArrayList<>();
    if (!Files.isDirectory(dimensionDirectory)) {
      return subWorlds;
    }

    try (DirectoryStream<Path> directories = Files.newDirectoryStream(
        dimensionDirectory,
        Files::isDirectory
    )) {
      for (Path directory : directories) {
        try {
          subWorlds.add(Integer.parseInt(directory.getFileName().toString()));
        } catch (NumberFormatException ignored) {
        }
      }
    } catch (IOException exception) {
      LOGGER.warn("Failed to list sub-worlds in {}", dimensionDirectory, exception);
    }

    subWorlds.sort(null);
    return subWorlds;
  }

  /**
   * @return the share of sampled columns that match the saved map, or {@link #NO_OVERLAP} if none
   *     of the sampled chunks were saved there
   */
  static float match(Path directory, List<ChunkSample> samples) {
    Long2ObjectMap<MapRegion> regions = new Long2ObjectOpenHashMap<>();
    int compared = 0;
    int matched = 0;
    for (ChunkSample sample : samples) {
      int regionX = sample.chunkX() >> MapRegion.CHUNK_SHIFT;
      int regionZ = sample.chunkZ() >> MapRegion.CHUNK_SHIFT;
      long key = MapRegion.key(regionX, regionZ);
      if (!regions.containsKey(key)) {
        regions.put(key, readRegion(directory, regionX, regionZ));
      }

      MapRegion region = regions.get(key);
      int localChunkX = sample.chunkX() & (MapRegion.CHUNKS - 1);
      int localChunkZ = sample.chunkZ() & (MapRegion.CHUNKS - 1);
      if (region == null || !region.hasChunk(localChunkX, localChunkZ)) {
        continue;
      }

      int baseX = localChunkX << 4;
      int baseZ = localChunkZ << 4;
      for (int z = 0; z < ChunkData.CHUNK_SIZE; z++) {
        for (int x = 0; x < ChunkData.CHUNK_SIZE; x++) {
          int index = z * ChunkData.CHUNK_SIZE + x;
          int heightDifference = region.height(baseX + x, baseZ + z) - sample.heights()[index];
          compared++;
          if (region.color(baseX + x, baseZ + z) == sample.colors()[index]
              && Math.abs(heightDifference) <= MAX_HEIGHT_DIFFERENCE) {
            matched++;
          }
        }
      }
    }

    return compared == 0 ? NO_OVERLAP : matched / (float) compared;
  }

  static long lastUsed(Path directory) {
    try {
      return Long.parseLong(Files.readString(directory.resolve(LAST_USED_FILE)).trim());
    } catch (IOException | NumberFormatException exception) {
      return 0L;
    }
  }

  static void markUsed(Path directory) throws IOException {
    MapRegionStore.writeAtomically(
        directory.resolve(LAST_USED_FILE),
        Long.toString(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8)
    );
  }

  @Nullable
  private static MapRegion readRegion(Path directory, int x, int z) {
    Path file = MapRegionStore.regionFile(directory, x, z);
    if (!Files.isRegularFile(file)) {
      return null;
    }

    try {
      return MapRegion.decode(x, z, Files.readAllBytes(file));
    } catch (IOException exception) {
      return null;
    }
  }

  record ChunkSample(int chunkX, int chunkZ, int[] colors, short[] heights) {

    static ChunkSample of(ChunkData data) {
      int[] colors = new int[ChunkData.CHUNK_SIZE * ChunkData.CHUNK_SIZE];
      short[] heights = new short[colors.length];
      for (int z = 0; z < ChunkData.CHUNK_SIZE; z++) {
        for (int x = 0; x < ChunkData.CHUNK_SIZE; x++) {
          int index = z * ChunkData.CHUNK_SIZE + x;
          colors[index] = data.getColor(x, z);
          heights[index] = (short) data.getHeight(x, z);
        }
      }

      return new ChunkSample(data.getX(), data.getZ(), colors, heights);
    }
  }
}
