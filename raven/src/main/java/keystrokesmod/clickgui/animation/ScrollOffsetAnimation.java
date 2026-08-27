package keystrokesmod.clickgui.animation;

public class ScrollOffsetAnimation {
    private float from;
    private float to;
    private long startMs;
    private final long durationMs;

    public ScrollOffsetAnimation(long durationMs) {
        this.durationMs = durationMs;
        this.from = 0f;
        this.to = 0f;
        this.startMs = 0L;
    }

    public void reset(float value) {
        this.from = value;
        this.to = value;
        this.startMs = 0L;
    }

    public void setTarget(float newTarget) {
        this.from = getValue();
        this.to = newTarget;
        this.startMs = System.currentTimeMillis();
    }

    public void extend(float delta) {
        this.from = getValue();
        this.to += delta;
        this.startMs = System.currentTimeMillis();
    }

    public void clampTarget(float min, float max) {
        this.to = Math.max(min, Math.min(max, this.to));
    }

    public float getValue() {
        if (startMs == 0L) {
            return to;
        }
        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed >= durationMs) {
            startMs = 0L;
            from = to;
            return to;
        }
        float t = (float) elapsed / (float) durationMs;
        float ease = expoOut(t);
        return from + (to - from) * ease;
    }

    public boolean isAnimating() {
        if (startMs == 0L) {
            return false;
        }
        return System.currentTimeMillis() - startMs < durationMs;
    }

    public float getTarget() {
        return to;
    }

    private static float expoOut(float t) {
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        return 1f - (float) Math.pow(2.0, -10.0 * t);
    }
}
