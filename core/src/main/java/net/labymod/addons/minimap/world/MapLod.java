package net.labymod.addons.minimap.world;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import net.labymod.addons.minimap.util.Compression;

/**
 * Pre-shaded overview of a region, {@value #SIZE}&times;{@value #SIZE} pixels with two blocks per
 * pixel. A {@value #SMALL_SIZE}&times;{@value #SMALL_SIZE} version for further out is derived from
 * it.
 */
public final class MapLod {

  public static final int SIZE = 256;
  public static final int SMALL_SIZE = SIZE / 2;
  private static final int SCALE = MapRegion.BLOCKS / SIZE;

  private MapLod() {
  }

  public static int[] build(MapRegion region) {
    int[] pixels = new int[SIZE * SIZE];
    for (int pixelZ = 0; pixelZ < SIZE; pixelZ++) {
      for (int pixelX = 0; pixelX < SIZE; pixelX++) {
        int count = 0;
        int red = 0;
        int green = 0;
        int blue = 0;
        for (int offsetZ = 0; offsetZ < SCALE; offsetZ++) {
          int localZ = pixelZ * SCALE + offsetZ;
          for (int offsetX = 0; offsetX < SCALE; offsetX++) {
            int localX = pixelX * SCALE + offsetX;
            if (!region.hasColumn(localX, localZ)) {
              continue;
            }

            int height = region.height(localX, localZ);
            int north = localZ > 0 && region.hasColumn(localX, localZ - 1)
                ? region.height(localX, localZ - 1)
                : height;
            int west = localX > 0 && region.hasColumn(localX - 1, localZ)
                ? region.height(localX - 1, localZ)
                : height;
            int color = MapShading.shade(region.color(localX, localZ), height, north, west);
            red += color >> 16 & 0xFF;
            green += color >> 8 & 0xFF;
            blue += color & 0xFF;
            count++;
          }
        }

        if (count > 0) {
          pixels[pixelZ * SIZE + pixelX] =
              0xFF000000 | red / count << 16 | green / count << 8 | blue / count;
        }
      }
    }

    return pixels;
  }

  public static int[] small(int[] pixels) {
    int[] small = new int[SMALL_SIZE * SMALL_SIZE];
    downsample(pixels, SIZE, small, SMALL_SIZE, 0, 0, SIZE / SMALL_SIZE);
    return small;
  }

  /**
   * Averages {@code factor}&times;{@code factor} source pixels into each target pixel, skipping
   * transparent ones.
   */
  public static void downsample(
      int[] source, int sourceSize,
      int[] target, int targetSize,
      int targetX, int targetY,
      int factor
  ) {
    int size = sourceSize / factor;
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        int count = 0;
        int red = 0;
        int green = 0;
        int blue = 0;
        for (int offsetY = 0; offsetY < factor; offsetY++) {
          int row = (y * factor + offsetY) * sourceSize + x * factor;
          for (int offsetX = 0; offsetX < factor; offsetX++) {
            int color = source[row + offsetX];
            if (color >>> 24 == 0) {
              continue;
            }

            red += color >> 16 & 0xFF;
            green += color >> 8 & 0xFF;
            blue += color & 0xFF;
            count++;
          }
        }

        if (count > 0) {
          target[(targetY + y) * targetSize + targetX + x] =
              0xFF000000 | red / count << 16 | green / count << 8 | blue / count;
        }
      }
    }
  }

  public static byte[] encode(int[] pixels) {
    ByteBuffer buffer = ByteBuffer.allocate(pixels.length * Integer.BYTES);
    buffer.asIntBuffer().put(pixels);
    return Compression.deflate(buffer.array());
  }

  public static int[] decode(byte[] data) throws IOException {
    byte[] raw;
    try {
      raw = Compression.inflate(data);
    } catch (DataFormatException exception) {
      throw new IOException("Corrupt map overview", exception);
    }

    if (raw.length != SIZE * SIZE * Integer.BYTES) {
      throw new IOException("Corrupt map overview");
    }

    int[] pixels = new int[SIZE * SIZE];
    ByteBuffer.wrap(raw).asIntBuffer().get(pixels);
    return pixels;
  }
}
