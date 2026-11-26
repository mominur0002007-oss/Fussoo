uniform mat4 uMvp;
uniform mat4 uTexMatrix;
uniform vec2 uUvScale;
uniform vec2 uUvOffset;
uniform float uUvRotation;
attribute vec3 aPos;
attribute vec2 aUv;
varying vec2 vUv;

vec2 rotate2(vec2 p, float a) {
    float s = sin(a);
    float c = cos(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

void main() {
    vec2 uv = aUv - 0.5;
    uv = rotate2(uv, uUvRotation);
    uv = uv * uUvScale + uUvOffset;
    vUv = (uTexMatrix * vec4(uv, 1.0, 0.0)).xy;
    gl_Position = uMvp * vec4(aPos, 1.0);
}
