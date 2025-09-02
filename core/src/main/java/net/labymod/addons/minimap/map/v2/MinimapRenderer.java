package net.labymod.addons.minimap.map.v2;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.labymod.addons.minimap.api.config.MinimapConfigProvider;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.data.ChunkDataStorage;
import net.labymod.api.client.gui.screen.ScreenContext;

public final class MinimapRenderer {

  private final MinimapBounds minimapBounds = new MinimapBounds();
  private final MiniMapView minimapView;
  private final MapView postEffectTexture;

  public MinimapRenderer(MinimapConfigProvider configProvider, String suffix, ChunkDataStorage storage) {
    this.minimapView = new MiniMapView(configProvider, storage, this.minimapBounds, suffix);
    this.postEffectTexture = new PostEffectMapView(suffix);
  }

  public void tick() {
    this.minimapView.tick();
    this.postEffectTexture.tick();
  }

  public void setZoomSupplier(IntSupplier zoomSupplier) {
    this.minimapView.setZoomSupplier(zoomSupplier);
  }

  public MinimapBounds minimapBounds() {
    return this.minimapBounds;
  }

  public void renderMinimap(BooleanSupplier allowed, Runnable renderer) {
    if (!allowed.getAsBoolean()) {
      return;
    }

    renderer.run();
  }

  public void render(ScreenContext context, float x, float y, float width, float height) {
    this.minimapView.render(context, x, y, width, height);
    this.postEffectTexture.render(context, x, y, width, height);
  }

  public void resize(int newWidth, int newHeight) {
    this.minimapView.resize(newWidth, newHeight);
    this.postEffectTexture.resize(newWidth, newHeight);
  }

  public void initialize() {
    this.minimapView.initialize();
    this.postEffectTexture.initialize();
  }
}
