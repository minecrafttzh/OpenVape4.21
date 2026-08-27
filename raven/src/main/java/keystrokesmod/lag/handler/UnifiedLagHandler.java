package keystrokesmod.lag.handler;

import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.queue.BiTrackLagNodeQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@SuppressWarnings("DuplicatedCode")
public final class UnifiedLagHandler extends AbstractFastTrackProvider {

    private final @NotNull BiTrackLagNodeQueue queue = new BiTrackLagNodeQueue(this);

    private final @NotNull Set<Packet<?>> packetFastTrack = Collections.newSetFromMap(
            Collections.synchronizedMap(new IdentityHashMap<Packet<?>, Boolean>())
    );
    private volatile @Nullable Vec3 serverPosition;

    public void requestLag(final @NotNull LagRequest request) {
        queue.requestLag(request);
    }

    public void releaseExpiredPackets(final @NotNull EnumLagDirection direction, long maxAgeMs) {
        queue.releaseExpiredPackets(direction, maxAgeMs);
    }

    public @Nullable Vec3 getLastReleasedServerPosition() {
        return serverPosition;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSendPacket(final @NotNull SendPacketEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            clearServerPositions();
            return;
        }

        final @NotNull Packet<?> packet = event.getPacket();
        final boolean fastTracked = consumeFastTrack(packet);

        if (event.isCanceled()) {
            return;
        }

        if (fastTracked) {
            updateServerPosition(packet);
            return;
        }

        if (queue.tick(packet, EnumLagDirection.OUTBOUND)) {
            event.setCanceled(true);
            return;
        }

        updateServerPosition(packet);
    }

    @SubscribeEvent
    public void onReceivePacket(final @NotNull ReceivePacketEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            clearServerPositions();
            return;
        }

        final @NotNull Packet<?> packet = event.getPacket();
        final boolean fastTracked = consumeFastTrack(packet);

        if (event.isCanceled()) {
            return;
        }

        if (fastTracked) {
            return;
        }

        if (queue.tick(packet, EnumLagDirection.INBOUND)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGameTick(final @NotNull GameTickEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            clearServerPositions();
            return;
        }

        queue.tick(null, null);
    }

    @Override
    public void forPacket(final @NotNull Packet<?> packet) {
        packetFastTrack.add(packet);
    }

    private boolean consumeFastTrack(final @NotNull Packet<?> packet) {
        return packetFastTrack.remove(packet);
    }

    private void updateServerPosition(final @NotNull Packet<?> packet) {
        if (!(packet instanceof C03PacketPlayer)) {
            return;
        }

        C03PacketPlayer movementPacket = (C03PacketPlayer) packet;
        if (!movementPacket.isMoving()) {
            return;
        }

        serverPosition = new Vec3(
                movementPacket.getPositionX(),
                movementPacket.getPositionY(),
                movementPacket.getPositionZ()
        );
    }

    private void clearServerPositions() {
        serverPosition = null;
    }

}
