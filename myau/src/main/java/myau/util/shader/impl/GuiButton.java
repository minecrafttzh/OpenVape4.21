package myau.util.shader.impl;

import myau.font.CFontRenderer;
import myau.font.FontProcess;
import myau.util.RenderUtil;
import myau.util.animations.Animation;
import myau.util.animations.impl.DecelerateAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import myau.util.animations.Direction;

import java.awt.*;

public class GuiButton extends net.minecraft.client.gui.GuiButton
{
    private final Animation hoverAnimation = new DecelerateAnimation(300, 1, Direction.BACKWARDS);
    private static final org.lwjgl.util.Color PRIMARY_COLOR = new org.lwjgl.util.Color((byte)228, (byte)143, (byte)255);
    private static final org.lwjgl.util.Color SECONDARY_COLOR = new org.lwjgl.util.Color((byte)255, (byte)113, (byte)82);

    public GuiButton(int buttonId, int x, int y, String buttonText)
    {
        this(buttonId, x, y, 200, 20, buttonText);
    }

    public GuiButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText)
    {
        super(buttonId, x, y, widthIn, heightIn, buttonText);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY)
    {
        if (this.visible)
        {
            CFontRenderer fontRenderer = FontProcess.getFont("sans");

            int textWidth = fontRenderer.getStringWidth(this.displayString);
            int textX = this.xPosition + (this.width - textWidth) / 2;
            int textY = this.yPosition + (this.height - fontRenderer.FONT_HEIGHT) / 2;

            boolean isMouseOverText = mouseX >= textX - 4 && mouseY >= textY - 4 &&
                    mouseX < textX + textWidth + 8 && mouseY < textY + fontRenderer.FONT_HEIGHT + 8;

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);

            org.lwjgl.util.Color gradientColor = RenderUtil.interpolateColorsBackAndForth(15, 75, PRIMARY_COLOR, SECONDARY_COLOR, false);

             int textColor = isMouseOverText ? (0xFF000000 | (gradientColor.getRed() << 16)  | (gradientColor.getGreen() << 8) | gradientColor.getBlue()) : 0xFFFFFFFF;
            fontRenderer.drawCenteredString(this.displayString,
                    this.xPosition + this.width / 2,
                    this.yPosition + (this.height - 8) / 2,
                    textColor);

            if (isMouseOverText) {
                hoverAnimation.setDirection(Direction.FORWARDS);
            } else {
                hoverAnimation.setDirection(Direction.BACKWARDS);
            }

            int highlightHeight = 1;
            int highlightY = textY + fontRenderer.FONT_HEIGHT + 3;
            float animWidth = (float) ((textWidth + 8) * hoverAnimation.getOutput());

            RenderUtil.drawRect(textX - 4, highlightY, (float) animWidth, (float) highlightHeight, 0xFFFFFFFF);

            this.mouseDragged(mc, mouseX, mouseY);
        }
    }
}