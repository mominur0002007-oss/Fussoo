precision mediump float;
uniform sampler2D uTex;
uniform vec2 uTanHalfEye;    // tan(half fov) of the visible eye area
uniform float uOverscan;     // eye texture covers fov * overscan
uniform vec2 uCenterOffset;  // eye alignment in tan-angle units
uniform float uK1;
uniform float uK2;
uniform float uChromatic;
varying vec2 vNdc;

void main() {
    vec2 t = vNdc * uTanHalfEye - uCenterOffset;
    float r2 = dot(t, t);
    float f = 1.0 + uK1 * r2 + uK2 * r2 * r2;

    vec2 denom = 2.0 * uTanHalfEye * uOverscan;
    vec2 uv = 0.5 + (t * f) / denom;

    if (uChromatic <= 0.0001) {
        vec3 c = texture2D(uTex, clamp(uv, 0.001, 0.999)).rgb;
        gl_FragColor = vec4(c, 1.0);
    } else {
        vec2 dir = (uv - 0.5) * uChromatic;
        float r = texture2D(uTex, clamp(uv + dir, 0.001, 0.999)).r;
        float g = texture2D(uTex, clamp(uv, 0.001, 0.999)).g;
        float b = texture2D(uTex, clamp(uv - dir, 0.001, 0.999)).b;
        gl_FragColor = vec4(r, g, b, 1.0);
    }
}
