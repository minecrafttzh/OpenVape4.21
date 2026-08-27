package myau.util;

import java.awt.*;

public class ColorUtil {
    public static final Color RED = new Color(255, 0, 0);
    public static final Color GOLD = new Color(255, 165, 0);
    public static final Color YELLOW = new Color(255, 255, 0);
    public static final Color GREEN = new Color(0, 255, 0);

    public static Color fromHSB(float hue, float saturation, float brightness) {
        return new Color(Color.HSBtoRGB(hue, saturation, brightness));
    }

    public static Color interpolate(float progress, Color startColor, Color endColor) {
        progress = Math.min(Math.max(progress, 0.0f), 1.0f);
        return new Color((int) ((float) startColor.getRed() + progress * (float) (endColor.getRed() - startColor.getRed())), (int) ((float) startColor.getGreen() + progress * (float) (endColor.getGreen() - startColor.getGreen())), (int) ((float) startColor.getBlue() + progress * (float) (endColor.getBlue() - startColor.getBlue())));
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(255, Math.max(0, alpha)));
    }

    public static Color interpolateColorC(Color color1, Color color2, float amount) {
        amount = Math.min(1, Math.max(0, amount));
        return new Color(interpolateInt(color1.getRed(), color2.getRed(), amount),
                interpolateInt(color1.getGreen(), color2.getGreen(), amount),
                interpolateInt(color1.getBlue(), color2.getBlue(), amount),
                interpolateInt(color1.getAlpha(), color2.getAlpha(), amount));
    }

    public static float interpolateFloat(float oldValue, float newValue, double interpolationValue){
        return interpolate(oldValue, newValue, (float) interpolationValue).floatValue();
    }

    public static int interpolateInt(int oldValue, int newValue, double interpolationValue){
        return interpolate(oldValue, newValue, (float) interpolationValue).intValue();
    }

    public static Double interpolate(double oldValue, double newValue, double interpolationValue){
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }

    public static Color getHealthBlend(float percent) {
        if (percent >= 0.9f) {
            return GREEN;
        }
        if (percent >= 0.55f) {
            return ColorUtil.interpolate((percent - 0.55f) / 0.35f, YELLOW, GREEN);
        }
        if (percent >= 0.45f) {
            return YELLOW;
        }
        if (percent >= 0.1f) {
            return ColorUtil.interpolate((percent - 0.1f) / 0.35f, RED, YELLOW);
        }
        return RED;
    }

    public static Color darker(Color color, float factor) {
        return ColorUtil.scale(color, factor, color.getAlpha());
    }

    public static Color scale(Color color, float scaleFactor, int alpha) {
        return new Color(Math.min(Math.max((int) ((float) color.getRed() * scaleFactor), 0), 255), Math.min(Math.max((int) ((float) color.getGreen() * scaleFactor), 0), 255), Math.min(Math.max((int) ((float) color.getBlue() * scaleFactor), 0), 255), alpha);
    }

    public static int astolfoColors(int offset, int total) {
        float speed = 2900F;
        float hue = (float) (System.currentTimeMillis() % (int) speed) + ((total - offset) * 9);
        while (hue > speed) {
            hue -= speed;
        }
        hue /= speed;
        if (hue > 0.5) {
            hue = 0.5F - (hue - 0.5F);
        }
        hue += 0.5F;
        return Color.HSBtoRGB(hue, 0.5F, 1F);
    }

    public static Color applyOpacity(Color color, float opacity) {
        float alpha = Math.max(0.0f, Math.min(1.0f, opacity));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255));
    }

    // --- 新增的方法 ---

    /**
     * 生成彩虹色
     * @param seconds 循环周期(秒)
     * @param offset 偏移量
     * @param saturation 饱和度
     * @param brightness 亮度
     */
    public static Color rainbow(int seconds, int offset, float saturation, float brightness) {
        float hue = ((System.currentTimeMillis() + offset) % (seconds * 1000)) / (float) (seconds * 1000);
        return new Color(Color.HSBtoRGB(hue, saturation, brightness));
    }

    /**
     * 生成淡入淡出效果 (呼吸灯)
     * @param color 基础颜色
     * @param index 索引(用于列表中的波浪效果)
     * @param count 总数(控制波浪频率)
     */
    public static Color fade(Color color, int index, int count) {
        float[] hsb = new float[3];
        Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);
        float brightness = Math.abs((((System.currentTimeMillis() % 2000) / 1000.0f + (index / (float) count) * 2.0f) % 2.0f) - 1.0f);
        brightness = 0.5f + 0.5f * brightness;
        hsb[2] = brightness % 1.0f;
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
    }
}
