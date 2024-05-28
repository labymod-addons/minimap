package net.labymod.addons.minimap.util;

import net.labymod.api.client.resources.ResourceLocation;

public final class Util {

  private static final String NAMESPACE = "minimap";

  public static ResourceLocation newDefaultNamespace(String path) {
    return ResourceLocation.create(NAMESPACE, path);
  }

}
