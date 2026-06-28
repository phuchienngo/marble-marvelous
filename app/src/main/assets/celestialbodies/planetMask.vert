precision mediump float;

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform mat4 u_normalMatrix;
uniform mat4 u_modelViewTrans;

attribute vec3 a_position;
attribute vec3 a_normal;

varying vec3 v_normal, v_eye;

void main()  {
    v_normal = normalize((u_normalMatrix * vec4(a_normal, 1.)).xyz);
    v_eye = normalize((u_modelViewTrans * vec4(a_position, 1.0)).xyz);

    gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);
}
