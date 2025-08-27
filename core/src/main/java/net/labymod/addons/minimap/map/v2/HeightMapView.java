package net.labymod.addons.minimap.map.v2;

import net.labymod.addons.minimap.debug.MinimapDebugger;
import net.labymod.addons.minimap.debug.MinimapDebugger.TextureInfo;
import net.labymod.laby3d.api.opengl.GlResource;

public class HeightMapView extends MapView {

  public HeightMapView(String suffix) {
    super("minimap/heightmap_" + suffix);
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
    texture.setId(((GlResource) this.texture().deviceTexture()).getId());
    texture.setSize(this.getWidth(), this.getHeight());
  }
}
