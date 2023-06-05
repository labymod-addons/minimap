package net.labymod.addons.minimap.map;

import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.config.MinimapUpdateMethod;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.client.world.chunk.HeightmapType;
import net.labymod.api.util.ColorUtil;

public class MinimapGenerator {

  private final ChunkColorProvider chunkColorProvider = new ChunkColorProvider();

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
          // TODO lastChunkBounds?!
          if (player != null
              && (Math.abs(x1 - this.lastChunkBounds.getX1()) > 8
              || Math.abs(z1 - this.lastChunkBounds.getZ1()) > 8)) {
            this.teleportStarted = System.currentTimeMillis() + 1000;
          }

          this.lastBounds.update(x1, z1, x2, z2, depth);

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
            this.teleportStarted = System.currentTimeMillis() + 1000;
          }

          this.lastChunkBounds.update(chunkX1, chunkZ1, chunkX2, chunkZ2, depth);
          break;
      }
    }

    return true;
  }

  public GameImage getMinimap(
      boolean undergroundViewEnabled, int x1, int z1, int x2, int z2, int midX, int midY, int midZ) {
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
          this.chunkColorProvider.getOverworldPixels(chunkColors, chunk);
        }

        int cCX = cX << 4;
        int ccZ = cZ << 4;

        int tIndex = 0;
        for (int tX = 0; tX < 16; tX++) {
          for (int tZ = 0; tZ < 16; tZ++) {
            int destX = (cCX + tX) - x1;
            int destZ = (ccZ + tZ) - z1;

            if (destX >= 0 && destX < image.getWidth() && destZ >= 0 && destZ < image.getHeight()) {
              image.setRGBA(destX, destZ, ColorUtil.toValue(chunkColors[tIndex], 255));
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
