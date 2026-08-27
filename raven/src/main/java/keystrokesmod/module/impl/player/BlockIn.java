package keystrokesmod.module.impl.player;

import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockWall;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.util.*;

public class BlockIn extends Module {

    private static final EnumFacing[] HORIZONTALS = {
            EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH
    };

    private static final SupportOffset[] SUPPORTS = {
            new SupportOffset(0, 1, 0, EnumFacing.DOWN),
            new SupportOffset(0, -1, 0, EnumFacing.UP),
            new SupportOffset(0, 0, -1, EnumFacing.NORTH),
            new SupportOffset(0, 0, 1, EnumFacing.SOUTH),
            new SupportOffset(1, 0, 0, EnumFacing.EAST),
            new SupportOffset(-1, 0, 0, EnumFacing.WEST),
    };

    private static final double REACH = 4.5;
    private static final double GRID_INSET = 0.05;
    private static final double GRID_STEP = 0.2;
    private static final int GRID_N = (int) Math.round(1.0 / GRID_STEP);
    private final SliderSetting speed;
    private final SliderSetting randomization;
    private final SliderSetting rotationTol;
    private final KeySetting selectKeybind;
    private final ButtonSetting ignoreBlocksToggle;
    private final ItemListSetting ignoredBlocks;

    private boolean placing;
    private boolean slotWasSwapped;
    private int prevSlot = -1;
    private int plannedSlot = -1;
    private boolean placeQueued;

    private BlockPos targetHitPos;
    private EnumFacing targetSide;
    private float aimYaw;
    private float aimPitch;

    private BlockPos hitAt;
    private EnumFacing hitSide;
    private Vec3 placeAt;

    private float fillCount;
    private float lastFillCount = -1;
    private float circleProgress;
    private float animStartProgress;
    private float animTargetProgress;
    private long animStartTime;

    private boolean lastTargetAdjacent;

    public BlockIn() {
        super("Block In", category.player);
        this.registerSetting(speed = new SliderSetting("Speed", 10, 1, 30, 1));
        this.registerSetting(randomization = new SliderSetting("Randomization", "%", 10, 0, 100, 1));
        this.registerSetting(rotationTol = new SliderSetting("Rotation Tolerance", "\u00B0", 25, 20, 100, 1));
        this.registerSetting(selectKeybind = new KeySetting("Select Keybind", 0));
        this.registerSetting(ignoreBlocksToggle = new ButtonSetting("Ignore blocks", false));
        this.registerSetting(ignoredBlocks = new ItemListSetting("Items"));
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        disablePlacing();
        placeQueued = false;
        fillCount = 0;
        lastFillCount = -1;
        circleProgress = 0;
    }

    @Override
    public void guiUpdate() {
        ignoredBlocks.setVisible(ignoreBlocksToggle.isToggled(), this);
    }

    @SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (!Utils.nullCheck()) return;
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            return;
        }

        runTargetSelection();

        if (mc.currentScreen != null) disablePlacing();
        if (!placing || targetHitPos == null) return;

        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
        float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
        float[] sm = RotationUtils.smoothRotation(baseYaw, basePitch, aimYaw, aimPitch,
                (int) speed.getInput(), (float) randomization.getInput());
        double r = REACH;
        MovingObjectPosition mop = RotationUtils.rayCastBlock(r, sm[0], sm[1]);

        if (mop != null) {
            BlockPos hitBlock = mop.getBlockPos();
            EnumFacing side = mop.sideHit;
            if (hitBlock.equals(targetHitPos) && side == targetSide) {
                double tol = rotationTol.getInput();
                if (Math.abs(sm[0] - RotationUtils.serverRotations[0]) <= tol
                        && Math.abs(sm[1] - RotationUtils.serverRotations[1]) <= tol) {
                    hitAt = hitBlock;
                    hitSide = side;
                    placeAt = mop.hitVec;
                    placeQueued = true;
                }
            }
        }

        RotationHelper.get().forceMovementFix = true;
        e.setYaw(sm[0]);
        e.setPitch(sm[1]);
    }

    private void runTargetSelection() {
        clearAim();

        if (!selectKeybind.isPressed() || mc.currentScreen != null) {
            disablePlacing();
            circleProgress = 0f;
            return;
        }

        int strongSlot = pickBlockSlot(true);
        int weakSlot = pickBlockSlot(false);
        if (strongSlot == -1 && weakSlot == -1) {
            disablePlacing();
            return;
        }

        plannedSlot = (strongSlot != -1 ? strongSlot : weakSlot);

        if (!getTarget()) {
            disablePlacing();
            return;
        }

        if (lastTargetAdjacent) plannedSlot = (strongSlot != -1 ? strongSlot : weakSlot);
        else plannedSlot = (weakSlot != -1 ? weakSlot : strongSlot);

        if (!placing) enablePlacing();

        if (mc.gameSettings.keyBindAttack.isKeyDown() || mc.gameSettings.keyBindUseItem.isKeyDown()) {
            clearAim();
        }

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        equipPlannedSlot();
    }


    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!Utils.nullCheck()) return;

        if (placeQueued) {
            placeQueued = false;
            if (hitAt != null && hitSide != null && placeAt != null) {
                if (mc.playerController.onPlayerRightClick(
                        mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(),
                        hitAt, hitSide, placeAt)) {
                    mc.thePlayer.swingItem();
                }
            }
        }

        fillCount = 0;
        if (selectKeybind.isPressed() && mc.currentScreen == null) {
            BlockPos feet = new BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX),
                    MathHelper.floor_double(mc.thePlayer.posY),
                    MathHelper.floor_double(mc.thePlayer.posZ)
            );

            if (!BlockUtils.replaceable(feet.up().up())) fillCount++;

            for (EnumFacing dir : HORIZONTALS) {
                BlockPos side = feet.offset(dir);
                if (!BlockUtils.replaceable(side)) fillCount++;
                if (!BlockUtils.replaceable(side.up())) fillCount++;
            }

            if (fillCount != lastFillCount) {
                animStartProgress = circleProgress;
                animTargetProgress = Math.max(0f, Math.min(1f, fillCount / 9f));
                animStartTime = System.currentTimeMillis();
                lastFillCount = fillCount;
            }
        }
    }


    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Utils.nullCheck()) return;
        if (fillCount <= 0) return;

        long elapsed = System.currentTimeMillis() - animStartTime;
        if (elapsed < 50L) {
            float t = (float) elapsed / 50f;
            circleProgress = lerp(animStartProgress, animTargetProgress, quadInOutEasing(t));
        } else {
            circleProgress = animTargetProgress;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        float cx = sr.getScaledWidth() / 2f - 1f;
        float cy = sr.getScaledHeight() / 2f;
        float radius = 10f;
        float thickness = 3f;

        RenderUtils.draw2DCircle(cx, cy, radius, 100, thickness, 0f, 0f, 0f, 0.5f);

        if (circleProgress >= 0.999f) {
            RenderUtils.draw2DCircle(cx, cy, radius, 100, thickness, 0f, 1f, 0f, 1f);
            return;
        }

        float startAngle = 90f;
        float endAngle = startAngle + circleProgress * 360f + 0.5f;

        float ratio = Math.max(0f, Math.min(1f, circleProgress));
        int r = (int) ((1f - ratio) * 255f + 0.5f);
        int g = (int) (ratio * 255f + 0.5f);
        int color = ((255 & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8);

        RenderUtils.draw2DCircleArc(cx, cy, radius, startAngle, endAngle, thickness, color);
    }


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if (placing && e.button > -1) {
            e.setCanceled(true);
        }
    }

    public boolean isPlacing() {
        return placing;
    }

    private void enablePlacing() {
        if (placing) return;
        placing = true;
        slotWasSwapped = false;
        prevSlot = mc.thePlayer.inventory.currentItem;
    }

    private void disablePlacing() {
        if (!placing) return;

        if (slotWasSwapped && prevSlot != -1 && prevSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = prevSlot;
        }

        placing = false;
        slotWasSwapped = false;
        prevSlot = -1;
        plannedSlot = -1;

        if (mc.currentScreen == null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), Mouse.isButtonDown(0));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), Mouse.isButtonDown(1));
        }
    }

    private void clearAim() {
        targetHitPos = null;
        targetSide = null;
    }

    private void equipPlannedSlot() {
        int cur = mc.thePlayer.inventory.currentItem;
        if (plannedSlot != -1 && plannedSlot != cur) {
            mc.thePlayer.inventory.currentItem = plannedSlot;
            slotWasSwapped = true;
        }
    }


    private int pickBlockSlot(boolean preferStrong) {
        int best = -1;
        float bestScore = preferStrong ? -1 : Float.MAX_VALUE;

        for (int slot = 8; slot >= 0; --slot) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[slot];
            if (s == null || s.stackSize == 0) continue;
            if (!(s.getItem() instanceof ItemBlock)) continue;
            if (ignoreBlocksToggle.isToggled() && ignoredBlocks.matches(s)) continue;

            Block block = ((ItemBlock) s.getItem()).getBlock();
            float score = BlockUtils.getFistBreakTicks(block);

            if (preferStrong ? score > bestScore : score < bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }


    private boolean getTarget() {
        AimResult result = roofAim();
        if (result == null) result = sidesAim();
        if (result == null) return false;

        BlockPos placed = result.supportBlock.offset(result.face);
        lastTargetAdjacent = isDirectAdjacentPlacement(placed);

        targetHitPos = result.supportBlock;
        targetSide = result.face;
        aimYaw = result.yaw;
        aimPitch = result.pitch;
        return true;
    }

    private AimResult roofAim() {
        Vec3 pos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        BlockPos aboveHead = new BlockPos(
                MathHelper.floor_double(pos.xCoord),
                MathHelper.floor_double(pos.yCoord) + 2,
                MathHelper.floor_double(pos.zCoord)
        );
        if (!BlockUtils.replaceable(aboveHead)) return null;

        if (plannedSlot < 0 || plannedSlot > 8) return null;
        ItemStack held = mc.thePlayer.inventory.mainInventory[plannedSlot];
        double r = REACH;
        Vec3 eye = new Vec3(pos.xCoord, pos.yCoord + mc.thePlayer.getEyeHeight(), pos.zCoord);
        double r2 = r * r;
        double rp12 = (r + 1) * (r + 1);

        int minY = MathHelper.floor_double(eye.yCoord) + 1;
        int maxY = MathHelper.floor_double(eye.yCoord + r);
        int minX = MathHelper.floor_double(eye.xCoord - r);
        int maxX = MathHelper.floor_double(eye.xCoord + r);
        int minZ = MathHelper.floor_double(eye.zCoord - r);
        int maxZ = MathHelper.floor_double(eye.zCoord + r);

        ArrayList<BlockCandidate> cands = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = (x + 0.5) - eye.xCoord;
                    double dy = (y + 0.5) - eye.yCoord;
                    double dz = (z + 0.5) - eye.zCoord;
                    if (dx * dx + dy * dy + dz * dz > rp12) continue;

                    BlockPos bp = new BlockPos(x, y, z);
                    if (BlockUtils.replaceable(bp)) continue;
                    Block block = BlockUtils.getBlock(bp);
                    if (BlockUtils.isInteractable(block) || block instanceof BlockFence || block instanceof BlockWall) continue;

                    double d2 = BlockUtils.dist2PointAABB(eye, bp);
                    if (d2 > r2) continue;

                    cands.add(new BlockCandidate(d2, bp));
                }
            }
        }

        cands.sort((a, b) -> Double.compare(a.dist, b.dist));

        for (BlockCandidate cand : cands) {
            AimResult res = getBestRotationsToBlock(held, cand.pos, eye, r, minY);
            if (res != null) return res;
        }
        return null;
    }

    private AimResult getBestRotationsToBlock(ItemStack held, BlockPos targetCell, Vec3 eye, double reachVal, int minY) {
        float baseYaw = RotationUtils.serverRotations[0];
        float basePitch = RotationUtils.serverRotations[1];

        boolean faceUp = Math.abs(eye.yCoord - (targetCell.getY() + 1)) < Math.abs(eye.yCoord - targetCell.getY());
        boolean faceSouth = Math.abs(eye.zCoord - (targetCell.getZ() + 1)) < Math.abs(eye.zCoord - targetCell.getZ());
        boolean faceEast = Math.abs(eye.xCoord - (targetCell.getX() + 1)) < Math.abs(eye.xCoord - targetCell.getX());

        double bx = targetCell.getX(), by = targetCell.getY(), bz = targetCell.getZ();
        double jit = GRID_STEP * 0.1;

        ArrayList<RotationCandidate> cands = new ArrayList<>((GRID_N + 1) * (GRID_N + 1) * 3 + 1);
        cands.add(new RotationCandidate(0, baseYaw, basePitch));

        for (int row = 0; row <= GRID_N; row++) {
            double v = clamp01(row * GRID_STEP + jitter(jit));
            for (int col = 0; col <= GRID_N; col++) {
                double u = clamp01(col * GRID_STEP + jitter(jit));

                float[] rY = RotationUtils.getRotationsFromEye(eye,
                        bx + u, faceUp ? by + 1 - GRID_INSET : by + GRID_INSET, bz + v);
                cands.add(new RotationCandidate(
                        Math.abs(MathHelper.wrapAngleTo180_float(rY[0] - baseYaw)) + Math.abs(rY[1] - basePitch),
                        rY[0], rY[1]));

                float[] rZ = RotationUtils.getRotationsFromEye(eye,
                        bx + u, by + v, faceSouth ? bz + 1 - GRID_INSET : bz + GRID_INSET);
                cands.add(new RotationCandidate(
                        Math.abs(MathHelper.wrapAngleTo180_float(rZ[0] - baseYaw)) + Math.abs(rZ[1] - basePitch),
                        rZ[0], rZ[1]));

                float[] rX = RotationUtils.getRotationsFromEye(eye,
                        faceEast ? bx + 1 - GRID_INSET : bx + GRID_INSET, by + v, bz + u);
                cands.add(new RotationCandidate(
                        Math.abs(MathHelper.wrapAngleTo180_float(rX[0] - baseYaw)) + Math.abs(rX[1] - basePitch),
                        rX[0], rX[1]));
            }
        }

        cands.sort((a, b) -> Double.compare(a.cost, b.cost));

        int byY = targetCell.getY();
        for (RotationCandidate c : cands) {
            MovingObjectPosition mop = RotationUtils.rayCastBlock(reachVal, c.yaw, c.pitch);
            if (mop == null) continue;
            BlockPos hitBlock = mop.getBlockPos();
            EnumFacing face = mop.sideHit;
            if (hitBlock.equals(targetCell) && hitBlock.getY() >= minY
                    && !(face == EnumFacing.DOWN && byY == minY)
                    && BlockUtils.canPlaceBlockOnSide(held, hitBlock, face)) {
                return new AimResult(hitBlock, face, c.yaw, c.pitch);
            }
        }
        return null;
    }

    private AimResult sidesAim() {
        BlockPos feet = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY),
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        BlockPos head = feet.up();
        double r = REACH;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);

        ArrayList<BlockPos> baseline = new ArrayList<>(8);
        for (EnumFacing dir : HORIZONTALS) {
            baseline.add(feet.offset(dir));
            baseline.add(head.offset(dir));
        }

        ArrayList<BlockPos> primaryGoals = new ArrayList<>(baseline.size());
        for (BlockPos pos : baseline) {
            if (!BlockUtils.replaceable(pos)) continue;
            if (!BlockUtils.hasAirNeighbor(pos, feet, head)) continue;
            primaryGoals.add(pos);
        }
        if (primaryGoals.isEmpty()) return null;

        Vec3 enemyPos = Utils.getClosestPlayerPos(100);
        if (enemyPos != null) {
            baseline.sort((a, b) -> {
                double da = sq(a.getX() + 0.5 - enemyPos.xCoord)
                        + sq(a.getY() + 0.5 - enemyPos.yCoord)
                        + sq(a.getZ() + 0.5 - enemyPos.zCoord);
                double db = sq(b.getX() + 0.5 - enemyPos.xCoord)
                        + sq(b.getY() + 0.5 - enemyPos.yCoord)
                        + sq(b.getZ() + 0.5 - enemyPos.zCoord);
                return Double.compare(da, db);
            });
            int picked = 0;
            for (int i = 0; i < baseline.size() && picked < 3; i++) {
                BlockPos pos = baseline.get(i);
                if (!BlockUtils.replaceable(pos)) continue;
                if (!BlockUtils.hasAirNeighbor(pos, feet, head)) continue;
                AimResult rEnemy = findBestForGoals(Collections.singletonList(pos), r, eye);
                if (rEnemy != null) return rEnemy;
                picked++;
            }
        }

        AimResult result = findBestForGoals(primaryGoals, r, eye);
        if (result != null) return result;

        ArrayList<BlockPos> frontier = new ArrayList<>(primaryGoals);
        HashSet<Long> seen = new HashSet<>(frontier.size() * 8);
        for (BlockPos g : frontier) seen.add(g.toLong());

        for (int iter = 0; iter < 5; iter++) {
            if (frontier.isEmpty()) break;

            ArrayList<BlockPos> layer = new ArrayList<>(frontier.size() * 3);
            for (BlockPos g : frontier) {
                for (EnumFacing f : EnumFacing.values()) {
                    BlockPos s = g.offset(f);
                    if (!BlockUtils.replaceable(s)) continue;
                    if (!seen.add(s.toLong())) continue;
                    layer.add(s);
                }
            }

            if (!layer.isEmpty()) {
                AimResult rLayer = findBestForGoals(layer, r, eye);
                if (rLayer != null) return rLayer;
            }
            frontier = layer;
        }
        return null;
    }

    private AimResult findBestForGoals(List<BlockPos> goals, double reachVal, Vec3 eye) {
        if (goals == null || goals.isEmpty()) return null;
        if (plannedSlot < 0 || plannedSlot > 8) return null;

        ItemStack held = mc.thePlayer.inventory.mainInventory[plannedSlot];
        float curYaw = RotationUtils.serverRotations[0];
        float curPitch = RotationUtils.serverRotations[1];

        MovingObjectPosition now = RotationUtils.rayCastBlock(reachVal, curYaw, curPitch);
        if (now != null) {
            BlockPos support = now.getBlockPos();
            EnumFacing faceHit = now.sideHit;

            if (!BlockUtils.replaceable(support) && BlockUtils.canPlaceBlockOnSide(held, support, faceHit)) {
                for (BlockPos goal : goals) {
                    AimResult ok = tryPlacement(reachVal, RotationUtils.serverRotations[0],
                            RotationUtils.serverRotations[1], support, faceHit, goal);
                    if (ok != null) return ok;
                }
            }
        }

        double jit = GRID_STEP * 0.1;
        double insetTop = 1 - GRID_INSET - 1e-3;
        double insetBot = GRID_INSET + 1e-3;

        ArrayList<PlacementCandidate> cands = new ArrayList<>(Math.max(16, goals.size() * 6 * (GRID_N + 1) * (GRID_N + 1)));

        for (BlockPos g : goals) {
            for (SupportOffset s : SUPPORTS) {
                BlockPos support = new BlockPos(g.getX() + s.dx, g.getY() + s.dy, g.getZ() + s.dz);
                if (BlockUtils.replaceable(support) || !BlockUtils.canPlaceBlockOnSide(held, support, s.face)) continue;

                double sx = support.getX(), sy = support.getY(), sz = support.getZ();

                for (int row = 0; row <= GRID_N; row++) {
                    boolean ltr = (row & 1) == 0;
                    double v = clamp01(row * GRID_STEP + jitter(jit));

                    for (int col = 0; col <= GRID_N; col++) {
                        double cu = clamp01(col * GRID_STEP + jitter(jit));
                        double u = ltr ? cu : 1.0 - cu;

                        double px, py, pz;
                        if (s.dy != 0) {
                            px = sx + u; pz = sz + v;
                            py = sy + (s.dy < 0 ? insetTop : insetBot);
                        } else if (s.dz != 0) {
                            px = sx + u; py = sy + v;
                            pz = sz + (s.dz < 0 ? insetTop : insetBot);
                        } else {
                            pz = sz + u; py = sy + v;
                            px = sx + (s.dx < 0 ? insetTop : insetBot);
                        }

                        float[] rot = RotationUtils.getRotationsFromEye(eye, px, py, pz);
                        float dYaw = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - curYaw));
                        float dPit = Math.abs(rot[1] - curPitch);
                        if (dYaw < 0.1f && dPit < 0.1f) continue;

                        cands.add(new PlacementCandidate(dYaw + dPit, rot[0], rot[1], support, s.face, g));
                    }
                }
            }
        }

        if (cands.isEmpty()) return null;

        cands.sort((a, b) -> Double.compare(a.cost, b.cost));

        for (PlacementCandidate c : cands) {
            AimResult ok = tryPlacement(reachVal, c.yaw, c.pitch, c.support, c.face, c.goal);
            if (ok != null) return ok;
        }
        return null;
    }


    private AimResult tryPlacement(double reachVal, float yaw, float pit, BlockPos expectedSupport, EnumFacing expectedFace, BlockPos goal) {
        MovingObjectPosition mop = RotationUtils.rayCastBlock(reachVal, yaw, pit);
        if (mop == null) return null;
        BlockPos hitBlock = mop.getBlockPos();
        EnumFacing faceHit = mop.sideHit;
        if (!hitBlock.equals(expectedSupport)) return null;
        if (faceHit != expectedFace) return null;
        BlockPos placed = hitBlock.offset(faceHit);
        if (!placed.equals(goal)) return null;
        return new AimResult(hitBlock, faceHit, yaw, pit);
    }

    private boolean isDirectAdjacentPlacement(BlockPos p) {
        BlockPos feet = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY),
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        int dx = p.getX() - feet.getX();
        int dy = p.getY() - feet.getY();
        int dz = p.getZ() - feet.getZ();
        if (dx == 0 && dz == 0 && dy == 2) return true;
        return (dy == 0 || dy == 1)
                && ((Math.abs(dx) == 1 && dz == 0) || (Math.abs(dz) == 1 && dx == 0));
    }

    private static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    private static float quadInOutEasing(float t) {
        if (t < 0.5f) return 2f * t * t;
        return -1f + (4f - 2f * t) * t;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static double jitter(double range) {
        return range > 0 ? (Math.random() * 2 - 1) * range : 0;
    }


    private static class SupportOffset {
        final int dx, dy, dz;
        final EnumFacing face;
        SupportOffset(int dx, int dy, int dz, EnumFacing face) {
            this.dx = dx; this.dy = dy; this.dz = dz; this.face = face;
        }
    }

    private static class BlockCandidate {
        final double dist;
        final BlockPos pos;
        BlockCandidate(double dist, BlockPos pos) { this.dist = dist; this.pos = pos; }
    }

    private static class RotationCandidate {
        final double cost;
        final float yaw, pitch;
        RotationCandidate(double cost, float yaw, float pitch) {
            this.cost = cost; this.yaw = yaw; this.pitch = pitch;
        }
    }

    private static class PlacementCandidate {
        final double cost;
        final float yaw, pitch;
        final BlockPos support, goal;
        final EnumFacing face;
        PlacementCandidate(double cost, float yaw, float pitch, BlockPos support, EnumFacing face, BlockPos goal) {
            this.cost = cost; this.yaw = yaw; this.pitch = pitch;
            this.support = support; this.face = face; this.goal = goal;
        }
    }

    private static class AimResult {
        final BlockPos supportBlock;
        final EnumFacing face;
        final float yaw, pitch;
        AimResult(BlockPos supportBlock, EnumFacing face, float yaw, float pitch) {
            this.supportBlock = supportBlock; this.face = face; this.yaw = yaw; this.pitch = pitch;
        }
    }
}
