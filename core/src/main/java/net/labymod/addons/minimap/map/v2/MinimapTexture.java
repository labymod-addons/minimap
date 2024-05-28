package net.labymod.addons.minimap.map.v2;

public class MinimapTexture extends DynamicTexture {

  public MinimapTexture() {
    super("minimap/level");
  }

  @Override
  public void tick() {
    this.image().fillRect(0, 0, this.getWidth(), this.getHeight(), 0);
    this.updateTexture();
  }
}
