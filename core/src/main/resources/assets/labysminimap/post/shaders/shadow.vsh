#version 150

in vec3 Position;
in vec2 UV;

uniform mat4 ProjectionMatrix;
uniform vec2 DestinationSize;

out vec2 pos;

void main() {
  vec4 outPosition = ProjectionMatrix * vec4(Position.xy, 0.0, 1.0);
  gl_Position = vec4(outPosition.xy, 0.2, 1.0);

  pos = Position.xy / DestinationSize;
}
