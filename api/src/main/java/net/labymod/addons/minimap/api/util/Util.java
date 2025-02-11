package net.labymod.addons.minimap.api.util;

import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.ThemeTextureLocation;

public final class Util {

  public static final String NAMESPACE = "labysminimap";

  public static ResourceLocation newDefaultNamespace(String path) {
    return ResourceLocation.create(NAMESPACE, path);
  }

  public static ThemeTextureLocation newThemeLocation(String path) {
    return ThemeTextureLocation.of(NAMESPACE + ":" + path);
  }
}
