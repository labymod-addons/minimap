package net.labymod.addons.minimap.world;

import org.jetbrains.annotations.Nullable;

/**
 * What the world map colors the terrain by. The map draws every mode but {@link #TERRAIN} as a
 * translucent overlay on the terrain.
 */
public enum MapMode {

  TERRAIN,
  BIOME,
  HEIGHT;

  private static final int OVERLAY_ALPHA = 0xC0;
  private static final int NO_COLOR = -1;
  private static final int CELLS = MapRegion.BLOCKS / MapRegion.BIOME_CELL_SIZE;
  private static final int[] HEIGHT_STOPS = {-64, 0, 40, 62, 64, 90, 130, 180, 240};
  private static final int[] HEIGHT_COLORS = {
      0x10142A, 0x1B2A4A, 0x2E5C9A, 0x4A90C8, 0x3E8E3E, 0x9CBF4A, 0xC9A15A, 0x8C6A4A, 0xF0F0F0
  };

  /**
   * @param size        pixels per side, a divisor of {@link MapRegion#BLOCKS}
   * @param blendRadius biome blend radius in blocks. Averages biome colors over it and fades
   *                    between biome cells. 0 keeps hard edges.
   * @return the overlay row by row, 0 where the region has no column. {@code null} for
   *     {@link #TERRAIN}.
   */
  public int @Nullable [] overlay(MapRegion region, int size, int blendRadius) {
    if (this == TERRAIN) {
      return null;
    }

    int[] pixels = new int[size * size];
    int step = MapRegion.BLOCKS / size;
    int offset = step / 2;
    int[] cells = this == BIOME ? biomeCells(region, blendRadius) : null;
    for (int y = 0; y < size; y++) {
      int localZ = y * step + offset;
      for (int x = 0; x < size; x++) {
        int localX = x * step + offset;
        if (!region.hasColumn(localX, localZ)) {
          continue;
        }

        int rgb;
        if (cells == null) {
          rgb = heightColor(region.height(localX, localZ));
        } else if (blendRadius == 0) {
          rgb = cells[(localZ / MapRegion.BIOME_CELL_SIZE) * CELLS + localX / MapRegion.BIOME_CELL_SIZE];
        } else {
          rgb = sampleCells(cells, localX, localZ);
        }

        if (rgb != NO_COLOR) {
          pixels[y * size + x] = OVERLAY_ALPHA << 24 | rgb;
        }
      }
    }

    return pixels;
  }

  /**
   * Draws a translucent overlay color onto an opaque one.
   */
  public static int blend(int base, int overlay) {
    int alpha = overlay >>> 24;
    int inverse = 255 - alpha;
    int red = ((overlay >> 16 & 0xFF) * alpha + (base >> 16 & 0xFF) * inverse) / 255;
    int green = ((overlay >> 8 & 0xFF) * alpha + (base >> 8 & 0xFF) * inverse) / 255;
    int blue = ((overlay & 0xFF) * alpha + (base & 0xFF) * inverse) / 255;
    return base & 0xFF000000 | red << 16 | green << 8 | blue;
  }

  /**
   * @return the average color of the cells within the blend radius, for every biome cell.
   *     {@link #NO_COLOR} where nothing is saved.
   */
  private static int[] biomeCells(MapRegion region, int blendRadius) {
    int[] colors = new int[CELLS * CELLS];
    int center = MapRegion.BIOME_CELL_SIZE / 2;
    for (int cellZ = 0; cellZ < CELLS; cellZ++) {
      for (int cellX = 0; cellX < CELLS; cellX++) {
        int localX = cellX * MapRegion.BIOME_CELL_SIZE + center;
        int localZ = cellZ * MapRegion.BIOME_CELL_SIZE + center;
        String biome = region.hasColumn(localX, localZ) ? region.biome(localX, localZ) : null;
        colors[cellZ * CELLS + cellX] = biome == null ? NO_COLOR : BiomeColors.color(biome);
      }
    }

    int radius = (blendRadius + MapRegion.BIOME_CELL_SIZE - 1) / MapRegion.BIOME_CELL_SIZE;
    if (radius == 0) {
      return colors;
    }

    int[] blended = new int[colors.length];
    for (int cellZ = 0; cellZ < CELLS; cellZ++) {
      for (int cellX = 0; cellX < CELLS; cellX++) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;
        for (int z = Math.max(0, cellZ - radius); z <= Math.min(CELLS - 1, cellZ + radius); z++) {
          for (int x = Math.max(0, cellX - radius); x <= Math.min(CELLS - 1, cellX + radius); x++) {
            int color = colors[z * CELLS + x];
            if (color == NO_COLOR) {
              continue;
            }

            red += color >> 16 & 0xFF;
            green += color >> 8 & 0xFF;
            blue += color & 0xFF;
            count++;
          }
        }

        blended[cellZ * CELLS + cellX] = count == 0
            ? NO_COLOR
            : red / count << 16 | green / count << 8 | blue / count;
      }
    }

    return blended;
  }

  /**
   * Interpolates between the four biome cells around a block. Skips cells without a color.
   */
  private static int sampleCells(int[] cells, int localX, int localZ) {
    float cellX = (localX - (MapRegion.BIOME_CELL_SIZE - 1) / 2.0F) / MapRegion.BIOME_CELL_SIZE;
    float cellZ = (localZ - (MapRegion.BIOME_CELL_SIZE - 1) / 2.0F) / MapRegion.BIOME_CELL_SIZE;
    int x0 = Math.max(0, (int) Math.floor(cellX));
    int z0 = Math.max(0, (int) Math.floor(cellZ));
    int x1 = Math.min(CELLS - 1, x0 + 1);
    int z1 = Math.min(CELLS - 1, z0 + 1);
    float fractionX = Math.max(0.0F, Math.min(1.0F, cellX - x0));
    float fractionZ = Math.max(0.0F, Math.min(1.0F, cellZ - z0));

    float red = 0.0F;
    float green = 0.0F;
    float blue = 0.0F;
    float weight = 0.0F;
    for (int corner = 0; corner < 4; corner++) {
      boolean right = (corner & 1) != 0;
      boolean bottom = (corner & 2) != 0;
      int color = cells[(bottom ? z1 : z0) * CELLS + (right ? x1 : x0)];
      if (color == NO_COLOR) {
        continue;
      }

      float cornerWeight = (right ? fractionX : 1.0F - fractionX) * (bottom ? fractionZ : 1.0F - fractionZ);
      red += (color >> 16 & 0xFF) * cornerWeight;
      green += (color >> 8 & 0xFF) * cornerWeight;
      blue += (color & 0xFF) * cornerWeight;
      weight += cornerWeight;
    }

    if (weight <= 0.0F) {
      return NO_COLOR;
    }

    return (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
  }

  private static int heightColor(int height) {
    if (height <= HEIGHT_STOPS[0]) {
      return HEIGHT_COLORS[0];
    }

    for (int index = 1; index < HEIGHT_STOPS.length; index++) {
      if (height < HEIGHT_STOPS[index]) {
        float progress = (height - HEIGHT_STOPS[index - 1])
            / (float) (HEIGHT_STOPS[index] - HEIGHT_STOPS[index - 1]);
        return mix(HEIGHT_COLORS[index - 1], HEIGHT_COLORS[index], progress);
      }
    }

    return HEIGHT_COLORS[HEIGHT_COLORS.length - 1];
  }

  private static int mix(int from, int to, float progress) {
    int red = (int) ((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * progress);
    int green = (int) ((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * progress);
    int blue = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * progress);
    return red << 16 | green << 8 | blue;
  }
}
