package net.labymod.addons.minimap.map.v2;

import java.util.function.Function;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.Entity;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.client.world.block.BlockColorProvider;
import net.labymod.api.client.world.block.BlockState;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.client.world.lighting.LightType;
import net.labymod.api.util.ColorUtil;
import net.labymod.api.util.color.format.ColorFormat;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.vector.IntVector3;

public class MinimapChunk {

  private static final int CHUNK_SIZE = 16;

  private final BlockColorProvider blockColorProvider;
  private final Chunk chunk;
  private final int[] colors = new int[CHUNK_SIZE * CHUNK_SIZE];
  private final int[] undergroundColors = new int[CHUNK_SIZE * CHUNK_SIZE];
  private final int[] heightmap = new int[CHUNK_SIZE * CHUNK_SIZE];
  private final byte[] lightLevels = new byte[CHUNK_SIZE * CHUNK_SIZE];
  private final ClientWorld level;

  private int minY;
  private int maxY;

  private boolean compiled;
  private boolean underground;

  public MinimapChunk(Chunk chunk) {
    this.blockColorProvider = Laby.references().blockColorProvider();
    this.level = Laby.references().clientWorld();
    this.chunk = chunk;
  }

  public void compile(boolean underground) {
    if (this.compiled && this.underground == underground) {
      return;
    }

    this.underground = underground;

    ColorFormat format = ColorFormat.ARGB32;
    for (int x = 0; x < CHUNK_SIZE; x++) {
      for (int z = 0; z < CHUNK_SIZE; z++) {

        BlockState highestBlock = this.getBlockState(x, z, false);
        BlockState block = this.getBlockState(x, z, true);
        if (highestBlock == null || block == null) {
          this.setColor(x, z, 0xFF000000);
          continue;
        }

        int baseColor = format.withAlpha(this.getColor(format, block), 255);
        BlockState above = this.chunk.getBlockState(x, block.position().getY() + 1, z);

        this.setHeight(x, z, highestBlock.position().getY() - (highestBlock.hasCollision() ? 0 : 1));
        this.setLightLevel(x, z, above);
        if (block.isWater()) {
          BlockState blockStateUnderWater = this.getBlockBelow(block, BlockState::isWater);

          baseColor = format.pack(baseColor, 220);

          int colorUnderWater = format.withAlpha(this.getColor(format, blockStateUnderWater), 255);
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

  private BlockState getBlockState(int x, int z, boolean checkUnderground) {
    int y;
    if (checkUnderground) {
      Entity cameraEntity = Laby.labyAPI().minecraft().getCameraEntity();
      int entityY = MathHelper.floor(cameraEntity.getPosY());
      y = this.underground ? entityY : this.chunk.getHeightBasedOnSection(64);
    } else {
      y = this.chunk.getHeightBasedOnSection(64);
    }

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

  private BlockState getBlockBelow(
      BlockState state,
      Function<BlockState, Boolean> filter
  ) {
    IntVector3 position = state.position();
    int x = position.getX() & 15;
    int y = position.getY();
    int z = position.getZ() & 15;

    int minBuildHeight = this.level.getMinBuildHeight();

    BlockState blockState = null;
    while (y > minBuildHeight) {
      blockState = this.chunk.getBlockState(x, y, z);
      if (filter.apply(blockState)) {
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
    int index = this.getIndex(x, z);
    if (this.underground) {
      this.undergroundColors[index] = argb;
    } else {
      this.colors[index] = argb;
    }
  }

  public int getColor(int x, int z) {
    int index = this.getIndex(x, z);
    return this.underground ? this.undergroundColors[index] : this.colors[index];
  }

  private void setLightLevel(int x, int z, BlockState state) {
    this.setLightLevel(
        x, z,
        state.getLightLevel(LightType.SKY), state.getLightLevel(LightType.BLOCK)
    );
  }

  private void setLightLevel(int x, int z, int skyLevel, int blockLevel) {
    this.lightLevels[this.getIndex(x, z)] = (byte) (skyLevel << 4 | blockLevel);
  }

  public int getLightLevel(LightType type, int x, int z) {
    final byte combinedLightLevel = this.lightLevels[this.getIndex(x, z)];
    return switch (type) {
      case SKY -> combinedLightLevel >> 4;
      case BLOCK -> combinedLightLevel & 0x0f;
    };
  }

  public int getBlockLightLevel(int x, int z) {
    return this.getLightLevel(LightType.BLOCK, x, z);
  }

  public int getSkyLightLevel(int x, int z) {
    return this.getLightLevel(LightType.SKY, x, z);
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
