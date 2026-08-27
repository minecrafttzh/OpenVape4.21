package myau.ui.impl.gui;

import myau.util.AnimationUtil;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import myau.util.font.impl.FontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.Color;

public class ModernGuiButton extends GuiButton {
    private float hoverProgress;

    public ModernGuiButton(int buttonId, int x, int y, int width, int height, String buttonText) {
        super(buttonId, x, y, width, height, buttonText);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }

        this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
        hoverProgress = AnimationUtil.animate(this.hovered ? 1.0f : 0.0f, hoverProgress, 0.25f, 1.0f);

        int bgNormal = new Color(20, 20, 20, 120).getRGB();
        int bgHover = new Color(40, 40, 45, 200).getRGB();
        int outlineNormal = new Color(255, 255, 255, 60).getRGB();
        int outlineHover = new Color(255, 255, 255, 180).getRGB();
        int textNormal = this.enabled ? new Color(200, 200, 200).getRGB() : new Color(130, 130, 130).getRGB();

        int finalBg = AnimationUtil.interpolateColor(bgNormal, bgHover, hoverProgress);
        int finalOutline = AnimationUtil.interpolateColor(outlineNormal, outlineHover, hoverProgress);
        int finalText = AnimationUtil.interpolateColor(textNormal, -1, hoverProgress);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        RenderUtil.drawRoundedRect(this.xPosition, this.yPosition, this.width, this.height, 5.0f, finalBg, true, true, true, true);
        RenderUtil.drawRoundedRectOutline(this.xPosition, this.yPosition, this.width, this.height, 5.0f, 1.0f, finalOutline, true, true, true, true);

        if (FontManager.productSans20 != null) {
            FontRenderer fr = FontManager.productSans20;
            ScaledResolution sr = new ScaledResolution(mc);
            float baseScale = 9.0f / (float) fr.getHeight();
            float guiScaleCorrection = (float) sr.getScaleFactor() / 2.0f;
            float finalScale = baseScale * guiScaleCorrection;
            float centerX = this.xPosition + this.width / 2.0f;
            float centerY = this.yPosition + this.height / 2.0f;

            GlStateManager.pushMatrix();
            GlStateManager.translate(centerX, centerY, 0.0f);
            GlStateManager.scale(finalScale, finalScale, 1.0f);
            fr.drawCenteredString(this.displayString, 0.0, -(fr.getHeight() / 2.0) + 1.0, finalText);
            GlStateManager.popMatrix();
        } else {
            this.drawCenteredString(mc.fontRendererObj, this.displayString,
                    this.xPosition + this.width / 2,
                    this.yPosition + (this.height - 8) / 2,
                    finalText);
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        this.mouseDragged(mc, mouseX, mouseY);
    }
}
