
/* Normal Blending */
vec4 normalBlending(vec4 src, vec4 dst) {
    float outA = src.a + dst.a * (1. - src.a);
    vec3 outRGB = (src.rgb * src.a + dst.rgb * dst.a  * (1. - src.a)) / (outA  + 0.001);

    return vec4(outRGB, outA);
}