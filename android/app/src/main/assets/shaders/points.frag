precision mediump float;
varying vec3 vColor;
void main() {
    vec2 d = gl_PointCoord - vec2(0.5);
    float r = length(d) * 2.0;
    float alpha = 1.0 - smoothstep(0.55, 1.0, r);
    gl_FragColor = vec4(vColor, alpha);
}
