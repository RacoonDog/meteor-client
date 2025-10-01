#version 330 core

layout (location = 0) in vec4 pos;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

layout (std140) uniform FABEData {
    vec4 u_Offset;
    vec4 u_Color;
};

void main() {
    vec4 theRealPosition = pos + u_Offset;
    gl_Position = u_Proj * u_ModelView * theRealPosition;
}
