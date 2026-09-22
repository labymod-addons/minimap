package net.labymod.addons.minimap.map.v2.texture;

import java.util.Arrays;
import java.util.EnumMap;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.map.v2.texture.SectionTexture.Variant;
import net.labymod.api.client.resources.texture.DynamicTexture;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.laby3d.api.textures.DeviceTextureView;
import net.labymod.laby3d.api.textures.SamplerDescription;
import net.labymod.laby3d.api.textures.SamplerDescription.Filter;

public class CompositeSectionTexture {

  private static final long FADE_DURATION_NANOS = 300_000_000L;
  private static final long HIDDEN = Long.MIN_VALUE;
  private static final long PENDING = Long.MIN_VALUE + 1;

  private final EnumMap<SectionTexture.Variant, SectionTexture> textures;
  private final EnumMap<SectionTexture.Variant, SectionTexture> previousTextures;
  private final DynamicTexture fadeTexture;
  private final long[] fadeStarts;
  private final int size;
  private final int x;
  private final int z;
  private boolean fading;
  private boolean previousDirty;

  public CompositeSectionTexture(int x, int z, int size) {
    this.x = x;
    this.z = z;
    this.size = size;
    this.textures = new EnumMap<>(Variant.class);
    this.previousTextures = new EnumMap<>(Variant.class);
    for (Variant variant : Variant.VALUES) {
      this.textures.put(variant, new SectionTexture(variant.suffix(), x, z, size));
      this.previousTextures.put(
          variant,
          new SectionTexture(variant.suffix() + "_previous", x, z, size)
      );
    }

    this.fadeStarts = new long[size * size];
    Arrays.fill(this.fadeStarts, HIDDEN);
    this.fadeTexture = new DynamicTexture(
        Util.newDefaultNamespace("texture/minimap/section_" + x + "_" + z + "_fade"),
        size, size,
        SamplerDescription.builder()
            .setFilter(Filter.NEAREST)
            .build()
    );
  }

  public int x() {
    return this.x;
  }

  public int z() {
    return this.z;
  }

  public SectionTexture getTexture(Variant variant) {
    return this.textures.get(variant);
  }

  public SectionTexture getPreviousTexture(Variant variant) {
    return this.previousTextures.get(variant);
  }

  public DeviceTextureView fadeTextureView() {
    return this.fadeTexture.deviceTextureView();
  }

  public void clearTexture(Variant variant, int clearColor) {
    this.getTexture(variant).clearTexture(clearColor);
  }

  /**
   * Frees the GPU and CPU memory of every texture. The section can't be used afterwards.
   */
  public void dispose() {
    for (SectionTexture texture : this.textures.values()) {
      texture.dispose();
    }

    for (SectionTexture texture : this.previousTextures.values()) {
      texture.dispose();
    }

    this.fadeTexture.release();
    this.fadeTexture.close();
  }

  public void updateTexture() {
    for (SectionTexture texture : this.textures.values()) {
      texture.updateTexture();
    }
  }

  /**
   * On its next build, each visible chunk crossfades from its current pixels to the new ones.
   */
  public void beginTransition() {
    for (int index = 0; index < this.fadeStarts.length; index++) {
      if (this.fadeStarts[index] != HIDDEN) {
        this.fadeStarts[index] = PENDING;
      }
    }

    this.fading = true;
  }

  /**
   * Call this before writing the chunk pixels. A pending chunk copies its old pixels into the
   * previous textures here.
   */
  public void beginChunkFade(int localChunkX, int localChunkZ) {
    int index = localChunkZ * this.size + localChunkX;
    long state = this.fadeStarts[index];
    if (state == PENDING) {
      int pixelX = localChunkX * SectionTexture.CHUNK_SIZE_X;
      int pixelZ = localChunkZ * SectionTexture.CHUNK_SIZE_Z;
      for (Variant variant : Variant.VALUES) {
        this.getPreviousTexture(variant).image().drawImage(
            this.getTexture(variant).image(),
            pixelX, pixelZ,
            pixelX, pixelZ,
            SectionTexture.CHUNK_SIZE_X, SectionTexture.CHUNK_SIZE_Z
        );
      }

      this.previousDirty = true;
    } else if (state != HIDDEN) {
      return;
    }

    this.fadeStarts[index] = System.nanoTime();
    this.fading = true;
  }

  public void updateFade(long now) {
    if (this.previousDirty) {
      for (SectionTexture texture : this.previousTextures.values()) {
        texture.updateTexture();
      }

      this.previousDirty = false;
    }

    if (!this.fading) {
      return;
    }

    GameImage image = this.fadeTexture.getImage();
    boolean fading = false;
    for (int localChunkZ = 0; localChunkZ < this.size; localChunkZ++) {
      for (int localChunkX = 0; localChunkX < this.size; localChunkX++) {
        long start = this.fadeStarts[localChunkZ * this.size + localChunkX];
        int alpha;
        if (start == HIDDEN) {
          alpha = 0;
        } else if (start == PENDING) {
          alpha = 255;
        } else {
          float progress = (now - start) / (float) FADE_DURATION_NANOS;
          progress = Math.max(0.0F, Math.min(progress, 1.0F));
          if (progress < 1.0F) {
            fading = true;
          }

          alpha = (int) (progress * progress * (3.0F - 2.0F * progress) * 255.0F);
        }

        image.setARGB(localChunkX, localChunkZ, alpha << 24 | alpha << 16 | alpha << 8 | alpha);
      }
    }

    this.fadeTexture.upload();
    this.fading = fading;
  }

}
