package net.labymod.addons.minimap.world;

import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.api.client.world.lighting.LightType;

/**
 * Read-only view of one saved chunk.
 */
final class RegionChunkData extends ChunkData {

  private final MapRegion region;
  private final int localChunkX;
  private final int localChunkZ;
  private final int baseX;
  private final int baseZ;

  RegionChunkData(MapRegion region, int localChunkX, int localChunkZ) {
    this.region = region;
    this.localChunkX = localChunkX;
    this.localChunkZ = localChunkZ;
    this.baseX = localChunkX << 4;
    this.baseZ = localChunkZ << 4;
  }

  @Override
  public int getHeight(int x, int z) {
    return this.region.height(this.baseX + x, this.baseZ + z);
  }

  @Override
  public void setHeight(int x, int z, int height) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getColor(int x, int z) {
    return this.region.color(this.baseX + x, this.baseZ + z);
  }

  @Override
  public void setColor(int x, int z, int color) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getLightLevel(int x, int z) {
    return this.region.lightLevel(this.baseX + x, this.baseZ + z);
  }

  @Override
  public int getLightLevel(LightType type, int x, int z) {
    int lightLevel = this.getLightLevel(x, z);
    return switch (type) {
      case SKY -> lightLevel >> 4;
      case BLOCK -> lightLevel & 0x0F;
    };
  }

  @Override
  public void setLightLevel(int x, int z, int combinedLightLevel) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getX() {
    return (this.region.x() << MapRegion.CHUNK_SHIFT) + this.localChunkX;
  }

  @Override
  public int getZ() {
    return (this.region.z() << MapRegion.CHUNK_SHIFT) + this.localChunkZ;
  }
}
