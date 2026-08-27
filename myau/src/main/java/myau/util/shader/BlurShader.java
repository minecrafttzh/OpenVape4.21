package myau.util.shader;

import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

public class BlurShader {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final String VERTEX = "#version 120\n" +
            "varying vec2 texCoord;\n" +
            "varying vec2 oneTexel;\n" +
            "uniform vec2 InSize;\n" +
            "void main() {\n" +
            "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "    texCoord = gl_MultiTexCoord0.st;\n" +
            "    oneTexel = 1.0 / InSize;\n" +
            "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}";

    private static final String FRAGMENT = "#version 120\n" +
            "uniform sampler2D DiffuseSampler;\n" +
            "varying vec2 texCoord;\n" +
            "varying vec2 oneTexel;\n" +
            "uniform vec2 InSize;\n" +
            "uniform vec2 BlurDir;\n" +
            "uniform vec2 BlurXY;\n" +
            "uniform vec2 BlurCoord;\n" +
            "uniform float Radius;\n" +
            "float SCurve (float x) {\n" +
            "    x = x * 2.0 - 1.0;\n" +
            "    return -x * abs(x) * 0.5 + x + 0.5;\n" +
            "}\n" +
            "vec4 BlurH (sampler2D source, vec2 size, vec2 uv, float radius) {\n" +
            "    if (uv.x / oneTexel.x >= BlurXY.x && uv.y / oneTexel.y >= BlurXY.y && uv.x / oneTexel.x <= (BlurCoord.x + BlurXY.x) && uv.y / oneTexel.y <= (BlurCoord.y + BlurXY.y))\n" +
            "    {\n" +
            "        vec4 A = vec4(0.0);\n" +
            "        vec4 C = vec4(0.0);\n" +
            "        float divisor = 0.0;\n" +
            "        float weight = 0.0;\n" +
            "        float radiusMultiplier = 1.0 / radius;\n" +
            "        for (float x = -radius; x <= radius; x++)\n" +
            "        {\n" +
            "            A = texture2D(source, uv + vec2(x * size) * BlurDir);\n" +
            "            weight = SCurve(1.0 - (abs(x) * radiusMultiplier));\n" +
            "            C += A * weight;\n" +
            "            divisor += weight;\n" +
            "        }\n" +
            "        return vec4(C.r / divisor, C.g / divisor, C.b / divisor, 1.0);\n" +
            "    }\n" +
            "    return texture2D(source, uv);\n" +
            "}\n" +
            "void main() {\n" +
            "    if (texCoord.x / oneTexel.x >= BlurXY.x - Radius && texCoord.y / oneTexel.y >= BlurXY.y - Radius && texCoord.x / oneTexel.x <= (BlurCoord.x + BlurXY.x) + Radius && texCoord.y / oneTexel.y <= (BlurCoord.y + BlurXY.y) + Radius) {\n" +
            "        gl_FragColor = BlurH(DiffuseSampler, oneTexel, texCoord, Radius);\n" +
            "    } else {\n" +
            "        gl_FragColor = texture2D(DiffuseSampler, texCoord);\n" +
            "    }\n" +
            "}";

    private static ShaderUtil shader;
    private static Framebuffer pass1;
    private static Framebuffer pass2;

    private static void ensureFramebuffers() {
        if (pass1 == null || pass1.framebufferWidth != mc.displayWidth || pass1.framebufferHeight != mc.displayHeight) {
            if (pass1 != null) {
                pass1.deleteFramebuffer();
            }
            pass1 = new Framebuffer(mc.displayWidth, mc.displayHeight, true);
            pass1.setFramebufferFilter(GL11.GL_LINEAR);
        }
        if (pass2 == null || pass2.framebufferWidth != mc.displayWidth || pass2.framebufferHeight != mc.displayHeight) {
            if (pass2 != null) {
                pass2.deleteFramebuffer();
            }
            pass2 = new Framebuffer(mc.displayWidth, mc.displayHeight, true);
            pass2.setFramebufferFilter(GL11.GL_LINEAR);
        }
    }

    private static ShaderUtil ensureShader() {
        if (shader == null) {
            shader = new ShaderUtil(VERTEX, FRAGMENT);
        }
        return shader;
    }

    private static void drawFlippedQuads(float width, float height) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, 0);       // 左上 (UV V=1)
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, height);  // 左下 (UV V=0)
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(width, height); // 右下
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(width, 0);   // 右上
        GL11.glEnd();
    }

    private static void drawQuads(float width, float height) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, height);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(width, height);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(width, 0);
        GL11.glEnd();
    }

    public static void renderBlur(float radius, float x, float y, float w, float h, float effectiveScale) {
        if (radius <= 0) return;

        ensureFramebuffers();
        ShaderUtil s = ensureShader();

        ScaledResolution sr = new ScaledResolution(mc);
        float screenW = mc.displayWidth;
        float screenH = mc.displayHeight;

        float factor = sr.getScaleFactor() * effectiveScale;
        float blurX = x * factor;
        float blurY = y * factor;
        float blurW = w * factor;
        float blurH = h * factor;
        float blurYInverted = screenH - blurY - blurH;

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();

        pass1.framebufferClear();
        pass1.bindFramebuffer(false);
        GL11.glClearColor(0, 0, 0, 0);

        s.init();
        s.setUniformi("DiffuseSampler", 0);
        s.setUniformf("InSize", screenW, screenH);
        s.setUniformf("BlurDir", 1.0f, 0.0f);
        s.setUniformf("BlurXY", blurX, blurYInverted);
        s.setUniformf("BlurCoord", blurW, blurH);
        s.setUniformf("Radius", radius);

        GlStateManager.bindTexture(mc.getFramebuffer().framebufferTexture);
        drawFlippedQuads((float) sr.getScaledWidth_double(), (float) sr.getScaledHeight_double());
        s.unload();

        pass2.framebufferClear();
        pass2.bindFramebuffer(false);
        GL11.glClearColor(0, 0, 0, 0);

        s.init();
        s.setUniformi("DiffuseSampler", 0);
        s.setUniformf("InSize", screenW, screenH);
        s.setUniformf("BlurDir", 0.0f, 1.0f);
        s.setUniformf("BlurXY", blurX, blurYInverted);
        s.setUniformf("BlurCoord", blurW, blurH);
        s.setUniformf("Radius", radius);

        GlStateManager.bindTexture(pass1.framebufferTexture);
        drawQuads((float) sr.getScaledWidth_double(), (float) sr.getScaledHeight_double());
        s.unload();

        mc.getFramebuffer().bindFramebuffer(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        GlStateManager.bindTexture(pass2.framebufferTexture);
        GL11.glColor4f(1, 1, 1, 1);

        drawQuads((float) sr.getScaledWidth_double(), (float) sr.getScaledHeight_double());

        GlStateManager.bindTexture(0);
        GlStateManager.disableBlend();
    }
}