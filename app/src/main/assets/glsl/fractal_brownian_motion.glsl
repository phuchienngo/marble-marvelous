#ifndef OCTAVES
#define OCTAVES 10
#endif

#ifndef LACUNARITY
#define LACUNARITY 1.7
#endif

#ifndef GAIN
#define GAIN .5
#endif

#define NOISE_SIMPLEX 0
#define NOISE_PERLIN 1

#ifndef NOISE_TYPE
#define NOISE_TYPE NOISE_PERLIN
#endif

float fbm_noise(vec3 st) {
#if NOISE_TYPE == NOISE_PERLIN
    return cnoise(st);
#elif NOISE_TYPE == NOISE_SIMPLEX
    return snoise(st.xy);
#endif
}

float fbm(vec3 st) {
    // Initial values
    float value = .0;
    float amplitude = .5;

    // Loop of octaves
    for (int i = 0; i < OCTAVES; i++) {
        value += amplitude * ((fbm_noise(st) * 0.5 / 0.72) + 0.5);
        st *= LACUNARITY;
        amplitude *= GAIN;
    }
    return value;
}
