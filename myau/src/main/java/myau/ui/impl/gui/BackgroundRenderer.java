package myau.ui.impl.gui;

import myau.config.MenuConfig;
import myau.util.RenderUtil;
import myau.util.shader.ShaderUtil;
import myau.util.shader.impl.MainMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import java.awt.Color;

public final class BackgroundRenderer {
    public static final int BACKGROUND_CLASSIC = 0;
    public static final int BACKGROUND_MYAU_PLUS = 1;
    public static final int BACKGROUND_RISE = 2;
    public static final int BACKGROUND_COSMOS = 3;
    public static final int BACKGROUND_MINECRAFT = 4;
    public static final int BACKGROUND_CIRCLE = 5;

    public static int currentBackgroundIndex = BACKGROUND_CLASSIC;

    private static ShaderUtil backgroundShader;
    private static long shaderStartTime = System.currentTimeMillis();
    private static boolean initialized;

    private static final String SHADER_1 =
            "#version 120\n" +
                    "uniform float time;\n" +
                    "uniform vec2 resolution;\n" +
                    "float random(vec2 st) { return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123); }\n" +
                    "float noise(vec2 st) {\n" +
                    "    vec2 i = floor(st); vec2 f = fract(st);\n" +
                    "    float a = random(i); float b = random(i + vec2(1.0, 0.0));\n" +
                    "    float c = random(i + vec2(0.0, 1.0)); float d = random(i + vec2(1.0, 1.0));\n" +
                    "    vec2 u = f * f * (3.0 - 2.0 * f);\n" +
                    "    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;\n" +
                    "}\n" +
                    "float fbm(vec2 st) {\n" +
                    "    float v = 0.0; float a = 0.5; vec2 shift = vec2(100.0);\n" +
                    "    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.50));\n" +
                    "    for (int i = 0; i < 5; ++i) { v += a * noise(st); st = rot * st * 2.0 + shift; a *= 0.5; }\n" +
                    "    return v;\n" +
                    "}\n" +
                    "void main() {\n" +
                    "    vec2 st = gl_FragCoord.xy / resolution.xy * 3.0; st.x *= resolution.x / resolution.y;\n" +
                    "    vec2 q = vec2(0.0); q.x = fbm(st + 0.00 * time); q.y = fbm(st + vec2(1.0));\n" +
                    "    vec2 r = vec2(0.0); r.x = fbm(st + 1.0 * q + vec2(1.7, 9.2) + 0.15 * time); r.y = fbm(st + 1.0 * q + vec2(8.3, 2.8) + 0.126 * time);\n" +
                    "    float f = fbm(st + r);\n" +
                    "    vec3 color = mix(vec3(0.101961, 0.619608, 0.666667), vec3(0.666667, 0.666667, 0.498039), clamp((f * f) * 4.0, 0.0, 1.0));\n" +
                    "    color = mix(color, vec3(0.0, 0.0, 0.164706), clamp(length(q), 0.0, 1.0));\n" +
                    "    color = mix(color, vec3(0.666667, 1.0, 1.0), clamp(length(r.x), 0.0, 1.0));\n" +
                    "    color *= 0.6; color = pow(color, vec3(1.2));\n" +
                    "    gl_FragColor = vec4(color, 1.0);\n" +
                    "}";

    private static final String SHADER_2 =
            "#version 120\n" +
                    "uniform vec2 resolution;\n" +
                    "uniform float time;\n" +
                    "mat2 m(float a) { float c=cos(a), s=sin(a); return mat2(c,-s,s,c); }\n" +
                    "float map(vec3 p) {\n" +
                    "    p.xz *= m(time * 0.4); p.xy*= m(time * 0.1);\n" +
                    "    vec3 q = p * 2.0 + time;\n" +
                    "    return length(p+vec3(sin(time * 0.7))) * log(length(p) + 1.0) + sin(q.x + sin(q.z + sin(q.y))) * 0.5 - 1.0;\n" +
                    "}\n" +
                    "void main() {\n" +
                    "    vec2 a = gl_FragCoord.xy / resolution.y - vec2(0.9, 0.5);\n" +
                    "    vec3 cl = vec3(0.0); float d = 2.5;\n" +
                    "    for (int i = 0; i <= 5; i++) {\n" +
                    "        vec3 p = vec3(0, 0, 4.0) + normalize(vec3(a, -1.0)) * d;\n" +
                    "        float rz = map(p); float f =  clamp((rz - map(p + 0.1)) * 0.5, -0.1, 1.0);\n" +
                    "        vec3 l = vec3(0.1, 0.3, 0.4) + vec3(5.0, 2.5, 3.0) * f;\n" +
                    "        cl = cl * l + smoothstep(2.5, 0.0, rz) * 0.6 * l; d += min(rz, 1.0);\n" +
                    "    }\n" +
                    "    gl_FragColor = vec4(cl, 1.0);\n" +
                    "}";

    private static final String SHADER_3 =
            "#version 120\n" +
                    "uniform float time;\n" +
                    "uniform vec2 resolution;\n" +
                    "#define PI 3.14\n" +
                    "mat2 rotate3d(float angle) { return mat2(cos(angle), -sin(angle), sin(angle), cos(angle)); }\n" +
                    "void main() {\n" +
                    "    vec2 p = (gl_FragCoord.xy * 2.0 - resolution) / min(resolution.x, resolution.y);\n" +
                    "    p = rotate3d((time * 0.94) * PI) * p;\n" +
                    "    float t; if (sin(time) == 1.0) t = 0.075 / abs(1.0 - length(p)); else t = 0.075 / abs(0.8 - length(p));\n" +
                    "    gl_FragColor = vec4(vec3(t) * vec3(0.13*(sin(time)+12.0), p.y*1.7, 3.5), 1.0);\n" +
                    "}";

    private static final String SHADER_4 =
            "#version 120\n" +
                    "uniform float time;\n" +
                    "uniform vec2 resolution;\n" +
                    "float hash(float n) { return fract(sin(n)*43758.5453); }\n" +
                    "bool getMaterialColor(int i, vec2 coord, out vec3 color) {\n" +
                    "    vec2 uv = floor(coord);\n" +
                    "    float n = uv.x + uv.y*347.0 + 4321.0 * float(i); float h = hash(n);\n" +
                    "    float br = 1. - h * (96./255.); color = vec3(150./255., 108./255., 74./255.);\n" +
                    "    if (i == 4) color = vec3(127./255., 127./255., 127./255.);\n" +
                    "    float xm1 = mod((uv.x * uv.x * 3. + uv.x * 81.) / 4., 4.);\n" +
                    "    if (i == 1) { if(uv.y < (xm1 + 18.)) color = vec3(106./255., 170./255., 64./255.); else if (uv.y < (xm1 + 19.)) br = br * (2. / 3.); }\n" +
                    "    if (i == 7) { color = vec3(103./255., 82./255., 49./255.); if (uv.x > 0. && uv.x < 15. && ((uv.y > 0. && uv.y < 15.) || (uv.y > 32. && uv.y < 47.))) { color = vec3(188./255., 152./255., 98./255.); float xd = (uv.x - 7.); float yd = (mod(uv.y, 16.) - 7.); if (xd < 0.) xd = 1. - xd; if (yd < 0.) yd = 1. - yd; if (yd > xd) xd = yd; br = 1. - (h * (32./255.) + mod(xd, 4.) * (32./255.)); } else if (h < 0.5) br = br * (1.5 - mod(uv.x, 2.)); }\n" +
                    "    if (i == 5) { color = vec3(181./255., 58./255., 21./255.); if (mod(uv.x + (floor(uv.y / 4.) * 5.), 8.) == 0. || mod(uv.y, 4.) == 0.) color = vec3(188./255., 175./255., 165./255.); }\n" +
                    "    if (i == 9) color = vec3(64./255., 64./255., 255./255.);\n" +
                    "    float brr = br; if (uv.y >= 32.) brr /= 2.;\n" +
                    "    if (i == 8) { color = vec3(80./255., 217./255., 55./255.); if (h < 0.5) return false; }\n" +
                    "    color *= brr; return true;\n" +
                    "}\n" +
                    "int getMap(vec3 pos) {\n" +
                    "    vec3 posf = floor((pos - vec3(32.))); float n = posf.x + posf.y*517.0 + 1313.0*posf.z; float h = hash(n);\n" +
                    "    if(h > sqrt(sqrt(dot(posf.yz, posf.yz)*0.16)) - 0.8) return 0;\n" +
                    "    return int(hash(n * 465.233) * 16.);\n" +
                    "}\n" +
                    "vec3 renderMinecraft(vec2 uv) {\n" +
                    "    float xRot = sin(time*0.5) * 0.4 + (3.1415 / 2.); float yRot = cos(time*0.5) * 0.4;\n" +
                    "    float yCos = cos(yRot); float ySin = sin(yRot); float xCos = cos(xRot); float xSin = sin(xRot);\n" +
                    "    vec3 opos = vec3(32.5 + time * 6.4, 32.5, 32.5);\n" +
                    "    float gggxd = (uv.x - 0.5) * (resolution.x / resolution.y); float ggyd = (1.-uv.y - 0.5); float ggzd = 1.;\n" +
                    "    float gggzd = ggzd * yCos + ggyd * ySin;\n" +
                    "    vec3 _posd = vec3(gggxd * xCos + gggzd * xSin, ggyd * yCos - ggzd * ySin, gggzd * xCos - gggxd * xSin);\n" +
                    "    vec3 col = vec3(0.); float br = 1.; vec3 bdist = vec3(255. - 100., 255. - 0., 255. - 50.); float ddist = 0.; float closest = 32.;\n" +
                    "    for (int d = 0; d < 3; d++) {\n" +
                    "        float dimLength = _posd[d]; float ll = abs(1. / dimLength); vec3 posd = _posd * ll;\n" +
                    "        float initial = fract(opos[d]); if (dimLength > 0.) initial = 1. - initial; float dist = ll * initial; vec3 pos = opos + posd * initial;\n" +
                    "        if (dimLength < 0.) pos[d] -= 1.;\n" +
                    "        for (int i=0; i<30; i++) {\n" +
                    "            if(dist > closest) continue;\n" +
                    "            int tex = getMap(pos);\n" +
                    "            if (tex > 0) {\n" +
                    "                vec2 texcoord; texcoord.x = mod(((pos.x + pos.z) * 16.), 16.); texcoord.y = mod((pos.y * 16.), 16.) + 16.;\n" +
                    "                if (d == 1) { texcoord.x = mod(pos.x * 16., 16.); texcoord.y = mod(pos.z * 16., 16.); if (posd.y < 0.) texcoord.y += 32.; }\n" +
                    "                if (getMaterialColor(tex, texcoord, col)) { ddist = 1. - (dist / 32.); br = bdist[d]; closest = dist; }\n" +
                    "            }\n" +
                    "            pos += posd; dist += ll;\n" +
                    "        }\n" +
                    "    }\n" +
                    "    return col * ddist * (br/255.);\n" +
                    "}\n" +
                    "void main() { gl_FragColor = vec4(renderMinecraft(gl_FragCoord.xy / resolution.xy), 1.0); }";

    private static final String SHADER_5 =
            "#version 120\n" +
                    "uniform float time;\n" +
                    "uniform vec2 resolution;\n" +
                    "#define PI 3.1415926535\n" +
                    "#define T time*3.\n" +
                    "#define PELLET_SIZE 1./16.\n" +
                    "#define PELLET_NUM 2\n" +
                    "#define THICKNESS 0.13\n" +
                    "#define RADIUS 0.7\n" +
                    "float sdArc(in vec2 p, in float a, in float ra, float rb) {\n" +
                    "    a *= PI; vec2 sc = vec2(sin(a),cos(a)); p.x = abs(p.x);\n" +
                    "    return ((sc.y*p.x>sc.x*p.y) ? length(p-sc*ra) : abs(length(p)-ra)) - rb;\n" +
                    "}\n" +
                    "mat2 rot(float a) { a *= PI; float s = sin(a), c = cos(a); return mat2(c,-s,s,c); }\n" +
                    "float s(float x) { return smoothstep(0.,1.,x); }\n" +
                    "float sminCubic(float a, float b, float k) { float h = max(k-abs(a-b), .0)/k; return min(a, b) - h*h*h*k*(1./6.); }\n" +
                    "vec3 pal(float x) { return mix(vec3(.988,.569,.086), vec3(1,.082,.537), x); }\n" +
                    "float f(float x) { return -2.*PELLET_SIZE*x; }\n" +
                    "float dist(vec2 p) {\n" +
                    "    const int n = PELLET_NUM; float N = float(n);\n" +
                    "    float d1 = sdArc(p*rot(f(floor(T)) + 1.), .5 - PELLET_SIZE, RADIUS, THICKNESS);\n" +
                    "    float d2 = 9e9;\n" +
                    "    for (int i = 0; i < n; i++) {\n" +
                    "        float j = float(i); float t = s(fract((T + j)/N));\n" +
                    "        float a = mix(-.5, .5 - f(1.), t) + f(T);\n" +
                    "        d2 = min(sdArc(p * rot(a), PELLET_SIZE, RADIUS, THICKNESS), d2);\n" +
                    "    }\n" +
                    "    float r = abs(length(p) - RADIUS) - THICKNESS;\n" +
                    "    float d = sminCubic(d1, d2, .2);\n" +
                    "    return max(d, r);\n" +
                    "}\n" +
                    "void main() {\n" +
                    "    vec2 uv = gl_FragCoord.xy/resolution.xy - .5;\n" +
                    "    vec2 p = (2.*gl_FragCoord.xy - resolution.xy)/resolution.y;\n" +
                    "    float d = dist(p); float m = smoothstep(.01,.0,d);\n" +
                    "    float d1 = dist(p + vec2(0.,.15)); float s = smoothstep(.2,-.4,d1);\n" +
                    "    m = max(s,m);\n" +
                    "    vec3 col = m*pal(p.x - p.y + .5);\n" +
                    "    col += 1. - m; col *= 1. - 1.5 * dot(uv,uv);\n" +
                    "    gl_FragColor = vec4(col, 1.);\n" +
                    "}";

    private BackgroundRenderer() {
    }

    public static void init() {
        MenuConfig.load();
        int savedIndex = MenuConfig.getBackgroundIndex();
        if (!initialized) {
            initialized = true;
            reloadShader(savedIndex);
        } else if (savedIndex != currentBackgroundIndex) {
            reloadShader(savedIndex);
        }
    }

    public static void reloadShader(int index) {
        int clamped = Math.max(BACKGROUND_CLASSIC, Math.min(BACKGROUND_CIRCLE, index));
        currentBackgroundIndex = clamped;
        MenuConfig.setBackgroundIndex(clamped);

        releaseShader();
        shaderStartTime = System.currentTimeMillis();

        if (clamped == BACKGROUND_CLASSIC) {
            return;
        }

        try {
            backgroundShader = new ShaderUtil(shaderForIndex(clamped));
        } catch (Exception ignored) {
            backgroundShader = null;
        }
    }

    public static void drawClassic(long initTime) {
        GlStateManager.disableAlpha();
        MainMenu.draw(initTime);
        GlStateManager.enableAlpha();
    }

    public static void draw(int width, int height) {
        init();

        if (currentBackgroundIndex == BACKGROUND_CLASSIC) {
            drawFallback(width, height);
            return;
        }

        if (backgroundShader == null || backgroundShader.getProgramID() == 0) {
            reloadShader(currentBackgroundIndex);
        }

        if (backgroundShader == null || backgroundShader.getProgramID() == 0) {
            drawFallback(width, height);
            return;
        }

        GlStateManager.disableCull();
        GlStateManager.disableAlpha();

        try {
            backgroundShader.init();
            backgroundShader.setUniformf("time", (System.currentTimeMillis() - shaderStartTime) / 1000.0f);
            Minecraft mc = Minecraft.getMinecraft();
            backgroundShader.setUniformf("resolution", (float) mc.displayWidth, (float) mc.displayHeight);

            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer worldRenderer = tessellator.getWorldRenderer();
            worldRenderer.begin(7, DefaultVertexFormats.POSITION);
            worldRenderer.pos(0.0D, height, 0.0D).endVertex();
            worldRenderer.pos(width, height, 0.0D).endVertex();
            worldRenderer.pos(width, 0.0D, 0.0D).endVertex();
            worldRenderer.pos(0.0D, 0.0D, 0.0D).endVertex();
            tessellator.draw();
        } finally {
            backgroundShader.unload();
            GlStateManager.enableAlpha();
            GlStateManager.enableCull();
        }
    }

    public static String getBackgroundName(int index) {
        switch (index) {
            case BACKGROUND_CLASSIC:
                return "Classic";
            case BACKGROUND_MYAU_PLUS:
                return "Myau+";
            case BACKGROUND_RISE:
                return "Rise";
            case BACKGROUND_COSMOS:
                return "Cosmos";
            case BACKGROUND_MINECRAFT:
                return "Minecraft";
            case BACKGROUND_CIRCLE:
                return "Circle";
            default:
                return "Unknown";
        }
    }

    private static void releaseShader() {
        if (backgroundShader != null) {
            backgroundShader.delete();
            backgroundShader = null;
        }
    }

    private static String shaderForIndex(int index) {
        switch (index) {
            case BACKGROUND_MYAU_PLUS:
                return SHADER_1;
            case BACKGROUND_RISE:
                return SHADER_2;
            case BACKGROUND_COSMOS:
                return SHADER_3;
            case BACKGROUND_MINECRAFT:
                return SHADER_4;
            case BACKGROUND_CIRCLE:
                return SHADER_5;
            default:
                return SHADER_1;
        }
    }

    private static void drawFallback(int width, int height) {
        RenderUtil.drawGradientRect(0, 0, width, height, new Color(20, 20, 30).getRGB(), new Color(5, 5, 10).getRGB());
    }
}
