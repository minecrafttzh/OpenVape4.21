package myau.util;

import net.minecraft.client.gui.Gui;
import org.lwjgl.util.Color;

public class RenderUtils {
    public static void drawRect(float left, float top, float width, float height, Color color) {
        drawRect(left, top, width, height, ((color.getRed() & 0xFF) << 16) | ((color.getGreen() & 0xFF) << 8) | (color.getBlue() & 0xFF) | ((color.getAlpha() & 0xFF) << 24));
    }

    public static void drawRect(float left, float top, float width, float height, int color) {
        float right = left + width;
        float bottom = top + height;
        float x1 = Math.min(left, right);
        float y1 = Math.min(top, bottom);
        float x2 = Math.max(left, right);
        float y2 = Math.max(top, bottom);

        Gui.drawRect(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), color);
    }

    public static Color interpolateColorC(Color color1, Color color2, float amount) {
        amount = Math.min(1, Math.max(0, amount));
        return new Color(interpolateInt(color1.getRed(), color2.getRed(), amount),
                interpolateInt(color1.getGreen(), color2.getGreen(), amount),
                interpolateInt(color1.getBlue(), color2.getBlue(), amount),
                interpolateInt(color1.getAlpha(), color2.getAlpha(), amount));
    }

    private static int interpolateInt(int oldValue, int newValue, double interpolationValue) {
        return (int) (oldValue + (newValue - oldValue) * interpolationValue);
    }


}
