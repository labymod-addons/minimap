package net.labymod.addons.minimap.api.renderer;

import net.labymod.addons.minimap.api.MinimapConfigProvider;
import net.labymod.api.reference.annotation.Referenceable;
import java.util.function.Function;

@Referenceable
public interface TileRendererDispatcher {

  <T> void register(TileRenderer<T> renderer);

  <T> void register(Function<MinimapConfigProvider, TileRenderer<T>> tileRendererFactory);

}
