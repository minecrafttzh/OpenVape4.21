/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  org.lwjgl.opengl.GL11
 */
package myau.util.render;

import java.awt.Color;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

public class BlurShadowRenderer {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void renderFrostedGlass(float x, float y, float w, float h, float radius, int blurStrength, int alpha) {
        if (blurStrength <= 0) {
            return;
        }
        GL11.glDisable(3553);
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        Color bgColor = new Color(0, 0, 0, Math.min(255, alpha));
        for (int i = 0; i < blurStrength; ++i) {
            float offset = (float)i * 0.6f;
            int currentAlpha = (int)((float)bgColor.getAlpha() * (1.0f - (float)i / (float)blurStrength));
            int color = new Color(0, 0, 0, currentAlpha).getRGB();
            BlurShadowRenderer.drawRoundedRect(x - offset, y - offset, w + offset * 2.0f, h + offset * 2.0f, radius, color);
        }
        GL11.glEnable(3553);
        GL11.glDisable(3042);
    }

    private static void drawRoundedRect(float x, float y, float w, float h, float radius, int color) {
        float alpha = (float)(color >> 24 & 0xFF) / 255.0f;
        float red = (float)(color >> 16 & 0xFF) / 255.0f;
        float green = (float)(color >> 8 & 0xFF) / 255.0f;
        float blue = (float)(color & 0xFF) / 255.0f;
        GL11.glColor4f(red, green, blue, alpha);
        GL11.glBegin(7);
        GL11.glVertex2f(x + radius, y);
        GL11.glVertex2f(x + w - radius, y);
        GL11.glVertex2f(x + w - radius, y + h);
        GL11.glVertex2f(x + radius, y + h);
        GL11.glVertex2f(x, y + radius);
        GL11.glVertex2f(x + radius, y + radius);
        GL11.glVertex2f(x + radius, y + h - radius);
        GL11.glVertex2f(x, y + h - radius);
        GL11.glVertex2f(x + w - radius, y + radius);
        GL11.glVertex2f(x + w, y + radius);
        GL11.glVertex2f(x + w, y + h - radius);
        GL11.glVertex2f(x + w - radius, y + h - radius);
        GL11.glEnd();
        BlurShadowRenderer.drawArc(x + radius, y + radius, radius, 180.0f, 270.0f, red, green, blue, alpha);
        BlurShadowRenderer.drawArc(x + w - radius, y + radius, radius, 270.0f, 360.0f, red, green, blue, alpha);
        BlurShadowRenderer.drawArc(x + w - radius, y + h - radius, radius, 0.0f, 90.0f, red, green, blue, alpha);
        BlurShadowRenderer.drawArc(x + radius, y + h - radius, radius, 90.0f, 180.0f, red, green, blue, alpha);
    }

    private static void drawArc(float cx, float cy, float r, float start, float end, float red, float green, float blue, float alpha) {
        GL11.glColor4f(red, green, blue, alpha);
        GL11.glBegin(6);
        GL11.glVertex2f(cx, cy);
        for (float i = start; i <= end; i += 5.0f) {
            double a = Math.toRadians(i);
            GL11.glVertex2f((float)(cx + Math.cos(a) * r), (float)(cy + Math.sin(a) * r));
        }
        GL11.glEnd();
    }
}
