#version 150

//#define SHADOW

uniform sampler2D DiffuseSampler;
uniform sampler2D HeightmapSampler;
uniform sampler2D LightmapSampler;
uniform vec2 DestinationSize;
uniform vec3 PixelSize;
uniform vec3 SunPosition;
uniform float DayTime;

in vec2 pos;

out vec4 fragColor;

const vec4 SHADOW_BRIGHTNESS = vec4(0.8, 0.8, 0.8, 1);
const int MAX_STEPS = 200;

float getHeightRaw(vec2 pos) {
  vec2 resolution = 1 / PixelSize.xy;

  vec2 position = pos * resolution;
  vec2 lerpP = fract(position);

  position = floor(position);
  position *= PixelSize.xy;


  float topLeft = texture2D(HeightmapSampler, position).r;
  float bottomLeft = texture2D(HeightmapSampler, position + PixelSize.zy).r;

  float topRight = texture2D(HeightmapSampler, position + PixelSize.xz).r;
  float bottomRight = texture2D(HeightmapSampler, position + PixelSize.xy).r;

  float top = mix(topLeft, topRight, lerpP.x);
  float bottom = mix(bottomLeft, bottomRight, lerpP.x);

  return mix(top, bottom, lerpP.y);
}

float getHeight(vec2 pos) {
  // TODO(Christian) Add water level
  return getHeightRaw(pos);
}

vec3 getNormal(vec2 pos) {
  float strength = 200;
  float left = strength * getHeight(pos - PixelSize.xz);
  float right = strength * getHeight(pos + PixelSize.xz);

  float down = strength * getHeight(pos - PixelSize.zy);
  float up = strength * getHeight(pos + PixelSize.zy);

  return normalize(vec3(left-right, down-up, 1.));
}

void main() {
  vec4 heightColor = TEXTURE(HeightmapSampler, pos);
  float height = heightColor.r;

  vec3 currentPos = vec3(pos, height);
  vec3 sunDir = SunPosition - vec3(0.5, 0.5, 0);
  #ifdef SHADOW
  vec3 stepDir = normalize(sunDir);
  #else
  vec3 stepDir = normalize(vec3(0.5, 0.5, 0));
  #endif

  float minStepSize = min(PixelSize.x, PixelSize.y);

  float shadow = 0;
  int n = 0;
  for (int i = 0; i <= MAX_STEPS; i++) {
    n++;
    float offsetHeight = getHeight(currentPos.xy);

    if (offsetHeight > currentPos.z) {
      shadow = 1;
      break;
    }

    if (currentPos.z > 1.0) {
      break;
    }

    currentPos += stepDir * max(minStepSize, (currentPos.z - offsetHeight) * 0.05);
  }

  if (n == MAX_STEPS) {
    shadow = 1;
  }

  vec3 normal = getNormal(pos);
  float normalShadow = (dot(normal, -stepDir) + 1) / 2;

  shadow = clamp(shadow + normalShadow, 0, 1);

  vec4 col = TEXTURE(DiffuseSampler, pos);

  vec4 shadowCol = col * SHADOW_BRIGHTNESS * vec4(1, 1, 1 + shadow * 0.2, 1.0);

  #ifdef SHADOW
  shadowCol = mix(col, shadowCol, shadow);
  #else
  shadowCol = mix(col, shadowCol, normalShadow);
  #endif

  vec4 lightColor = TEXTURE(LightmapSampler, pos);

  if (lightColor.a <= 0) {
    lightColor.a = 1;
  }
  vec4 black = vec4(0, 0, 0, 1);
  float isBlack = float(all(equal(lightColor, black)));

  float dayTime = clamp(DayTime, 0.4, 1);
  vec4 skyColor = vec4(vec3(dayTime), 1);
  vec4 mixedLight = mix(lightColor, skyColor, max(dayTime, isBlack));
  vec4 vanillaLight = lightColor * vec4(1, 1, 1, 1.75);
  fragColor = shadowCol * mixedLight;
  //fragColor = vec4(isBlack, isBlack, isBlack, 1);
}