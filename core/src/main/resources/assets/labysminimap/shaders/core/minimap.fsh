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

vec4 shade(sampler2D diffuseSampler, sampler2D heightmapSampler, sampler2D lightmapSampler) {
  float height = TEXTURE(heightmapSampler, texCoord).r;
  vec4 baseColor = TEXTURE(diffuseSampler, texCoord);

  vec3 sunDirection = SunPosition - vec3(0.5, 0.5, 0.0);

  float dx = TEXTURE(heightmapSampler, texCoord + vec2(PixelSize.x, 0.0)).r - height;
  float dy = TEXTURE(heightmapSampler, texCoord + vec2(0.0, PixelSize.y)).r - height;

  // Reconstruct the normal from the gradients
  vec3 normal = normalize(vec3(-dx, -dy, 0.03));

  // Calculate lighting using the sun direction and normal
  float lightIntensity = max(dot(normal, sunDirection), 0.0);
  lightIntensity = lightIntensity * 0.5 + 0.5;

  // Apply shadows and highlights to the base color
  vec3 litColor = baseColor.rgb * lightIntensity;

  return vec4(litColor, baseColor.a) * getLighting(lightmapSampler);
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
