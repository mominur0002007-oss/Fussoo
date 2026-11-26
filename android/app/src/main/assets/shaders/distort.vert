attribute vec3 aPos;   // x,y in [-1,1], z unused
varying vec2 vNdc;
void main() {
    vNdc = aPos.xy;
    gl_Position = vec4(aPos.xy, 0.0, 1.0);
}
