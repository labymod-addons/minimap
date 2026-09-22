package net.labymod.addons.minimap.map.v2.texture;

import net.labymod.addons.minimap.api.util.Util;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.DynamicTexture;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.laby3d.api.textures.DeviceTexture;
import net.labymod.laby3d.api.textures.DeviceTextureView;
import net.labymod.laby3d.api.textures.SamplerDescription;
import net.labymod.laby3d.api.textures.SamplerDescription.Filter;

public class SectionTexture {

  private static final IdProvider ID_PROVIDER = (suffix, x, z) -> Util.newDefaultNamespace(
      "texture/minimap/section_" + x + "_" + z + "_" + suffix
  );
  public static final int CHUNK_SIZE_X = 16;
  public static final int CHUNK_SIZE_Z = 16;

  private final int width;
  private final int height;
  private final DynamicTexture texture;

  public SectionTexture(String suffix, int x, int z, int size) {
    this.width = CHUNK_SIZE_X * size;
    this.height = CHUNK_SIZE_Z * size;
    this.texture = new DynamicTexture(
        ID_PROVIDER.apply(suffix, x, z),
        this.width, this.height,
        SamplerDescription.builder()
            .setFilter(Filter.NEAREST)
            .build()
    );
  }

  public int width() {
    return this.width;
  }

  public int height() {
    return this.height;
  }

  public GameImage image() {
    GameImage image = this.texture.getImage();
    if (image == null) {
      throw new NullPointerException("The image of the texture is null");
    }

    return image;
  }

  public void clearTexture(int clearColor) {
    this.image().fillRect(0, 0, this.width, this.height, clearColor);
  }

  public void updateTexture() {
    this.texture.upload();
  }

  public void dispose() {
    this.texture.release();
    this.texture.close();
  }

  public DeviceTexture deviceTexture() {
    return this.texture.deviceTexture();
  }

  public DeviceTextureView deviceTextureView() {
    return this.texture.deviceTextureView();
  }

  public enum Variant {
    COLOR("color"),
    HEIGHTMAP("heightmap"),
    LIGHTMAP("lightmap"),
    ;

    public static final Variant[] VALUES = values();
    private final String suffix;

    Variant(String suffix) {
      this.suffix = suffix;
    }

    public String suffix() {
      return this.suffix;
    }
  }

  private interface IdProvider {

    ResourceLocation apply(String suffix, int x, int z);

  }
}
