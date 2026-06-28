// generates pseudorandom number in [0, 1]
// seed - world space position of a fragemnt
// freq - modifier for seed. The bigger, the faster
// the pseudorandom numbers will change with change of world space position
float random(in vec3 seed, in float freq) {
   // project seed on random constant vector
   float dt = dot(floor(seed * freq), vec3(53.1215, 21.1352, 9.1322));
   // return only fractional part
   return fract(sin(dt) * 2105.2354);
}