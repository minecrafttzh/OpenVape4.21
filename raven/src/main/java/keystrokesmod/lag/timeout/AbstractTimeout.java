package keystrokesmod.lag.timeout;

public abstract class AbstractTimeout {

    private volatile boolean forcefullyTimedOut = false;

    protected abstract boolean shouldHaveTimedOut();

    public final boolean isTimedOut() {
        return forcefullyTimedOut || shouldHaveTimedOut();
    }

    public final void forceTimeOut() {
        forcefullyTimedOut = true;
    }

}
