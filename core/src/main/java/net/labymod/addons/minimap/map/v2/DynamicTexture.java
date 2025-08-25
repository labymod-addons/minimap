package net.labymod.addons.minimap.map.v2;

import net.labymod.addons.minimap.MinimapRenderStates;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.api.Laby;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.states.GuiTextureSet;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.GameImage;

public abstract class DynamicTexture {

  private static final int DEFAULT_WIDTH = 16 * 24;
  private static final int DEFAULT_HEIGHT = 16 * 24;
  private final ResourceLocation location;
  private boolean initialized;
  private net.labymod.api.client.resources.texture.DynamicTexture texture;
  private GameImage image;

  public DynamicTexture(String path) {
    this(path, DEFAULT_WIDTH, DEFAULT_HEIGHT);
  }

  public DynamicTexture(String path, int defaultWidth, int defaultHeight) {
    this.location = Util.newDefaultNamespace(path);
    this.image = GameImage.IMAGE_PROVIDER.createImage(defaultWidth, defaultHeight);
  }

  public abstract void tick();

  public abstract void reInitialize();

  public void initialize() {
    if (this.initialized) {
      this.reInitialize();
      return;
    }

    this.initialized = true;

    this.texture = new net.labymod.api.client.resources.texture.DynamicTexture(
        this.location,
        this.image
    );
    Laby.references().textureRepository().register(this.location, this.texture);
    this.clearImage(0);
    this.updateTexture();
  }

  public void updateTexture() {
    this.texture.upload();
  }

  public void resize(int newWidth, int newHeight) {
    if (this.image.getWidth() == newWidth && this.image.getHeight() == newHeight) {
      return;
    }

    this.image.close();
    this.image = GameImage.IMAGE_PROVIDER.createImage(newWidth, newHeight);
    this.texture.setImageAndUpload(this.image);
  }

  public GameImage image() {
    return this.image;
  }

  public void render(ScreenContext context, float x, float y, float width, float height) {
    context.canvas().submitGuiBlit(
        MinimapRenderStates.GUI_TEXTURED,
        GuiTextureSet.single(this.texture.deviceTextureView()),
        x, y, width, height,
        0.0F, 0.0F, 1.0F, 1.0F,
        -1
    );
  }

  public ResourceLocation location() {
    return this.location;
  }

  public void clearImage(int clearColor) {
    this.image().fillRect(0, 0, this.getWidth(), this.getHeight(), clearColor);
  }

  public int getWidth() {
    return this.image.getWidth();
  }

  public int getHeight() {
    return this.image.getHeight();
  }

  public net.labymod.api.client.resources.texture.DynamicTexture texture() {
    return this.texture;
  }
}
