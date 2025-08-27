package net.labymod.addons.minimap.api.map;

import net.labymod.addons.minimap.api.util.Util;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public enum MinimapDisplayType {
  ROUND(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round")),
      (context, radius) -> {
        context.canvas().submitCircle(
            radius, radius,
            radius - Util.BORDER_PADDING,
            Integer.MAX_VALUE
        );
      }
  ),
  SQUARE(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/square")),
      (context, radius) -> {
        float size = radius * 2F - Util.BORDER_PADDING * 2F;
        context.canvas().submitRelativeRect(
            Util.BORDER_PADDING, Util.BORDER_PADDING,
            size, size,
            Integer.MAX_VALUE
        );
      }
  ),
  MINECRAFT_MAP_SQUARE(
      Stage.BEFORE_TEXTURE,
      Icon.texture(ResourceLocation.create("minecraft", "textures/map/map_background.png")),
      (context, radius) -> {
        float size = radius * 2F - Util.BORDER_PADDING * 2F;
        context.canvas().submitRelativeRect(
            Util.BORDER_PADDING, Util.BORDER_PADDING,
            size, size,
            Integer.MAX_VALUE
        );
      }
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

  public void renderStencil(ScreenContext context, float radius) {
    this.stencil.render(context, radius);
  }

  public static enum Stage {
    BEFORE_TEXTURE,
    AFTER_TEXTURE
  }

  public interface MinimapStencil {

    void render(ScreenContext context, float radius);
  }
}
