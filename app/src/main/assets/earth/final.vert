precision mediump float;

uniform mat4 u_projTrans;

attribute vec3 a_position;
attribute vec2 a_texCoord0;

varying vec2 v_uv;

void main()  {
    v_uv = a_texCoord0;
    v_uv.y = 1.0 - v_uv.y;
    gl_Position = u_projTrans * vec4(a_position, 1.0);
}