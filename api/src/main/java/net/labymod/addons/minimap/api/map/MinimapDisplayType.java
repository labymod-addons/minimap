package net.labymod.addons.minimap.api.map;

import net.labymod.addons.minimap.api.MinimapHudWidgetConfig;
import net.labymod.api.Laby;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.client.resources.texture.ThemeTextureLocation;
import org.jetbrains.annotations.NotNull;

public enum MinimapDisplayType {
  ROUND(
      Icon.texture(ThemeTextureLocation.of("labysminimap:overlay/round")),
      (stack, radius) -> Laby.labyAPI().renderPipeline().circleRenderer()
          .pos(radius, radius)
          .radius(radius - MinimapHudWidgetConfig.BORDER_PADDING)
          .color(Integer.MAX_VALUE)
          .render(stack)
  ),
  SQUARE(
      Icon.texture(ThemeTextureLocation.of("labysminimap:overlay/square")),
      (stack, radius) -> Laby.labyAPI().renderPipeline().rectangleRenderer()
          .pos(MinimapHudWidgetConfig.BORDER_PADDING, MinimapHudWidgetConfig.BORDER_PADDING)
          .size(radius * 2F - MinimapHudWidgetConfig.BORDER_PADDING * 2F)
          .color(Integer.MAX_VALUE)
          .render(stack)
  );

  private final Icon icon;
  private final MinimapStencil stencil;

  MinimapDisplayType(Icon icon, MinimapStencil stencil) {
    this.icon = icon;
    this.stencil = stencil;
  }

  @NotNull
  public Icon icon() {
    return this.icon;
  }

  @NotNull
  public MinimapStencil stencil() {
    return this.stencil;
  }

  public interface MinimapStencil {

    void render(Stack stack, float radius);
  }
}
