uniform mat4 uMvp;
uniform float uPointScale;
attribute vec3 aPos;
attribute vec3 aColor;
varying vec3 vColor;
void main() {
    vColor = aColor;
    gl_Position = uMvp * vec4(aPos, 1.0);
    gl_PointSize = max(1.0, uPointScale);
}
