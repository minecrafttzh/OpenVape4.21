package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class Trajectories extends Module {
    private enum PhysicsModel {
        ARROW,
        THROWABLE,
        FISH_HOOK
    }

    private static final class TrajectoryProps {
        final PhysicsModel physicsModel;
        final double gravity;
        final double drag;
        final double dragInWater;
        final double hitboxRadius;
        final double width;
        final double height;
        final double initialVelocity;
        final boolean requiresInitialTickCorrection;
        final boolean ignoreBlockWithoutBoundingBox;

        TrajectoryProps(PhysicsModel physicsModel, double gravity, double drag, double dragInWater, double hitboxRadius,
                        double width, double height, double initialVelocity,
                        boolean requiresInitialTickCorrection, boolean ignoreBlockWithoutBoundingBox) {
            this.physicsModel = physicsModel;
            this.gravity = gravity;
            this.drag = drag;
            this.dragInWater = dragInWater;
            this.hitboxRadius = hitboxRadius;
            this.width = width;
            this.height = height;
            this.initialVelocity = initialVelocity;
            this.requiresInitialTickCorrection = requiresInitialTickCorrection;
            this.ignoreBlockWithoutBoundingBox = ignoreBlockWithoutBoundingBox;
        }
    }

    private static final class FluidState {
        final boolean inWater;
        final boolean inLava;
        final Vec3 flowDirection;

        FluidState(boolean inWater, boolean inLava, Vec3 flowDirection) {
            this.inWater = inWater;
            this.inLava = inLava;
            this.flowDirection = flowDirection;
        }
    }

    private static final class BlockCollisionResult {
        final MovingObjectPosition hit;
        final double distanceSq;

        BlockCollisionResult(MovingObjectPosition hit, double distanceSq) {
            this.hit = hit;
            this.distanceSq = distanceSq;
        }
    }

    private static final int HIT_NONE = 0;
    private static final int HIT_ENTITY = 1;
    private static final int HIT_WALL = 2;
    private static final int HIT_GROUND = 3;

    private ButtonSetting disableUnchargedBow;
    private ButtonSetting highlightEntities;
    private ButtonSetting shortenLine;
    private SliderSetting lineThickness;
    private ButtonSetting addPlayerVelocity;

    private ColorSetting defaultColor;
    private ColorSetting enemyColor;
    private ColorSetting wallColor;
    private ColorSetting groundColor;
    private SliderSetting maxTicks;
    private ButtonSetting showLanding;
    private ButtonSetting onlyWhenHolding;

    public Trajectories() {
        super("Trajectories", category.render);
        this.registerSetting(disableUnchargedBow = new ButtonSetting("Disable uncharged bow", true));
        this.registerSetting(highlightEntities = new ButtonSetting("Highlight on entity", true));
        this.registerSetting(shortenLine = new ButtonSetting("Shorten line", false));
        this.registerSetting(lineThickness = new SliderSetting("Line thickness", 2.0, 1.0, 5.0, 0.1));

        this.registerSetting(defaultColor = new ColorSetting("Default", 170, 0, 255));
        this.registerSetting(enemyColor = new ColorSetting("Enemy Hit", 255, 50, 50));
        this.registerSetting(wallColor = new ColorSetting("Wall Hit", 50, 255, 50));
        this.registerSetting(groundColor = new ColorSetting("Ground Hit", 85, 255, 255));

        this.registerSetting(maxTicks = new SliderSetting("Max ticks", 100.0, 30.0, 200.0, 10.0));
        this.registerSetting(showLanding = new ButtonSetting("Show landing", true));
        this.registerSetting(onlyWhenHolding = new ButtonSetting("Only when holding", true));
        this.registerSetting(addPlayerVelocity = new ButtonSetting("Add player velocity", false));
    }

    private static final double ARROW_WATER_DRAG = 0.6D;
    private static final double THROWABLE_WATER_DRAG = 0.8D;
    private static final double FISH_HOOK_DRAG = 0.92D;
    private static final double WATER_FLOW_ACCELERATION = 0.014D;
    private static final double WATER_CHECK_EXPAND_Y = -0.4000000059604645D;
    private static final double WATER_CHECK_CONTRACT = 0.001D;
    private static final double LAVA_CHECK_EXPAND_XZ = -0.10000000149011612D;
    private static final double LAVA_CHECK_EXPAND_Y = -0.4000000059604645D;
    private static final int FISH_HOOK_WATER_SLICE_COUNT = 5;
    private static final double FISH_HOOK_BUOYANCY_ACCELERATION = 0.03999999910593033D;
    private static final double FISH_HOOK_WATER_VERTICAL_DAMPING = 0.8D;
    private static final double FISH_HOOK_WATER_DRAG_MULTIPLIER = 0.9D;
    private static final double FIRST_PERSON_RENDER_CLIP_DISTANCE = 0.5D;
    private static final double COLLISION_EPSILON_SQ = 1.0E-7D;
    private static final double ENTITY_HIT_EXPANSION = 0.3D;

    private float getBowVelocity(float partialTicks) {
        int timeLeft = mc.thePlayer.getItemInUseCount();
        float drawTicks = (72000 - timeLeft) + partialTicks;
        float f = drawTicks / 20.0f;
        f = (f * f + f * 2.0f) / 3.0f;
        if (f > 1.0f) f = 1.0f;
        return f * 2.0f * 1.5f;
    }

    private TrajectoryProps getProjectileProperties(Item item, EntityPlayer player, float partialTicks) {
        if (item == Items.bow) {
            float vel = getBowVelocity(partialTicks);
            return new TrajectoryProps(PhysicsModel.ARROW, 0.05, 0.99, ARROW_WATER_DRAG, 0.5, 0.5, 0.5, vel, false, true);
        }
        if (item == Items.ender_pearl) {
            return new TrajectoryProps(PhysicsModel.THROWABLE, 0.03, 0.99, THROWABLE_WATER_DRAG, 0.25, 0.25, 0.25, 1.5, true, false);
        }
        if (item == Items.snowball || item == Items.egg) {
            return new TrajectoryProps(PhysicsModel.THROWABLE, 0.03, 0.99, THROWABLE_WATER_DRAG, 0.25, 0.25, 0.25, 1.5, true, false);
        }
        if (item == Items.experience_bottle) {
            return new TrajectoryProps(PhysicsModel.THROWABLE, 0.07, 0.99, THROWABLE_WATER_DRAG, 0.25, 0.25, 0.25, 0.7, true, false);
        }
        if (item == Items.potionitem) {
            return new TrajectoryProps(PhysicsModel.THROWABLE, 0.05, 0.99, THROWABLE_WATER_DRAG, 0.25, 0.25, 0.25, 0.5, true, false);
        }
        if (item == Items.fishing_rod) {
            return new TrajectoryProps(PhysicsModel.FISH_HOOK, 0.04, FISH_HOOK_DRAG, FISH_HOOK_DRAG, 0.25, 0.25, 0.25, 1.5, false, false);
        }
        return null;
    }

    private static final float BEDBUG_SNOWBALL_VELOCITY = 1.75f;

    private boolean isHypixelBedwars() {
        if (!Utils.isHypixel() || mc.theWorld == null) return false;
        net.minecraft.scoreboard.Scoreboard sb = mc.theWorld.getScoreboard();
        if (sb == null) return false;
        net.minecraft.scoreboard.ScoreObjective objective = sb.getObjectiveInDisplaySlot(1);
        return objective != null && Utils.stripString(objective.getDisplayName()).contains("BED WARS");
    }

    private boolean isBedbugSnowball(ItemStack stack) {
        if (stack == null || stack.getItem() != Items.snowball) return false;
        String name = stack.getDisplayName();
        if (name == null) return false;
        return Utils.stripString(name).toLowerCase().contains("bedbug");
    }

    private FluidState sampleFluidState(double posX, double posY, double posZ, TrajectoryProps props) {
        AxisAlignedBB entityBox = getEntityBox(posX, posY, posZ, props);
        AxisAlignedBB waterCheckBox = entityBox.expand(0.0D, WATER_CHECK_EXPAND_Y, 0.0D)
                .contract(WATER_CHECK_CONTRACT, WATER_CHECK_CONTRACT, WATER_CHECK_CONTRACT);

        int minX = MathHelper.floor_double(waterCheckBox.minX);
        int maxX = MathHelper.floor_double(waterCheckBox.maxX + 1.0D);
        int minY = MathHelper.floor_double(waterCheckBox.minY);
        int maxY = MathHelper.floor_double(waterCheckBox.maxY + 1.0D);
        int minZ = MathHelper.floor_double(waterCheckBox.minZ);
        int maxZ = MathHelper.floor_double(waterCheckBox.maxZ + 1.0D);

        if (!mc.theWorld.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new FluidState(false, false, new Vec3(0.0D, 0.0D, 0.0D));
        }

        boolean inWater = false;
        Vec3 flowDirection = new Vec3(0.0D, 0.0D, 0.0D);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x < maxX; ++x) {
            for (int y = minY; y < maxY; ++y) {
                for (int z = minZ; z < maxZ; ++z) {
                    mutablePos.set(x, y, z);
                    IBlockState blockState = mc.theWorld.getBlockState(mutablePos);

                    if (blockState.getBlock().getMaterial() != Material.water) {
                        continue;
                    }

                    double liquidSurfaceY = (double) ((float) (y + 1) -
                            BlockLiquid.getLiquidHeightPercent((Integer) blockState.getValue(BlockLiquid.LEVEL)));

                    if ((double) maxY >= liquidSurfaceY) {
                        inWater = true;
                        flowDirection = blockState.getBlock().modifyAcceleration(mc.theWorld, mutablePos, mc.thePlayer, flowDirection);
                    }
                }
            }
        }

        if (flowDirection.lengthVector() > 0.0D) {
            flowDirection = flowDirection.normalize();
        }

        boolean inLava = mc.theWorld.isMaterialInBB(
                entityBox.expand(LAVA_CHECK_EXPAND_XZ, LAVA_CHECK_EXPAND_Y, LAVA_CHECK_EXPAND_XZ),
                Material.lava
        );

        return new FluidState(inWater, inLava, flowDirection);
    }

    private AxisAlignedBB getEntityBox(double posX, double posY, double posZ, TrajectoryProps props) {
        return new AxisAlignedBB(
                posX - props.width * 0.5D,
                posY,
                posZ - props.width * 0.5D,
                posX + props.width * 0.5D,
                posY + props.height,
                posZ + props.width * 0.5D
        );
    }

    private double sampleWaterSubmersion(double posX, double posY, double posZ, TrajectoryProps props) {
        AxisAlignedBB entityBox = getEntityBox(posX, posY, posZ, props);
        double submersion = 0.0D;

        for (int slice = 0; slice < FISH_HOOK_WATER_SLICE_COUNT; ++slice) {
            double boxHeight = entityBox.maxY - entityBox.minY;
            double minSliceY = entityBox.minY + boxHeight * (double) slice / (double) FISH_HOOK_WATER_SLICE_COUNT;
            double maxSliceY = entityBox.minY + boxHeight * (double) (slice + 1) / (double) FISH_HOOK_WATER_SLICE_COUNT;
            AxisAlignedBB sliceBox = new AxisAlignedBB(
                    entityBox.minX,
                    minSliceY,
                    entityBox.minZ,
                    entityBox.maxX,
                    maxSliceY,
                    entityBox.maxZ
            );

            if (mc.theWorld.isAABBInMaterial(sliceBox, Material.water)) {
                submersion += 1.0D / (double) FISH_HOOK_WATER_SLICE_COUNT;
            }
        }

        return submersion;
    }

    private void applyWaterFlowAcceleration(FluidState fluidState, double[] mot) {
        if (!fluidState.inWater || fluidState.flowDirection.lengthVector() <= 0.0D) {
            return;
        }

        mot[0] += fluidState.flowDirection.xCoord * WATER_FLOW_ACCELERATION;
        mot[1] += fluidState.flowDirection.yCoord * WATER_FLOW_ACCELERATION;
        mot[2] += fluidState.flowDirection.zCoord * WATER_FLOW_ACCELERATION;
    }

    private void tickVelocity(TrajectoryProps props, FluidState fluidState, double[] mot) {
        double drag = fluidState.inWater ? props.dragInWater : props.drag;
        mot[0] *= drag;
        mot[1] *= drag;
        mot[2] *= drag;
        mot[1] -= props.gravity;
    }

    private void tickFishingHookVelocity(double posX, double posY, double posZ, TrajectoryProps props, double[] mot) {
        double drag = props.drag;
        double waterSubmersion = sampleWaterSubmersion(posX, posY, posZ, props);
        double buoyancy = waterSubmersion * 2.0D - 1.0D;

        mot[1] += FISH_HOOK_BUOYANCY_ACCELERATION * buoyancy;

        if (waterSubmersion > 0.0D) {
            drag *= FISH_HOOK_WATER_DRAG_MULTIPLIER;
            mot[1] *= FISH_HOOK_WATER_VERTICAL_DAMPING;
        }

        mot[0] *= drag;
        mot[1] *= drag;
        mot[2] *= drag;
    }

    private void tickPostMoveVelocity(double posX, double posY, double posZ, TrajectoryProps props, FluidState fluidState, double[] mot) {
        if (props.physicsModel == PhysicsModel.FISH_HOOK) {
            tickFishingHookVelocity(posX, posY, posZ, props, mot);
            return;
        }

        if (fluidState.inLava && !fluidState.inWater) {
            // Vanilla 1.8.9 projectiles still use air drag in lava; lava only affects burning state.
        }
        tickVelocity(props, fluidState, mot);
    }

    private AxisAlignedBB expandEntityCollisionBox(AxisAlignedBB box) {
        return box.expand(ENTITY_HIT_EXPANSION, ENTITY_HIT_EXPANSION, ENTITY_HIT_EXPANSION);
    }

    private AxisAlignedBB getBlockSweepBounds(Vec3 start, Vec3 end) {
        return new AxisAlignedBB(
                Math.min(start.xCoord, end.xCoord),
                Math.min(start.yCoord, end.yCoord),
                Math.min(start.zCoord, end.zCoord),
                Math.max(start.xCoord, end.xCoord),
                Math.max(start.yCoord, end.yCoord),
                Math.max(start.zCoord, end.zCoord)
        );
    }

    private AxisAlignedBB getVanillaProjectileBounds(Block block, BlockPos pos) {
        block.setBlockBoundsBasedOnState(mc.theWorld, pos);
        return block.getSelectedBoundingBox(mc.theWorld, pos);
    }

    private AxisAlignedBB intersectBoxes(AxisAlignedBB first, AxisAlignedBB second) {
        double minX = Math.max(first.minX, second.minX);
        double minY = Math.max(first.minY, second.minY);
        double minZ = Math.max(first.minZ, second.minZ);
        double maxX = Math.min(first.maxX, second.maxX);
        double maxY = Math.min(first.maxY, second.maxY);
        double maxZ = Math.min(first.maxZ, second.maxZ);

        if (maxX - minX <= 1.0E-7D || maxY - minY <= 1.0E-7D || maxZ - minZ <= 1.0E-7D) {
            return null;
        }

        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private BlockCollisionResult rayTraceBlockCollisionBoxes(Vec3 start, Vec3 end, TrajectoryProps props) {
        AxisAlignedBB sweepBounds = getBlockSweepBounds(start, end);
        int minX = MathHelper.floor_double(sweepBounds.minX);
        int maxX = MathHelper.floor_double(sweepBounds.maxX + 1.0D);
        int minY = MathHelper.floor_double(sweepBounds.minY);
        int maxY = MathHelper.floor_double(sweepBounds.maxY + 1.0D);
        int minZ = MathHelper.floor_double(sweepBounds.minZ);
        int maxZ = MathHelper.floor_double(sweepBounds.maxZ + 1.0D);

        if (!mc.theWorld.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new BlockCollisionResult(null, Double.MAX_VALUE);
        }

        List<AxisAlignedBB> collisionBoxes = new ArrayList<>();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        MovingObjectPosition bestHit = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (int x = minX; x < maxX; ++x) {
            for (int y = minY; y < maxY; ++y) {
                for (int z = minZ; z < maxZ; ++z) {
                    mutablePos.set(x, y, z);
                    IBlockState blockState = mc.theWorld.getBlockState(mutablePos);
                    Block block = blockState.getBlock();

                    if ((!props.ignoreBlockWithoutBoundingBox
                            || block.getCollisionBoundingBox(mc.theWorld, mutablePos, blockState) != null)
                            && block.canCollideCheck(blockState, false)) {
                        collisionBoxes.clear();
                        AxisAlignedBB vanillaProjectileBounds = getVanillaProjectileBounds(block, mutablePos);
                        block.addCollisionBoxesToList(mc.theWorld, mutablePos, blockState, sweepBounds, collisionBoxes, null);

                        for (AxisAlignedBB collisionBox : collisionBoxes) {
                            AxisAlignedBB projectileCollisionBox = intersectBoxes(collisionBox, vanillaProjectileBounds);
                            if (projectileCollisionBox == null) {
                                continue;
                            }

                            MovingObjectPosition hit = projectileCollisionBox.calculateIntercept(start, end);
                            if (hit == null) {
                                continue;
                            }

                            double distanceSq = start.squareDistanceTo(hit.hitVec);
                            if (distanceSq + COLLISION_EPSILON_SQ < bestDistanceSq) {
                                bestDistanceSq = distanceSq;
                                bestHit = new MovingObjectPosition(hit.hitVec, hit.sideHit, new BlockPos(mutablePos));
                            }
                        }
                    }
                }
            }
        }

        return new BlockCollisionResult(bestHit, bestDistanceSq);
    }

    private BlockCollisionResult getNearestBlockCollision(Vec3 start, Vec3 end, TrajectoryProps props) {
        MovingObjectPosition vanillaHit = mc.theWorld.rayTraceBlocks(start, end, false, props.ignoreBlockWithoutBoundingBox, false);
        double vanillaDistanceSq = vanillaHit != null ? start.squareDistanceTo(vanillaHit.hitVec) : Double.MAX_VALUE;

        BlockCollisionResult collisionBoxHit = rayTraceBlockCollisionBoxes(start, end, props);
        if (collisionBoxHit.hit != null && collisionBoxHit.distanceSq + COLLISION_EPSILON_SQ < vanillaDistanceSq) {
            return collisionBoxHit;
        }

        return new BlockCollisionResult(vanillaHit, vanillaDistanceSq);
    }

    private List<double[]> clipRenderPoints(List<double[]> renderPoints, double hiddenDistance) {
        if (hiddenDistance <= 0.0D || renderPoints.size() < 2) {
            return renderPoints;
        }

        List<double[]> clippedPoints = new ArrayList<>();
        double remainingDistance = hiddenDistance;
        double[] previousPoint = renderPoints.get(0);

        for (int i = 1; i < renderPoints.size(); ++i) {
            double[] currentPoint = renderPoints.get(i);
            double deltaX = currentPoint[0] - previousPoint[0];
            double deltaY = currentPoint[1] - previousPoint[1];
            double deltaZ = currentPoint[2] - previousPoint[2];
            double segmentLength = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

            if (remainingDistance > 0.0D) {
                if (segmentLength <= 1.0E-7D) {
                    previousPoint = currentPoint;
                    continue;
                }

                if (segmentLength <= remainingDistance) {
                    remainingDistance -= segmentLength;
                    previousPoint = currentPoint;
                    continue;
                }

                double t = remainingDistance / segmentLength;
                clippedPoints.add(new double[] {
                        previousPoint[0] + deltaX * t,
                        previousPoint[1] + deltaY * t,
                        previousPoint[2] + deltaZ * t
                });
                clippedPoints.add(currentPoint);
                remainingDistance = 0.0D;
            } else {
                clippedPoints.add(currentPoint);
            }

            previousPoint = currentPoint;
        }

        return clippedPoints;
    }

    private ItemStack getHeldProjectile(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held == null) return null;
        Item item = held.getItem();
        if (item == Items.ender_pearl || item == Items.snowball || item == Items.egg || item == Items.experience_bottle) {
            return held;
        }
        if (item == Items.potionitem) {
            if (ItemPotion.isSplash(held.getMetadata())) {
                return held;
            }
            return null;
        }
        if (item instanceof ItemBow) {
            return held;
        }
        if (item == Items.fishing_rod) {
            return held;
        }
        return null;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || mc.theWorld == null) {
            return;
        }
        EntityPlayer player = mc.thePlayer;
        float partialTicks = e.partialTicks;
        ItemStack heldStack = getHeldProjectile(player);
        if (onlyWhenHolding.isToggled() && heldStack == null) return;
        if (heldStack == null) return;

        Item item = heldStack.getItem();
        TrajectoryProps props = getProjectileProperties(item, player, partialTicks);
        if (props == null) return;

        double velocity = props.initialVelocity;
        if (item == Items.snowball && isHypixelBedwars() && isBedbugSnowball(heldStack)) {
            velocity = BEDBUG_SNOWBALL_VELOCITY;
        }

        if (item == Items.bow && disableUnchargedBow.isToggled() && (!player.isUsingItem() || velocity < 0.1)) return;

        float yaw = (float) Math.toRadians(player.rotationYaw);
        float pitch = (float) Math.toRadians(player.rotationPitch);
        double posX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - MathHelper.cos(yaw) * 0.16f;
        double posY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks + player.getEyeHeight() - 0.10;
        double posZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - MathHelper.sin(yaw) * 0.16f;
        double hiddenRenderDistance = mc.gameSettings.thirdPersonView == 0 ? FIRST_PERSON_RENDER_CLIP_DISTANCE : 0.0D;

        double motX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        double motY = -MathHelper.sin(pitch);
        double motZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);
        double len = Math.sqrt(motX * motX + motY * motY + motZ * motZ);
        motX /= len;
        motY /= len;
        motZ /= len;
        motX *= velocity;
        motY *= velocity;
        motZ *= velocity;

        if (addPlayerVelocity.isToggled()) {
            motX += player.motionX;
            motY += player.onGround ? 0 : player.motionY;
            motZ += player.motionZ;
        }

        double[] mot = new double[]{motX, motY, motZ};
        if (props.requiresInitialTickCorrection) {
            FluidState fluidState = sampleFluidState(posX, posY, posZ, props);
            applyWaterFlowAcceleration(fluidState, mot);
            tickVelocity(props, fluidState, mot);
            motX = mot[0];
            motY = mot[1];
            motZ = mot[2];
        }

        List<double[]> renderPoints = new ArrayList<>();
        MovingObjectPosition hitBlock = null;
        Entity hitEntity = null;
        AxisAlignedBB hitEntityBox = null;
        int hitType = HIT_NONE;
        Vec3 hitPos = null;
        Vec3 terminalPos = null;
        RenderManager rm = mc.getRenderManager();
        final int maxSteps = (int) maxTicks.getInput();
        final int SUB = 4;
        final double hw = props.hitboxRadius;

        outer:
        for (int i = 0; i < maxSteps; i++) {
            FluidState fluidState = sampleFluidState(posX, posY, posZ, props);
            mot[0] = motX;
            mot[1] = motY;
            mot[2] = motZ;
            applyWaterFlowAcceleration(fluidState, mot);
            motX = mot[0];
            motY = mot[1];
            motZ = mot[2];

            double nextX = posX + motX;
            double nextY = posY + motY;
            double nextZ = posZ + motZ;

            Vec3 start = new Vec3(posX, posY, posZ);
            Vec3 end = new Vec3(nextX, nextY, nextZ);
            BlockCollisionResult blockCollision = getNearestBlockCollision(start, end, props);
            MovingObjectPosition blockMop = blockCollision.hit;
            double blockDistSq = blockCollision.distanceSq;
            Vec3 clampedEnd = blockMop != null
                    ? new Vec3(blockMop.hitVec.xCoord, blockMop.hitVec.yCoord, blockMop.hitVec.zCoord)
                    : end;

            AxisAlignedBB broadBox = new AxisAlignedBB(posX - hw, posY - hw, posZ - hw, posX + hw, posY + hw, posZ + hw).addCoord(motX, motY, motZ).expand(1.0, 1.0, 1.0);
            List<Entity> candidates = mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.getRenderViewEntity(), broadBox);
            Entity bestEntity = null;
            Vec3 bestHitVec = null;
            AxisAlignedBB bestBox = null;
            double bestDistSq = blockDistSq;
            for (Entity en : candidates) {
                if (!(en instanceof EntityLivingBase)) continue;
                if (en instanceof EntityArmorStand) continue;
                if (!en.canBeCollidedWith()) continue;
                if (((EntityLivingBase) en).deathTime != 0) continue;
                if (en instanceof EntityPlayer && AntiBot.isBot(en)) continue;

                AxisAlignedBB entityBox = en.getEntityBoundingBox();
                AxisAlignedBB expandedEntityBox = expandEntityCollisionBox(entityBox);
                MovingObjectPosition mop = expandedEntityBox.calculateIntercept(start, clampedEnd);
                if (mop == null) continue;

                double dSq = start.squareDistanceTo(mop.hitVec);
                if (dSq + COLLISION_EPSILON_SQ < bestDistSq) {
                    bestDistSq = dSq;
                    bestEntity = en;
                    bestHitVec = mop.hitVec;
                    bestBox = expandedEntityBox;
                }
            }
            if (bestEntity != null) {
                double hitT = Math.sqrt(bestDistSq) / Math.sqrt(motX * motX + motY * motY + motZ * motZ);
                hitT = Math.max(0, Math.min(1, hitT));
                int subCount = (int) Math.ceil(hitT * SUB);
                for (int s = 0; s < subCount; s++) {
                    double t = (double) s / SUB;
                    renderPoints.add(new double[]{posX + motX * t - rm.viewerPosX, posY + motY * t - rm.viewerPosY, posZ + motZ * t - rm.viewerPosZ});
                }
                renderPoints.add(new double[]{bestHitVec.xCoord - rm.viewerPosX, bestHitVec.yCoord - rm.viewerPosY, bestHitVec.zCoord - rm.viewerPosZ});
                hitEntity = bestEntity;
                hitEntityBox = bestBox;
                hitType = HIT_ENTITY;
                hitPos = bestHitVec;
                break outer;
            }
            if (blockMop != null) {
                Vec3 hitVec = blockMop.hitVec;
                int side = blockMop.sideHit.getIndex();
                hitType = (side == 0 || side == 1) ? HIT_GROUND : HIT_WALL;
                hitPos = hitVec;
                double segLenSq = motX * motX + motY * motY + motZ * motZ;
                double hitDx = hitVec.xCoord - posX;
                double hitDy = hitVec.yCoord - posY;
                double hitDz = hitVec.zCoord - posZ;
                double hitT = segLenSq > 0 ? Math.sqrt((hitDx * hitDx + hitDy * hitDy + hitDz * hitDz) / segLenSq) : 0;
                hitT = Math.max(0, Math.min(1, hitT));
                int subCount = (int) Math.ceil(hitT * SUB);
                for (int s = 0; s < subCount; s++) {
                    double t = (double) s / SUB;
                    renderPoints.add(new double[]{posX + motX * t - rm.viewerPosX, posY + motY * t - rm.viewerPosY, posZ + motZ * t - rm.viewerPosZ});
                }
                renderPoints.add(new double[]{hitVec.xCoord - rm.viewerPosX, hitVec.yCoord - rm.viewerPosY, hitVec.zCoord - rm.viewerPosZ});
                hitBlock = blockMop;
                break outer;
            }
            for (int s = 0; s < SUB; s++) {
                double t = (double) s / SUB;
                renderPoints.add(new double[]{posX + motX * t - rm.viewerPosX, posY + motY * t - rm.viewerPosY, posZ + motZ * t - rm.viewerPosZ});
            }
            posX = nextX;
            posY = nextY;
            posZ = nextZ;
            terminalPos = new Vec3(posX, posY, posZ);

            mot[0] = motX;
            mot[1] = motY;
            mot[2] = motZ;
            tickPostMoveVelocity(posX, posY, posZ, props, fluidState, mot);
            motX = mot[0];
            motY = mot[1];
            motZ = mot[2];

            if (posY < -64) break;
        }

        if (hitPos == null && props.physicsModel == PhysicsModel.FISH_HOOK && terminalPos != null) {
            hitPos = terminalPos;
        }

        int color;
        switch (hitType) {
            case HIT_ENTITY: color = enemyColor.getColor(); break;
            case HIT_WALL: color = wallColor.getColor(); break;
            case HIT_GROUND: color = groundColor.getColor(); break;
            default: color = defaultColor.getColor(); break;
        }
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        float lineW = (float) lineThickness.getInput();
        GL11.glColor4f(r, g, b, 1.0f);
        GL11.glLineWidth(lineW);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        boolean first = true;
        for (double[] pt : clipRenderPoints(renderPoints, hiddenRenderDistance)) {
            if (first && shortenLine.isToggled()) {
                first = false;
                continue;
            }
            first = false;
            GL11.glVertex3d(pt[0], pt[1], pt[2]);
        }
        GL11.glEnd();
        if (hitEntity != null && highlightEntities.isToggled() && hitEntityBox != null) {
            double ex = hitEntity.lastTickPosX + (hitEntity.posX - hitEntity.lastTickPosX) * partialTicks;
            double ey = hitEntity.lastTickPosY + (hitEntity.posY - hitEntity.lastTickPosY) * partialTicks;
            double ez = hitEntity.lastTickPosZ + (hitEntity.posZ - hitEntity.lastTickPosZ) * partialTicks;
            AxisAlignedBB renderBox = new AxisAlignedBB(hitEntityBox.minX - hitEntity.posX + ex, hitEntityBox.minY - hitEntity.posY + ey, hitEntityBox.minZ - hitEntity.posZ + ez, hitEntityBox.maxX - hitEntity.posX + ex, hitEntityBox.maxY - hitEntity.posY + ey, hitEntityBox.maxZ - hitEntity.posZ + ez);
            GL11.glColor4f(r, g, b, 1.0f);
            RenderUtils.drawOutlinedBox(renderBox, rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ);
        } else if (hitBlock != null && !showLanding.isToggled()) {
            BlockPos bpos = hitBlock.getBlockPos();
            AxisAlignedBB selBox = BlockUtils.getBlockSelectionBox(bpos);
            if (selBox != null) {
                GL11.glColor4f(r, g, b, 1.0f);
                RenderUtils.drawOutlinedBox(selBox, rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ);
            }
        }
        if (showLanding.isToggled() && hitPos != null) {
            renderLandingIndicator(hitPos, rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ, r, g, b, hitType);
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
    }

    private void renderLandingIndicator(Vec3 hitPos, double camX, double camY, double camZ, float r, float g, float b, int hitType) {
        double boxSize = hitType == HIT_ENTITY ? 0.4 : 0.2;
        AxisAlignedBB worldBox = new AxisAlignedBB(hitPos.xCoord - boxSize, hitPos.yCoord - boxSize, hitPos.zCoord - boxSize, hitPos.xCoord + boxSize, hitPos.yCoord + boxSize, hitPos.zCoord + boxSize);
        GL11.glLineWidth(2.0f);
        GL11.glColor4f(r, g, b, 1.0f);
        RenderUtils.drawOutlinedBox(worldBox, camX, camY, camZ);
        AxisAlignedBB renderBox = worldBox.offset(-camX, -camY, -camZ);
        GL11.glColor4f(r, g, b, 0.3f);
        RenderUtils.drawBoundingBox(renderBox, r, g, b, 0.3f);
        double x = hitPos.xCoord - camX;
        double y = hitPos.yCoord - camY;
        double z = hitPos.zCoord - camZ;
        double crossSize = hitType == HIT_ENTITY ? 0.5 : 0.3;
        GL11.glLineWidth(1.5f);
        GL11.glColor4f(r, g, b, 1.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x - crossSize, y, z);
        GL11.glVertex3d(x + crossSize, y, z);
        GL11.glVertex3d(x, y - crossSize, z);
        GL11.glVertex3d(x, y + crossSize, z);
        GL11.glVertex3d(x, y, z - crossSize);
        GL11.glVertex3d(x, y, z + crossSize);
        GL11.glEnd();
    }
}
