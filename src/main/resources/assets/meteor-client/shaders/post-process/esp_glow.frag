#version 330 core

in vec2 texCoord;

uniform sampler2D u_MaskTexture;
uniform sampler2D u_BlurTexture;

layout (std140) uniform OutlineData {
    int width;
    float fillOpacity;
    int shapeMode;
    float glowMultiplier;
    int blendMode;
} u_Outline;

layout (std140) uniform BlurData {
    vec2 u_HalfTexelSize;
    float u_Offset;
};

out vec4 color;

void main() {
    vec4 mask = texture(u_MaskTexture, texCoord);

    if (mask.a != 0.0) {
        if (u_Outline.shapeMode == 0) discard;

        color = vec4(mask.rgb, mask.a * u_Outline.fillOpacity);
    } else {
        if (u_Outline.shapeMode == 1) discard;

        vec4 blur = (
            texture(u_BlurTexture, texCoord + vec2(- u_HalfTexelSize.x * 2, 0) * u_Offset) +
            texture(u_BlurTexture, texCoord + vec2(- u_HalfTexelSize.x, u_HalfTexelSize.y) * u_Offset) * 2 +
            texture(u_BlurTexture, texCoord + vec2(0, u_HalfTexelSize.y * 2) * u_Offset) +
            texture(u_BlurTexture, texCoord + u_HalfTexelSize * u_Offset) * 2 +
            texture(u_BlurTexture, texCoord + vec2(u_HalfTexelSize.x * 2, 0) * u_Offset) +
            texture(u_BlurTexture, texCoord + vec2(u_HalfTexelSize.x, -u_HalfTexelSize.y) * u_Offset) * 2 +
            texture(u_BlurTexture, texCoord + vec2(0, -u_HalfTexelSize.y * 2) * u_Offset) +
            texture(u_BlurTexture, texCoord - u_HalfTexelSize * u_Offset) * 2
        ) / 12;

        color = vec4(blur.rgb / blur.a, min(blur.a * u_Outline.glowMultiplier, 1.0));
    }
}
