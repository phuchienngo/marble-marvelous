precision mediump float;

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform mat4 u_normalMatrix;
uniform mat4 u_cloudMatrix;
uniform mat4 u_modelViewTrans;

attribute vec3 a_position;
attribute vec2 a_texCoord0;
attribute vec3 a_normal;
uniform vec3 u_lightDirection;

varying vec3 v_normal, v_cloudNormal, v_lookupNormal, v_eye;
varying vec3 v_tangentLight;

vec3 biggestAngle(vec3 base, vec3 v1, vec3 v2) {
  vec3 c1 = cross(base, v1);
  vec3 c2 = cross(base, v2);
  return (dot(c2, c2) > dot(c1, c1)) ? c2 : c1;
}

void main()  {
  gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);
	v_lookupNormal = a_normal;
	v_normal = normalize((u_normalMatrix * vec4(a_normal, 1.)).xyz);
	v_cloudNormal = normalize((u_cloudMatrix * vec4(a_normal, 1.)).xyz);
	v_eye = normalize((u_modelViewTrans * vec4(a_position, 1.0)).xyz);

  //TODO pre-calculate binormals and tangents when we load the geometry
  // Binormal and tangent
  vec3 n = a_normal;
	vec3 b = normalize(cross(n, biggestAngle(n, vec3(1.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0))));
  vec3 t = normalize(cross(n, b));

  // Light in tangent space to use with normal map
  v_tangentLight = (u_normalMatrix * vec4(
    dot(u_lightDirection, t),
    dot(u_lightDirection, b),
    dot(u_lightDirection, n),
    1.0
  )).xyz;
}
