package net.labymod.addons.minimap.map;

import net.labymod.api.Laby;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.client.world.block.BlockColorProvider;
import net.labymod.api.client.world.block.BlockState;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.client.world.chunk.Heightmap;
import net.labymod.api.client.world.chunk.HeightmapType;
import net.labymod.api.util.Color;
import net.labymod.api.util.ColorUtil;
import net.labymod.api.util.color.format.ColorFormat;

public class OverworldChunkTileColorProvider implements ChunkTileColorProvider {

  private static final int SKY_COLOR = ColorFormat.ARGB32.pack(142, 163, 255, 255);

  private static final int CHUNK_SIZE_X = 16;
  private static final int CHUNK_SIZE_Z = 16;

  private final BlockColorProvider blockColorProvider = Laby.references().blockColorProvider();

  @Override
  public void getColor(Chunk chunk, int[] colors) {
    int index = 0;

    Heightmap heightmap = chunk.heightmap(HeightmapType.WORLD_SURFACE);
    ClientWorld level = Laby.references().clientWorld();

    for (int x = 0; x < CHUNK_SIZE_X; x++) {
      for (int z = 0; z < CHUNK_SIZE_Z; z++) {
        int y = heightmap.getHeight(x, z) - 1;

        BlockState block = chunk.getBlockState(x, y, z);
        BlockState blockAbove = chunk.getBlockState(x, y, z);

        if (block.isLava()) {
          block = blockAbove;
          y++;
        }

        if (block.isWater()) {
          BlockState previousBlock = null;

          int minY = y - 25;
          for (int i = minY; i < y; i++) {
            BlockState blockState = chunk.getBlockState(x, i, z);
            if (blockState.isWater()) {
              break;
            }

            previousBlock = blockState;
            minY = i;
          }

          if (previousBlock != null) {
            ColorFormat colorFormat = ColorFormat.ARGB32;
            int blockColor = colorFormat.pack(
                this.getBlockColor(level, minY, previousBlock), 255);
            int waterColor = this.getBlockColor(level, y, block);

            waterColor = colorFormat.pack(waterColor, 150);
            blockColor = this.applyLight(blockColor, previousBlock);
            int blendedColors = ColorUtil.blendColors(blockColor, waterColor);
            colors[index] = blendedColors;
            index++;
            continue;
          }
        }

        colors[index] = this.getBlockColor(level, y, block);
        index++;
      }
    }
  }


  private int getBlockColor(ClientWorld level, int y, BlockState block) {
    ColorFormat format = ColorFormat.ARGB32;
    int blockColor;
    if (block.block().isAir()) {
      return SKY_COLOR;
    } else {
      int multiplier = this.blockColorProvider.getColorMultiplier(block);

      float multiplierRed = format.normalizedRed(multiplier);
      float multiplierGreen = format.normalizedGreen(multiplier);
      float multiplierBlue = format.normalizedBlue(multiplier);

      int blockTileColor = this.blockColorProvider.getColor(block);
      float red = format.normalizedRed(blockTileColor);
      float green = format.normalizedGreen(blockTileColor);
      float blue = format.normalizedBlue(blockTileColor);

      blockColor = format.pack(red * multiplierRed, green * multiplierGreen, blue * multiplierBlue,
          format.normalizedAlpha(blockTileColor));
    }

    return blockColor;
  }

  private int applyLight(int baseColor, BlockState block) {
    ColorFormat format = ColorFormat.ARGB32;

    int lightLevel = (block.getLightLevel() * 17);

    int red = format.red(baseColor);
    int green = format.green(baseColor);
    int blue = format.blue(baseColor);
    float[] values = java.awt.Color.RGBtoHSB(red, green, blue, null);

    float value = (lightLevel / 255.0F);
    values[2] *= 0.6F * value + 0.2F;

    return format.pack(Color.HSBtoRGB(values[0], values[1], values[2]), format.alpha(baseColor));
  }

  private float normalize(int value, int min, int max, float newMin, float newMax) {
    float oldRange = max - min;
    float newRange = newMax - newMin;
    return (((value - min) + newRange) / oldRange) + newMin;
  }
}
