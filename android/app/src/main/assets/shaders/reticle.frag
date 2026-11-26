precision mediump float;
uniform float uProgress;   // 0..1 dwell progress
uniform float uRingRadius; // in uv units (0..1 from centre)
uniform vec3 uColor;
varying vec2 vUv;

const float PI = 3.14159265;

void main() {
    vec2 p = vUv * 2.0 - 1.0;
    float r = length(p);

    float ringOuter = smoothstep(uRingRadius + 0.06, uRingRadius + 0.02, r);
    float ringInner = smoothstep(uRingRadius - 0.10, uRingRadius - 0.04, r);
    float ring = ringOuter * ringInner;

    float angle = atan(p.y, p.x);
    float norm = (angle + PI * 0.5) / (2.0 * PI);
    norm = norm - floor(norm);
    float arc = step(norm, uProgress);

    float dot0 = smoothstep(0.10, 0.05, r);

    float alpha = ring * (0.35 + 0.55 * uProgress) + ring * arc * 0.6 + dot0 * 0.85;
    if (alpha < 0.002) {
        discard;
    }
    gl_FragColor = vec4(uColor, alpha);
}
