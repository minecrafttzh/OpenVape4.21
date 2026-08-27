package myau.util.shader;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public class ShadowShader {

    private static final String SHADOW_SHADER_SOURCE =
            "#version 120\n" +
                    "\n" +
                    "uniform vec2 rectSize;\n" +
                    "uniform float radius;\n" +
                    "uniform float softness;\n" +
                    "uniform vec4 color;\n" +
                    "\n" +
                    "float roundedBoxSDF(vec2 centerPos, vec2 size, float radius) {\n" +
                    "    return length(max(abs(centerPos) - size + radius, 0.0)) - radius;\n" +
                    "}\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec2 totalSize = rectSize + vec2(softness * 2.0);\n" +
                    "    vec2 pos = (gl_TexCoord[0].st * totalSize) - (totalSize / 2.0);\n" +
                    "    \n" +
                    "    float dist = roundedBoxSDF(pos, rectSize / 2.0, radius);\n" +
                    "    \n" +
                    "    float smoothedAlpha = (1.0 - smoothstep(0.0, softness, dist)) * color.a;\n" +
                    "    \n" +
                    "    gl_FragColor = vec4(color.rgb, smoothedAlpha);\n" +
                    "}";

    private static final ShaderUtil shadowShader = new ShaderUtil(SHADOW_SHADER_SOURCE);

    public static void drawShadow(float x, float y, float width, float height, float radius, float softness, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();

        shadowShader.init();

        shadowShader.setUniformf("rectSize", width, height);
        shadowShader.setUniformf("radius", radius);
        shadowShader.setUniformf("softness", softness);

        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        shadowShader.setUniformf("color", r, g, b, a);

        float x1 = x - softness;
        float y1 = y - softness;
        float x2 = x + width + softness;
        float y2 = y + height + softness;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x1, y1);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x1, y2);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x2, y2);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x2, y1);
        GL11.glEnd();

        shadowShader.unload();

        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}