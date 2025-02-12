package net.labymod.addons.minimap.map.v2;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.config.MinimapConfiguration;
import net.labymod.addons.minimap.data.ChunkDataStorage;
import net.labymod.api.Laby;
import net.labymod.api.client.gfx.pipeline.GFXRenderPipeline;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.event.EventBus;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.lifecycle.GameTickEvent;

public final class MinimapRenderer {

  private final Supplier<MinimapConfiguration> configuration;
  private final MinimapBounds minimapBounds = new MinimapBounds();
  private final DynamicTexture minimapTexture;
  private final DynamicTexture postEffectTexture;

  public MinimapRenderer(Supplier<MinimapConfiguration> configuration) {
    this.configuration = configuration;
    ChunkDataStorage storage = new ChunkDataStorage();
    this.minimapTexture = new MinimapTexture(configuration, storage, this.minimapBounds);
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

  public void renderMinimap(BooleanSupplier allowed, Runnable renderer) {
    if (!allowed.getAsBoolean()) {
      return;
    }

    GFXRenderPipeline renderPipeline = Laby.references().gfxRenderPipeline();
    renderPipeline.renderToActivityTarget(target -> {
      renderPipeline.clear(target);
      renderer.run();
    });
    renderPipeline.clear(renderPipeline.getActivityRenderTarget());
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

  public MinimapConfiguration configuration() {
    return this.configuration.get();
  }

}
