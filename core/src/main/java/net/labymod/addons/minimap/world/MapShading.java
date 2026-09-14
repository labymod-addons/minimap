package net.labymod.addons.minimap.world;

/**
 * CPU copy of the terrain shading in {@code minimap.fsh}, for images drawn without that shader.
 */
final class MapShading {

  private MapShading() {
  }

  static int shade(int argb, int height, int north, int west) {
    int slope = (height - north) + (height - west);
    float intensity = slope < 0
        ? 1.0F + Math.max(slope, -5) * 0.1F
        : 1.0F + Math.min(slope, 3) * 0.04F;

    float red = (argb >> 16 & 0xFF) * intensity;
    float green = (argb >> 8 & 0xFF) * intensity;
    float blue = (argb & 0xFF) * intensity;
    float luminance = red * 0.299F + green * 0.587F + blue * 0.114F;

    red = (luminance + (red - luminance) * 0.8F) * 0.92F;
    green = (luminance + (green - luminance) * 0.8F) * 0.92F;
    blue = (luminance + (blue - luminance) * 0.8F) * 0.92F;
    return argb & 0xFF000000 | clamp(red) << 16 | clamp(green) << 8 | clamp(blue);
  }

  private static int clamp(float value) {
    return Math.max(0, Math.min(255, (int) value));
  }
}
