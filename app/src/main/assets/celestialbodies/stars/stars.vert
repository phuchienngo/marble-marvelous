#version 310 es

uniform mat4 u_projectionMatrix;
uniform mat4 u_worldMatrix;
uniform float u_main_opacity;

in float a_size;
in vec4 a_position;
in float a_opacity;

out float v_opacity;

void main() {
    v_opacity = a_opacity * u_main_opacity;
    gl_Position = u_projectionMatrix * u_worldMatrix * a_position;
    gl_PointSize = a_size;
}