package net.labymod.addons.minimap.map.v2;

import net.labymod.addons.minimap.debug.MinimapDebugger;
import net.labymod.addons.minimap.debug.MinimapDebugger.TextureInfo;

public class LightMapView extends MapView {

  public LightMapView(String suffix) {
    super("minimap/lightmap_" + suffix);
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

    TextureInfo texture = MinimapDebugger.LIGHTMAP_TEXTURE;
    //texture.setId(this.getId());
    texture.setSize(this.getWidth(), this.getHeight());
  }
}
