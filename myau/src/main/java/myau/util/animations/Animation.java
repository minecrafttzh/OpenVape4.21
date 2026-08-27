package myau.util.animations;

import myau.util.TimerUtil;

public abstract class Animation {

    public TimerUtil TimerUtil = new TimerUtil();
    protected int duration;
    private double startPoint, endPoint, value;
    protected Direction direction;

    public Animation(int ms, double endPoint) {
        this(ms, endPoint, Direction.FORWARDS);
    }

    public Animation(int ms, double endPoint, Direction direction) {
        this.duration = ms;
        this.endPoint = endPoint;
        this.direction = direction;
    }


    public boolean finished(Direction direction) {
        return isDone() && this.direction.equals(direction);
    }

    public double getLinearOutput() {
        return 1 - ((TimerUtil.getTime() / (double) duration) * endPoint);
    }

    public double getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(double endPoint) {
        this.endPoint = endPoint;
    }

    public void reset() {
        TimerUtil.reset();
    }

    public boolean isDone() {
        return TimerUtil.hasTimeElapsed(duration);
    }

    public void changeDirection() {
        setDirection(direction.opposite());
    }

    public Direction getDirection() {
        return direction;
    }

    public Animation setDirection(Direction direction) {
        if (this.direction != direction) {
            this.direction = direction;
            TimerUtil.setTime(System.currentTimeMillis() - (duration - Math.min(duration, TimerUtil.getTime())));
        }
        return this;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    protected boolean correctOutput() {
        return false;
    }

    public double getOutput() {
        if (direction.forwards()) {
            if (isDone()) {
                return endPoint;
            }

            return getEquation(TimerUtil.getTime() / (double) duration) * endPoint;
        } else {
            if (isDone()) {
                return 0.0;
            }

            if (correctOutput()) {
                double revTime = Math.min(duration, Math.max(0, duration - TimerUtil.getTime()));
                return getEquation(revTime / duration) * endPoint;
            }

            return (1 - getEquation(TimerUtil.getTime() / (double) duration)) * endPoint;
        }
    }

    protected abstract double getEquation(double x);

    public float getFloatValue() {
        return (float) value;
    }
}