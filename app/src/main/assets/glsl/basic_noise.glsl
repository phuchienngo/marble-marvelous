float random (in vec2 st) {
    return fract(sin(dot(st.xy,
                         vec2(12.9898,78.233)))
                 * 43758.5453123);
}

float noise (in vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);

    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));

    vec2 u = smoothstep(0., 1., f);

    return mix(
        	mix(a, b, u.x),
        	mix(c, d, u.x),
        	u.y
    );
}

vec2 random2(in vec2 st) {
    return normalize(vec2(
        random(st * vec2(42.9401, 941.2301) + vec2(-9534.1, 41.4394)) * 2. - 1.,
    	random(st * vec2(-456.321, 14.6943) + vec2(-585.456, 14.658)) * 2. - 1.
    ));
}

vec2 noise2 (in vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);

    vec2 a = random2(i);
    vec2 b = random2(i + vec2(1.0, 0.0));
    vec2 c = random2(i + vec2(0.0, 1.0));
    vec2 d = random2(i + vec2(1.0, 1.0));

    vec2 u = smoothstep(0., 1., f);

    return mix(
        	mix(a, b, u.x),
        	mix(c, d, u.x),
        	u.y
    );
}

vec2 octaveNoise2(in vec2 st, in vec2 time) {
    const int octaves = 9;
    vec2 samplePoint = st;
    float gain = 0.5;
    float scale = 2.0;
    float amplitude = 1.;
    vec2 sum = vec2(0.);
    for (int i = 0; i < octaves; i++) {
        sum += noise2(samplePoint) * amplitude;
        amplitude *= gain;
        samplePoint = vec2(23.592,504.2491) + time + samplePoint * scale;
    }
    // totalAmplitude*scalar = 1; totalAmplitude = 1 / (1 - r), where r = falloff
    // scalar / (1 - r) = 1 -> scalar = (1 - r)
    float amplitudeScalar = 1. - gain;
    return sum * amplitudeScalar;
}

vec2 iterativeNoise2WarpingNoise(in vec2 st, in vec2 offset) {
    const int iterations = 15;
    vec2 noisy2Value = vec2(0);
    vec2 time = vec2(u_time / 90.0);
    vec2 st_2 = st + noisy2Value;
    float scalarInput = u_scale.x * 0.100;
    float scalarValue = u_scale.y * 2.;
    for (int i = 0; i < iterations; i++) {
        vec2 v = (st + noisy2Value) * (1. + scalarInput);
        noisy2Value = octaveNoise2(v, time) * scalarValue + offset;
    }
    return noisy2Value;
}
