package net.labymod.addons.minimap.map;

import net.labymod.api.client.world.block.BlockState;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.client.world.chunk.HeightmapType;
import net.labymod.api.util.color.format.ColorFormat;

public class ChunkColorProvider {

  private static final int WATER_COLOR = ColorFormat.ARGB32.pack(20, 20, 100, 255);
  private static final int SKY_COLOR = ColorFormat.ARGB32.pack(142, 163, 255);

  public void getOverworldPixels(int[] chunkColors, Chunk chunk) {
    int index = 0;

    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        int y = chunk.heightmap(HeightmapType.WORLD_SURFACE).getHeight(x, z) - 1;
        BlockState block = chunk.getBlockState(x, y, z);
        BlockState blockAbove = chunk.getBlockState(x, y + 1, z);

        if (block.isWater()) {
          block = blockAbove;
          ++y;
        }

        chunkColors[index] = this.getColor(block, y);
        index++;
      }
    }
  }

  public void getUndergroundPixels(int[] chunkColors, Chunk chunk, int depth) {
    int index = 0;

    BlockState[] heightArray = new BlockState[21];
    int backgroundColor = ColorFormat.ARGB32.pack(70, 70, 70, 255);

    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        int color = backgroundColor;

        int height = 1;
        for (int s = 0; s > -20; s--) {
          BlockState block = chunk.getBlockState(x & 15, depth + s, z & 15);
          height--;
          heightArray[s * -1] = block;
          if (!block.block().isAir()) {
            break;
          }
        }

        for (int h = height; h < 3; h++) {
          int blockY = depth + h;

          BlockState block =
              h < 0 ? heightArray[h * -1] : chunk.getBlockState(x & 15, blockY, z & 15);

          if (!block.block().isAir()) {
            color = this.getColor(block, blockY);
          }
        }

        chunkColors[index] = color;
        index++;
      }
    }
  }

  private int getColor(BlockState block, int y) {
    int color;

    if (block.block().isAir()) {
      color = SKY_COLOR;
    } else {
      color = block.getTopColor();
    }

    // Heightmap
    color = this.adjustPixelBrightness(color, y % 2 == 0 ? -6 : 0);

    // Light level
    color = this.adjustPixelBrightness(
        color,
        (int) (-50D + block.getLightLevel() * 5D + 20D)
    );

    return color;
  }

  private int adjustPixelBrightness(int color, int brightness) {
    ColorFormat colorFormat = ColorFormat.ARGB32;
    int r = colorFormat.red(color);
    int g = colorFormat.green(color);
    int b = colorFormat.blue(color);

    r = Math.min(Math.max(0, r + brightness), 255);
    g = Math.min(Math.max(0, g + brightness), 255);
    b = Math.min(Math.max(0, b + brightness), 255);

    return colorFormat.pack(r, g, b, colorFormat.alpha(color));
  }
}
