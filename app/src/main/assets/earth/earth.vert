precision lowp float;

uniform vec3 lightPosition;
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 normalMatrix;
uniform mat4 cloudTransformMatrix;
uniform mat4 cloudShadowMatrix;

attribute vec3 a_position;
attribute vec2 a_texCoord0;
attribute vec3 a_normal;

varying vec3 vNormal, vLookupNormal;
varying vec3 vCloudNormal, vCloudShadowNormal;
varying vec3 vEye;

void main()  {
    vec4 mvPosition = modelViewMatrix * vec4( a_position, 1. );
    gl_Position = projectionMatrix * mvPosition;

	vEye = mvPosition.xyz;

	vLookupNormal = a_normal;
	vCloudNormal = (vec4(a_normal, 1.) * cloudTransformMatrix).xyz;
	vCloudShadowNormal = (vec4(a_normal, 1.) * cloudTransformMatrix * cloudShadowMatrix).xyz;
	vNormal = (normalMatrix * vec4(a_normal, 1.)).xyz;
}