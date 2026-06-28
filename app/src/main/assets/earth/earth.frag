precision mediump float;

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

const float cloudIntensityDay = 0.62;
const float cloudIntensityNight = 0.36;
const float cloudShadowIntensity = 0.34;
const float cloudBlurOffset = 0.0045;

const float lightShinePhase = 1.;
const float lightsRandomness = 30.;

const vec3 dayCloudShadowColor = vec3(.60, .65, .70);
const vec3 dayCloudBaseColor = vec3(.85, .88, .90);
const vec3 dayCloudHighlightColor = vec3(.98, .99, .98);
const vec3 nightCloudColor = vec3(.20, .23, .28);

// Unsharp mask on the day surface: the day map is a fixed 2048² texture, so the
// only way to add apparent crispness is to boost local contrast (coastlines /
// terrain edges). Keep it subtle to avoid halos and amplifying texture artefacts.
const float daySharpenAmount = 0.32;
const float daySharpenOffset = 0.0016;

// Day diffuse with a 4-tap unsharp mask. Uses a continuous tangent frame (same
// trick as sampleCloud) so there is no hard cubemap-axis seam.
vec3 sampleDaySharp(vec3 normal) {
    vec3 n = normalize(normal);
    vec3 tangent = normalize(cross(n, vec3(0., 1., 0.)) + vec3(1e-4, 0., 0.));
    vec3 bitangent = cross(n, tangent);

    vec3 center = textureCube( dayMap, n ).rgb;
    vec3 blur = textureCube( dayMap, normalize(n + tangent * daySharpenOffset) ).rgb;
    blur += textureCube( dayMap, normalize(n - tangent * daySharpenOffset) ).rgb;
    blur += textureCube( dayMap, normalize(n + bitangent * daySharpenOffset) ).rgb;
    blur += textureCube( dayMap, normalize(n - bitangent * daySharpenOffset) ).rgb;
    blur *= 0.25;

    return clamp(center + (center - blur) * daySharpenAmount, 0.0, 1.0);
}

float sampleCloud(vec3 normal) {
    vec3 n = normalize(normal);
    // Continuous tangent frame: derive the blur directions smoothly so there is
    // NO hard latitude switch (the old `if (abs(n.y) > .75)` flipped the basis at
    // one latitude and drew a visible stripe across the globe). The frame is only
    // degenerate at the exact poles (a single point), which is invisible.
    vec3 tangent = normalize(cross(n, vec3(0., 1., 0.)) + vec3(1e-4, 0., 0.));
    vec3 bitangent = cross(n, tangent);

    // 5-tap blur, center-weighted: just enough to anti-alias the low-res cloud
    // cubemap / cube seams WITHOUT washing out detail (clouds were softer than the
    // 2048² earth map; this keeps the edges crisper than an even blur would).
    float cloud = textureCube( cloudMap, n ).r * .64;
    cloud += textureCube( cloudMap, normalize(n + tangent * cloudBlurOffset) ).r * .09;
    cloud += textureCube( cloudMap, normalize(n - tangent * cloudBlurOffset) ).r * .09;
    cloud += textureCube( cloudMap, normalize(n + bitangent * cloudBlurOffset) ).r * .09;
    cloud += textureCube( cloudMap, normalize(n - bitangent * cloudBlurOffset) ).r * .09;
    return cloud;
}

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
    float cloudColor = sampleCloud(vCloudNormal);
    float cloudShadow = sampleCloud(vCloudShadowNormal);
    // Single soft coverage ramp instead of several stacked smoothsteps — stacking
    // discrete thresholds on the low-res cloud map quantized into contour stripes.
    float cloudCoverage = smoothstep(0.05, 0.55, cloudColor);
    float cloudShadowCoverage = smoothstep(0.06, 0.58, cloudShadow);
    // Soft relief from the shadow-offset parallax sample: where the cloud is
    // thicker than its offset neighbour it reads as a lit, raised top.
    float cloudRelief = clamp((cloudColor - cloudShadow) * 2.0 + .50, 0., 1.);
    float cloudSun = smoothstep(-.20, .80, lightDirection);
    float dayCloudOpacity = cloudIntensityDay * cloudCoverage * (.55 + cloudRelief * .35);
    float nightCloudOpacity = cloudIntensityNight * cloudCoverage * (.70 + cloudRelief * .25);
    vec3 dayCloudColor = mix(dayCloudBaseColor, dayCloudHighlightColor, cloudRelief);
    dayCloudColor = mix(dayCloudColor, dayCloudShadowColor, (1. - cloudRelief) * .32);
    dayCloudColor *= .90 + cloudSun * .12;
    vec3 nightCloudLitColor = mix(nightCloudColor * .78, nightCloudColor, cloudRelief);
    vec3 dayDiff = vec3(1.);

    // It has part of day texture
    if (lightDirection > -0.25) {
        dayDiff = sampleDaySharp(vLookupNormal);
        dayDiff *= 1. - cloudShadowCoverage * cloudIntensityDay * cloudShadowIntensity; // Shadow
        dayDiff = mix(dayDiff, dayCloudColor, dayCloudOpacity); // Color
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
        nightDiff = mix(nightDiff, nightCloudLitColor, nightCloudOpacity);
        nightDiff *= smoothstep(0., 1., eyeLight);
        vec3 nightDiffNoLight = mix(nightDiff, min(nightDiff, vec3(0.08, 0.09, 0.13)), smoothstep(0., .13, length(nightDiff) / 1.73205080757));

        // Add storms
        float storms = textureCube( stormsMap, vCloudNormal ).r;
        vec3 stormsVec = vec3(0.8, 0.8, 1.) * .9 * storms * smoothstep(0.35, 0.80, cloudCoverage);
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
    specColor -= cloudShadowCoverage * .28;
    specShine *= .30 + (1. - dayCloudOpacity) * .70;
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
