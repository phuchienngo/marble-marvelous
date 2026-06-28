#version 310 es

precision highp float;

uniform sampler2D u_texture;

in float v_opacity;
out vec4 fragColor;

void main() {
  vec2 uv2 = (gl_PointCoord - 0.5) * 2.0;

  fragColor = vec4(1.);
  fragColor.a = v_opacity * (1.-smoothstep(0.5, 1., uv2.x * uv2.x + uv2.y * uv2.y));
//  fragColor = vec4(1.,0.,0.,1.);
}