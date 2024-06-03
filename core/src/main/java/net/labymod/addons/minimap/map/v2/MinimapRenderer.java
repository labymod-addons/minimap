package net.labymod.addons.minimap.map.v2;

import net.labymod.addons.minimap.api.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.api.Laby;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.event.EventBus;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.lifecycle.GameTickEvent;
import java.util.function.Supplier;

public final class MinimapRenderer {

  private final MinimapBounds minimapBounds = new MinimapBounds();
  private DynamicTexture minimapTexture;
  private DynamicTexture postEffectTexture;

  public MinimapRenderer(Supplier<MinimapHudWidgetConfig> config) {
    MinimapChunkStorage storage = new MinimapChunkStorage();
    this.minimapTexture = new MinimapTexture(config, storage, this.minimapBounds);
    this.postEffectTexture = new PostEffectTexture();
    EventBus eventBus = Laby.references().eventBus();
    eventBus.registerListener(this);
    eventBus.registerListener(storage);
  }

  @Subscribe
  public void onTick(GameTickEvent event) {
    if (event.phase() != Phase.POST) {
      return;
    }

    this.minimapTexture.tick();
    this.postEffectTexture.tick();
  }

  public MinimapBounds minimapBounds() {
    return this.minimapBounds;
  }

  public void render(Stack stack, float x, float y, float width, float height) {
    this.minimapTexture.render(stack, x, y, width, height);
    this.postEffectTexture.render(stack, x, y, width, height);
  }

  public void resize(int newWidth, int newHeight) {
    this.minimapTexture.resize(newWidth, newHeight);
    this.postEffectTexture.resize(newWidth, newHeight);
  }

  public void initialize() {
    this.minimapTexture.initialize();
    this.postEffectTexture.initialize();
  }
}
