package net.labymod.addons.minimap.map.v2;

import net.labymod.api.Laby;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.client.world.block.BlockColorProvider;
import net.labymod.api.client.world.block.BlockState;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.util.ColorUtil;
import net.labymod.api.util.color.format.ColorFormat;
import net.labymod.api.util.math.vector.IntVector3;

public class MinimapChunk {

  private static final int CHUNK_SIZE = 16;

  private final BlockColorProvider blockColorProvider;
  private final Chunk chunk;
  private final int[] colors = new int[CHUNK_SIZE * CHUNK_SIZE];
  private final int[] heightmap = new int[CHUNK_SIZE * CHUNK_SIZE];
  private final ClientWorld level;

  private int minY;
  private int maxY;

  private boolean compiled;

  public MinimapChunk(Chunk chunk) {
    this.blockColorProvider = Laby.references().blockColorProvider();
    this.level = Laby.references().clientWorld();
    this.chunk = chunk;
  }

  public void compile() {
    if (this.compiled) {
      return;
    }

    ColorFormat format = ColorFormat.ARGB32;
    for (int x = 0; x < CHUNK_SIZE; x++) {
      for (int z = 0; z < CHUNK_SIZE; z++) {

        BlockState blockState = this.getBlockState(x, z);
        if (blockState == null) {
          this.setColor(x, z, 0xFF000000);
          continue;
        }

        int baseColor = 0xFF000000 | this.getColor(format, blockState);

        this.setHeight(x, z, blockState.position().getY() - (blockState.hasCollision() ? 0 : 1));
        if (blockState.isWater()) {
          BlockState blockStateUnderWater = this.getBlockStateUnderWater(blockState);

          baseColor = format.pack(baseColor, 220);

          int colorUnderWater = 0xFF000000 | this.getColor(format, blockStateUnderWater);
          baseColor = ColorUtil.blendColors(colorUnderWater, baseColor);
          this.setHeight(x, z, blockStateUnderWater.position().getY());
        }

        this.setColor(x, z, baseColor);
      }
    }

    this.compiled = true;
  }

  private int getColor(ColorFormat format, BlockState state) {
    int baseColor = this.blockColorProvider.getColor(state);
    int multiplier = this.blockColorProvider.getColorMultiplier(state);

    float redMultiplier = format.normalizedRed(multiplier);
    float greenMultiplier = format.normalizedGreen(multiplier);
    float blueMultiplier = format.normalizedBlue(multiplier);

    return format.mul(
        baseColor,
        redMultiplier,
        greenMultiplier,
        blueMultiplier,
        1.0F
    );
  }

  private BlockState getBlockState(int x, int z) {
    int y = this.level.getMaxBuildHeight();
    int minBuildHeight = this.level.getMinBuildHeight();

    BlockState blockState = null;
    while (y > minBuildHeight) {
      blockState = this.chunk.getBlockState(x, y, z);

      if (blockState != null && !blockState.block().isAir()) {
        break;
      }
      y--;
    }

    return blockState;
  }

  private BlockState getBlockStateUnderWater(BlockState water) {
    IntVector3 position = water.position();
    int x = position.getX() & 15;
    int y = position.getY();
    int z = position.getZ() & 15;
    int minBuildHeight = this.level.getMinBuildHeight();

    BlockState blockState = null;
    while (y > minBuildHeight) {
      blockState = this.chunk.getBlockState(x, y, z);
      if (!blockState.isWater()) {
        break;
      }

      y--;
    }

    return blockState;
  }

  public void resetCompilation() {
    this.compiled = false;
  }

  private void setHeight(int x, int z, int height) {
    this.heightmap[this.getIndex(x, z)] = height;

    if (height < this.minY) {
      this.minY = height;
    }

    if (height > this.maxY) {
      this.maxY = height;
    }
  }

  public int getHeight(int x, int z) {
    return this.heightmap[this.getIndex(x, z)];
  }

  public int getMinY() {
    return this.minY;
  }

  public int getMaxY() {
    return this.maxY;
  }

  private void setColor(int x, int z, int argb) {
    this.colors[this.getIndex(x, z)] = argb;
  }

  public int getColor(int x, int z) {
    return this.colors[this.getIndex(x, z)];
  }

  private int getIndex(int x, int z) {
    return x * CHUNK_SIZE + z;
  }

  public int getX() {
    return this.chunk.getChunkX();
  }

  public int getZ() {
    return this.chunk.getChunkZ();
  }

}
