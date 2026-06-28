float posterize(float value, float gamma, float numColors) {
    float c = pow(value, gamma);
    c = c * numColors;
    c = floor(c);
    c = c / numColors;
    return pow(c, 1.0 / gamma);
}
