package keystrokesmod.lag.queue.node.impl;

import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.queue.node.api.AbstractLagNode;
import org.jetbrains.annotations.NotNull;

public final class AddRequestLagNode extends AbstractLagNode {

    private final @NotNull LagRequest request;

    public AddRequestLagNode(@NotNull LagRequest request) {
        this.request = request;
    }

    public @NotNull LagRequest getRequest() {
        return request;
    }

}