#version 310 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_viewTrans;
uniform mat4 u_projTrans;

out vec2 v_uv;

void main()  {
    v_uv = a_texCoord0;
    v_uv.y = 1.0 - v_uv.y;

    gl_Position = u_projTrans * u_viewTrans * vec4(a_position, 1.0);
}