#version 150

#l3d_import <labymod:shaders/include/projection.glsl>
#l3d_import <labymod:shaders/include/globals.glsl>
#l3d_import <labymod:shaders/include/dynamic_transforms.glsl>

in vec3 Position;
in vec2 UV;
in vec4 Color;

out vec2 texCoord;
out vec4 vertexColor;

void main() {
  gl_Position = ProjectionMatrix * ModelViewMatrix * vec4(Position, 1.0);
  texCoord = UV;
  vertexColor = Color;
}
