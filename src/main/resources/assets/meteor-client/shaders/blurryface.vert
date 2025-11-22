#version 330 core

precision lowp float;

layout (location = 0) in vec2 pos;
layout (location = 1) in vec4 color;
out vec2 uv;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

void main() {
    vec4 transformed = u_Proj * u_ModelView * vec4(pos, 0, 1);
    gl_Position = transformed;
    uv = transformed.xy * .5 + .5;
}
