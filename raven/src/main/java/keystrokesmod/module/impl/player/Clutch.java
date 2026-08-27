package keystrokesmod.module.impl.player;

import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Clutch extends Module {
    private static final Map<String, Integer> BLOCK_SCORE = new HashMap<>();
    private static final double HALF_WIDTH = 0.3;
    private static final double[][] CORNERS = {{-HALF_WIDTH, -HALF_WIDTH}, {HALF_WIDTH, -HALF_WIDTH}, {-HALF_WIDTH, HALF_WIDTH}, {HALF_WIDTH, HALF_WIDTH}};

    static {
        BLOCK_SCORE.put("obsidian", 0);
        BLOCK_SCORE.put("end_stone", 1);
        BLOCK_SCORE.put("planks", 2);
        BLOCK_SCORE.put("log", 2);
        BLOCK_SCORE.put("log2", 2);
        BLOCK_SCORE.put("glass", 3);
        BLOCK_SCORE.put("stained_glass", 3);
        BLOCK_SCORE.put("hardened_clay", 4);
        BLOCK_SCORE.put("stained_hardened_clay", 4);
        BLOCK_SCORE.put("stone", 5);
        BLOCK_SCORE.put("wool", 5);
    }

    private final SliderSetting reach;
    private final SliderSetting speed;
    private final SliderSetting snapbackSpeed;
    private final SliderSetting maxDistance;
    private final SliderSetting rotationTolerance;
    private final ButtonSetting simulateFuturePosition;
    private final ButtonSetting autoClutch;
    private final SliderSetting minimumFallDistance;
    private final KeySetting selectKeybind;

    private BlockPos placeAtBlock;
    private EnumFacing hitSide;
    private Vec3 hitVec;
    private boolean placeQueued;
    private boolean placing;
    private boolean slotWasSwapped;
    private boolean autoClickerWasOn;
    private int prevSlot = -1;
    private int plannedSlot = -1;
    private float aimYaw;
    private float aimPitch;
    private BlockPos targetHitPos;
    private EnumFacing targetSide;
    private boolean hasAim;
    private boolean resetting;
    private BlockPos lastPlaced;
    private int clutchBlocksPlaced;
    private boolean autoClutchActive;
    private boolean autoClutchChecking;
    private int autoClutchCheckCounter;
    private boolean autoClutchLandedGuard;
    private int autoClutchLandedTick;
    private int prevHurtTime = -1;

    public Clutch() {
        super("Clutch", category.player);
        this.registerSetting(reach = new SliderSetting("Reach", " blocks", 4.5, 0.5, 4.5, 0.1));
        this.registerSetting(speed = new SliderSetting("Speed", 8, 0, 100, 1));
        this.registerSetting(snapbackSpeed = new SliderSetting("Snapback Speed", 12, 0, 100, 1));
        this.registerSetting(maxDistance = new SliderSetting("Max distance", " blocks", 10, 0, 20, 1));
        this.registerSetting(rotationTolerance = new SliderSetting("Rotation Tolerance", "\u00B0", 25, 20, 100, 1));
        this.registerSetting(simulateFuturePosition = new ButtonSetting("Simulate future position", true));
        this.registerSetting(autoClutch = new ButtonSetting("Auto Clutch", false));
        this.registerSetting(minimumFallDistance = new SliderSetting("Minimum fall distance", " blocks", 10, 3, 20, 1));
        this.registerSetting(selectKeybind = new KeySetting("Select Keybind", 0));
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        hasAim = false;
        resetting = false;
        clutchBlocksPlaced = 0;
        autoClutchActive = false;
        autoClutchChecking = false;
        autoClutchCheckCounter = 0;
        autoClutchLandedGuard = false;
        autoClutchLandedTick = 0;
        prevHurtTime = -1;
    }

    @Override
    public void onDisable() {
        clearAim(false);
        disablePlacing(true);
        placeQueued = false;
        autoClutchActive = false;
        autoClutchChecking = false;
        autoClutchLandedGuard = false;
    }

    @SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (!Utils.nullCheck()) return;
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            return;
        }

        runPrePlayerInteract();

        if (mc.currentScreen != null) disablePlacing(false);

        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
        float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];

        if (resetting) {
            aimYaw = mc.thePlayer.rotationYaw;
            aimPitch = mc.thePlayer.rotationPitch;
            float[] smoothed = getRotationsSmoothed(baseYaw, basePitch, aimYaw, aimPitch, true);
            if (Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - aimYaw)) < 0.5f && Math.abs(smoothed[1] - aimPitch) < 0.5f) {
                resetting = false;
                restoreInputsAndAutoClicker();
                return;
            }
            RotationHelper.get().forceMovementFix = true;
            e.setYaw(smoothed[0]);
            e.setPitch(smoothed[1]);
            return;
        }

        if (!hasAim) return;

        float[] smoothed = getRotationsSmoothed(baseYaw, basePitch, aimYaw, aimPitch, false);

        if (placing && targetHitPos != null) {
            MovingObjectPosition mop = RotationUtils.rayCastBlock(reach.getInput(), smoothed[0], smoothed[1]);
            if (mop != null && targetHitPos.equals(mop.getBlockPos()) && targetSide == mop.sideHit) {
                int maxBlocks = (int) maxDistance.getInput();
                if (maxBlocks == 0 || clutchBlocksPlaced < maxBlocks) {
                    double tolerance = rotationTolerance.getInput();
                    if (Math.abs(MathHelper.wrapAngleTo180_float(smoothed[0] - RotationUtils.serverRotations[0])) <= tolerance
                            && Math.abs(smoothed[1] - RotationUtils.serverRotations[1]) <= tolerance) {
                        placeAtBlock = mop.getBlockPos();
                        hitSide = mop.sideHit;
                        hitVec = mop.hitVec;
                        placeQueued = true;
                    }
                }
            }
        }

        RotationHelper.get().forceMovementFix = true;
        e.setYaw(smoothed[0]);
        e.setPitch(smoothed[1]);
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!Utils.nullCheck() || !placeQueued) return;

        placeQueued = false;
        if (placeAtBlock != null && hitSide != null && hitVec != null
                && mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), placeAtBlock, hitSide, hitVec)) {
            if (hitSide != EnumFacing.UP) clutchBlocksPlaced++;
            lastPlaced = placeAtBlock;
            mc.thePlayer.swingItem();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if ((placing || resetting || hasAim) && e.button > -1) {
            e.setCanceled(true);
        }
    }

    private void runPrePlayerInteract() {
        if (mc.thePlayer.onGround) clutchBlocksPlaced = 0;
        int ticksExisted = mc.thePlayer.ticksExisted;

        updateAutoClutch(ticksExisted);

        boolean active = selectKeybind.isPressed() || autoClutchActive;
        if (mc.currentScreen != null || !active) {
            clearAim(true);
            disablePlacing(false);
            return;
        }

        BlockPos below = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        if (!canPlaceThrough(below)) {
            disablePlacing(false);
            return;
        }

        int weakSlot = pickBlockSlot();
        if (weakSlot == -1) {
            disablePlacing(false);
            return;
        }

        plannedSlot = weakSlot;
        AimResult target = clutchAim();
        if (target != null) {
            targetHitPos = target.ray.getBlockPos();
            targetSide = target.ray.sideHit;
            aimYaw = target.yaw;
            aimPitch = target.pitch;
            hasAim = true;
            resetting = false;
        }

        if (hasAim && !placing) enablePlacing();

        if (placing || resetting || hasAim) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            equipPlannedSlot();
        }
    }

    private void updateAutoClutch(int ticksExisted) {
        if (autoClutch.isToggled()) {
            int curHurtTime = mc.thePlayer.hurtTime;
            if (curHurtTime > prevHurtTime) {
                autoClutchChecking = true;
                autoClutchCheckCounter = 0;
                autoClutchLandedGuard = false;
            }
            prevHurtTime = curHurtTime;

            if (autoClutchChecking && !autoClutchActive && !autoClutchLandedGuard) {
                if (autoClutchCheckCounter == 0 || autoClutchCheckCounter % 3 == 0) {
                    if (willFallFar(minimumFallDistance.getInput())) {
                        autoClutchActive = true;
                    }
                }
                autoClutchCheckCounter++;
            }

            if (autoClutchLandedGuard) {
                boolean expired = ticksExisted - autoClutchLandedTick >= 10;
                boolean jumped = mc.gameSettings.keyBindJump.isKeyDown();
                boolean airborneUp = !mc.thePlayer.onGround && mc.thePlayer.motionY > 0;
                if (expired || jumped || airborneUp) {
                    autoClutchActive = false;
                    autoClutchChecking = false;
                    autoClutchLandedGuard = false;
                }
            }

            if (autoClutchActive && mc.thePlayer.onGround && mc.thePlayer.hurtTime < mc.thePlayer.maxHurtTime - 2) {
                if (!autoClutchLandedGuard) {
                    autoClutchLandedGuard = true;
                    autoClutchLandedTick = ticksExisted;
                    if (!willFallSoon()) {
                        autoClutchActive = false;
                        autoClutchChecking = false;
                        autoClutchLandedGuard = false;
                    }
                }
            }

            if (!autoClutchActive && !autoClutchLandedGuard && mc.thePlayer.onGround && mc.thePlayer.hurtTime == 0) {
                autoClutchChecking = false;
                autoClutchCheckCounter = 0;
            }
        } else {
            autoClutchActive = false;
            autoClutchChecking = false;
            autoClutchLandedGuard = false;
            prevHurtTime = mc.thePlayer.hurtTime;
        }
    }

    private void enablePlacing() {
        if (placing) return;
        placing = true;
        if (!slotWasSwapped) prevSlot = mc.thePlayer.inventory.currentItem;
        autoClickerWasOn = autoClickerWasOn || (ModuleManager.autoClicker != null && ModuleManager.autoClicker.isEnabled());
        if (autoClickerWasOn && ModuleManager.autoClicker != null) {
            ModuleManager.autoClicker.disable();
        }
    }

    private void disablePlacing(boolean forceRestore) {
        if (!placing && !forceRestore) return;

        placing = false;
        plannedSlot = -1;

        if ((forceRestore || !hasAim) && slotWasSwapped && prevSlot != -1 && prevSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = prevSlot;
            slotWasSwapped = false;
        }
        if (forceRestore) {
            prevSlot = -1;
            restoreInputsAndAutoClicker();
        }
    }

    private void clearAim(boolean allowSnapback) {
        if (slotWasSwapped && prevSlot != -1 && prevSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = prevSlot;
            slotWasSwapped = false;
        }
        targetHitPos = null;
        targetSide = null;
        lastPlaced = null;
        clutchBlocksPlaced = 0;
        if (allowSnapback && hasAim) resetting = true;
        hasAim = false;
        prevSlot = -1;
    }

    private void restoreInputsAndAutoClicker() {
        if (mc.currentScreen == null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), Mouse.isButtonDown(0));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), Mouse.isButtonDown(1));
        }
        if (autoClickerWasOn && ModuleManager.autoClicker != null) {
            ModuleManager.autoClicker.enable();
            autoClickerWasOn = false;
        }
    }

    private boolean willFallFar(double minFall) {
        double startY = mc.thePlayer.posY;
        PredictionState prediction = PredictionState.fromPlayer();
        for (int t = 0; t < 60; t++) {
            prediction.tick(false);
            if (prediction.onGround) {
                return false;
            }
            double fall = startY - prediction.posY;
            if (fall > minFall) {
                return true;
            }
        }
        return false;
    }

    private boolean willFallSoon() {
        PredictionState prediction = PredictionState.fromPlayer();
        for (int t = 0; t < 10; t++) {
            prediction.tick(true);
            if (!prediction.onGround && prediction.motionY < 0) {
                return true;
            }
        }
        return false;
    }

    private AimResult clutchAim() {
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);

        Vec3 futurePos = playerPos;
        if (simulateFuturePosition.isToggled()) {
            PredictionState prediction = PredictionState.fromPlayer();
            for (int t = 0; t < 20; t++) {
                prediction.tick(false);
                if (prediction.posY < playerPos.yCoord - 2 || prediction.onGround) break;
            }
            futurePos = prediction.getPos();
        }

        int feetX = MathHelper.floor_double(playerPos.xCoord);
        int feetZ = MathHelper.floor_double(playerPos.zCoord);
        int feetY = MathHelper.floor_double(playerPos.yCoord);
        int minX = feetX - 5;
        int maxX = feetX + 4;
        int minZ = feetZ - 5;
        int maxZ = feetZ + 4;
        int maxY = feetY - 1;
        int minY = feetY - 4;

        ArrayList<BlockCandidate> candidates = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (canPlaceThrough(pos)) continue;

                    double currentDist = BlockUtils.dist2PointAABB(playerPos, pos);
                    double futureDist = BlockUtils.dist2PointAABB(futurePos, pos);
                    double score = simulateFuturePosition.isToggled() ? (currentDist * 0.3 + futureDist * 0.7) : currentDist;
                    if (pos.equals(lastPlaced)) score *= 0.95;
                    candidates.add(new BlockCandidate(score, pos));
                }
            }
        }

        candidates.sort((a, b) -> Double.compare(a.score, b.score));

        ItemStack held = plannedSlot >= 0 && plannedSlot <= 8 ? mc.thePlayer.inventory.mainInventory[plannedSlot] : null;
        for (BlockCandidate candidate : candidates) {
            boolean underPlayer = isBlockUnderPlayer(candidate.pos, playerPos);
            AimResult result = getBestRotationsToBlock(held, candidate.pos, eye, reach.getInput(), underPlayer);
            if (result != null) return result;
        }

        return null;
    }

    private boolean isBlockUnderPlayer(BlockPos blockPos, Vec3 pos) {
        if (blockPos.getY() >= MathHelper.floor_double(pos.yCoord)) return false;
        for (double[] corner : CORNERS) {
            int cx = MathHelper.floor_double(pos.xCoord + corner[0]);
            int cz = MathHelper.floor_double(pos.zCoord + corner[1]);
            if (blockPos.getX() == cx && blockPos.getZ() == cz) return true;
        }
        return false;
    }

    private AimResult getBestRotationsToBlock(ItemStack held, BlockPos targetCell, Vec3 eye, double reachVal, boolean underPlayer) {
        double inset = 0.05;
        double step = 0.2;
        double jitter = step * 0.1;
        boolean faceSouth = Math.abs(eye.zCoord - (targetCell.getZ() + 1)) < Math.abs(eye.zCoord - targetCell.getZ());
        boolean faceEast = Math.abs(eye.xCoord - (targetCell.getX() + 1)) < Math.abs(eye.xCoord - targetCell.getX());
        float baseYaw = normYaw(RotationUtils.serverRotations[0]);
        float basePitch = RotationUtils.serverRotations[1];
        int n = (int) Math.round(1 / step);

        ArrayList<RotationCandidate> candidates = new ArrayList<>();
        candidates.add(new RotationCandidate(0, baseYaw, basePitch));

        for (int row = 0; row <= n; row++) {
            double v = clamp01(row * step + randomRange(-jitter, jitter));
            for (int col = 0; col <= n; col++) {
                double u = clamp01(col * step + randomRange(-jitter, jitter));

                if (underPlayer) {
                    float[] rV = getRotationsWrapped(eye, targetCell.getX() + u, targetCell.getY() + 1 - inset, targetCell.getZ() + v);
                    double costV = Math.abs(wrapYawDelta(baseYaw, rV[0])) + Math.abs(rV[1] - basePitch);
                    candidates.add(new RotationCandidate(costV, rV[0], rV[1]));
                }

                float[] rZ = getRotationsWrapped(eye, targetCell.getX() + u, targetCell.getY() + v, faceSouth ? targetCell.getZ() + 1 - inset : targetCell.getZ() + inset);
                double costZ = Math.abs(wrapYawDelta(baseYaw, rZ[0])) + Math.abs(rZ[1] - basePitch);
                candidates.add(new RotationCandidate(costZ, rZ[0], rZ[1]));

                float[] rX = getRotationsWrapped(eye, faceEast ? targetCell.getX() + 1 - inset : targetCell.getX() + inset, targetCell.getY() + v, targetCell.getZ() + u);
                double costX = Math.abs(wrapYawDelta(baseYaw, rX[0])) + Math.abs(rX[1] - basePitch);
                candidates.add(new RotationCandidate(costX, rX[0], rX[1]));
            }
        }

        candidates.sort((a, b) -> Double.compare(a.cost, b.cost));

        for (RotationCandidate candidate : candidates) {
            float yaw = unwrapYaw(candidate.yaw, RotationUtils.serverRotations[0]);
            MovingObjectPosition ray = RotationUtils.rayCastBlock(reachVal, yaw, candidate.pitch);
            if (ray == null) continue;

            EnumFacing face = ray.sideHit;
            if (face == EnumFacing.DOWN) continue;
            if (face == EnumFacing.UP && !underPlayer) continue;
            if (!targetCell.equals(ray.getBlockPos())) continue;
            if (!BlockUtils.canPlaceBlockOnSide(held, ray.getBlockPos(), face)) continue;

            return new AimResult(ray, yaw, candidate.pitch);
        }

        return null;
    }

    private int pickBlockSlot() {
        boolean playingBedwars = Utils.getBedwarsStatus() == 2;
        if (!playingBedwars) {
            int current = mc.thePlayer.inventory.currentItem;
            if (isBlockSlot(current)) return current;

            for (int slot = 8; slot >= 0; --slot) {
                if (isBlockSlot(slot)) return slot;
            }
            return -1;
        }

        int best = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int slot = 8; slot >= 0; --slot) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
            if (stack == null || stack.stackSize == 0 || !(stack.getItem() instanceof ItemBlock)) continue;

            Block block = ((ItemBlock) stack.getItem()).getBlock();
            ResourceLocation id = Block.blockRegistry.getNameForObject(block);
            if (id == null) continue;

            Integer score = BLOCK_SCORE.get(id.getResourcePath());
            if (score == null) continue;

            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private boolean isBlockSlot(int slot) {
        if (slot < 0 || slot > 8) return false;
        ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock;
    }

    private void equipPlannedSlot() {
        int current = mc.thePlayer.inventory.currentItem;
        if (plannedSlot != -1 && plannedSlot != current) {
            mc.thePlayer.inventory.currentItem = plannedSlot;
            slotWasSwapped = true;
        }
    }

    private float[] getRotationsSmoothed(float currentYaw, float currentPitch, float targetYaw, float targetPitch, boolean snapback) {
        float curYaw = currentYaw;
        float curPitch = currentPitch;
        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - curYaw);
        float deltaPitch = targetPitch - curPitch;

        if (Math.abs(deltaYaw) < 0.1f) curYaw = targetYaw;
        if (Math.abs(deltaPitch) < 0.1f) curPitch = targetPitch;
        if (curYaw == targetYaw && curPitch == targetPitch) {
            return new float[]{curYaw, RotationUtils.clampPitch(curPitch)};
        }

        float maxStep = (float) (snapback ? snapbackSpeed.getInput() : speed.getInput());
        float factor = 1f - (float) randomRange(0, 0.2);
        maxStep *= factor;

        float totalDelta = Math.abs(deltaYaw) + Math.abs(deltaPitch);
        if (totalDelta <= maxStep) {
            curYaw = targetYaw;
            curPitch = targetPitch;
        } else if (maxStep > 0) {
            float scale = maxStep / totalDelta;
            curYaw += deltaYaw * scale;
            curPitch += deltaPitch * scale;
        }

        return new float[]{curYaw, RotationUtils.clampPitch(curPitch)};
    }

    private boolean canPlaceThrough(BlockPos pos) {
        Block block = BlockUtils.getBlock(pos);
        Material material = block.getMaterial();
        return material == Material.air || material == Material.water || material == Material.lava || block == Blocks.fire;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static double randomRange(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private static float normYaw(float yaw) {
        yaw = ((yaw % 360f) + 360f) % 360f;
        return yaw > 180f ? yaw - 360f : yaw;
    }

    private static float wrapYawDelta(float base, float target) {
        return MathHelper.wrapAngleTo180_float(target - base);
    }

    private static float unwrapYaw(float yaw, float prevYaw) {
        return prevYaw + MathHelper.wrapAngleTo180_float(yaw - prevYaw);
    }

    private static float[] getRotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
        double dx = tx - eye.xCoord;
        double dy = ty - eye.yCoord;
        double dz = tz - eye.zCoord;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontalDistance));
        return new float[]{normYaw(yaw), RotationUtils.clampPitch(pitch)};
    }

    private static class BlockCandidate {
        final double score;
        final BlockPos pos;

        BlockCandidate(double score, BlockPos pos) {
            this.score = score;
            this.pos = pos;
        }
    }

    private static class RotationCandidate {
        final double cost;
        final float yaw;
        final float pitch;

        RotationCandidate(double cost, float yaw, float pitch) {
            this.cost = cost;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static class AimResult {
        final MovingObjectPosition ray;
        final float yaw;
        final float pitch;

        AimResult(MovingObjectPosition ray, float yaw, float pitch) {
            this.ray = ray;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static class PredictionState {
        private AxisAlignedBB box;
        private double motionX;
        private double motionY;
        private double motionZ;
        private double posY;
        private boolean onGround;

        static PredictionState fromPlayer() {
            PredictionState state = new PredictionState();
            state.box = mc.thePlayer.getEntityBoundingBox();
            state.motionX = mc.thePlayer.motionX;
            state.motionY = mc.thePlayer.motionY;
            state.motionZ = mc.thePlayer.motionZ;
            state.posY = mc.thePlayer.posY;
            state.onGround = mc.thePlayer.onGround;
            return state;
        }

        Vec3 getPos() {
            return new Vec3((box.minX + box.maxX) / 2.0, box.minY, (box.minZ + box.maxZ) / 2.0);
        }

        void tick(boolean stopHorizontal) {
            if (stopHorizontal) {
                motionX = 0.0;
                motionZ = 0.0;
            }

            motionY -= 0.08;
            move(motionX, motionY, motionZ);
            motionY *= 0.9800000190734863;
            motionX *= 0.91;
            motionZ *= 0.91;
        }

        private void move(double x, double y, double z) {
            double originalX = x;
            double originalY = y;
            double originalZ = z;

            List<AxisAlignedBB> collisions = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box.addCoord(x, y, z));
            for (AxisAlignedBB collision : collisions) {
                y = collision.calculateYOffset(box, y);
            }
            box = box.offset(0.0, y, 0.0);

            for (AxisAlignedBB collision : collisions) {
                x = collision.calculateXOffset(box, x);
            }
            box = box.offset(x, 0.0, 0.0);

            for (AxisAlignedBB collision : collisions) {
                z = collision.calculateZOffset(box, z);
            }
            box = box.offset(0.0, 0.0, z);

            onGround = originalY != y && originalY < 0.0;
            posY = box.minY;

            if (originalX != x) motionX = 0.0;
            if (originalY != y) motionY = 0.0;
            if (originalZ != z) motionZ = 0.0;
        }
    }
}