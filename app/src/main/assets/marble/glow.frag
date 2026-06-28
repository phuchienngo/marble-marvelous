#version 310 es
precision lowp float;

uniform vec2 u_offset;
uniform highp sampler2D u_texture;

in vec2 v_uv;
out vec4 fragColor;

float getValue(sampler2D image, vec2 uv) {
    return uv.x > 1.1 || uv.x < -0.1 || uv.y > 1.1 || uv.y < -0.1 ? 0. : texture(image, uv).r;
}

vec4 glow(sampler2D image, vec2 uv, vec2 offset) {
  vec2 offset1 = vec2(1.411764705882353) * offset;
  vec2 offset2 = vec2(3.2941176470588234) * offset;
  vec2 offset3 = vec2(5.176470588235294) * offset;

  // center
  float imgValue = getValue(image, uv);
  float color = imgValue * 0.1964825501511404;

  // Neighbor pixels
  color += getValue(image, uv + offset1) * 0.2969069646728344;
  color += getValue(image, uv - offset1) * 0.2969069646728344;
  color += getValue(image, uv + offset2) * 0.09447039785044732;
  color += getValue(image, uv - offset2) * 0.09447039785044732;
  color += getValue(image, uv + offset3) * 0.010381362401148057;
  color += getValue(image, uv - offset3) * 0.010381362401148057;
  return vec4(max(color, 0.), 0., 0., 1.);
}


void main()  {
    fragColor = glow(u_texture, v_uv, u_offset);
}