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
      },
      true
  ),
  ROUND_CLASSIC(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_classic")),
      (context, radius) -> {
        context.canvas().submitCircle(
            radius, radius,
            radius - Util.BORDER_PADDING,
            Integer.MAX_VALUE
        );
      },
      true
  ),
  ROUND_FUTURE_WIDE(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_future_wide")),
      (context, radius) ->
      {
        context.canvas().submitCircle(
            radius, radius,
            radius - Util.BORDER_PADDING,
            Integer.MAX_VALUE
        );
      },
      true
  ),
  ROUND_FUTURE_THIN(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_future_thin")),
      (context, radius) ->
      {
        context.canvas().submitCircle(
            radius, radius,
            radius - Util.BORDER_PADDING + 1,
            Integer.MAX_VALUE
        );
      },
      true
  ),
  ROUND_METAL_GOLD(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_metal_gold")),
      (context, radius) -> {
        context.canvas().submitCircle(
            radius, radius,
            radius - Util.BORDER_PADDING,
            Integer.MAX_VALUE
        );
      },
      true
  ),
  ROUND_SIMPLE_CHROME(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_simple_chrome")),
      (context, radius) -> {
        context.canvas().submitCircle(
            radius, radius,
            radius + 1,
            Integer.MAX_VALUE
        );
      },
      true
  ),
  ROUND_SIMPLE_METAL(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_simple_metal")),
      (context, radius) -> {
        context.canvas().submitCircle(
            radius, radius,
            radius + 1,
            Integer.MAX_VALUE
        );
      },
      true
  ),
  ROUND_WOOD(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_wood")),
      (context, radius) -> {
        context.canvas().submitCircle(
            radius, radius,
            radius - Util.BORDER_PADDING,
            Integer.MAX_VALUE
        );
      },
      true
  ),
  SQUARE(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/square")),
      (context, radius) ->
      {
        float size = radius * 2F - Util.BORDER_PADDING * 2F;
        context.canvas().submitRelativeRect(
            Util.BORDER_PADDING, Util.BORDER_PADDING,
            size, size,
            Integer.MAX_VALUE
        );
      }
  ),
  SQUARE_PARCHMENT(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/square_parchment")),
      (context, radius) ->
      {
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
      (context, radius) ->
      {
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
  private final boolean circle;

  MinimapDisplayType(Stage stage, Icon icon, MinimapStencil stencil) {
    this(stage, icon, stencil, false);
  }

  MinimapDisplayType(Stage stage, Icon icon, MinimapStencil stencil, boolean circle) {
    this.stage = stage;
    this.icon = icon;
    this.stencil = stencil;
    this.circle = circle;
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

  public boolean isCircle() {
    return this.circle;
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
