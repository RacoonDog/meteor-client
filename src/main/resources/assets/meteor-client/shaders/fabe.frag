#version 330 core

layout (std140) uniform FABEData {
    vec4 u_Offset;
    vec4 u_Color;
};

out vec4 color;

void main() {
    color = u_Color;
}
