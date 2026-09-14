#version 150

#l3d_import <labymod:shaders/include/projection.glsl>
#l3d_import <labymod:shaders/include/globals.glsl>
#l3d_import <labymod:shaders/include/dynamic_transforms.glsl>
#l3d_import <labymod:shaders/include/clip_data.glsl>

#ifdef UNIFORM_BLOCK
layout(std140) uniform Minimap {
  vec3 PixelSize;
  vec3 SunPosition;
  float DayTime;
  vec4 ColorAdjustments;
};
#else
uniform vec3 PixelSize;
uniform vec3 SunPosition;
uniform float DayTime;
uniform vec4 ColorAdjustments;
#endif

uniform sampler2D DiffuseSampler;
uniform sampler2D HeightmapSampler;
uniform sampler2D LightmapSampler;
uniform sampler2D FadeSampler;
uniform sampler2D PreviousDiffuseSampler;
uniform sampler2D PreviousHeightmapSampler;
uniform sampler2D PreviousLightmapSampler;

in vec2 texCoord;
in vec2 fragCoord;

out vec4 fragColor;

const vec4 BLACK_COLOR = vec4(0, 0, 0, 1);

vec4 getLighting(sampler2D lightmapSampler) {
  vec4 lightColor = TEXTURE(lightmapSampler, texCoord);
  lightColor.a = clamp(lightColor.a, 0, 1);
  float black = float(all(equal(lightColor, BLACK_COLOR)));

  float dayTime = clamp(DayTime, 0.4, 1);
  vec4 skyColor = vec4(vec3(dayTime), 1);
  return mix(lightColor, skyColor, max(dayTime, black));
}

// Decodes the 16 bit block height. Red holds the low byte, green the high byte.
float getHeight(sampler2D heightmapSampler, vec2 coord) {
  vec2 halfTexel = PixelSize.xy * 0.5;
  vec4 encoded = TEXTURE(heightmapSampler, clamp(coord, halfTexel, 1.0 - halfTexel));
  return round(encoded.r * 255.0) + round(encoded.g * 255.0) * 256.0;
}

vec4 shade(sampler2D diffuseSampler, sampler2D heightmapSampler, sampler2D lightmapSampler) {
  vec4 baseColor = TEXTURE(diffuseSampler, texCoord);

  float height = getHeight(heightmapSampler, texCoord);
  float north = getHeight(heightmapSampler, texCoord - vec2(0.0, PixelSize.y));
  float west = getHeight(heightmapSampler, texCoord - vec2(PixelSize.x, 0.0));

  // Blocks lower than their north and west neighbours lose 10% per block, higher ones gain 4%
  float slope = (height - north) + (height - west);
  float lightIntensity = slope < 0.0
      ? 1.0 + max(slope, -5.0) * 0.1
      : 1.0 + min(slope, 3.0) * 0.04;

  // Pull colors 20% towards gray
  vec3 color = baseColor.rgb * lightIntensity;
  float luminance = dot(color, vec3(0.299, 0.587, 0.114));
  color = mix(vec3(luminance), color, 0.8) * 0.92;

  return vec4(color, baseColor.a) * getLighting(lightmapSampler);
}

void main() {
  float fade = TEXTURE(FadeSampler, texCoord).r;
  vec4 current = shade(DiffuseSampler, HeightmapSampler, LightmapSampler);
  vec4 previous = shade(PreviousDiffuseSampler, PreviousHeightmapSampler, PreviousLightmapSampler);

  // Blend premultiplied colors, a straight mix darkens chunks fading in from transparent
  float alpha = mix(previous.a, current.a, fade);
  vec3 premultiplied = mix(previous.rgb * previous.a, current.rgb * current.a, fade);
  vec4 color = vec4(alpha > 0.0 ? premultiplied / alpha : current.rgb, alpha);

  float clipAlpha = labyClipTest(fragCoord);
  if (clipAlpha < 0.01) discard;
  color.a *= clipAlpha;

  fragColor = color;
}
