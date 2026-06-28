#version 310 es

uniform mat4 u_projectionViewTransform;
uniform mat4 u_viewTransform;
uniform mat4 u_modelWorldTransform;

in vec3 a_position;
in vec2 a_texCoord0;
in vec3 a_normal;

out vec2 v_uv;
out vec3 v_normal;
out vec3 v_viewNormal;
out vec3 v_viewPosition;

void main()  {
    v_uv = a_texCoord0;
    v_normal = a_normal;
    v_viewNormal = mat3(u_modelWorldTransform) * a_normal;
    v_viewPosition = (u_viewTransform * u_modelWorldTransform * vec4(a_position, 1.0)).xyz;
    gl_Position = u_projectionViewTransform * u_modelWorldTransform * vec4(a_position, 1.0);
}
