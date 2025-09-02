#version 150

#l3d_import <labymod:shaders/include/post_processor.glsl>

in vec3 Position;
in vec2 UV;

out vec2 pos;

void main() {
  vec4 outPosition = ProjectionMatrix * vec4(Position.xy, 0.0, 1.0);
  gl_Position = vec4(outPosition.xy, 0.2, 1.0);

  pos = Position.xy / DestinationSize;
}
