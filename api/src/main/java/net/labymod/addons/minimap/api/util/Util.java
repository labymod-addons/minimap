package net.labymod.addons.minimap.api.util;

import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.ThemeTextureLocation;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.util.color.format.ColorFormat;

public final class Util {

  public static final String NAMESPACE = "labysminimap";
  public static final float BORDER_PADDING = 5.0F;

  public static final float SHADOW_OFFSET = 0.5F;
  public static final float SHADOW_SCALE = 0.25F;
  public static final int SHADOW_COLOR = applyShadowColor(0xFFffffff);
  public static final float MINIMAP_SCALE = 0.95F;


  private static final long BITS = 32L;
  private static final long MASK = 0xFFFFFFFFL;

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

  public static int applyShadowColor(int argb) {
    return ColorFormat.ARGB32.mul(argb, SHADOW_SCALE, SHADOW_SCALE, SHADOW_SCALE, 1.0F);
  }
}
