uniform mat4 uMvp;
attribute vec3 aPos;
attribute vec2 aUv;
varying vec2 vUv;

void main() {
    // Android bitmaps are uploaded with their first (top) row at texel row 0,
    // which GL treats as v = 0. Our quads have v = 0 on the bottom edge, so the
    // texture has to be flipped here or every panel, icon, thumbnail and
    // captured web page renders upside down.
    vUv = vec2(aUv.x, 1.0 - aUv.y);
    gl_Position = uMvp * vec4(aPos, 1.0);
}
