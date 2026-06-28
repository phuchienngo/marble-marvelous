precision highp float;

varying vec2 v_texCoords;
uniform vec2 u_resolution;
uniform sampler2D u_texture;
uniform vec4 u_tint_color;

/* Include methods */
#include <glsl/basic_dithering.glsl>

void main() {
  float tintIntensity = texture2D(u_texture, v_texCoords).r;

  highp vec3 st = vec3(gl_FragCoord.xy / u_resolution.yy, 3.0);
  highp float dithering = basicDithering(st.xy, 10.);
  gl_FragColor = vec4(u_tint_color.rgb, u_tint_color.a * smoothstep(0.08, 1., tintIntensity - dithering));
}