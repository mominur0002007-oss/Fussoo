precision mediump float;
uniform sampler2D uTex;
uniform float uAlpha;
uniform vec3 uTint;
varying vec2 vUv;
void main() {
    vec4 c = texture2D(uTex, vUv);
    gl_FragColor = vec4(c.rgb * uTint, c.a * uAlpha);
}
