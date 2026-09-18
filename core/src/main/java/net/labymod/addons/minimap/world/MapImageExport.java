package net.labymod.addons.minimap.world;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.labymod.api.util.logging.Logging;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a rectangle of a saved map into a PNG, one sample per pixel. Reads one region at a time,
 * so memory use doesn't grow with the rectangle. Runs off the render thread.
 */
final class MapImageExport {

  private static final Logging LOGGER = Logging.getLogger();

  private MapImageExport() {
  }

  /**
   * @param blocksPerPixel how many blocks one pixel covers, below 1 when zoomed in
   */
  static void write(
      Path directory,
      MapMode mode,
      int biomeBlend,
      double minX, double minZ,
      double blocksPerPixel,
      int width, int height,
      Path file
  ) throws IOException {
    int[] pixels = new int[width * height];
    int minRegionX = (int) Math.floor(minX) >> MapRegion.BLOCK_SHIFT;
    int minRegionZ = (int) Math.floor(minZ) >> MapRegion.BLOCK_SHIFT;
    int maxRegionX = (int) Math.floor(minX + width * blocksPerPixel) >> MapRegion.BLOCK_SHIFT;
    int maxRegionZ = (int) Math.floor(minZ + height * blocksPerPixel) >> MapRegion.BLOCK_SHIFT;
    for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
      for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
        MapRegion region = readRegion(directory, regionX, regionZ);
        if (region != null) {
          draw(region, mode.overlay(region, MapRegion.BLOCKS, biomeBlend), minX, minZ, blocksPerPixel, width, height, pixels);
        }
      }
    }

    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, width, height, pixels, 0, width);
    Files.createDirectories(file.getParent());
    ImageIO.write(image, "png", file.toFile());
  }

  /**
   * @param overlay the region's overlay at one pixel per block, {@code null} for none
   */
  private static void draw(
      MapRegion region,
      int @Nullable [] overlay,
      double minX, double minZ,
      double blocksPerPixel,
      int width, int height,
      int[] pixels
  ) {
    int regionMinX = region.x() * MapRegion.BLOCKS;
    int regionMinZ = region.z() * MapRegion.BLOCKS;
    int fromX = firstPixel(regionMinX, minX, blocksPerPixel, width);
    int toX = firstPixel(regionMinX + MapRegion.BLOCKS, minX, blocksPerPixel, width);
    int fromY = firstPixel(regionMinZ, minZ, blocksPerPixel, height);
    int toY = firstPixel(regionMinZ + MapRegion.BLOCKS, minZ, blocksPerPixel, height);
    for (int y = fromY; y < toY; y++) {
      int localZ = local(minZ, y, blocksPerPixel, regionMinZ);
      for (int x = fromX; x < toX; x++) {
        int localX = local(minX, x, blocksPerPixel, regionMinX);
        if (!region.hasColumn(localX, localZ)) {
          continue;
        }

        int columnHeight = region.height(localX, localZ);
        int north = localZ > 0 && region.hasColumn(localX, localZ - 1)
            ? region.height(localX, localZ - 1)
            : columnHeight;
        int west = localX > 0 && region.hasColumn(localX - 1, localZ)
            ? region.height(localX - 1, localZ)
            : columnHeight;
        int color = MapShading.shade(region.color(localX, localZ), columnHeight, north, west);
        int overlayColor = overlay == null ? 0 : overlay[localZ * MapRegion.BLOCKS + localX];
        pixels[y * width + x] = overlayColor == 0 ? color : MapMode.blend(color, overlayColor);
      }
    }
  }

  /**
   * @return the first pixel whose center lies at or after the block
   */
  private static int firstPixel(int block, double min, double blocksPerPixel, int size) {
    int pixel = (int) Math.ceil((block - min) / blocksPerPixel - 0.5D);
    return Math.max(0, Math.min(size, pixel));
  }

  private static int local(double min, int pixel, double blocksPerPixel, int regionMin) {
    int block = (int) Math.floor(min + (pixel + 0.5D) * blocksPerPixel);
    return Math.max(0, Math.min(MapRegion.BLOCKS - 1, block - regionMin));
  }

  @Nullable
  private static MapRegion readRegion(Path directory, int x, int z) {
    try {
      return MapRegion.decode(x, z, Files.readAllBytes(MapRegionStore.regionFile(directory, x, z)));
    } catch (NoSuchFileException exception) {
      return null;
    } catch (IOException exception) {
      LOGGER.warn("Failed to read map region {}, {} for the export", x, z, exception);
      return null;
    }
  }
}
