/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.client.renderer.texture.DynamicTexture
 *  org.lwjgl.opengl.GL11
 */
package myau.util.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;

public class CustomFontRenderer {
    private final DynamicTexture texture;
    private final int[] widths = new int[256];
    private final int height;

    public CustomFontRenderer(String fontPath, int size) {
        try (InputStream is = this.getClass().getResourceAsStream(fontPath)) {
            if (is == null) {
                throw new RuntimeException("Font not found: " + fontPath);
            }
            Font baseFont = Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(Font.PLAIN, (float) size);
            this.height = 16 + size / 2;
            this.texture = this.generateTexture(baseFont);
        }
        catch (FontFormatException | IOException e) {
            throw new RuntimeException("Failed to load font: " + fontPath, e);
        }
    }

    private DynamicTexture generateTexture(Font font) {
        BufferedImage img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setFont(font);
        g.setColor(Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        int x = 0;
        int y = 0;
        for (int i = 0; i < 256; ++i) {
            char c = (char) i;
            if (c == '\n' || c == '\r') continue;
            int w = g.getFontMetrics().charWidth(c);
            if (w <= 0) {
                w = 1;
            }
            this.widths[i] = w;
            if (x + w > 512) {
                x = 0;
                y += this.height;
            }
            g.drawString(String.valueOf(c), x, y + this.height - 4);
            x += w;
        }
        g.dispose();
        return new DynamicTexture(img);
    }

    public void drawString(String text, float x, float y, int color) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.bindTexture(this.texture.getGlTextureId());
        float alpha = (float) (color >> 24 & 0xFF) / 255.0f;
        float red = (float) (color >> 16 & 0xFF) / 255.0f;
        float green = (float) (color >> 8 & 0xFF) / 255.0f;
        float blue = (float) (color & 0xFF) / 255.0f;
        GL11.glColor4f(red, green, blue, alpha);
        float posX = x;
        for (char c : text.toCharArray()) {
            int w;
            if (c >= '\u0100' || (w = this.widths[c]) <= 0) continue;
            float u = 0.0f;
            float v = 0.0f;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(u, v);
            GL11.glVertex2f(posX, y);
            GL11.glTexCoord2f(u, 1.0f);
            GL11.glVertex2f(posX, y + (float) this.height);
            GL11.glTexCoord2f(1.0f, 1.0f);
            GL11.glVertex2f(posX + (float) w, y + (float) this.height);
            GL11.glTexCoord2f(1.0f, v);
            GL11.glVertex2f(posX + (float) w, y);
            GL11.glEnd();
            posX += (float) w;
        }
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public int getStringWidth(String text) {
        int width = 0;
        for (char c : text.toCharArray()) {
            if (c >= '\u0100') continue;
            width += this.widths[c];
        }
        return width;
    }

    public int getFontHeight() {
        return this.height;
    }
}

