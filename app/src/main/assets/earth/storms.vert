#version 310 es
precision lowp float;

uniform mat4 u_projectionMatrix;
uniform mat4 u_viewMatrix;


in vec3 a_position;
in float a_size;
in float a_opacity;

out float v_opacity;

void main() {
    v_opacity = a_opacity;
    gl_Position = u_projectionMatrix * u_viewMatrix * vec4(a_position, 1.);
    gl_PointSize = a_size / gl_Position.w;
}