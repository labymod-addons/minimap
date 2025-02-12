package net.labymod.addons.minimap.data;

import net.labymod.api.client.world.block.BlockState;
import net.labymod.api.client.world.lighting.LightType;

public abstract class ChunkData {

  public static final int CHUNK_SIZE = 16;

  protected ChunkData() {
  }

  public abstract int getHeight(int x, int z);

  public abstract void setHeight(int x, int z, int height);

  public abstract int getColor(int x, int z);

  public abstract void setColor(int x, int z, int color);

  public int getBlockLightLevel(int x, int z) {
    return this.getLightLevel(LightType.BLOCK, x, z);
  }

  public int getSkyLightLevel(int x, int z) {
    return this.getLightLevel(LightType.SKY, x, z);
  }

  public abstract int getLightLevel(LightType type, int x, int z);

  public void setLightLevel(int x, int z, BlockState blockState) {
    this.setLightLevel(
        x, z,
        blockState.getLightLevel(LightType.SKY),
        blockState.getLightLevel(LightType.BLOCK)
    );
  }

  public void setLightLevel(int x, int y, int skyLevel, int blockLevel) {
    this.setLightLevel(x, y, (skyLevel << 4 | blockLevel));
  }

  public abstract void setLightLevel(int x, int z, int combinedLightLevel);

  public abstract int getX();

  public abstract int getZ();

  protected int getIndex(int x, int z) {
    return x * CHUNK_SIZE + z;
  }

}
