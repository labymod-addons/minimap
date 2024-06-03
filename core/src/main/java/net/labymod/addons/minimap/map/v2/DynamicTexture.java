package net.labymod.addons.minimap.map.v2;

import net.labymod.addons.minimap.util.Util;
import net.labymod.api.Laby;
import net.labymod.api.client.gfx.texture.GFXTextureFilter;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.api.client.resources.texture.concurrent.RefreshableTexture;

public abstract class DynamicTexture {

  private static final int DEFAULT_WIDTH = 16 * 24;
  private static final int DEFAULT_HEIGHT = 16 * 24;
  private final ResourceLocation location;
  private final Icon icon;
  private RefreshableTexture texture;
  private GameImage image;

  public DynamicTexture(String path) {
    this(path, DEFAULT_WIDTH, DEFAULT_HEIGHT);
  }

  public DynamicTexture(String path, int defaultWidth, int defaultHeight) {
    this.location = Util.newDefaultNamespace(path);
    this.icon = Icon.texture(this.location);
    this.image = GameImage.IMAGE_PROVIDER.createImage(defaultWidth, defaultHeight);
  }

  public abstract void tick();

  public void initialize() {
    if (this.texture != null) {
      this.texture.release();
    }

    this.texture = Laby.references().asynchronousTextureUploader().newRefreshableTexture(
        GFXTextureFilter.LINEAR,
        GFXTextureFilter.LINEAR
    );

    this.texture.bindTo(this.location);
    this.image().fillRect(0, 0, this.getWidth(), this.getHeight(), 0);
    this.updateTexture();
  }

  public void updateTexture() {
    this.texture.queueUpdate(this.image());
  }

  public void resize(int newWidth, int newHeight) {
    if (this.image.getWidth() == newWidth && this.image.getHeight() == newHeight) {
      return;
    }

    this.image.close();
    this.image = GameImage.IMAGE_PROVIDER.createImage(newWidth, newHeight);
  }

  public GameImage image() {
    return this.image;
  }

  public void render(Stack stack, float x, float y, float width, float height) {
    this.icon().render(stack, x, y, width, height);
  }

  public Icon icon() {
    return this.icon;
  }

  public int getWidth() {
    return this.image.getWidth();
  }

  public int getHeight() {
    return this.image.getHeight();
  }

}
