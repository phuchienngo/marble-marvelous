vec4 convertToViewSpace(vec4 point) {
    // convert point to clip space
    vec4 convertedPoint = (u_projTrans * u_transform * point);     // Range:   [-w,w]^4

    // convert point to NDC space (Normalized Device Coordinates)
    convertedPoint.xyz /= convertedPoint.w;
    convertedPoint.w = 1.0 / convertedPoint.w;

    // Vertex in window-space
    convertedPoint.xyz *= 0.5;
    convertedPoint.xyz += vec3(0.5); // Rescale: [0,1]^3
    convertedPoint.xy /= u_resolution.yy;

    return convertedPoint;
}
