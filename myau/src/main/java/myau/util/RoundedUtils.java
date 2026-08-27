package myau.util;

public class RoundedUtils {

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        RenderUtil.drawRoundedRect(x, y, width, height, radius, color, true, true, true, true);
    }

    public static void drawOutlineRect(float x, float y, float width, float height, float lineWidth, int color) {
        RenderUtil.drawRoundedRectOutline(x, y, width, height, 0.0F, lineWidth, color, false, false, false, false);
    }
}
