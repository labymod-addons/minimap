package net.labymod.addons.minimap.map.v2.texture;

import java.util.EnumMap;
import net.labymod.addons.minimap.map.v2.texture.SectionTexture.Variant;

public class CompositeSectionTexture {

  private final EnumMap<SectionTexture.Variant, SectionTexture> textures;
  private final int x;
  private final int z;

  public CompositeSectionTexture(int x, int z, int size) {
    this.x = x;
    this.z = z;
    this.textures = new EnumMap<>(Variant.class);
    for (Variant variant : Variant.VALUES) {
      this.textures.put(variant, new SectionTexture(variant, x, z, size));
    }
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

  public void clearTexture(Variant variant, int clearColor) {
    this.getTexture(variant).clearTexture(clearColor);
  }

  public void updateTexture() {
    for (SectionTexture texture : this.textures.values()) {
      texture.updateTexture();
    }
  }

}
