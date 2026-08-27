package keystrokesmod.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles entity-only visual sync when Timer speed is 0.
 * Entity packets are replayed live so entities keep moving.
 * Everything else is buffered and replayed on resume.
 */
public class FrozenEntitySync {

    private static final FrozenEntitySync INSTANCE = new FrozenEntitySync();
    public static FrozenEntitySync get() { return INSTANCE; }

    private final Queue<Packet<?>> liveEntityQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Packet<?>> deferredQueue = new ConcurrentLinkedQueue<>();

    private boolean active;
    private long lastFrozenTickNano;
    private double frozenTickAccumulator;

    private static final long TICK_NANOS = 50_000_000L; // 50 ms = 20 TPS
    private static final int MAX_STEPS_PER_FRAME = 10;

    public boolean isActive() {
        return active;
    }

    public void start() {
        if (active) return;
        active = true;
        lastFrozenTickNano = System.nanoTime();
        frozenTickAccumulator = 0;
    }

    public void stop() {
        if (!active) return;
        active = false;
        flush();
    }

    public void clearAll() {
        active = false;
        liveEntityQueue.clear();
        deferredQueue.clear();
        frozenTickAccumulator = 0;
    }

    /**
     * Called from ReceivePacketEvent when frozen. Returns true if the packet was intercepted.
     */
    public boolean intercept(Packet<?> packet) {
        if (!active) return false;
        if (isEntityPacket(packet)) {
            liveEntityQueue.add(packet);
        } else {
            deferredQueue.add(packet);
        }
        return true;
    }

    /**
     * Called every frame while timer is frozen. Drains live entity packets and runs entity ticks.
     */
    public void pumpFrame() {
        if (!active) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        drainLiveQueue();

        long now = System.nanoTime();
        long elapsed = now - lastFrozenTickNano;
        lastFrozenTickNano = now;

        frozenTickAccumulator += elapsed;
        int steps = 0;
        while (frozenTickAccumulator >= TICK_NANOS && steps < MAX_STEPS_PER_FRAME) {
            frozenTickAccumulator -= TICK_NANOS;
            steps++;
            tickNonLocalEntities(mc);
        }
        if (steps >= MAX_STEPS_PER_FRAME) {
            frozenTickAccumulator = 0;
        }

        float frozenPartial = (float) (frozenTickAccumulator / (double) TICK_NANOS);
        net.minecraft.util.Timer timer = ((keystrokesmod.mixin.impl.accessor.IAccessorMinecraft) mc).getTimer();
        timer.renderPartialTicks = frozenPartial;

        snapLocalPlayerInterpolation(mc.thePlayer);
    }

    /**
     * Snap the local player's interpolation anchors so lastTickPos == pos
     * and prevRotation == rotation. This prevents the changing renderPartialTicks
     * from shifting the player's rendered position/rotation between frames.
     */
    private void snapLocalPlayerInterpolation(EntityPlayerSP player) {
        player.lastTickPosX = player.posX;
        player.lastTickPosY = player.posY;
        player.lastTickPosZ = player.posZ;
        player.prevRotationYaw = player.rotationYaw;
        player.prevRotationPitch = player.rotationPitch;
        player.prevRotationYawHead = player.rotationYawHead;
        player.prevRenderYawOffset = player.renderYawOffset;
        player.prevLimbSwingAmount = player.limbSwingAmount;
        player.prevSwingProgress = player.swingProgress;
        player.prevCameraPitch = player.cameraPitch;
    }

    /**
     * On resume, replay all deferred (non-entity) packets in arrival order.
     */
    public void flush() {
        drainLiveQueue();
        Packet<?> p;
        while ((p = deferredQueue.poll()) != null) {
            PacketUtils.receivePacketNoEvent(p);
        }
        frozenTickAccumulator = 0;
    }

    private void drainLiveQueue() {
        Packet<?> p;
        while ((p = liveEntityQueue.poll()) != null) {
            PacketUtils.receivePacketNoEvent(p);
        }
    }

    private void tickNonLocalEntities(Minecraft mc) {
        EntityPlayerSP local = mc.thePlayer;

        for (int i = 0; i < mc.theWorld.weatherEffects.size(); i++) {
            Entity entity = mc.theWorld.weatherEffects.get(i);
            try {
                ++entity.ticksExisted;
                entity.onUpdate();
            } catch (Throwable ignored) {}
            if (entity.isDead) {
                mc.theWorld.weatherEffects.remove(i--);
            }
        }

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == null || entity.isDead || entity == local) continue;
            if (entity.ridingEntity != null) {
                if (!entity.ridingEntity.isDead && entity.ridingEntity.riddenByEntity == entity) {
                    continue;
                }
                entity.ridingEntity.riddenByEntity = null;
                entity.ridingEntity = null;
            }
            try {
                mc.theWorld.updateEntityWithOptionalForce(entity, true);
            } catch (Throwable ignored) {}
        }
    }

    private static boolean isEntityPacket(Packet<?> packet) {
        return packet instanceof S0CPacketSpawnPlayer
            || packet instanceof S0FPacketSpawnMob
            || packet instanceof S0EPacketSpawnObject
            || packet instanceof S10PacketSpawnPainting
            || packet instanceof S11PacketSpawnExperienceOrb
            || packet instanceof S2CPacketSpawnGlobalEntity
            || packet instanceof S12PacketEntityVelocity
            || packet instanceof S13PacketDestroyEntities
            || packet instanceof S14PacketEntity
            || packet instanceof S18PacketEntityTeleport
            || packet instanceof S19PacketEntityHeadLook
            || packet instanceof S0BPacketAnimation
            || packet instanceof S0APacketUseBed
            || packet instanceof S1BPacketEntityAttach
            || packet instanceof S1CPacketEntityMetadata
            || packet instanceof S04PacketEntityEquipment
            || packet instanceof S1DPacketEntityEffect
            || packet instanceof S1EPacketRemoveEntityEffect
            || packet instanceof S19PacketEntityStatus
            || packet instanceof S0DPacketCollectItem;
    }
}
