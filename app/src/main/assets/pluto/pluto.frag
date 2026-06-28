#ifdef GL_ES
precision mediump float;
#endif

uniform samplerCube u_dayMap;
uniform samplerCube u_normalMap;
uniform vec3 u_lightDirection;
uniform float u_intensity;

varying vec3 v_eye, v_lookupNormal, v_normal, v_cloudNormal;
varying vec3 v_tangentLight;

const float normalStrength = 0.6;
const float diffuseStrength = 1.1;
const vec4 atmosphereColor1 = vec4(132. / 255., 157. / 255., 230. / 255., 0.4);
const vec4 atmosphereColor2 = vec4(188.0/255.0,	207.0/255.0, 216.0/255.0, 0.3);
const float cloudOpacity = 0.80;

void main()  {

    vec3 normal = textureCube( u_normalMap, v_lookupNormal ).rgb * 2.0 - 1.0;

    vec3 N = normalize( v_normal );
    vec3 L = normalize( u_lightDirection );
    vec3 E = normalize( -v_eye );

    float eyeLight = abs( dot( N, E ) );
    float diffuseLightSt = max(dot(N, L) * diffuseStrength * u_intensity, 0.0); // Light direction and intensity
    float normalLightSt = max(dot(normal, v_tangentLight) * normalStrength, 0.0);

    // Diffuse colors
    vec3 base = textureCube( u_dayMap, v_lookupNormal ).rgb;
    
    // Lights
    vec3 diffuseLight = base * clamp(0.0, diffuseStrength, diffuseLightSt);
    vec3 normalLight = base * normalLightSt * 0.0;

    // Combine all lights
    base = diffuseLight + normalLight;

    // Add a thin atmosphere
    float atmosphere = 1.0 - eyeLight;
    atmosphere = smoothstep(0.2, 0.85, atmosphere) * diffuseLightSt;
    vec4 atmosphereColor = mix(atmosphereColor1, atmosphereColor2, smoothstep(0.1, 0.5, eyeLight));
    vec3 atmosphereColorComposed = mix(base, atmosphereColor.rgb, atmosphereColor.a);
    base = mix(atmosphereColorComposed * atmosphere, base, smoothstep(0.1, 0.8, eyeLight));
//    base += atmosphereColor.rgb * atmosphere * atmosphereColor.a;

    float smoothEdge = smoothstep(0.15, 0.2, eyeLight);
    gl_FragColor = vec4(base, smoothEdge); // Compose final vector with smooth borders
//    gl_FragColor.rgb = mix(vec3(0., 0., 0.), gl_FragColor.rgb, u_intensity);
//    gl_FragColor = vec4(atmosphereColor, smoothEdge);
}
