#version 330 core

layout (location = 0) in vec4 pos;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

layout (std140) uniform FABEPosition {
    vec4 u_Position;
};

void main() {
    gl_Position = u_Proj * u_ModelView * (pos + u_Position);
}
