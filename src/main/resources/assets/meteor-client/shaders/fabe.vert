#version 330 core

layout (location = 0) in vec4 pos;
layout (location = 1) in vec4 color;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

layout (std140) uniform FABEData {
    vec4 u_Offset;
};

out vec4 v_Color;

void main() {
    gl_Position = u_Proj * u_ModelView * (pos + u_Offset);

    v_Color = color;
}
