package net.labymod.addons.minimap.map.v2;

import net.labymod.addons.minimap.debug.MinimapDebugger;
import net.labymod.addons.minimap.debug.MinimapDebugger.TextureInfo;

public class HeightmapTexture extends DynamicTexture {

  public HeightmapTexture() {
    super("minimap/heightmap");
  }

  @Override
  public void tick() {

  }

  @Override
  public void reInitialize() {

  }

  @Override
  public void updateTexture() {
    super.updateTexture();
    TextureInfo texture = MinimapDebugger.HEIGHTMAP_TEXTURE;
    //texture.setId(this.getId());
    texture.setSize(this.getWidth(), this.getHeight());
  }
}
