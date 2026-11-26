uniform mat4 uMvp;
uniform mat4 uTexMatrix;

// Sub-rectangle of the source frame to show (SBS / TAB halves, cropping).
uniform vec2 uUvScale;     // 0..1 fraction of width/height, e.g. (0.5, 1.0)
uniform vec2 uUvOffset;    // where that rectangle starts, e.g. (0.0, 0.0) or (0.5, 0.0)
// Orientation correction applied inside that rectangle.
uniform float uUvRotation; // radians
uniform float uFlipV;      // 1.0 mirrors vertically, 0.0 leaves it alone

attribute vec3 aPos;
attribute vec2 aUv;
varying vec2 vUv;

vec2 rotate2(vec2 p, float a) {
    float s = sin(a);
    float c = cos(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

void main() {
    // 1. Pick the sub-rectangle (SBS left/right half, TAB bottom/top half).
    vec2 uv = aUv * uUvScale + uUvOffset;

    // 2. Rotate around the centre of that rectangle.
    vec2 centre = uUvOffset + uUvScale * 0.5;
    uv = rotate2(uv - centre, uUvRotation) + centre;

    // 3. Optional mirroring, kept inside the rectangle.
    if (uFlipV > 0.5) {
        uv.y = uUvOffset.y + uUvScale.y - (uv.y - uUvOffset.y);
    }

    // 4. Finally the SurfaceTexture transform. This matrix is defined for
    //    normalised 0..1 texture coordinates, so it must be applied last and
    //    never to centre-based coordinates.
    vUv = (uTexMatrix * vec4(uv, 1.0, 0.0)).xy;
    gl_Position = uMvp * vec4(aPos, 1.0);
}
