package net.labymod.addons.minimap.map.v2.renderer;

import net.labymod.addons.minimap.api.MinimapConfigProvider;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent;
import net.labymod.addons.minimap.api.renderer.TileRenderer;
import net.labymod.addons.minimap.api.renderer.TileRendererDispatcher;
import net.labymod.api.event.Subscribe;
import net.labymod.api.models.Implements;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Singleton
@Implements(TileRendererDispatcher.class)
public class DefaultTileRendererDispatcher implements TileRendererDispatcher {

  private final List<TileRenderer<?>> renderers;
  private final MinimapConfigProvider configProvider;

  public DefaultTileRendererDispatcher(MinimapConfigProvider configProvider) {
    this.configProvider = configProvider;
    this.renderers = new ArrayList<>();

    this.register(PlayerTileRenderer::new);
    this.register(EntityTileRenderer::new);
  }

  @Subscribe
  public void onRender(MinimapRenderEvent event) {
    for (TileRenderer<?> renderer : this.renderers) {
      renderer.renderTile(event);
    }
  }

  @Override
  public <T> void register(TileRenderer<T> renderer) {
    this.renderers.add(renderer);
  }

  @Override
  public <T> void register(Function<MinimapConfigProvider, TileRenderer<T>> tileRendererFactory) {
    this.renderers.add(tileRendererFactory.apply(this.configProvider));
  }
}
