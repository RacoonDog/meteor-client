#version 330 core

precision lowp float;

in vec2 texCoord;
out vec4 color;

uniform sampler2D u_Texture;

layout (std140) uniform BlurData {
    vec2 u_HalfTexelSize;
    float u_Offset;
};

void main() {
    color = (
        texture(u_Texture, texCoord) * 4 +
        texture(u_Texture, texCoord - u_HalfTexelSize * u_Offset) +
        texture(u_Texture, texCoord + u_HalfTexelSize * u_Offset) +
        texture(u_Texture, texCoord + vec2(u_HalfTexelSize.x, -u_HalfTexelSize.y) * u_Offset) +
        texture(u_Texture, texCoord - vec2(u_HalfTexelSize.x, -u_HalfTexelSize.y) * u_Offset)
    ) / 8;
    if (opaque == 1) color.a = 1;
}
