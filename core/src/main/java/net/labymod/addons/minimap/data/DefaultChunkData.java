package net.labymod.addons.minimap.data;

import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.client.world.lighting.LightType;

public class DefaultChunkData extends ChunkData {

  private final int[] colors = new int[CHUNK_SIZE * CHUNK_SIZE];
  private final int[] heightmap = new int[CHUNK_SIZE * CHUNK_SIZE];
  private final byte[] lightLevels = new byte[CHUNK_SIZE * CHUNK_SIZE];

  public DefaultChunkData(Chunk chunk) {
    super(chunk);
  }

  @Override
  public int getHeight(int x, int z) {
    return this.heightmap[this.getIndex(x, z)];
  }

  @Override
  public void setHeight(int x, int z, int height) {
    this.heightmap[this.getIndex(x, z)] = height;
  }

  @Override
  public int getColor(int x, int z) {
    return this.colors[this.getIndex(x, z)];
  }

  @Override
  public void setColor(int x, int z, int color) {
    this.colors[this.getIndex(x, z)] = color;
  }

  @Override
  public int getLightLevel(LightType type, int x, int z) {
    byte lightLevel = this.lightLevels[this.getIndex(x, z)];
    return switch (type) {
      case SKY -> lightLevel >> 4;
      case BLOCK -> lightLevel & 0x0F;
    };
  }

  @Override
  public void setLightLevel(int x, int z, int combinedLightLevel) {
    this.lightLevels[this.getIndex(x, z)] = (byte) combinedLightLevel;
  }
}
