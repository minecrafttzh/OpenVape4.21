package myau.ui.impl.clickgui.rise;

import java.awt.*;

/**
 * Color scheme matching Rise v6 ClickGUI dark theme exactly.
 */
public final class RiseColors {
    // Exact Rise v6 colors
    public static final Color BACKGROUND = new Color(23, 26, 33, 254);
    public static final Color SECONDARY = new Color(18, 20, 25, 255);
    public static final Color TEXT = new Color(255, 255, 255, 255);
    public static final Color TEXT_SECONDARY = new Color(255, 255, 255, 220);
    public static final Color TEXT_TRINARY = new Color(255, 255, 255, 130);
    public static final Color OVERLAY = new Color(0, 0, 0, 50);

    private RiseColors() {}

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static int withAlphaRGB(Color color, int alpha) {
        return withAlpha(color, alpha).getRGB();
    }
}
