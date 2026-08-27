package keystrokesmod.helper;

import keystrokesmod.event.*;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class RotationHelper {

    private static RotationHelper INSTANCE = new RotationHelper();

    private Float serverYaw = null;
    private Float serverPitch = null;

    private boolean setRotations = false;

    public boolean forceMovementFix = false;
    private boolean serverRelativeMovementInputs = false;

    // Tick-scoped swap state for temporarily overriding entity rotations
    private float savedYaw, savedPitch;
    private float savedPrevYaw, savedPrevPitch;
    public boolean swappedForMouseOver;

    private boolean rotationsUpdatedThisTick = false;

    private boolean needsArmYawUpdate = false;

    private Minecraft mc = Minecraft.getMinecraft();

    /**
     * Returns yaw expressed in the same angular "branch" as prevYaw (avoids modulo jumps).
     * Port from KillAura.
     */
    public static float unwrapYaw(float yaw, float prevYaw) {
        return prevYaw + ((((yaw - prevYaw + 180f) % 360f) + 360f) % 360f - 180f);
    }

    /**
     * Returns rotations to look at target, using event values or server rotations as base.
     * Applies unwrap (via fixRotation) and optional smoothing. Use in ClientRotationEvent handlers.
     */
    public float[] getRotationsToTarget(Entity target, ClientRotationEvent e, float smoothingFactor) {
        if (target == null || mc.thePlayer == null) return null;
        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
        float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
        float[] rot = RotationUtils.getRotations(target, baseYaw, basePitch);
        if (rot == null) return null;
        float factor = Math.max(1f, smoothingFactor);
        float yaw = baseYaw + MathHelper.wrapAngleTo180_float(rot[0] - baseYaw) / factor;
        float pitch = basePitch + (rot[1] - basePitch) / factor;
        return new float[] { yaw, pitch };
    }

    /**
     * Returns rotations to look at target, using player rotation as base.
     * Applies smoothing. Use for Normal (non-silent) mode.
     */
    public float[] getRotationsToTarget(Entity target, float smoothingFactor) {
        if (target == null || mc.thePlayer == null) return null;
        float baseYaw = mc.thePlayer.rotationYaw;
        float basePitch = mc.thePlayer.rotationPitch;
        float[] rot = RotationUtils.getRotations(target, baseYaw, basePitch);
        if (rot == null) return null;
        float factor = Math.max(1f, smoothingFactor);
        float yaw = baseYaw + MathHelper.wrapAngleTo180_float(rot[0] - baseYaw) / factor;
        float pitch = basePitch + (rot[1] - basePitch) / factor;
        return new float[] { yaw, pitch };
    }

    /**
     * Returns rotations to look at target with speed (0-30).
     * Uses event values or server rotations as base. For ClientRotationEvent handlers.
     */
    public float[] getRotationsToTarget(Entity target, ClientRotationEvent e, int speed) {
        return getRotationsToTarget(target, e, speed, 0.0, 0.0, 0f);
    }

    /**
     * Returns rotations to look at target with speed (0-30), multipoint (0-100%), and randomization (0-100%).
     * Uses event values or server rotations as base. For ClientRotationEvent handlers.
     */
    public float[] getRotationsToTarget(Entity target, ClientRotationEvent e, int speed, double horizontalMultipoint, double verticalMultipoint, float randomizationPercent) {
        return getRotationsToTarget(target, e, speed, horizontalMultipoint, verticalMultipoint, randomizationPercent, false, 10.0);
    }

    /**
     * Same as above with useBackupPoints and range. When useBackupPoints true, uses raycast fallback; range used for raycasts.
     */
    public float[] getRotationsToTarget(Entity target, ClientRotationEvent e, int speed, double horizontalMultipoint, double verticalMultipoint, float randomizationPercent, boolean useBackupPoints, double range) {
        return getRotationsToTarget(target, e, speed, horizontalMultipoint, verticalMultipoint, randomizationPercent, useBackupPoints, range, false, true);
    }

    public float[] getRotationsToTarget(Entity target, ClientRotationEvent e, int speed, double horizontalMultipoint, double verticalMultipoint, float randomizationPercent, boolean useBackupPoints, double range, boolean allowThroughBlocks, boolean allowThroughEntities) {
        if (target == null || mc.thePlayer == null) return null;
        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
        float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
        float[] rot = useBackupPoints
                ? RotationUtils.getRotationsWithBackup(target, horizontalMultipoint, verticalMultipoint, baseYaw, basePitch, range, allowThroughBlocks, allowThroughEntities)
                : RotationUtils.getRotations(target, horizontalMultipoint, verticalMultipoint, baseYaw, basePitch);
        if (rot == null) return null;
        return RotationUtils.smoothRotation(baseYaw, basePitch, rot[0], rot[1], speed, randomizationPercent);
    }

    /**
     * Returns rotations to look at target with speed (0-30).
     * Uses player rotation as base. For Normal (non-silent) mode.
     */
    public float[] getRotationsToTarget(Entity target, int speed) {
        return getRotationsToTarget(target, speed, 0.0, 0.0, 0f);
    }

    /**
     * Returns rotations to look at target with speed (0-30), multipoint (0-100%), and randomization (0-100%).
     * Uses player rotation as base. For Normal (non-silent) mode.
     */
    public float[] getRotationsToTarget(Entity target, int speed, double horizontalMultipoint, double verticalMultipoint, float randomizationPercent) {
        return getRotationsToTarget(target, speed, horizontalMultipoint, verticalMultipoint, randomizationPercent, false, 10.0);
    }

    /**
     * Same as above with useBackupPoints and range. When useBackupPoints true, uses raycast fallback; range used for raycasts.
     */
    public float[] getRotationsToTarget(Entity target, int speed, double horizontalMultipoint, double verticalMultipoint, float randomizationPercent, boolean useBackupPoints, double range) {
        return getRotationsToTarget(target, speed, horizontalMultipoint, verticalMultipoint, randomizationPercent, useBackupPoints, range, false, true);
    }

    public float[] getRotationsToTarget(Entity target, int speed, double horizontalMultipoint, double verticalMultipoint, float randomizationPercent, boolean useBackupPoints, double range, boolean allowThroughBlocks, boolean allowThroughEntities) {
        if (target == null || mc.thePlayer == null) return null;
        float baseYaw = mc.thePlayer.rotationYaw;
        float basePitch = mc.thePlayer.rotationPitch;
        float[] rot = useBackupPoints
                ? RotationUtils.getRotationsWithBackup(target, horizontalMultipoint, verticalMultipoint, baseYaw, basePitch, range, allowThroughBlocks, allowThroughEntities)
                : RotationUtils.getRotations(target, horizontalMultipoint, verticalMultipoint, baseYaw, basePitch);
        if (rot == null) return null;
        return RotationUtils.smoothRotation(baseYaw, basePitch, rot[0], rot[1], speed, randomizationPercent);
    }

    /**
     * Gathers server rotations via ClientRotationEvent once per tick.
     * Called early in runTick (before getMouseOver) so that objectMouseOver
     * uses server rotations, and also as a fallback from onPreUpdate.
     * Guard uses rotationsUpdatedThisTick (reset at GameTickEvent) because
     * ticksExisted only increments during updateEntities, which runs after getMouseOver.
     */
    public void updateServerRotations() {
        if (mc.thePlayer == null) {
            return;
        }
        if (rotationsUpdatedThisTick) {
            return;
        }
        rotationsUpdatedThisTick = true;

        ClientRotationEvent event = new ClientRotationEvent(this.serverYaw, this.serverPitch);

        MinecraftForge.EVENT_BUS.post(event);

        this.serverYaw = event.yaw;
        this.serverPitch = event.pitch;

        if (this.serverYaw == null && this.serverPitch == null) {
            return;
        }

        if (this.serverYaw != null){
            if (Math.abs(this.serverYaw - mc.thePlayer.rotationYaw) >= 1.0f) {
                final int randomFactor = (int) Settings.randomYawFactor.getInput();
                if (randomFactor != 0) {
                    final int n13 = randomFactor * 100 + Utils.randomizeInt(-30, 30);
                    this.serverYaw += Utils.randomizeInt(-n13, n13) / 100.0f;
                }
            }
        }

        float[] fixed = RotationUtils.fixRotation(
                this.serverYaw == null ? mc.thePlayer.rotationYaw : this.serverYaw,
                this.serverPitch == null ? mc.thePlayer.rotationPitch : this.serverPitch,
                RotationUtils.serverRotations[0],
                RotationUtils.serverRotations[1]
        );
        this.serverYaw = fixed[0];
        this.serverPitch = fixed[1];

        if (this.serverYaw != mc.thePlayer.rotationYaw && (event.yaw == null || !event.yaw.isNaN())) {
            this.setRotations = true;
        }

        if (this.serverPitch != mc.thePlayer.rotationPitch && (event.pitch == null || !event.pitch.isNaN())) {
            this.setRotations = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPreUpdate(PreUpdateEvent e) {
        updateServerRotations();
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        if (!this.setRotations) {
            return;
        }
        if (this.serverYaw != null && !this.serverYaw.isNaN()) e.setYaw(this.serverYaw);
        if (this.serverPitch != null && !this.serverPitch.isNaN()) e.setPitch(this.serverPitch);
    }

    @SubscribeEvent
    public void onRunTick(GameTickEvent e) {
        if (this.setRotations && this.serverYaw != null && mc.thePlayer != null) {
            float serverYawVal = RotationUtils.serverRotations[0];
            float unwrapped = unwrapYaw(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw), serverYawVal);
            mc.thePlayer.rotationYaw = unwrapped;
            mc.thePlayer.prevRotationYaw = unwrapped;
        }
        this.serverYaw = this.serverPitch = null;
        this.setRotations = this.forceMovementFix = false;
        this.serverRelativeMovementInputs = false;
        this.rotationsUpdatedThisTick = false;
        this.swappedForMouseOver = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (this.setRotations && mc.thePlayer != null) {
            mc.thePlayer.prevRenderArmYaw = mc.thePlayer.rotationYaw;
            mc.thePlayer.renderArmYaw = mc.thePlayer.rotationYaw;
        }
    }

    public boolean isActive() {
        return this.setRotations && (this.serverYaw != null || this.serverPitch != null);
    }

    /**
     * Temporarily overrides an entity's rotation fields for raytrace or movement math.
     * Saves all four fields so endSwap can fully restore them.
     * Sets prev = current to prevent interpolation artifacts in getLook(partialTicks).
     */
    public void beginSwap(Entity e, float yaw, float pitch, boolean swapPitch) {
        this.savedYaw = e.rotationYaw;
        this.savedPrevYaw = e.prevRotationYaw;
        this.savedPitch = e.rotationPitch;
        this.savedPrevPitch = e.prevRotationPitch;

        e.rotationYaw = yaw;
        e.prevRotationYaw = yaw;

        if (swapPitch) {
            e.rotationPitch = pitch;
            e.prevRotationPitch = pitch;
        }
    }

    /**
     * Restores the entity's rotation fields saved by beginSwap.
     */
    public void endSwap(Entity e) {
        e.rotationYaw = this.savedYaw;
        e.prevRotationYaw = this.savedPrevYaw;
        e.rotationPitch = this.savedPitch;
        e.prevRotationPitch = this.savedPrevPitch;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPostInput(PostPlayerInputEvent event) {
        if (!fixMovement()) {
            return;
        }

        if (this.serverRelativeMovementInputs) {
            return;
        }

        float sneakMultiplier = mc.thePlayer.movementInput.sneak ? 0.3F : 1F;

        float yaw = this.serverYaw;
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;

        if (forward == 0 && strafe == 0) {
            return;
        }

        double angle = MathHelper.wrapAngleTo180_double(Math.toDegrees(getDirection(mc.thePlayer.rotationYaw, forward, strafe)));

        float closestForward = 0, closestStrafe = 0, closestDifference = Float.MAX_VALUE;

        for (float pfRaw = -1F; pfRaw <= 1F; pfRaw += 1F) {
            for (float psRaw = -1F; psRaw <= 1F; psRaw += 1F) {
                if (pfRaw == 0 && psRaw == 0) {
                    continue;
                }

                float predictedForward = pfRaw * sneakMultiplier;
                float predictedStrafe = psRaw * sneakMultiplier;

                double predictedAngle = MathHelper.wrapAngleTo180_double(Math.toDegrees(getDirection(yaw, predictedForward, predictedStrafe)));
                double difference = Math.abs(angle - predictedAngle);

                if (difference < closestDifference) {
                    closestDifference = (float) difference;
                    closestForward = predictedForward;
                    closestStrafe = predictedStrafe;
                }
            }
        }

        mc.thePlayer.movementInput.moveForward = closestForward;
        mc.thePlayer.movementInput.moveStrafe  = closestStrafe;
    }

    @SubscribeEvent
    public void onStrafe(StrafeEvent e) {
        if (fixMovement()) {
            e.setYaw(this.serverYaw);
        }
    }

    @SubscribeEvent
    public void onJump(JumpEvent e) {
        if (fixMovement()) {
            e.setYaw(this.serverYaw);
        }
    }

    public boolean fixMovement() {
        return ((ModuleManager.movementFix != null && ModuleManager.movementFix.isEnabled()) || this.forceMovementFix) && this.setRotations;
    }

    public static double getDirection(float rotationYaw, double moveForward, double moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;

        float forward = 1F;

        if (moveForward < 0F) forward = -0.5F;
        else if (moveForward > 0F) forward = 0.5F;

        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;

        return Math.toRadians(rotationYaw);
    }

    public static RotationHelper get() {
        return INSTANCE;
    }

    public void setRotations(float yaw, float pitch) {
        this.serverYaw = yaw;
        this.serverPitch = pitch;
        this.setRotations = true;
    }

    public void setYaw(float yaw) {
        this.serverYaw = yaw;
        this.setRotations = true;
    }

    public void setPitch(float pitch) {
        this.serverPitch = pitch;
        this.setRotations = true;
    }

    public void setServerRelativeMovementInputs(boolean serverRelativeMovementInputs) {
        this.serverRelativeMovementInputs = serverRelativeMovementInputs;
    }

    public Float getServerYaw() {
        return serverYaw;
    }

    public Float getServerPitch() {
        return serverPitch;
    }
}
