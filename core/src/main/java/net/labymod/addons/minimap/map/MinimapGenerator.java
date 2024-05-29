package net.labymod.addons.minimap.map;

import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.api.map.MinimapUpdateMethod;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.client.world.chunk.HeightmapType;
import net.labymod.api.util.ColorUtil;
import net.labymod.api.util.color.format.ColorFormat;

public class MinimapGenerator {

  private final ChunkColorProvider chunkColorProvider = new ChunkColorProvider();
  private final ChunkTileColorProvider colorProvider = new OverworldChunkTileColorProvider();

  private final MinimapBounds lastBounds = new MinimapBounds();
  private final MinimapBounds lastChunkBounds = new MinimapBounds();

  private int highestBlockY;

  private boolean underground;

  private long teleportStarted = -1L;

  private GameImage image;
  private boolean imageAvailable;

  public boolean isUpdateNecessary(
      ClientPlayer player,
      int x1,
      int z1,
      int x2,
      int z2,
      int depth,
      MinimapUpdateMethod updateMethod) {
    if (true) {
      return false;
    }

    int depthRange = this.underground ? 1 : 5;

    // Trigger on teleport
    boolean skipUpdateCheck = false;
    if (this.teleportStarted != -1 && this.teleportStarted < System.currentTimeMillis()) {
      skipUpdateCheck = true;
      this.teleportStarted = -1;
    }

    if (!skipUpdateCheck) {
      switch (updateMethod) {
        case BLOCK_TRIGGER:
          if (this.lastBounds.equals(x1, z1, x2, z2)
              && this.lastBounds.equalsDepthRange(depth, depthRange)
              && this.imageAvailable) {
            return false;
          }

          // Trigger on teleport
          if (player != null
              && (Math.abs(x1 - this.lastBounds.getX1()) > 128
              || Math.abs(z1 - this.lastBounds.getZ1()) > 128)) {
            this.teleportStarted = System.currentTimeMillis() + 2500;
          }

          break;
        case CHUNK_TRIGGER:
          int chunkX1 = x1 >> 4;
          int chunkZ1 = z1 >> 4;
          int chunkX2 = x2 >> 4;
          int chunkZ2 = z2 >> 4;

          if (this.lastChunkBounds.equals(chunkX1, chunkZ1, chunkX2, chunkZ2)
              && this.lastChunkBounds.equalsDepthRange(depth, 5)
              && this.imageAvailable) {
            return false;
          }

          // Trigger on teleport
          if (player != null && (Math.abs(chunkX1 - this.lastChunkBounds.getX1()) > 2
              || Math.abs(chunkZ1 - this.lastChunkBounds.getZ1()) > 2)) {
            this.teleportStarted = System.currentTimeMillis() + 2500;
          }

          this.lastChunkBounds.update(chunkX1, chunkZ1, chunkX2, chunkZ2, depth);
          break;
      }
    }

    this.lastBounds.update(x1, z1, x2, z2, depth);
    return true;
  }

  public GameImage getMinimap(
      boolean undergroundViewEnabled,
      int x1,
      int z1,
      int x2,
      int z2,
      int midX,
      int midY,
      int midZ,
      boolean hasBlindness
  ) {
    ClientWorld world = Laby.labyAPI().minecraft().clientWorld();

    if (world == null) {
      return null;
    }

    if (this.image == null
        || this.image.getWidth() != x2 - x1
        || this.image.getHeight() != z2 - z1) {
      if (this.image != null) {
        this.image.close();
      }

      this.image = Laby.references().gameImageProvider().createImage(x2 - x1, z2 - z1);
    }

    GameImage image = this.image;

    int chunkX1 = x1 >> 4;
    int chunkZ1 = z1 >> 4;
    int chunkX2 = x2 >> 4;
    int chunkZ2 = z2 >> 4;

    int midChunkX = midX >> 4;
    int midChunkZ = midZ >> 4;

    int midInChunkX = midX & 15;
    int midInChunkZ = midZ & 15;

    this.underground = undergroundViewEnabled && (this.highestBlockY - midY) > 10;

    int[] chunkColors = new int[16 * 16];

    for (int cX = chunkX1; cX <= chunkX2; cX++) {
      for (int cZ = chunkZ1; cZ <= chunkZ2; cZ++) {
        Chunk chunk = world.getChunk(cX, cZ);

        if (this.underground) {
          this.chunkColorProvider.getUndergroundPixels(chunkColors, chunk, midY);
        } else {
          this.colorProvider.getColor(chunk, chunkColors);
        }

        int cCX = cX << 4;
        int ccZ = cZ << 4;

        int tIndex = 0;
        for (int tX = 0; tX < 16; tX++) {
          for (int tZ = 0; tZ < 16; tZ++) {
            int destX = (cCX + tX) - x1;
            int destZ = (ccZ + tZ) - z1;

            if (destX >= 0 && destX < image.getWidth() && destZ >= 0 && destZ < image.getHeight()) {
              int chunkColor = chunkColors[tIndex];

              int alpha = ColorFormat.ARGB32.alpha(chunkColor);

              if (alpha <= 51) {
                chunkColor = ColorFormat.ARGB32.pack(chunkColor, 255);
              }

              image.setARGB(destX, destZ, chunkColor);
            }

            tIndex++;
          }
        }

        if (cX == midChunkX && cZ == midChunkZ) {
          this.highestBlockY = chunk
              .heightmap(HeightmapType.WORLD_SURFACE)
              .getHeight(midInChunkX, midInChunkZ);
        }
      }
    }

    int width = image.getWidth();
    int height = image.getHeight();
    int centerX = (width / 2);
    int centerY = (height / 2);

    float radius = 3;
    float smoothing = radius - 1.0F;

    float fullRadius = radius + smoothing;

    try {
      if (hasBlindness) {
        for (int y = 0; y < width; y++) {
          for (int x = 0; x < height; x++) {

            float distX = centerX - x - 1;
            float distY = centerY - y;
            float distSquared = (float) Math.sqrt(distX * distX + distY * distY);

            if (distSquared > fullRadius) {
              image.setARGB(x, y, 0xFF000000);
            } else if (distSquared > radius) {

              float distanceFromEdge = distSquared - radius;
              float proportionInside = (Math.min(distanceFromEdge, smoothing) / smoothing);

              int argb = image.getARGB(x, y);
              ColorFormat format = ColorFormat.ARGB32;

              int packedArgb = format.pack(0, 0, 0, proportionInside);
              image.setARGB(x, y, ColorUtil.blendColors(argb, packedArgb));
            }
          }
        }
      }
    } catch (Throwable throwable) {
      System.out.println("Failed");
    }

    this.imageAvailable = true;
    return image;
  }

  public void reset() {
    this.imageAvailable = false;
    if (this.image != null) {
      this.image.close();
      this.image = null;
    }
  }

  public int getHighestBlockY() {
    return this.highestBlockY;
  }

  public boolean isImageAvailable() {
    return this.imageAvailable;
  }
}
