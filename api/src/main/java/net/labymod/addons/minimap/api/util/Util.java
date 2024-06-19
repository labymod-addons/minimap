package net.labymod.addons.minimap.api.util;

import net.labymod.api.client.resources.ResourceLocation;

public final class Util {

  public static final String NAMESPACE = "labysminimap";

  public static ResourceLocation newDefaultNamespace(String path) {
    return ResourceLocation.create(NAMESPACE, path);
  }

}
