precision lowp float;

uniform samplerCube dayMap;
uniform samplerCube nightMap;
uniform samplerCube cloudMap;
uniform samplerCube stormsMap;
uniform vec3 lightPosition;
uniform vec4 terminatorColor1;
uniform vec4 terminatorColor2;
uniform float nightLightsPhase;
uniform float lightIntensity;
uniform vec4 atmosphereColor1;
uniform vec4 atmosphereColor2;

varying vec3 vEye, vLookupNormal;
varying vec3 vCloudNormal, vCloudShadowNormal;
varying vec3 vNormal;

const float cloudIntensityDay = 0.9;
const float cloudIntensityNight = 0.6;
const float cloudShadowIntensity = 0.5;

const float lightShinePhase = 1.;
const float lightsRandomness = 30.;

const vec3 nightCloudColor = vec3(.26, .29, .34);

void main()  {
    vec3 lPos = lightPosition;
    vec3 N = normalize( vNormal );
    vec3 L = normalize( lPos - vEye );
    vec3 E = normalize( -vEye );

    float eyeLight = abs( dot( N, E ) );
    float lightDirection = dot(N, L); // Light direction and intensity
    float inverseLightDirection = 1. - lightDirection;


    vec3 base = vec3(0.);

    // Add clouds
    float cloudColor = textureCube( cloudMap, vCloudNormal ).r;
    float cloudShadow = textureCube( cloudMap, vCloudShadowNormal ).r;
    vec3 dayDiff = vec3(1.);

    // It has part of day texture
    if (lightDirection > -0.25) {
        dayDiff = textureCube( dayMap, vLookupNormal ).rgb;
        dayDiff = mix(dayDiff, vec3(0.), cloudShadow * cloudIntensityDay * cloudShadowIntensity); // Shadow
        dayDiff = mix(dayDiff, vec3(1.0), cloudColor * cloudIntensityDay); // Color
        base = dayDiff;
    }

    // Sunset color
    const float minSunsetAmount = 0.6; // Old value was 0.7
    if (inverseLightDirection >= minSunsetAmount) {
        vec4 rimColor = mix(terminatorColor2, terminatorColor1, eyeLight);
        float sunsetAlpha = smoothstep( minSunsetAmount, 1.1, inverseLightDirection);
        base = mix(base, base * rimColor.rgb, sunsetAlpha * rimColor.a);
    }


    // Apply fresnel to day
    float fresnel = smoothstep(0.6, 1., eyeLight) * 0.02;
    base -= vec3(fresnel);

    // Daylight, don't calculate either night or storms.
    if (lightDirection < .25 || lightIntensity != 1.0) {
        float nightIntensity = 1. - lightIntensity;
        float sunset = clamp(smoothstep( -.25, .1, -lightDirection) + nightIntensity, 0., 1.);
        float dusk = clamp(smoothstep( -.1, .25, -lightDirection) + nightIntensity, 0., 1.);
        float extraColor = smoothstep(0.7, 1., nightIntensity) * 0.5 + 1.;

        // Night
        vec3 nightDiff = textureCube( nightMap, vLookupNormal ).rgb;
        // Apply night's dark edge
        float nightRim = smoothstep(0., 0.8, 1. - eyeLight);
        nightDiff -= nightRim * 0.05;
        // Clouds at night
        nightDiff = mix(nightDiff, nightCloudColor, cloudColor * cloudIntensityNight);
        nightDiff *= smoothstep(0., 1., eyeLight);
        vec3 nightDiffNoLight = mix(nightDiff, min(nightDiff, vec3(0.08, 0.09, 0.13)), smoothstep(0., .13, length(nightDiff) / 1.73205080757));

        // Add storms
        float storms = textureCube( stormsMap, vCloudNormal ).r;
        vec3 stormsVec = vec3(0.8, 0.8, 1.) * .9 * storms * smoothstep(0.72, 0.82, cloudColor);
        stormsVec *= smoothstep(0.2, 1., eyeLight); // Don't display storms around the edge of the earth
        nightDiff += stormsVec;

        // Add night pixels
        base = mix(base, nightDiffNoLight, sunset);
        base = mix(base, nightDiff, dusk);
        base *= vec3(extraColor);
    }

    //
    // Specular
    float specShine = 0.0;
    vec3 R = normalize( reflect( -L, N ) );
    specShine = dot(R, E);

    // Stretching it, because we can :)
//    specShine = max(0.0, pow(specShine, 16.0) * 0.4);
    // Equivalent to spechShine ^ 16 * 0.4)
    specShine = smoothstep(0.7, 1.11, specShine) * 0.4;
    vec3 specColor = mix(vec3(1.00, 0.66, 0.44), vec3(.2), eyeLight);
    specColor -= cloudShadow * .5;
    specShine *= .4 + (1. - cloudColor) * .6;
    specShine *= smoothstep(-1.5, 1., dayDiff.b - dayDiff.r - dayDiff.g);
    base += (specColor * specShine + specShine * specColor * (1. - smoothstep(.2, .5, eyeLight)) * 7.) * lightIntensity;

    // Atmosphere

    // Glow around the edge of sphere
    float edgeGlow = 1. - eyeLight;
    // Make it shorter
    edgeGlow = smoothstep(0.12, 1., edgeGlow);
    // Fade borders
    edgeGlow *= 1. - smoothstep( .8, .9, edgeGlow );
    // Hide when night
    edgeGlow *= lightDirection * lightIntensity;

    float edgeMix = smoothstep(0.1, 0.38, eyeLight);
    vec4 finalColor = mix(atmosphereColor2, atmosphereColor1, edgeMix);

    base = mix(base, finalColor.rgb, finalColor.a * edgeGlow);


    // Compose final vector with smooth borders
    float edgeRim = 1. - eyeLight;
    edgeRim = 1. - smoothstep( .78, .81, edgeRim );

    gl_FragColor = mix(vec4(0., 0., 0., edgeRim), vec4(base, edgeRim), lightIntensity);
}