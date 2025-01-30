package net.labymod.addons.minimap.api.map;

import net.labymod.addons.minimap.api.MinimapHudWidgetConfig;
import net.labymod.api.Laby;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.ThemeTextureLocation;
import org.jetbrains.annotations.NotNull;

public enum MinimapDisplayType {
  ROUND(
      Stage.AFTER_TEXTURE,
      Icon.texture(ThemeTextureLocation.of("labysminimap:overlay/round")),
      (stack, radius) -> Laby.labyAPI().renderPipeline().circleRenderer()
          .pos(radius, radius)
          .radius(radius - MinimapHudWidgetConfig.BORDER_PADDING)
          .color(Integer.MAX_VALUE)
          .render(stack)
  ),
  SQUARE(
      Stage.AFTER_TEXTURE,
      Icon.texture(ThemeTextureLocation.of("labysminimap:overlay/square")),
      (stack, radius) -> Laby.labyAPI().renderPipeline().rectangleRenderer()
          .pos(MinimapHudWidgetConfig.BORDER_PADDING, MinimapHudWidgetConfig.BORDER_PADDING)
          .size(radius * 2F - MinimapHudWidgetConfig.BORDER_PADDING * 2F)
          .color(Integer.MAX_VALUE)
          .render(stack)
  ),
  MINECRAFT_MAP_SQUARE(
      Stage.BEFORE_TEXTURE,
      Icon.texture(ResourceLocation.create("minecraft", "textures/map/map_background.png")),
      (stack, radius) -> Laby.labyAPI().renderPipeline().rectangleRenderer()
          .pos(MinimapHudWidgetConfig.BORDER_PADDING, MinimapHudWidgetConfig.BORDER_PADDING)
          .size(radius * 2F - MinimapHudWidgetConfig.BORDER_PADDING * 2F)
          .color(Integer.MAX_VALUE)
          .render(stack)
  );

  private final Stage stage;
  private final Icon icon;
  private final MinimapStencil stencil;

  MinimapDisplayType(Stage stage, Icon icon, MinimapStencil stencil) {
    this.stage = stage;
    this.icon = icon;
    this.stencil = stencil;
  }

  @NotNull
  public Stage stage() {
    return this.stage;
  }

  @NotNull
  public Icon icon() {
    return this.icon;
  }

  @NotNull
  public MinimapStencil stencil() {
    return this.stencil;
  }

  public static enum Stage {
    BEFORE_TEXTURE,
    AFTER_TEXTURE
  }

  public interface MinimapStencil {

    void render(Stack stack, float radius);
  }
}
