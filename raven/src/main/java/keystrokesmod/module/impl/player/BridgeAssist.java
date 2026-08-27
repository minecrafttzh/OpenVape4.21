package keystrokesmod.module.impl.player;

import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.script.model.SimulatedPlayer;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class BridgeAssist extends Module {
    private static final EnumFacing[] SIDES = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
    };

    private final SliderSetting edgeOffset;
    private final SliderSetting unsneakDelay;
    private final SliderSetting sneakOnJump;
    private final ButtonSetting sneakKeyPressed;
    private final ButtonSetting holdingBlocks;
    private final ButtonSetting lookingDown;
    private final ButtonSetting notMovingForward;

    private final ButtonSetting prePlace;

    private boolean sneakingFromModule;
    private boolean placed;
    private boolean forceRelease;
    private int sneakJumpDelayTicks = -1;
    private int sneakJumpStartTick = -1;
    private int unsneakDelayTicks = -1;
    private int unsneakStartTick = -1;

    public BridgeAssist() {
        super("Bridge Assist", category.player);

        this.registerSetting(prePlace = new ButtonSetting("Pre place", false));

        GroupSetting sneakingGroup = new GroupSetting("Sneaking");
        this.registerSetting(sneakingGroup);
        this.registerSetting(edgeOffset = new SliderSetting(sneakingGroup, "Edge offset", " blocks", 0, 0, 0.3, 0.01));
        this.registerSetting(unsneakDelay = new SliderSetting(sneakingGroup, "Unsneak delay", "ms", 50, 50, 300, 5));
        this.registerSetting(sneakOnJump = new SliderSetting(sneakingGroup, "Sneak on jump", "ms", 0, 0, 500, 5));

        GroupSetting conditionsGroup = new GroupSetting("Conditions");
        this.registerSetting(conditionsGroup);
        this.registerSetting(sneakKeyPressed = new ButtonSetting(conditionsGroup, "Sneak key pressed", false));
        this.registerSetting(holdingBlocks = new ButtonSetting(conditionsGroup, "Holding blocks", false));
        this.registerSetting(lookingDown = new ButtonSetting(conditionsGroup, "Looking down", false));
        this.registerSetting(notMovingForward = new ButtonSetting(conditionsGroup, "Not moving forward", false));

        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        double offset = edgeOffset.getInput();
        return offset == Math.rint(offset) ? Integer.toString((int) offset) : Double.toString(Utils.round(offset, 2));
    }

    @Override
    public void onDisable() {
        sneakingFromModule = false;
        resetUnsneak();
    }

    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!Utils.nullCheck() || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) return;

        boolean manualSneak = isManualSneak();
        boolean requireSneak = sneakKeyPressed.isToggled();

        if (manualSneak && !requireSneak) {
            resetUnsneak();
            return;
        }

        if (requireSneak && (!manualSneak || (e.getForward() == 0 && e.getStrafe() == 0))) {
            if (!manualSneak) resetUnsneak();
            repressSneak(e);
            return;
        }

        if (notMovingForward.isToggled() && e.getForward() > 0) {
            clearSneak(e);
            return;
        }
        if (lookingDown.isToggled() && mc.thePlayer.rotationPitch < 70) {
            clearSneak(e);
            return;
        }
        if (holdingBlocks.isToggled()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemBlock)) {
                clearSneak(e);
                return;
            }
        }

        if (e.isJump() && mc.thePlayer.onGround && (e.getForward() != 0 || e.getStrafe() != 0) && sneakOnJump.getInput() > 0) {
            if (!requireSneak || forceRelease) {
                sneakJumpStartTick = mc.thePlayer.ticksExisted;
                double raw = sneakOnJump.getInput() / 50.0;
                int base = (int) raw;
                sneakJumpDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
                pressSneak(e, true);
                return;
            }
        }

        SimulatedPlayer sim = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput);
        sim.movementInput.sneak = false;
        sim.tick();

        double offset = computeEdgeOffset(sim.getEntityBoundingBox());

        if (Double.isNaN(offset)) {
            if (e.isJump() && (sneakOnJump.getInput() <= 0 || (e.getForward() == 0 && e.getStrafe() == 0))) {
                if (sneakingFromModule) tryReleaseSneak(e, true);
            } else if (mc.thePlayer.onGround) {
                pressSneak(e, true);
            } else if (sneakingFromModule) {
                tryReleaseSneak(e, true);
            }
            return;
        }

        if (offset > edgeOffset.getInput()) {
            pressSneak(e, true);
        } else if (sneakingFromModule) {
            tryReleaseSneak(e, true);
        }
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement c08 = (C08PacketPlayerBlockPlacement) e.getPacket();
            if (c08.getPlacedBlockDirection() != 255 && sneakingFromModule && sneakKeyPressed.isToggled()) {
                placed = true;
            }
        }
    }

    @SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (!prePlace.isToggled()) return;
        if (!Utils.nullCheck() || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) return;
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            return;
        }

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return;
        if (lookingDown.isToggled() && mc.thePlayer.rotationPitch < 70f) return;
        if (notMovingForward.isToggled() && mc.thePlayer.movementInput.moveForward > 0f) return;

        float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
        double reach = mc.playerController.getBlockReachDistance();

        TargetResult target = findTarget(basePitch, reach);
        if (target == null) return;

        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
        float[] sm = RotationUtils.smoothRotation(baseYaw, basePitch, target.yaw, target.pitch, 15, 20f);

        e.setYaw(sm[0]);
        e.setPitch(sm[1]);
    }

    private void pressSneak(PrePlayerInputEvent e, boolean resetDelay) {
        e.setSneak(true);
        sneakingFromModule = true;
        if (resetDelay) unsneakStartTick = -1;
        repressSneak(e);
    }

    private void tryReleaseSneak(PrePlayerInputEvent e, boolean resetDelay) {
        int existed = mc.thePlayer.ticksExisted;
        if (unsneakStartTick == -1 && sneakJumpStartTick == -1) {
            unsneakStartTick = existed;
            double raw = (unsneakDelay.getInput() - 50) / 50.0;
            int base = (int) raw;
            unsneakDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
        }

        if (sneakJumpStartTick != -1 && existed - sneakJumpStartTick < sneakJumpDelayTicks) {
            pressSneak(e, false);
            return;
        }
        if (unsneakStartTick != -1 && existed - unsneakStartTick < unsneakDelayTicks) {
            pressSneak(e, false);
            return;
        }

        releaseSneak(e, resetDelay);
    }

    private void releaseSneak(PrePlayerInputEvent e, boolean resetDelay) {
        if (!sneakKeyPressed.isToggled()) {
            e.setSneak(false);
        } else if (sneakingFromModule && isManualSneak() && (placed || !mc.thePlayer.onGround)) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            e.setSneak(false);
            forceRelease = true;
        } else if (forceRelease) {
            e.setSneak(false);
        }

        sneakingFromModule = false;
        placed = false;
        if (resetDelay) resetUnsneak();
    }

    private void repressSneak(PrePlayerInputEvent e) {
        if (forceRelease && isManualSneak()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            e.setSneak(true);
        }
        forceRelease = false;
    }

    private void clearSneak(PrePlayerInputEvent e) {
        sneakingFromModule = false;
        resetUnsneak();
        if (sneakKeyPressed.isToggled()) repressSneak(e);
    }

    private void resetUnsneak() {
        unsneakStartTick = -1;
        sneakJumpStartTick = -1;
        sneakJumpDelayTicks = -1;
        unsneakDelayTicks = -1;
    }

    private boolean isManualSneak() {
        return Utils.isBindDown(mc.gameSettings.keyBindSneak);
    }

    private double computeEdgeOffset(AxisAlignedBB simBox) {
        AxisAlignedBB groundCheck = new AxisAlignedBB(
                simBox.minX, simBox.minY - 0.01, simBox.minZ,
                simBox.maxX, simBox.minY, simBox.maxZ
        );

        List<AxisAlignedBB> groundBoxes = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, groundCheck);
        if (groundBoxes.isEmpty()) return Double.NaN;

        double feetX = (simBox.minX + simBox.maxX) / 2.0;
        double feetZ = (simBox.minZ + simBox.maxZ) / 2.0;

        double minDist = Double.MAX_VALUE;
        for (AxisAlignedBB box : groundBoxes) {
            double closestX = Math.max(box.minX, Math.min(feetX, box.maxX));
            double closestZ = Math.max(box.minZ, Math.min(feetZ, box.maxZ));
            double dx = Math.abs(feetX - closestX);
            double dz = Math.abs(feetZ - closestZ);
            double dist = Math.max(dx, dz);
            minDist = Math.min(minDist, dist);
        }

        return minDist;
    }

    private TargetResult findTarget(float currentPitch, double reach) {
        float yaw = mc.thePlayer.rotationYaw;

        AxisAlignedBB bbox = mc.thePlayer.getEntityBoundingBox();
        int standY = MathHelper.floor_double(bbox.minY) - 1;
        int minX = MathHelper.floor_double(bbox.minX);
        int maxX = MathHelper.floor_double(bbox.maxX);
        int minZ = MathHelper.floor_double(bbox.minZ);
        int maxZ = MathHelper.floor_double(bbox.maxZ);

        ArrayList<FaceTarget> targets = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos standBlock = new BlockPos(x, standY, z);
                if (BlockUtils.replaceable(standBlock)) continue;
                for (EnumFacing face : SIDES) {
                    BlockPos placed = standBlock.offset(face);
                    if (!BlockUtils.replaceable(placed)) continue;
                    targets.add(new FaceTarget(standBlock, face));
                }
            }
        }
        if (targets.isEmpty()) return null;

        float bestDelta = Float.MAX_VALUE;
        float bestPitch = Float.NaN;
        BlockPos bestSupport = null;
        EnumFacing bestFace = null;
        float randScale = 0.2f;

        for (float pitch = 60f; pitch <= 90f; ) {
            float step = 1.0f + (float) (Math.random() * 2 - 1) * (0.3f + randScale * 0.4f);
            if (step < 0.4f) step = 0.4f;
            if (step > 1.8f) step = 1.8f;
            pitch += step;
            float samplePitch = Math.min(pitch, 90f);
            MovingObjectPosition mop = RotationUtils.rayCastBlock(reach, yaw, samplePitch);
            if (mop == null) continue;
            EnumFacing hitFace = mop.sideHit;
            if (hitFace == EnumFacing.UP || hitFace == EnumFacing.DOWN) continue;

            BlockPos hitBlock = mop.getBlockPos();
            for (FaceTarget t : targets) {
                if (hitBlock.equals(t.block) && hitFace == t.face) {
                    float delta = Math.abs(samplePitch - currentPitch);
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        bestPitch = samplePitch;
                        bestSupport = t.block;
                        bestFace = t.face;
                    }
                    break;
                }
            }
            if (pitch >= 90f) break;
        }

        if (bestSupport == null || bestFace == null || Float.isNaN(bestPitch)) return null;
        return new TargetResult(yaw, bestPitch, bestSupport, bestFace);
    }

    private static class FaceTarget {
        final BlockPos block;
        final EnumFacing face;
        FaceTarget(BlockPos block, EnumFacing face) {
            this.block = block;
            this.face = face;
        }
    }

    private static class TargetResult {
        final float yaw, pitch;
        final BlockPos support;
        final EnumFacing face;
        TargetResult(float yaw, float pitch, BlockPos support, EnumFacing face) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.support = support;
            this.face = face;
        }
    }
}
