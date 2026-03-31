package net.labymod.addons.minimap.api.map;

import net.labymod.addons.minimap.api.util.Util;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.state.ClipShape;
import net.labymod.api.client.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public enum MinimapDisplayType {
  ROUND(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round")),
      radius -> ClipShape.circle(radius, radius, radius - Util.BORDER_PADDING),
      true
  ),
  ROUND_CLASSIC(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_classic")),
      radius -> ClipShape.circle(radius, radius, radius - Util.BORDER_PADDING),
      true
  ),
  ROUND_FUTURE_WIDE(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_future_wide")),
      radius -> ClipShape.circle(radius, radius, radius - Util.BORDER_PADDING),
      true
  ),
  ROUND_FUTURE_THIN(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_future_thin")),
      radius -> ClipShape.circle(radius, radius, radius - Util.BORDER_PADDING + 1),
      true
  ),
  ROUND_METAL_GOLD(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_metal_gold")),
      radius -> ClipShape.circle(radius, radius, radius - Util.BORDER_PADDING),
      true
  ),
  ROUND_SIMPLE_CHROME(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_simple_chrome")),
      radius -> ClipShape.circle(radius, radius, radius + 1),
      true
  ),
  ROUND_SIMPLE_METAL(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_simple_metal")),
      radius -> ClipShape.circle(radius, radius, radius + 1),
      true
  ),
  ROUND_WOOD(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/round_wood")),
      radius -> ClipShape.circle(radius, radius, radius - Util.BORDER_PADDING),
      true
  ),
  SQUARE(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/square")),
      radius -> {
        float size = radius * 2F - Util.BORDER_PADDING * 2F;
        return ClipShape.rect(Util.BORDER_PADDING, Util.BORDER_PADDING, size, size);
      }
  ),
  SQUARE_PARCHMENT(
      Stage.AFTER_TEXTURE,
      Icon.texture(Util.newThemeLocation("overlay/square_parchment")),
      radius -> {
        float size = radius * 2F - Util.BORDER_PADDING * 2F;
        return ClipShape.rect(Util.BORDER_PADDING, Util.BORDER_PADDING, size, size);
      }
  ),
  MINECRAFT_MAP_SQUARE(
      Stage.BEFORE_TEXTURE,
      Icon.texture(ResourceLocation.create("minecraft", "textures/map/map_background.png")),
      radius -> {
        float size = radius * 2F - Util.BORDER_PADDING * 2F;
        return ClipShape.rect(Util.BORDER_PADDING, Util.BORDER_PADDING, size, size);
      }
  );

  private final Stage stage;
  private final Icon icon;
  private final ClipShapeFactory clipShapeFactory;
  private final boolean circle;

  MinimapDisplayType(Stage stage, Icon icon, ClipShapeFactory clipShapeFactory) {
    this(stage, icon, clipShapeFactory, false);
  }

  MinimapDisplayType(Stage stage, Icon icon, ClipShapeFactory clipShapeFactory, boolean circle) {
    this.stage = stage;
    this.icon = icon;
    this.clipShapeFactory = clipShapeFactory;
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

  public boolean isCircle() {
    return this.circle;
  }

  @NotNull
  public ClipShape clipShape(float radius) {
    return this.clipShapeFactory.create(radius);
  }

  public static enum Stage {
    BEFORE_TEXTURE,
    AFTER_TEXTURE
  }

  public interface ClipShapeFactory {

    ClipShape create(float radius);
  }
}
