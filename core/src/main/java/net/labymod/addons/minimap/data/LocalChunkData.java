package net.labymod.addons.minimap.data;

import java.nio.ByteBuffer;
import net.labymod.api.client.world.lighting.LightType;

// TODO (Christian) Versioning
public class LocalChunkData extends ChunkData {

  private static final int CHUNK_X_POSITION = 0;
  private static final int CHUNK_Z_POSITION = 4;
  private static final int CHUNK_POSITION_OFFSET = Integer.BYTES * 2;

  private static final int COLOR_SIZE = 4;
  private static final int HEIGHTMAP_SIZE = 4;
  private static final int LIGHT_LEVEL_SIZE = 1;

  private static final int COMPONENT_SIZE = COLOR_SIZE + HEIGHTMAP_SIZE + LIGHT_LEVEL_SIZE;
  private static final int BUFFER_SIZE = CHUNK_POSITION_OFFSET + (CHUNK_SIZE * CHUNK_SIZE * COMPONENT_SIZE);

  private final ByteBuffer buffer;

  public LocalChunkData() {
    this.buffer = ByteBuffer.allocate(BUFFER_SIZE);
  }

  public LocalChunkData(byte[] buffer) {
    if (buffer.length < BUFFER_SIZE || buffer.length > BUFFER_SIZE) {
      throw new IllegalStateException("Invalid chunk data");
    }

    this.buffer = ByteBuffer.wrap(buffer);
  }

  public void setPosition(int x, int z) {
    this.buffer.putInt(CHUNK_X_POSITION, x);
    this.buffer.putInt(CHUNK_Z_POSITION, z);
  }

  @Override
  public int getHeight(int x, int z) {
    return this.buffer.getInt(this.getBufferIndex(x, z, COLOR_SIZE));
  }

  @Override
  public void setHeight(int x, int z, int height) {
    int index = this.getBufferIndex(x, z, COLOR_SIZE);
    this.buffer.putInt(index, height);
  }

  @Override
  public int getColor(int x, int z) {
    return this.buffer.getInt(this.getBufferIndex(x, z, 0));
  }

  @Override
  public void setColor(int x, int z, int color) {
    int index = this.getBufferIndex(x, z, 0);
    this.buffer.putInt(index, color);
  }

  @Override
  public int getLightLevel(int x, int z) {
    return this.buffer.get(this.getBufferIndex(x, z, COLOR_SIZE + HEIGHTMAP_SIZE));
  }

  @Override
  public int getLightLevel(LightType type, int x, int z) {
    byte lightLevel = this.buffer.get(this.getBufferIndex(x, z, COLOR_SIZE + HEIGHTMAP_SIZE));
    return switch (type) {
      case SKY -> lightLevel >> 4;
      case BLOCK -> lightLevel & 0x0F;
    };
  }

  @Override
  public void setLightLevel(int x, int z, int combinedLightLevel) {
    int index = this.getBufferIndex(x, z, COLOR_SIZE + HEIGHTMAP_SIZE);
    this.buffer.put(index, (byte) combinedLightLevel);
  }

  @Override
  public int getX() {
    return this.buffer.getInt(CHUNK_X_POSITION);
  }

  @Override
  public int getZ() {
    return this.buffer.getInt(CHUNK_Z_POSITION);
  }

  public byte[] getRawBuffer() {
    byte[] destination = new byte[BUFFER_SIZE];
    this.buffer.get(destination);
    return destination;
  }

  private int getBufferIndex(int x, int z, int offset) {
    return CHUNK_POSITION_OFFSET + (z * CHUNK_SIZE + x) * COMPONENT_SIZE + offset;
  }
}
