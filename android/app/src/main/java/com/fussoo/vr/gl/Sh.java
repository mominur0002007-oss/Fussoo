package com.fussoo.vr.gl;

/** GLSL ES 2.0 sources. */
public final class Sh {

    private Sh() {
    }

    /** Quad in clip space with uv. */
    public static final String QUAD_VS =
            "attribute vec2 aPos;\n"
                    + "varying vec2 vUv;\n"
                    + "void main() {\n"
                    + "  vUv = aPos * 0.5 + 0.5;\n"
                    + "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
                    + "}\n";

    /** Curved projection screen; aPos is a -1..1 quad, uv is flipped for GL. */
    public static final String SCREEN_VS =
            "uniform mat4 uMvp;\n"
                    + "uniform float uWidth;\n"
                    + "uniform float uHeight;\n"
                    + "uniform float uCurve;\n"
                    + "attribute vec2 aPos;\n"
                    + "varying vec2 vUv;\n"
                    + "void main() {\n"
                    + "  vec2 n = aPos;\n"
                    + "  vec3 p = vec3(n.x * uWidth * 0.5, n.y * uHeight * 0.5,"
                    + " -uCurve * n.x * n.x * uWidth * 0.075);\n"
                    + "  vUv = vec2(n.x * 0.5 + 0.5, 0.5 - n.y * 0.5);\n"
                    + "  gl_Position = uMvp * vec4(p, 1.0);\n"
                    + "}\n";

    /** External OES video sampling. */
    public static final String SCREEN_FS =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "precision mediump float;\n"
                    + "uniform samplerExternalOES uTex;\n"
                    + "uniform float uDim;\n"
                    + "uniform float uHas;\n"
                    + "varying vec2 vUv;\n"
                    + "void main() {\n"
                    + "  vec3 c = vec3(0.02, 0.023, 0.028);\n"
                    + "  if (uHas > 0.5) {\n"
                    + "    c = texture2D(uTex, vUv).rgb;\n"
                    + "  }\n"
                    + "  gl_FragColor = vec4(c * (1.0 - uDim * 0.85), 1.0);\n"
                    + "}\n";

    /** Inside-out room: walls, floor highlight, aisle strips. */
    public static final String SHELL_VS =
            "uniform mat4 uMvp;\n"
                    + "uniform float uScale;\n"
                    + "attribute vec3 aPos;\n"
                    + "varying vec3 vDir;\n"
                    + "void main() {\n"
                    + "  vDir = aPos;\n"
                    + "  gl_Position = uMvp * vec4(aPos * uScale, 1.0);\n"
                    + "}\n";

    public static final String SHELL_FS =
            "precision mediump float;\n"
                    + "uniform vec3 uWall;\n"
                    + "uniform vec3 uFloor;\n"
                    + "uniform vec3 uAccent;\n"
                    + "uniform float uTime;\n"
                    + "varying vec3 vDir;\n"
                    + "void main() {\n"
                    + "  vec3 d = normalize(vDir);\n"
                    + "  float h = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);\n"
                    + "  vec3 c = mix(uFloor, uWall, smoothstep(0.32, 0.62, h));\n"
                    // Aisle strips on the floor, two soft lines either side of centre.
                    + "  float floorMask = smoothstep(0.46, 0.36, h);\n"
                    + "  float strip = smoothstep(0.035, 0.0, abs(abs(d.x) - 0.30));\n"
                    + "  c += uAccent * strip * floorMask * 0.50;\n"
                    // Soft glow behind the screen area.
                    + "  float glow = smoothstep(0.55, 1.0, -d.z) * smoothstep(0.60, 0.15, abs(h - 0.55));\n"
                    + "  c += uAccent * glow * 0.10;\n"
                    + "  float breathe = 0.5 + 0.5 * sin(uTime * 0.35);\n"
                    + "  c += uAccent * 0.006 * breathe * floorMask;\n"
                    + "  gl_FragColor = vec4(c, 1.0);\n"
                    + "}\n";

    /** HUD quads. */
    public static final String HUD_VS =
            "uniform mat4 uMvp;\n"
                    + "attribute vec2 aPos;\n"
                    + "attribute vec2 aUv;\n"
                    + "attribute float aA;\n"
                    + "varying vec2 vUv;\n"
                    + "varying float vA;\n"
                    + "void main() {\n"
                    + "  vUv = aUv;\n"
                    + "  vA = aA;\n"
                    + "  gl_Position = uMvp * vec4(aPos, 0.0, 1.0);\n"
                    + "}\n";

    public static final String HUD_FS =
            "precision mediump float;\n"
                    + "uniform sampler2D uTex;\n"
                    + "varying vec2 vUv;\n"
                    + "varying float vA;\n"
                    + "void main() {\n"
                    + "  vec4 t = texture2D(uTex, vUv);\n"
                    + "  gl_FragColor = vec4(t.rgb, t.a * vA);\n"
                    + "}\n";

    /** Lens pre-distortion with chromatic correction and bezel vignette. */
    public static final String WARP_FS =
            "precision mediump float;\n"
                    + "uniform sampler2D uTex;\n"
                    + "uniform float uBarrel;\n"
                    + "uniform float uZoom;\n"
                    + "uniform float uAspect;\n"
                    + "varying vec2 vUv;\n"
                    + "vec2 warp(vec2 p, float s) {\n"
                    + "  vec2 c = p - 0.5;\n"
                    + "  c.x *= uAspect;\n"
                    + "  c *= s * uZoom;\n"
                    + "  float r2 = dot(c, c);\n"
                    + "  c *= 1.0 + uBarrel * r2 + uBarrel * 0.30 * r2 * r2;\n"
                    + "  c.x /= uAspect;\n"
                    + "  return c + 0.5;\n"
                    + "}\n"
                    + "void main() {\n"
                    + "  vec2 g = warp(vUv, 1.0);\n"
                    + "  vec2 r = warp(vUv, 1.0016);\n"
                    + "  vec2 b = warp(vUv, 0.9984);\n"
                    + "  vec3 c = vec3(texture2D(uTex, r).r, texture2D(uTex, g).g,"
                    + " texture2D(uTex, b).b);\n"
                    + "  vec2 d = (vUv - 0.5) * vec2(uAspect, 1.0);\n"
                    + "  float v = smoothstep(0.98, 0.62, length(d));\n"
                    + "  gl_FragColor = vec4(c * mix(0.55, 1.0, v), 1.0);\n"
                    + "}\n";
}
