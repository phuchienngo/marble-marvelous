#version 310 es

precision highp float;

uniform samplerCube u_diffuseMap;
uniform samplerCube u_normalMap;
uniform vec3 u_lightDirection;
uniform float u_intensity;
in vec2 v_uv;
in vec3 v_normal;
in vec3 v_viewNormal;
in vec3 v_viewPosition;

out vec4 fragColor;

// http://hacksoflife.blogspot.ch/2009/11/per-pixel-tangent-space-normal-mapping.html
vec3 perturbNormal(vec3 normalMap, vec3 normal, vec3 viewPosition, vec2 uv) {
    vec3 q0 = dFdx(-viewPosition.xyz);
    vec3 q1 = dFdy(-viewPosition.xyz);
    vec2 st0 = dFdx(uv);
    vec2 st1 = dFdy(uv);

    vec3 S = normalize(q0 * st1.y - q1 * st0.y);
    vec3 T = normalize(-q0 * st1.x + q1 * st0.x);
    vec3 N = normalize(normal);

    mat3 tsn = mat3(S, T, N);
    return normalize(tsn * normalMap);
}

void main()  {
    vec3 normalMap = texture(u_normalMap, v_normal).xyz * 2. - 1.;
    normalMap.xy *= .1;
    vec3 normal = perturbNormal(normalMap, v_viewNormal, v_viewPosition, v_uv);

    float fresnel = 1. - abs(dot(v_viewNormal, normalize(v_viewPosition)));

    float lighting = dot(normal, u_lightDirection * 6.);
    lighting = smoothstep(0., 1., lighting);

    vec3 color = texture(u_diffuseMap, v_normal).rgb;

    // Slightly blue backlight
    color *= 1. + vec3(155./255., 115./255., 229./255.) * vec3(1. - min(lighting, 1.)) * vec3(.15);

    // Backlight intensity
    color *= .18 + (lighting * (1. + fresnel)) * .9;


    float sideLightIntensity = dot(normalize(v_viewPosition), -u_lightDirection) * .5 + .5;
    color *= 1. + sideLightIntensity * smoothstep(.4, .8, fresnel) * 1.7;


//    float lighting2 = dot(normal, -u_lightDirection * 5.);
//    lighting2 = smoothstep(0., 1., lighting2);
//    color += lighting2;


    color *= 1. - smoothstep(.3, .9, fresnel);
    fragColor = vec4(color, 1.);
    fragColor.rgb = mix(vec3(0.), fragColor.rgb, u_intensity);
}
