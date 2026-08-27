package keystrokesmod.utility;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class FireballSimulator {
    private static final int DEFAULT_MAX_TICKS = 300;
    private static final int IGNORE_SHOOTER_TICKS = 25;
    private static final double ENTITY_HIT_EXPANSION = 0.3D;
    private static final double SEARCH_EXPANSION = 1.0D;
    private static final double COLLISION_EPSILON_SQ = 1.0E-7D;
    private static final double WATER_FLOW_ACCELERATION = 0.014D;
    private static final double WATER_CHECK_EXPAND_Y = -0.4000000059604645D;
    private static final double WATER_CHECK_CONTRACT = 0.001D;
    private static final float AIR_MOTION_FACTOR = 0.95F;
    private static final float WATER_MOTION_FACTOR = 0.8F;

    private static final class BlockCollisionResult {
        private final MovingObjectPosition collision;
        private final Vec3 impactPosition;
        private final double distanceSq;

        private BlockCollisionResult(MovingObjectPosition collision, Vec3 impactPosition, double distanceSq) {
            this.collision = collision;
            this.impactPosition = impactPosition;
            this.distanceSq = distanceSq;
        }
    }

    public static Result simulate(EntityLargeFireball fireball) {
        return simulate(fireball, DEFAULT_MAX_TICKS);
    }

    public static Result simulate(EntityLargeFireball fireball, int maxTicks) {
        if (fireball == null) {
            throw new IllegalArgumentException("fireball cannot be null");
        }

        World world = fireball.worldObj;
        if (world == null) {
            Vec3 pos = new Vec3(fireball.posX, fireball.posY, fireball.posZ);
            return new Result(HitType.NONE, null, null, null, pos, 0);
        }

        double posX = fireball.posX;
        double posY = fireball.posY;
        double posZ = fireball.posZ;
        double motionX = fireball.motionX;
        double motionY = fireball.motionY;
        double motionZ = fireball.motionZ;
        double accelerationX = fireball.accelerationX;
        double accelerationY = fireball.accelerationY;
        double accelerationZ = fireball.accelerationZ;
        float width = fireball.width;
        float height = fireball.height;
        double halfWidth = width * 0.5D;
        EntityLivingBase shooter = fireball.shootingEntity;
        int ticksInAir = Math.max(0, fireball.ticksExisted);
        int simulatedTicks = 0;
        Vec3 finalPosition = new Vec3(posX, posY, posZ);

        for (int tick = 0; tick < Math.max(1, maxTicks); tick++) {
            simulatedTicks = tick + 1;
            double[] motion = motionRef(motionX, motionY, motionZ);
            WaterState waterState = sampleWaterState(world, posX, posY, posZ, width, height, fireball);

            if (waterState.flowDirection.lengthVector() > 0.0D) {
                motion[0] += waterState.flowDirection.xCoord * WATER_FLOW_ACCELERATION;
                motion[1] += waterState.flowDirection.yCoord * WATER_FLOW_ACCELERATION;
                motion[2] += waterState.flowDirection.zCoord * WATER_FLOW_ACCELERATION;
            }

            motionX = motion[0];
            motionY = motion[1];
            motionZ = motion[2];

            Vec3 start = new Vec3(posX, posY, posZ);
            Vec3 end = new Vec3(posX + motionX, posY + motionY, posZ + motionZ);
            AxisAlignedBB sweepBounds = getSweepBounds(posX, posY, posZ, motionX, motionY, motionZ, width, height);
            BlockCollisionResult blockCollision = getBlockCollision(world, start, end, sweepBounds, halfWidth, height);
            MovingObjectPosition blockHit = blockCollision.collision;
            Vec3 searchEnd = blockCollision.impactPosition != null ? blockCollision.impactPosition : end;
            double bestDistanceSq = blockCollision.distanceSq;
            MovingObjectPosition bestEntityHit = null;
            Vec3 bestEntityImpact = null;

            AxisAlignedBB searchBox = sweepBounds
                    .expand(SEARCH_EXPANSION, SEARCH_EXPANSION, SEARCH_EXPANSION);
            List<Entity> candidates = world.getEntitiesWithinAABBExcludingEntity(fireball, searchBox);

            for (Entity candidate : candidates) {
                if (!candidate.canBeCollidedWith()) {
                    continue;
                }

                if (candidate.isEntityEqual(shooter) && ticksInAir < IGNORE_SHOOTER_TICKS) {
                    continue;
                }

                AxisAlignedBB expandedBox = expandTargetForProjectile(
                        candidate.getEntityBoundingBox(),
                        halfWidth,
                        height,
                        ENTITY_HIT_EXPANSION
                );
                MovingObjectPosition entityHit = expandedBox.calculateIntercept(start, searchEnd);

                if (entityHit == null && expandedBox.isVecInside(start)) {
                    entityHit = new MovingObjectPosition(candidate, start);
                }

                if (entityHit == null) {
                    continue;
                }

                double distanceSq = start.squareDistanceTo(entityHit.hitVec);
                if (distanceSq + COLLISION_EPSILON_SQ < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    bestEntityHit = new MovingObjectPosition(candidate, entityHit.hitVec);
                    bestEntityImpact = entityHit.hitVec;
                }
            }

            if (bestEntityHit != null) {
                return new Result(HitType.ENTITY, bestEntityHit, bestEntityHit.hitVec, bestEntityImpact, bestEntityImpact, simulatedTicks);
            }

            if (blockHit != null) {
                return new Result(HitType.BLOCK, blockHit, blockHit.hitVec, blockCollision.impactPosition, blockCollision.impactPosition, simulatedTicks);
            }

            posX += motionX;
            posY += motionY;
            posZ += motionZ;
            finalPosition = new Vec3(posX, posY, posZ);

            motionX += accelerationX;
            motionY += accelerationY;
            motionZ += accelerationZ;

            float motionFactor = waterState.inWater ? WATER_MOTION_FACTOR : AIR_MOTION_FACTOR;
            motionX *= motionFactor;
            motionY *= motionFactor;
            motionZ *= motionFactor;
            ticksInAir++;

            if (posY < -64.0D) {
                break;
            }
        }

        return new Result(HitType.NONE, null, null, finalPosition, finalPosition, simulatedTicks);
    }

    private static AxisAlignedBB getBoundingBox(double posX, double posY, double posZ, float width, float height) {
        double halfWidth = width * 0.5D;
        return new AxisAlignedBB(
                posX - halfWidth,
                posY,
                posZ - halfWidth,
                posX + halfWidth,
                posY + height,
                posZ + halfWidth
        );
    }

    private static AxisAlignedBB getSweepBounds(double posX, double posY, double posZ,
                                                double motionX, double motionY, double motionZ,
                                                float width, float height) {
        AxisAlignedBB startBox = getBoundingBox(posX, posY, posZ, width, height);
        AxisAlignedBB endBox = startBox.offset(motionX, motionY, motionZ);
        return new AxisAlignedBB(
                Math.min(startBox.minX, endBox.minX),
                Math.min(startBox.minY, endBox.minY),
                Math.min(startBox.minZ, endBox.minZ),
                Math.max(startBox.maxX, endBox.maxX),
                Math.max(startBox.maxY, endBox.maxY),
                Math.max(startBox.maxZ, endBox.maxZ)
        );
    }

    private static AxisAlignedBB expandTargetForProjectile(AxisAlignedBB targetBox,
                                                           double halfWidth,
                                                           double projectileHeight,
                                                           double extraExpansion) {
        return new AxisAlignedBB(
                targetBox.minX - halfWidth - extraExpansion,
                targetBox.minY - projectileHeight - extraExpansion,
                targetBox.minZ - halfWidth - extraExpansion,
                targetBox.maxX + halfWidth + extraExpansion,
                targetBox.maxY + extraExpansion,
                targetBox.maxZ + halfWidth + extraExpansion
        );
    }

    private static BlockCollisionResult getBlockCollision(World world,
                                                          Vec3 start,
                                                          Vec3 end,
                                                          AxisAlignedBB sweepBounds,
                                                          double halfWidth,
                                                          double projectileHeight) {
        int minX = MathHelper.floor_double(sweepBounds.minX);
        int maxX = MathHelper.floor_double(sweepBounds.maxX + 1.0D);
        int minY = MathHelper.floor_double(sweepBounds.minY);
        int maxY = MathHelper.floor_double(sweepBounds.maxY + 1.0D);
        int minZ = MathHelper.floor_double(sweepBounds.minZ);
        int maxZ = MathHelper.floor_double(sweepBounds.maxZ + 1.0D);

        if (!world.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new BlockCollisionResult(null, null, Double.MAX_VALUE);
        }

        List<AxisAlignedBB> collisionBoxes = new ArrayList<>();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        MovingObjectPosition bestCollision = null;
        Vec3 bestImpactPosition = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (int x = minX; x < maxX; ++x) {
            for (int y = minY; y < maxY; ++y) {
                for (int z = minZ; z < maxZ; ++z) {
                    mutablePos.set(x, y, z);
                    IBlockState blockState = world.getBlockState(mutablePos);
                    Block block = blockState.getBlock();

                    if (!block.canCollideCheck(blockState, false)) {
                        continue;
                    }

                    collisionBoxes.clear();
                    block.addCollisionBoxesToList(world, mutablePos, blockState, sweepBounds, collisionBoxes, null);

                    for (AxisAlignedBB collisionBox : collisionBoxes) {
                        AxisAlignedBB expandedBox = expandTargetForProjectile(collisionBox, halfWidth, projectileHeight, 0.0D);
                        MovingObjectPosition collision = expandedBox.calculateIntercept(start, end);

                        if (collision == null && expandedBox.isVecInside(start)) {
                            collision = new MovingObjectPosition(start, EnumFacing.UP, new BlockPos(mutablePos));
                        }

                        if (collision == null) {
                            continue;
                        }

                        double distanceSq = start.squareDistanceTo(collision.hitVec);
                        if (distanceSq + COLLISION_EPSILON_SQ < bestDistanceSq) {
                            bestDistanceSq = distanceSq;
                            bestImpactPosition = collision.hitVec;
                            bestCollision = new MovingObjectPosition(collision.hitVec, collision.sideHit, new BlockPos(mutablePos));
                        }
                    }
                }
            }
        }

        return new BlockCollisionResult(bestCollision, bestImpactPosition, bestDistanceSq);
    }

    private static WaterState sampleWaterState(World world, double posX, double posY, double posZ,
                                               float width, float height, Entity fireball) {
        AxisAlignedBB waterCheckBox = getBoundingBox(posX, posY, posZ, width, height)
                .expand(0.0D, WATER_CHECK_EXPAND_Y, 0.0D)
                .contract(WATER_CHECK_CONTRACT, WATER_CHECK_CONTRACT, WATER_CHECK_CONTRACT);

        int minX = MathHelper.floor_double(waterCheckBox.minX);
        int maxX = MathHelper.floor_double(waterCheckBox.maxX + 1.0D);
        int minY = MathHelper.floor_double(waterCheckBox.minY);
        int maxY = MathHelper.floor_double(waterCheckBox.maxY + 1.0D);
        int minZ = MathHelper.floor_double(waterCheckBox.minZ);
        int maxZ = MathHelper.floor_double(waterCheckBox.maxZ + 1.0D);

        if (!world.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new WaterState(false, new Vec3(0.0D, 0.0D, 0.0D));
        }

        boolean inWater = false;
        Vec3 flow = new Vec3(0.0D, 0.0D, 0.0D);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x < maxX; ++x) {
            for (int y = minY; y < maxY; ++y) {
                for (int z = minZ; z < maxZ; ++z) {
                    mutablePos.set(x, y, z);
                    IBlockState blockState = world.getBlockState(mutablePos);

                    if (blockState.getBlock().getMaterial() != Material.water) {
                        continue;
                    }

                    double liquidSurfaceY = (double) ((float) (y + 1)
                            - BlockLiquid.getLiquidHeightPercent((Integer) blockState.getValue(BlockLiquid.LEVEL)));

                    if ((double) maxY < liquidSurfaceY) {
                        continue;
                    }

                    inWater = true;
                    flow = blockState.getBlock().modifyAcceleration(world, mutablePos, fireball, flow);
                }
            }
        }

        if (flow.lengthVector() > 0.0D) {
            flow = flow.normalize();
        }

        return new WaterState(inWater, flow);
    }

    private static double[] motionRef(double motionX, double motionY, double motionZ) {
        return new double[] { motionX, motionY, motionZ };
    }

    private static final class WaterState {
        private final boolean inWater;
        private final Vec3 flowDirection;

        private WaterState(boolean inWater, Vec3 flowDirection) {
            this.inWater = inWater;
            this.flowDirection = flowDirection;
        }
    }

    public enum HitType {
        NONE,
        BLOCK,
        ENTITY
    }

    public static final class Result {
        private final HitType hitType;
        private final MovingObjectPosition collision;
        private final Vec3 hitPosition;
        private final Vec3 impactPosition;
        private final Vec3 finalPosition;
        private final int simulatedTicks;

        private Result(HitType hitType, MovingObjectPosition collision, Vec3 hitPosition, Vec3 impactPosition,
                       Vec3 finalPosition, int simulatedTicks) {
            this.hitType = hitType;
            this.collision = collision;
            this.hitPosition = hitPosition;
            this.impactPosition = impactPosition;
            this.finalPosition = finalPosition;
            this.simulatedTicks = simulatedTicks;
        }

        public HitType getHitType() {
            return hitType;
        }

        public boolean collided() {
            return hitType != HitType.NONE;
        }

        public boolean hitBlock() {
            return hitType == HitType.BLOCK;
        }

        public boolean hitEntity() {
            return hitType == HitType.ENTITY;
        }

        public MovingObjectPosition getCollision() {
            return collision;
        }

        public Entity getTarget() {
            return collision != null ? collision.entityHit : null;
        }

        public Entity getHitEntity() {
            return getTarget();
        }

        public BlockPos getHitBlockPos() {
            return collision != null ? collision.getBlockPos() : null;
        }

        public Vec3 getHitPosition() {
            return hitPosition;
        }

        public Vec3 getImpactPosition() {
            return impactPosition;
        }

        public Vec3 getFinalPosition() {
            return finalPosition;
        }

        public int getSimulatedTicks() {
            return simulatedTicks;
        }
    }
}