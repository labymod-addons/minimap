package net.labymod.addons.minimap.map.v2;

import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.api.util.ColorUtil;

public class MinimapTexture extends DynamicTexture {

  private static final int CHUNK_X = 16;
  private static final int CHUNK_Z = 16;
  private static final int CHUNK_SIZE = 16;
  private final MinimapChunkStorage generator;

  public MinimapTexture(MinimapChunkStorage generator) {
    super("minimap/level");
    this.generator = generator;
  }

  @Override
  public void tick() {

    int[] zero = new int[] {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, -1, -1, 0, 0, 0, 0, 0, -1, -1, 0, 0, 0, 0, 0, 0,
        -1, 0, 0, -1, 0, 0, 0, -1, 0, 0, -1, 0, 0, 0, 0, 0,
        -1, 0, 0, -1, 0, 0, 0, -1, 0, 0, -1, 0, 0, 0, 0, 0,
        -1, 0, 0, -1, 0, 0, 0, -1, 0, 0, -1, 0, 0, 0, 0, 0,
        0, -1, -1, 0, 0, -1, 0, 0, -1, -1, 0, 0, 0, 0, 0, 0,
    };


    if (this.generator.shouldProcess()) {

      int minX = this.generator.getMinX();
      int minZ = this.generator.getMinZ();

      this.resize(16 * 18, 16 * 18);

      this.image().fillRect(0, 0, this.getWidth(), this.getHeight(), 0xFF000000);
      for (MinimapChunk chunk : this.generator.getChunks()) {
        chunk.compile();
        for (int x = 0; x < CHUNK_X; x++) {
          for (int z = 0; z < CHUNK_Z; z++) {
            int pixelX = ((chunk.getX() + Math.abs(minX)) * CHUNK_SIZE) + x;
            int pixelY = ((chunk.getZ() + Math.abs(minZ)) * CHUNK_SIZE) + z;
            try {
              GameImage image = this.image();
              int tileColor = 0xFF000000 | chunk.getColor(x, z);
              if (chunk.getX() == 0 && chunk.getZ() == 0) {
                tileColor = ColorUtil.blendColors(tileColor, zero[x * 16 + z]);
              }

              image.setARGB(pixelX, pixelY, tileColor);
            } catch (Throwable throwable) {

            }
          }
        }
      }

      this.generator.processed();
      this.updateTexture();
    }

  }
}
