package net.labymod.addons.minimap.world;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.util.Compression;
import org.jetbrains.annotations.Nullable;

/**
 * The saved surface of {@value #CHUNKS}&times;{@value #CHUNKS} chunks: color, height, light and
 * biome of every block column.
 */
public final class MapRegion {

  public static final int CHUNKS = 32;
  public static final int CHUNK_SHIFT = 5;
  public static final int BLOCKS = CHUNKS * ChunkData.CHUNK_SIZE;
  public static final int BLOCK_SHIFT = CHUNK_SHIFT + 4;
  public static final int BIOME_CELL_SIZE = 4;
  public static final int BIOME_CELLS = ChunkData.CHUNK_SIZE / BIOME_CELL_SIZE;
  public static final int MASK_LONGS = CHUNKS * CHUNKS / Long.SIZE;

  private static final int MAGIC = 0x4C4D4D52;
  private static final int VERSION = 1;
  private static final int COLUMNS = BLOCKS * BLOCKS;
  private static final int CHUNK_COUNT = CHUNKS * CHUNKS;
  private static final int MAX_BIOMES = 255;
  private static final AtomicLong REVISIONS = new AtomicLong();

  private final int x;
  private final int z;
  private final int[] colors;
  private final short[] heights;
  private final byte[] lightLevels;
  private final byte[] biomes;
  private final List<String> biomePalette;
  private final long[] present;
  private long revision = REVISIONS.incrementAndGet();

  public MapRegion(int x, int z) {
    this(
        x, z,
        new int[COLUMNS],
        new short[COLUMNS],
        new byte[COLUMNS],
        new byte[COLUMNS],
        new ArrayList<>(),
        new long[CHUNK_COUNT / Long.SIZE]
    );
  }

  private MapRegion(
      int x, int z,
      int[] colors,
      short[] heights,
      byte[] lightLevels,
      byte[] biomes,
      List<String> biomePalette,
      long[] present
  ) {
    this.x = x;
    this.z = z;
    this.colors = colors;
    this.heights = heights;
    this.lightLevels = lightLevels;
    this.biomes = biomes;
    this.biomePalette = biomePalette;
    this.present = present;
  }

  public static long key(int x, int z) {
    return (long) x << 32 | z & 0xFFFFFFFFL;
  }

  public static int keyX(long key) {
    return (int) (key >> 32);
  }

  public static int keyZ(long key) {
    return (int) key;
  }

  public int x() {
    return this.x;
  }

  public int z() {
    return this.z;
  }

  /**
   * Changes with every written chunk. Unique across all region instances, so a reloaded region
   * never matches textures built from an earlier instance.
   */
  public long revision() {
    return this.revision;
  }

  /**
   * Marks a chunk in a mask for {@link #clearChunks(long[])}.
   */
  public static void addToMask(long[] mask, int localChunkX, int localChunkZ) {
    int bit = localChunkZ * CHUNKS + localChunkX;
    mask[bit >> 6] |= 1L << bit;
  }

  public static long[] fullMask() {
    long[] mask = new long[MASK_LONGS];
    Arrays.fill(mask, -1L);
    return mask;
  }

  public boolean isEmpty() {
    for (long bits : this.present) {
      if (bits != 0L) {
        return false;
      }
    }

    return true;
  }

  public void clearChunks(long[] mask) {
    for (int index = 0; index < this.present.length; index++) {
      this.present[index] &= ~mask[index];
    }

    this.revision = REVISIONS.incrementAndGet();
  }

  /**
   * Copies every saved chunk of the other region at the same position into this one.
   */
  public void copyChunks(MapRegion other) {
    for (int chunk = 0; chunk < CHUNK_COUNT; chunk++) {
      if ((other.present[chunk >> 6] & 1L << chunk) == 0L) {
        continue;
      }

      int baseX = (chunk % CHUNKS) << 4;
      int baseZ = (chunk / CHUNKS) << 4;
      for (int z = 0; z < ChunkData.CHUNK_SIZE; z++) {
        int row = (baseZ + z) * BLOCKS + baseX;
        for (int x = 0; x < ChunkData.CHUNK_SIZE; x++) {
          int index = row + x;
          this.colors[index] = other.colors[index];
          this.heights[index] = other.heights[index];
          this.lightLevels[index] = other.lightLevels[index];
          this.biomes[index] = this.biomeId(other.biome(baseX + x, baseZ + z));
        }
      }

      this.present[chunk >> 6] |= 1L << chunk;
    }

    this.revision = REVISIONS.incrementAndGet();
  }

  public boolean hasChunk(int localChunkX, int localChunkZ) {
    int bit = localChunkZ * CHUNKS + localChunkX;
    return (this.present[bit >> 6] & 1L << bit) != 0L;
  }

  public boolean hasColumn(int localX, int localZ) {
    return this.hasChunk(localX >> 4, localZ >> 4);
  }

  public int color(int localX, int localZ) {
    return this.colors[localZ * BLOCKS + localX];
  }

  public int height(int localX, int localZ) {
    return this.heights[localZ * BLOCKS + localX];
  }

  /**
   * @return sky light in the upper, block light in the lower four bits
   */
  public int lightLevel(int localX, int localZ) {
    return this.lightLevels[localZ * BLOCKS + localX] & 0xFF;
  }

  public int blockLightLevel(int localX, int localZ) {
    return this.lightLevel(localX, localZ) & 0x0F;
  }

  @Nullable
  public String biome(int localX, int localZ) {
    int id = this.biomes[localZ * BLOCKS + localX] & 0xFF;
    return id == 0 ? null : this.biomePalette.get(id - 1);
  }

  /**
   * @param biomes the biome of each {@value #BIOME_CELL_SIZE}&times;{@value #BIOME_CELL_SIZE} block
   *               cell of the chunk, row by row
   */
  public void writeChunk(int localChunkX, int localChunkZ, ChunkData data, String[] biomes) {
    byte[] biomeIds = new byte[biomes.length];
    for (int index = 0; index < biomes.length; index++) {
      biomeIds[index] = this.biomeId(biomes[index]);
    }

    int baseX = localChunkX << 4;
    int baseZ = localChunkZ << 4;
    for (int z = 0; z < ChunkData.CHUNK_SIZE; z++) {
      int row = (baseZ + z) * BLOCKS + baseX;
      int biomeRow = z / BIOME_CELL_SIZE * BIOME_CELLS;
      for (int x = 0; x < ChunkData.CHUNK_SIZE; x++) {
        int index = row + x;
        this.colors[index] = data.getColor(x, z);
        this.heights[index] = (short) data.getHeight(x, z);
        this.lightLevels[index] = (byte) data.getLightLevel(x, z);
        this.biomes[index] = biomeIds[biomeRow + x / BIOME_CELL_SIZE];
      }
    }

    int bit = localChunkZ * CHUNKS + localChunkX;
    this.present[bit >> 6] |= 1L << bit;
    this.revision = REVISIONS.incrementAndGet();
  }

  public ChunkData chunk(int localChunkX, int localChunkZ) {
    return new RegionChunkData(this, localChunkX, localChunkZ);
  }

  public MapRegion copy() {
    MapRegion copy = new MapRegion(
        this.x, this.z,
        this.colors.clone(),
        this.heights.clone(),
        this.lightLevels.clone(),
        this.biomes.clone(),
        new ArrayList<>(this.biomePalette),
        this.present.clone()
    );
    copy.revision = this.revision;
    return copy;
  }

  public byte[] encode() {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(COLUMNS);
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(MAGIC);
      output.writeShort(VERSION);
      for (long bits : this.present) {
        output.writeLong(bits);
      }

      output.writeShort(this.biomePalette.size());
      for (String biome : this.biomePalette) {
        output.writeUTF(biome);
      }

      for (int chunk = 0; chunk < CHUNK_COUNT; chunk++) {
        if ((this.present[chunk >> 6] & 1L << chunk) == 0L) {
          continue;
        }

        int baseX = (chunk % CHUNKS) << 4;
        int baseZ = (chunk / CHUNKS) << 4;
        for (int z = 0; z < ChunkData.CHUNK_SIZE; z++) {
          int row = (baseZ + z) * BLOCKS + baseX;
          for (int x = 0; x < ChunkData.CHUNK_SIZE; x++) {
            int index = row + x;
            output.writeInt(this.colors[index]);
            output.writeShort(this.heights[index]);
            output.writeByte(this.lightLevels[index]);
            output.writeByte(this.biomes[index]);
          }
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }

    return Compression.deflate(bytes.toByteArray());
  }

  public static MapRegion decode(int x, int z, byte[] data) throws IOException {
    byte[] raw;
    try {
      raw = Compression.inflate(data);
    } catch (DataFormatException exception) {
      throw new IOException("Corrupt map region", exception);
    }

    DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw));
    if (input.readInt() != MAGIC) {
      throw new IOException("Not a map region");
    }

    int version = input.readShort();
    if (version != VERSION) {
      throw new IOException("Unsupported map region version " + version);
    }

    MapRegion region = new MapRegion(x, z);
    for (int index = 0; index < region.present.length; index++) {
      region.present[index] = input.readLong();
    }

    int paletteSize = input.readShort();
    for (int index = 0; index < paletteSize; index++) {
      region.biomePalette.add(input.readUTF());
    }

    for (int chunk = 0; chunk < CHUNK_COUNT; chunk++) {
      if ((region.present[chunk >> 6] & 1L << chunk) == 0L) {
        continue;
      }

      int baseX = (chunk % CHUNKS) << 4;
      int baseZ = (chunk / CHUNKS) << 4;
      for (int localZ = 0; localZ < ChunkData.CHUNK_SIZE; localZ++) {
        int row = (baseZ + localZ) * BLOCKS + baseX;
        for (int localX = 0; localX < ChunkData.CHUNK_SIZE; localX++) {
          int index = row + localX;
          region.colors[index] = input.readInt();
          region.heights[index] = input.readShort();
          region.lightLevels[index] = input.readByte();
          region.biomes[index] = input.readByte();
        }
      }
    }

    return region;
  }

  private byte biomeId(@Nullable String biome) {
    if (biome == null) {
      return 0;
    }

    int index = this.biomePalette.indexOf(biome);
    if (index == -1) {
      if (this.biomePalette.size() >= MAX_BIOMES) {
        return 0;
      }

      this.biomePalette.add(biome);
      index = this.biomePalette.size() - 1;
    }

    return (byte) (index + 1);
  }
}
