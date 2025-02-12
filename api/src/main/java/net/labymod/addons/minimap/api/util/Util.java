package net.labymod.addons.minimap.api.util;

import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.ThemeTextureLocation;
import net.labymod.api.client.world.chunk.Chunk;

public final class Util {

  private static final long BITS = 32L;
  private static final long MASK = 0xFFFFFFFFL;
  public static final String NAMESPACE = "labysminimap";

  public static ResourceLocation newDefaultNamespace(String path) {
    return ResourceLocation.create(NAMESPACE, path);
  }

  public static ThemeTextureLocation newThemeLocation(String path) {
    return ThemeTextureLocation.of(NAMESPACE + ":" + path);
  }

  public static long getChunkId(Chunk chunk) {
    return getChunkId(chunk.getChunkX(), chunk.getChunkZ());
  }

  public static long getChunkId(int x, int z) {
    return x & MASK | (z & MASK) << BITS;
  }
}
