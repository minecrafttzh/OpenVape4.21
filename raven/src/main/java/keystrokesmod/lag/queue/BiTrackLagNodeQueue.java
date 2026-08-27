package keystrokesmod.lag.queue;

import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.handler.AbstractFastTrackProvider;
import keystrokesmod.lag.queue.node.api.AbstractLagNode;
import keystrokesmod.lag.queue.node.impl.AddRequestLagNode;
import keystrokesmod.lag.queue.node.impl.PacketLagNode;
import net.minecraft.network.Packet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BiTrackLagNodeQueue {

    private final @NotNull TrackState incomingState;
    private final @NotNull TrackState outgoingState;

    public BiTrackLagNodeQueue(
            final @NotNull AbstractFastTrackProvider fastTrackProvider
    ) {
        incomingState = new TrackState(new ArrayList<>(), fastTrackProvider);
        outgoingState = new TrackState(new ArrayList<>(), fastTrackProvider);
    }

    public void clear() {
        incomingState.clear();
        outgoingState.clear();
    }

    public boolean tick(final @Nullable Packet<?> packet, final @Nullable EnumLagDirection direction) {
        if ((packet == null) != (direction == null)) {
            throw new NullPointerException();
        }

        if (direction == null) {
            incomingState.tick(null, null);
            outgoingState.tick(null, null);
            return false;
        } else switch (direction) {
            case INBOUND: {
                return incomingState.tick(packet, direction);
            }
            case OUTBOUND: {
                return outgoingState.tick(packet, direction);
            }
            default: {
                return false;
            }
        }
    }

    public void requestLag(final @NotNull LagRequest request) {
        for (final @NotNull EnumLagDirection direction : request.getDirections()) {
            switch (direction) {
                case OUTBOUND: {
                    outgoingState.addRequest(request);
                }
                break;
                case INBOUND: {
                    incomingState.addRequest(request);
                }
                break;
            }
        }
    }

    public void releaseExpiredPackets(final @NotNull EnumLagDirection direction, long maxAgeMs) {
        switch (direction) {
            case OUTBOUND:
                outgoingState.releaseExpiredPackets(maxAgeMs);
                break;
            case INBOUND:
                incomingState.releaseExpiredPackets(maxAgeMs);
                break;
        }
    }

    private static final class TrackState {

        private final @NotNull List<AbstractLagNode> track;
        private final @NotNull AbstractFastTrackProvider fastTrackProvider;

        private @Nullable LagRequest currentlyAwaiting = null;

        private TrackState(
                final @NotNull List<AbstractLagNode> track,
                final @NotNull AbstractFastTrackProvider fastTrackProvider
        ) {
            this.track = track;
            this.fastTrackProvider = fastTrackProvider;
        }

        private synchronized void addRequest(final @NotNull LagRequest request) {
            track.add(new AddRequestLagNode(request));
        }

        private synchronized boolean tick(final @Nullable Packet<?> packet, final @Nullable EnumLagDirection direction) {
            if (track.isEmpty() && (currentlyAwaiting == null || currentlyAwaiting.getTimeout().isTimedOut())) {
                currentlyAwaiting = null;
                return false;
            }

            if (packet != null) {
                //noinspection DataFlowIssue - non-null assured by BiTrackLagNodeQueue.tick
                track.add(new PacketLagNode(packet, direction));
            }

            @Nullable LagRequest awaiting = currentlyAwaiting;

            try {
                while (awaiting == null || awaiting.getTimeout().isTimedOut()) {
                    final @Nullable AbstractLagNode popped = pop();

                    if (popped == null) {
                        awaiting = null;
                        break;
                    }

                    if (popped instanceof PacketLagNode) {
                        ((PacketLagNode) popped).goThrough(fastTrackProvider);
                    } else if (popped instanceof AddRequestLagNode) {
                        awaiting = ((AddRequestLagNode) popped).getRequest();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            currentlyAwaiting = awaiting;

            return true;
        }

        private synchronized void releaseExpiredPackets(long maxAgeMs) {
            long cutoff = System.currentTimeMillis() - maxAgeMs;
            List<PacketLagNode> toRelease = new ArrayList<>();
            // Snapshot: goThrough() can re-enter and append to track via ReceivePacketEvent.
            for (AbstractLagNode node : new ArrayList<>(track)) {
                if (node instanceof PacketLagNode) {
                    PacketLagNode pkt = (PacketLagNode) node;
                    if (pkt.getQueuedAtMs() <= cutoff) {
                        toRelease.add(pkt);
                    }
                }
            }
            if (toRelease.isEmpty()) {
                return;
            }
            track.removeAll(toRelease);
            for (PacketLagNode pkt : toRelease) {
                pkt.goThrough(fastTrackProvider);
            }
        }

        private synchronized void clear() {
            track.clear();
            currentlyAwaiting = null;
        }

        private @Nullable AbstractLagNode pop() {
            return track.isEmpty() ? null : track.remove(0);
        }

    }

}
