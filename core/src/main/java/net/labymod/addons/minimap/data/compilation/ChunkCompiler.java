package net.labymod.addons.minimap.data.compilation;

import net.labymod.addons.minimap.data.ChunkData;

public interface ChunkCompiler<T extends ChunkData> {

  boolean isCompatible(ChunkData data);

  void compile(T t);

  void setPlayerPosition(int playerX, int playerY, int playerZ, boolean underground);

  default void setRoofed(boolean roofed) {
  }

  /**
   * @param radius blocks that grass and foliage colors are averaged over
   */
  default void setBiomeBlend(int radius) {
  }
}
