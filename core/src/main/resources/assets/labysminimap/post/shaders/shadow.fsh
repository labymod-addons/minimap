#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D HeightmapSampler;
uniform sampler2D LightmapSampler;
uniform vec2 DestinationSize;
uniform vec3 PixelSize;
uniform vec3 SunPosition;
uniform float DayTime;

in vec2 pos;

out vec4 fragColor;

const vec4 BLACK_COLOR = vec4(0, 0, 0, 1);

vec4 getLighting() {
  vec4 lightColor = TEXTURE(LightmapSampler, pos);
  lightColor.a = clamp(lightColor.a, 0, 1);
  float black = float(all(equal(lightColor, BLACK_COLOR)));

  float dayTime = clamp(DayTime, 0.4, 1);
  vec4 skyColor = vec4(vec3(dayTime), 1);
  return mix(lightColor, skyColor, max(dayTime, black));
}

void main() {
  float height = TEXTURE(HeightmapSampler, pos).r;
  vec4 baseColor = TEXTURE(DiffuseSampler, pos);

  vec3 sunDirection = SunPosition - vec3(0.5, 0.5, 0.0);

  float dx = TEXTURE(HeightmapSampler, pos + vec2(PixelSize.x, 0.0)).r - height;
  float dy = TEXTURE(HeightmapSampler, pos + vec2(0.0, PixelSize.y)).r - height;

  // Reconstruct the normal from the gradients
  vec3 normal = normalize(vec3(-dx, -dy, 0.03));

  // Calculate lighting using the sun direction and normal
  float lightIntensity = max(dot(normal, sunDirection), 0.0);
  lightIntensity = lightIntensity * 0.5 + 0.5;

  // Apply shadows and highlights to the base color
  vec3 litColor = baseColor.rgb * lightIntensity;

  fragColor = vec4(litColor, baseColor.a) * getLighting();
}