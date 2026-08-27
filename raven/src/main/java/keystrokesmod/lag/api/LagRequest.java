package keystrokesmod.lag.api;

import keystrokesmod.lag.timeout.AbstractTimeout;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class LagRequest {

    private final @NotNull Set<EnumLagDirection> directions;
    private final @NotNull AbstractTimeout timeout;

    public LagRequest(
            final @NotNull Set<EnumLagDirection> directions,
            final @NotNull AbstractTimeout timeout
    ) {
        this.directions = directions;
        this.timeout = timeout;
    }

    public @NotNull Set<EnumLagDirection> getDirections() {
        return directions;
    }

    public @NotNull AbstractTimeout getTimeout() {
        return timeout;
    }

}