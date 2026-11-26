uniform mat4 uMvp;
attribute vec3 aPos;
attribute vec3 aColor;
varying vec3 vColor;
void main() {
    vColor = aColor;
    gl_Position = uMvp * vec4(aPos, 1.0);
}
