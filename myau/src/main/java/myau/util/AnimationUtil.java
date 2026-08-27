package myau.util;

import lombok.Getter;
import lombok.Setter;

import java.util.function.Function;

import static java.lang.Math.*;

public class AnimationUtil {

    private long startTime;
    private double startValue;
    private double destinationValue;
    @Setter
    private double duration;
    @Setter
    private AnimationUtil.Easing easing;

    public AnimationUtil(AnimationUtil.Easing easing, int duration) {
        this.easing = easing;
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
    }

    public static float animateSmooth(float target, float current, float speed, float deltaTime) {
        float diff = target - current;
        if (Math.abs(diff) < 0.0001f) return target;
        float factor = (float) (1.0 - Math.exp(-speed * deltaTime));
        factor = Math.max(0, Math.min(1, factor));
        return current + diff * factor;
    }

    public static float animate(float target, float current, float speed, float deltaTime) {
        if (Math.abs(target - current) < 0.0001f) return target;
        float change = speed * deltaTime;
        if (current < target) {
            return Math.min(target, current + change);
        } else {
            return Math.max(target, current - change);
        }
    }

    public static int interpolateColor(int c1, int c2, float fraction) {
        fraction = Math.min(1, Math.max(0, fraction));
        int a1 = (c1 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * fraction);
        int r = (int) (r1 + (r2 - r1) * fraction);
        int g = (int) (g1 + (g2 - g1) * fraction);
        int b = (int) (b1 + (b2 - b1) * fraction);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void run(double destination) {
        if (destination != destinationValue) {
            this.startValue = getValue();
            this.destinationValue = destination;
            this.startTime = System.currentTimeMillis();
        }
    }

    public double getValue() {
        double progress = (System.currentTimeMillis() - startTime) / duration;
        if (progress >= 1.0) return destinationValue;
        return startValue + (destinationValue - startValue) * easing.getFunction().apply(progress);
    }

    @Getter
    public enum Easing {
        LINEAR(x -> x),
        EASE_OUT_QUINT(x -> 1 + (--x) * x * x * x * x),
        EASE_OUT_SINE(x -> sin(x * PI / 2)),
        EASE_OUT_ELASTIC(x -> x == 0 ? 0 : x == 1 ? 1 : pow(2, -10 * x) * sin((x * 10 - 0.75) * ((2 * PI) / 3)) * 0.5 + 1),
        EASE_IN_BACK(x -> (1.70158 + 1) * x * x * x - 1.70158 * x * x);

        private final Function<Double, Double> function;

        Easing(Function<Double, Double> function) {
            this.function = function;
        }
    }

}