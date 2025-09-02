package net.labymod.addons.minimap.api.renderer;

import java.util.function.Function;
import net.labymod.addons.minimap.api.config.MinimapConfigProvider;
import net.labymod.api.reference.annotation.Referenceable;

@Referenceable
public interface TileRendererDispatcher {

  <T> void register(TileRenderer<T> renderer);

  <T> void register(Function<MinimapConfigProvider, TileRenderer<T>> tileRendererFactory);

}
