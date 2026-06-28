#ifdef GL_ES
precision mediump float;
#endif

uniform vec3 u_lightDirection;
uniform vec2 u_resolution;
uniform float u_aod;
uniform float u_rim_fade_start;
uniform float u_rim_fade_end;
varying vec3 v_eye, v_normal;

const float diffuseStrength = 1.1;
const vec3 mainColor = vec3(1., 0., 0.);

void main()  {
    vec3 N = normalize( v_normal );
    vec3 L = normalize( u_lightDirection );
    vec3 E = normalize( -v_eye );

    float eyeLight = abs( dot( N, E ) );
    float dotDirection = dot(N, -L);
    dotDirection = smoothstep(-0.10, 0.7, dotDirection);
    float diffuseLightSt = max(dotDirection * diffuseStrength, 0.0); // Light direction and intensity

    // Diffuse colors
    vec3 base = mainColor;
    
    // Lights
    vec3 diffuseLight = base * clamp(0.0, diffuseStrength, diffuseLightSt);

    // Combine all lights
    base = diffuseLight;

    // AOD
    base = mix(base, mainColor, u_aod * u_aod);

    float edgeRim = 1. - eyeLight;
        edgeRim = 1. - smoothstep( u_rim_fade_start, u_rim_fade_end, edgeRim );
    vec2 st = gl_FragCoord.xy / u_resolution;
    vec2 stedge = 2. / u_resolution;
    vec2 stedge2 = (u_resolution - 2.) / u_resolution;
    base = st.x <= stedge.x || st.x >= stedge2.x ? vec3(0.) : base;
    gl_FragColor = vec4(base, edgeRim); // Compose final vector with smooth borders
}
