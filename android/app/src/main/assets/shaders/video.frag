#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES uTex;
uniform float uAlpha;
varying vec2 vUv;
void main() {
    vec3 c = texture2D(uTex, vUv).rgb;
    gl_FragColor = vec4(c, uAlpha);
}
