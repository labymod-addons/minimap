package net.labymod.addons.minimap.api.map;

import net.labymod.addons.minimap.api.util.Util;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.resources.texture.ThemeTextureLocation;
import net.labymod.api.util.Color;

public enum MinimapPlayerIcon {
  TRIANGLE(Icon.texture(ThemeTextureLocation.of(Util.NAMESPACE, "icons/player_triangle", 16, 16))),
  CROSSHAIR(Icon.texture(ThemeTextureLocation.of(Util.NAMESPACE, "icons/player_crosshair", 16, 16))),
  PLAYER_HEAD(null)
  ;

  private final Icon icon;

  MinimapPlayerIcon(Icon icon) {
    this.icon = icon;
  }

  public void render(ScreenContext context, float size, Color color) {
    float center = -size / 2;
    context.canvas().submitIcon(this.icon, center, center, size, size, false, color.get());
  }
}