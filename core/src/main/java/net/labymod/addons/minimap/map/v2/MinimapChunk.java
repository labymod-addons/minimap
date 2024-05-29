package net.labymod.addons.minimap.map.v2;

import net.labymod.api.Laby;
import net.labymod.api.client.world.block.BlockColorProvider;
import net.labymod.api.client.world.block.BlockState;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.client.world.chunk.Heightmap;
import net.labymod.api.client.world.chunk.HeightmapType;
import net.labymod.api.util.color.format.ColorFormat;

public class MinimapChunk {

  private static final int CHUNK_SIZE = 16;

  private final BlockColorProvider blockColorProvider;
  private final Chunk chunk;
  private final int[] colors = new int[CHUNK_SIZE * CHUNK_SIZE];
  private final Heightmap heightmap;

  private boolean compiled;

  public MinimapChunk(Chunk chunk) {
    this.blockColorProvider = Laby.references().blockColorProvider();
    this.chunk = chunk;
    this.heightmap = this.chunk.heightmap(HeightmapType.WORLD_SURFACE);
  }

  public void compile() {
    if (this.compiled) {
      return;
    }

    ColorFormat format = ColorFormat.ARGB32;
    for (int x = 0; x < CHUNK_SIZE; x++) {
      for (int z = 0; z < CHUNK_SIZE; z++) {
        int y = this.getHeight(x, z) - 1;

        BlockState blockState = this.chunk.getBlockState(x, y, z);
        int baseColor = this.blockColorProvider.getColor(blockState);
        int multiplier = this.blockColorProvider.getColorMultiplier(blockState);

        float redMultiplier = format.normalizedRed(multiplier);
        float greenMultiplier = format.normalizedGreen(multiplier);
        float blueMultiplier = format.normalizedBlue(multiplier);

        this.colors[x * CHUNK_SIZE + z] = format.mul(
            baseColor,
            redMultiplier,
            greenMultiplier,
            blueMultiplier,
            1.0F
        );
      }
    }

    this.compiled = true;
  }

  public void resetCompilation() {
    this.compiled = false;
  }

  public int getHeight(int x, int z) {
    return this.heightmap.getHeight(x, z);
  }

  public int getColor(int x, int y) {
    return this.colors[x * CHUNK_SIZE + y];
  }

  public int getX() {
    return this.chunk.getChunkX();
  }

  public int getZ() {
    return this.chunk.getChunkZ();
  }

}
