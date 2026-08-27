package myau.util.font.impl;

import myau.util.font.CenterMode;
import myau.util.font.FontResourceManager;
import myau.util.font.IFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class FontRenderer extends CharRenderer implements IFont {

    final CharData[] boldChars = new CharData[256];
    final CharData[] italicChars = new CharData[256];
    final CharData[] boldItalicChars = new CharData[256];
    final int[] colorCode = new int[32];
    final String colorcodeIdentifiers = "0123456789abcdefklmnor";
    DynamicTexture texBold, texItalic, texItalicBold;

    public FontRenderer(Font font) {
        super(font, true, true);
        this.setupMinecraftColorcodes();
        FontResourceManager.registerFont(this);
    }

    public void destroy() {
        super.destroy();
        if (this.texBold != null) this.texBold.deleteGlTexture();
        if (this.texItalic != null) this.texItalic.deleteGlTexture();
        if (this.texItalicBold != null) this.texItalicBold.deleteGlTexture();
    }

    public void drawString(String text, double x, double y, @NotNull CenterMode centerMode, boolean dropShadow, int color) {
        switch (centerMode) {
            case X:
                if (dropShadow) this.drawString(text, x - this.getStringWidth(text) / 2 + 0.5, y + 0.5, color, true);
                this.drawString(text, x - this.getStringWidth(text) / 2, y, color, false);
                return;
            case Y:
                if (dropShadow) this.drawString(text, x + 0.5, y - this.getHeight() / 2 + 0.5, color, true);
                this.drawString(text, x, y - this.getHeight() / 2, color, false);
                return;
            case XY:
                if (dropShadow)
                    this.drawString(text, x - this.getStringWidth(text) / 2 + 0.5, y - this.getHeight() / 2 + 0.5, color, true);
                this.drawString(text, x - this.getStringWidth(text) / 2, y - this.getHeight() / 2, color, false);
                return;
            default:
            case NONE:
                if (dropShadow) this.drawString(text, x + 0.5, y + 0.5, color, true);
                this.drawString(text, x, y, color, false);
        }
    }

    public void drawString(String text, double x, double y, int color, boolean shadow) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());

        if (text == null) return;

        if (shadow) {
            drawString(text, x + 1, y + 1, (color & 0xFCFCFC) >> 2 | color & 0xFF000000, false);
        }

        CharData[] currentData = this.charData;
        double alpha = (color >> 24 & 255) / 255f;
        x = (x - 1) * sr.getScaleFactor();
        y = (y - 3) * sr.getScaleFactor() - 0.2;

        GL11.glPushMatrix();
        GL11.glScaled((double) 1 / sr.getScaleFactor(), 1 / (double) sr.getScaleFactor(), 1 / (double) sr.getScaleFactor());
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.color(red, green, blue, (float) alpha);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(this.tex.getGlTextureId());
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.tex.getGlTextureId());

        GlStateManager.enableBlend();

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);

            if (character == '§') {
                int colorIndex = 21;
                try {
                    colorIndex = colorcodeIdentifiers.indexOf(text.charAt(index + 1));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (colorIndex < 16) {
                    GlStateManager.bindTexture(this.tex.getGlTextureId());
                    GlStateManager.color(red, green, blue, (float) alpha);
                } else {
                    GlStateManager.color(red, green, blue, (float) alpha);
                    GlStateManager.bindTexture(this.tex.getGlTextureId());
                }
                ++index;
            } else if (character < currentData.length) {
                // ASCII 字符
                drawLetter(x, y, currentData, character);
                x += currentData[character].width - 8.3 + this.charOffset;
            } else {
                // ------------------ Unicode 回退逻辑 ------------------

                // 1. 退出当前的自定义渲染缩放
                GL11.glPopMatrix();

                // 2. 调用原版 FontRenderer 绘制 Unicode
                // 注意：需要将高分辨率坐标 x, y 转换回逻辑坐标
                float logicalX = (float) (x / sr.getScaleFactor());
                float logicalY = (float) (y / sr.getScaleFactor()) + 3.0f; // 这里的 +3 是为了对齐垂直位置

                Minecraft.getMinecraft().fontRendererObj.drawString(String.valueOf(character), logicalX, logicalY, color, false);

                // 3. 重新进入自定义渲染状态
                GL11.glPushMatrix();
                GL11.glScaled((double) 1 / sr.getScaleFactor(), 1 / (double) sr.getScaleFactor(), 1 / (double) sr.getScaleFactor());

                // 4. 恢复颜色和纹理（因为原版绘制会重置它们）
                GlStateManager.color(red, green, blue, (float) alpha);
                GlStateManager.enableBlend();
                GlStateManager.bindTexture(this.tex.getGlTextureId()); // 简单起见，回退后重置为普通纹理，暂不处理 Unicode 粗斜体状态

                // 5. 增加宽度
                x += Minecraft.getMinecraft().fontRendererObj.getCharWidth(character) * sr.getScaleFactor();
            }
        }
        GlStateManager.disableBlend();
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_DONT_CARE);
        GL11.glPopMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void drawString(String text, double x, double y, int color) {
        drawString(text, x, y, color, false);
    }

    @Override
    public double width(String text) {
        return getStringWidth(text);
    }

    @Override
    public void drawCenteredString(String text, double x, double y, int color) {
        drawString(text, x, y, CenterMode.X, false, color);
    }

    @Override
    public double height() {
        return getHeight();
    }

    private void drawLetter(double x, double y, CharData[] currentData, char character) {
        GL11.glBegin(4);
        this.drawChar(currentData, character, x, y);
        GL11.glEnd();
    }

    public double getStringWidth(String text) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        if (text == null) return 0;

        double width = 0;
        CharData[] currentData = charData;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);

            if (character == '§') {
                index++;
            } else if (character < currentData.length) {
                // 原始宽度逻辑
                width += currentData[character].width - 8.3f + charOffset;
            } else {
                // Unicode 宽度逻辑
                width += Minecraft.getMinecraft().fontRendererObj.getCharWidth(character) * sr.getScaleFactor();
            }
        }

        return width / (double) sr.getScaleFactor();
    }

    public double getHeight() {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        return (this.fontHeight - 8) / (double) sr.getScaleFactor();
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
    }

    @Override
    public void setAntiAlias(boolean antiAlias) {
        super.setAntiAlias(antiAlias);
    }

    @Override
    public void setFractionalMetrics(boolean fractionalMetrics) {
        super.setFractionalMetrics(fractionalMetrics);
    }

    private void setupBoldItalicIDs() {
        if (this.texBold != null) this.texBold.deleteGlTexture();
        if (this.texItalic != null) this.texItalic.deleteGlTexture();
        if (this.texItalicBold != null) this.texItalicBold.deleteGlTexture();

        this.texBold = this.setupTexture(this.font.deriveFont(Font.BOLD), this.antiAlias, this.fractionalMetrics, this.boldChars);
        this.texItalic = this.setupTexture(this.font.deriveFont(Font.ITALIC), this.antiAlias, this.fractionalMetrics, this.italicChars);
        this.texItalicBold = this.setupTexture(this.font.deriveFont(Font.BOLD | Font.ITALIC), this.antiAlias, this.fractionalMetrics, this.boldItalicChars);
    }

    private void setupMinecraftColorcodes() {
        int index = 0;
        while (index < 32) {
            int noClue = (index >> 3 & 1) * 85;
            int red = (index >> 2 & 1) * 170 + noClue;
            int green = (index >> 1 & 1) * 170 + noClue;
            int blue = (index & 1) * 170 + noClue;
            if (index == 6) red += 85;
            if (index >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }
            this.colorCode[index] = (red & 255) << 16 | (green & 255) << 8 | blue & 255;
            ++index;
        }
    }
}
