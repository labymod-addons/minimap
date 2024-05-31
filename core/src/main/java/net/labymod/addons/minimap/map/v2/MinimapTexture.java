package net.labymod.addons.minimap.map.v2;

import net.labymod.addons.minimap.api.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.util.color.format.ColorFormat;
import net.labymod.api.util.math.MathHelper;
import java.util.function.Supplier;

public class MinimapTexture extends DynamicTexture {

  private static final int CHUNK_X = 16;
  private static final int CHUNK_Z = 16;

  private static final int SKY_COLOR = ColorFormat.ARGB32.pack(142,163, 255, 255);

  private final Supplier<MinimapHudWidgetConfig> config;
  private final MinimapBounds minimapBounds;
  private final MinimapChunkStorage storage;

  private int lastMidChunkX;
  private int lastMidChunkZ;

  public MinimapTexture(
      Supplier<MinimapHudWidgetConfig> config,
      MinimapChunkStorage storage,
      MinimapBounds minimapBounds
  ) {
    super("minimap/level");
    this.config = config;
    this.storage = storage;
    this.minimapBounds = minimapBounds;
  }

  @Override
  public void tick() {
    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    int zoom = this.config.get().zoom().get() * 10;
    int minX = (int) (player.getPosX() - zoom);
    int minZ = (int) (player.getPosZ() - zoom);
    int maxX = (int) (player.getPosX() + zoom);
    int maxZ = (int) (player.getPosZ() + zoom);

    int midChunkX = MathHelper.floor(player.getPosX()) >> 4;
    int midChunkZ = MathHelper.floor(player.getPosZ()) >> 4;

    boolean changed = false;
    if (this.lastMidChunkX != midChunkX && this.lastMidChunkZ != midChunkZ) {
      this.lastMidChunkX = midChunkX;
      this.lastMidChunkZ = midChunkZ;
      changed = true;
    }

    int minChunkX = minX >> 4;
    int minChunkZ = minZ >> 4;

    int maxChunkX = maxX >> 4;
    int maxChunkZ = maxZ >> 4;

    if (changed || this.storage.shouldProcess()) {
      this.resize(maxX - minX, maxZ - minZ);

      this.image().fillRect(0, 0, this.getWidth(), this.getHeight(), SKY_COLOR);
      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
          MinimapChunk chunk = this.storage.getChunk(chunkX, chunkZ);
          if (chunk == null) {
            continue;
          }

          chunk.compile();

          int offsetX = chunkX << 4;
          int offsetZ = chunkZ << 4;

          for (int pixelX = 0; pixelX < CHUNK_X; pixelX++) {
            for (int pixelZ = 0; pixelZ < CHUNK_Z; pixelZ++) {
              int destX = (offsetX + pixelX) - minX;
              int destZ = (offsetZ + pixelZ) - minZ;

              if (destX >= 0 && destX < this.image().getWidth() && destZ >= 0
                  && destZ < this.image().getHeight()) {
                int tileColor = chunk.getColor(pixelX, pixelZ);
                this.image().setARGB(destX, destZ, tileColor);
              }
            }
          }
        }
      }

      this.minimapBounds.update(minX, minZ, maxX, maxZ, 0);
      this.storage.processed();
      this.updateTexture();
    }

  }
}
