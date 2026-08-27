package keystrokesmod.utility;

import com.google.common.base.Predicates;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.impl.client.Settings;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RotationUtils implements IMinecraftInstance {
    public static float renderPitch;
    public static float prevRenderPitch;
    public static float renderYaw;
    public static float prevRenderYaw;
    public static float[] serverRotations = new float[] { 0, 0 } ;

    public static Float[] fakeRotations;
    public static boolean setFakeRotations;

    public static void setFakeRotations(float yaw, float pitch) {
        fakeRotations = new Float[] { yaw, pitch };
        setFakeRotations = true;
    }

    public static void setRenderYaw(float yaw) {
        mc.thePlayer.rotationYawHead = yaw;
        if (Settings.rotateBody.isToggled() && Settings.fullBody.isToggled()) {
            mc.thePlayer.prevRenderYawOffset = prevRenderYaw;
            mc.thePlayer.renderYawOffset = yaw;
        }
    }

    public static float[] getRotations(BlockPos blockPos, final float n, final float n2) {
        final float[] array = getRotations(blockPos);
        return fixRotation(array[0], array[1], n, n2);
    }

    public static float[] getRotationsToBlock(BlockPos blockPos, EnumFacing facing, final float yaw, final float pitch) {
        final float[] array = getRotationsToBlock(blockPos, facing);
        return fixRotation(array[0], array[1], yaw, pitch);
    }

    public static float[] getRotations(BlockPos blockPos) {
        double x = blockPos.getX() + 0.45 - mc.thePlayer.posX;
        double y = blockPos.getY() + 0.45 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double z = blockPos.getZ() + 0.45 - mc.thePlayer.posZ;

        float angleToBlock = (float) (Math.atan2(z, x) * (180 / Math.PI)) - 90.0f;
        float deltaYaw = MathHelper.wrapAngleTo180_float(angleToBlock - mc.thePlayer.rotationYaw);
        float yaw = mc.thePlayer.rotationYaw + deltaYaw;

        double distance = MathHelper.sqrt_double(x * x + z * z);
        float angleToBlockPitch = (float) (-(Math.atan2(y, distance) * (180 / Math.PI)));
        float deltaPitch = MathHelper.wrapAngleTo180_float(angleToBlockPitch - mc.thePlayer.rotationPitch);
        float pitch = mc.thePlayer.rotationPitch + deltaPitch;

        pitch = clampPitch(pitch);

        return new float[] { yaw, pitch };
    }

    public static float[] getRotations(double posX, double posY, double posZ) {
        double x = posX + 1.0 - mc.thePlayer.posX;
        double y = posY + 1.0 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double z = posZ + 1.0 - mc.thePlayer.posZ;

        float angleToBlock = (float) (Math.atan2(z, x) * (180 / Math.PI)) - 90.0f;
        float deltaYaw = MathHelper.wrapAngleTo180_float(angleToBlock - mc.thePlayer.rotationYaw);
        float yaw = mc.thePlayer.rotationYaw + deltaYaw;

        double distance = MathHelper.sqrt_double(x * x + z * z);
        float angleToBlockPitch = (float) (-(Math.atan2(y, distance) * (180 / Math.PI)));
        float deltaPitch = MathHelper.wrapAngleTo180_float(angleToBlockPitch - mc.thePlayer.rotationPitch);
        float pitch = mc.thePlayer.rotationPitch + deltaPitch;

        pitch = clampPitch(pitch);

        return new float[] { yaw, pitch };
    }

    public static float[] getRotations(Vec3 vec3) {
        double x = vec3.xCoord + 1.0D - mc.thePlayer.posX;
        double y = vec3.yCoord + 1.0D - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double z = vec3.zCoord + 1.0D - mc.thePlayer.posZ;

        float angleToBlock = (float) (Math.atan2(z, x) * (180 / Math.PI)) - 90.0f;
        float deltaYaw = MathHelper.wrapAngleTo180_float(angleToBlock - mc.thePlayer.rotationYaw);
        float yaw = mc.thePlayer.rotationYaw + deltaYaw;

        double distance = MathHelper.sqrt_double(x * x + z * z);
        float angleToBlockPitch = (float) (-(Math.atan2(y, distance) * (180 / Math.PI)));
        float deltaPitch = MathHelper.wrapAngleTo180_float(angleToBlockPitch - mc.thePlayer.rotationPitch);
        float pitch = mc.thePlayer.rotationPitch + deltaPitch;

        pitch = clampPitch(pitch);

        return new float[] { yaw, pitch };
    }

    public static float[] getRotations(Entity entity, final float yaw, final float pitch) {
        final float[] array = getRotations(entity);
        if (array == null) {
            return null;
        }
        return fixRotation(array[0], array[1], yaw, pitch);
    }

    public static float[] getRotationsToBlock(final BlockPos pos, final EnumFacing facing) {
        double diffX = pos.getX() + 0.45 - mc.thePlayer.posX;
        double diffY = pos.getY() + 0.45 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double diffZ = pos.getZ() + 0.45 - mc.thePlayer.posZ;
        if (facing != null) {
            diffX += facing.getDirectionVec().getX() * 0.5;
            diffY += facing.getDirectionVec().getY() * 0.5;
            diffZ += facing.getDirectionVec().getZ() * 0.5;
        }
        final double dist = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ);
        final float yaw = (float)(Math.atan2(diffZ, diffX) * 57.295780181884766) - 90.0f;
        final float pitch = (float)(-(Math.atan2(diffY, dist) * 57.295780181884766));
        return new float[] { mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw), clampPitch(mc.thePlayer.rotationPitch + MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch)) };
    }

    public static double distanceFromYaw(final Entity entity, final boolean b) {
        return Math.abs(MathHelper.wrapAngleTo180_double(i(entity.posX, entity.posZ) - ((b && PreMotionEvent.setRenderYaw()) ? RotationUtils.renderYaw : mc.thePlayer.rotationYaw)));
    }

    public static float i(final double n, final double n2) {
        return (float)(Math.atan2(n - mc.thePlayer.posX, n2 - mc.thePlayer.posZ) * 57.295780181884766 * -1.0);
    }

    public static boolean isPossibleToHit(Entity target, double reach, float[] rotations) {
        final Vec3 eyePosition = mc.thePlayer.getPositionEyes(1.0f);

        final float yaw = rotations[0];
        final float pitch = rotations[1];

        final float radianYaw = -yaw * 0.017453292f - (float)Math.PI;
        final float radianPitch = -pitch * 0.017453292f;

        final float cosYaw = MathHelper.cos(radianYaw);
        final float sinYaw = MathHelper.sin(radianYaw);
        final float cosPitch = -MathHelper.cos(radianPitch);
        final float sinPitch = MathHelper.sin(radianPitch);

        final Vec3 lookVector = new Vec3(
                sinYaw * cosPitch, // x
                sinPitch,         // y
                cosYaw * cosPitch // z
        );

        final double lookVecX = lookVector.xCoord * reach;
        final double lookVecY = lookVector.yCoord * reach;
        final double lookVecZ = lookVector.zCoord * reach;

        final Vec3 endPosition = eyePosition.addVector(lookVecX, lookVecY, lookVecZ);

        final Entity renderViewEntity = mc.getRenderViewEntity();
        final AxisAlignedBB expandedBox = renderViewEntity
                .getEntityBoundingBox()
                .addCoord(lookVecX, lookVecY, lookVecZ)
                .expand(1.0, 1.0, 1.0);

        final List<Entity> entitiesInPath = mc.theWorld.getEntitiesWithinAABBExcludingEntity(renderViewEntity, expandedBox);
        for (Entity entity : entitiesInPath) {
            if (entity == target && entity.canBeCollidedWith()) {
                final float borderSize = entity.getCollisionBorderSize();
                final AxisAlignedBB entityBox = entity.getEntityBoundingBox()
                        .expand(borderSize, borderSize, borderSize);
                final MovingObjectPosition intercept = entityBox.calculateIntercept(eyePosition, endPosition);
                return intercept != null;
            }
        }

        return false;
    }

    public static boolean inRange(final BlockPos blockPos, final double n) {
        final float[] array = RotationUtils.getRotations(blockPos);
        final Vec3 getPositionEyes = mc.thePlayer.getPositionEyes(1.0f);
        final float n2 = -array[0] * 0.017453292f;
        final float n3 = -array[1] * 0.017453292f;
        final float cos = MathHelper.cos(n2 - 3.1415927f);
        final float sin = MathHelper.sin(n2 - 3.1415927f);
        final float n4 = -MathHelper.cos(n3);
        final Vec3 vec3 = new Vec3(sin * n4, MathHelper.sin(n3), cos * n4);
        Block block = BlockUtils.getBlock(blockPos);
        IBlockState blockState = BlockUtils.getBlockState(blockPos);
        if (block != null && blockState != null) {
            AxisAlignedBB boundingBox = block.getCollisionBoundingBox(mc.theWorld, blockPos, blockState);
            if (boundingBox != null) {
                Vec3 targetVec = getPositionEyes.addVector(vec3.xCoord * n, vec3.yCoord * n, vec3.zCoord * n);
                MovingObjectPosition intercept = boundingBox.calculateIntercept(getPositionEyes, targetVec);
                if (intercept != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public static float[] getRotations(final Entity entity) {
        return getRotations(entity, PLAYER_OFFSETS.NONE);
    }

    public static float[] getRotations(final Entity entity, PLAYER_OFFSETS playerOffset) {
        if (entity == null) {
            return null;
        }
        double deltaX = entity.posX - mc.thePlayer.posX;
        double deltaZ = entity.posZ - mc.thePlayer.posZ;
        double deltaY;
        if (entity instanceof EntityLivingBase) {
            final EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
            deltaY = entityLivingBase.posY + playerOffset.getHeightOffset(entityLivingBase) * 0.9 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        }
        else {
            deltaY = (entity.getEntityBoundingBox().minY + entity.getEntityBoundingBox().maxY) / 2.0 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        }
        return new float[] { mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float((float) (Math.atan2(deltaZ, deltaX) * 57.295780181884766) - 90.0f - mc.thePlayer.rotationYaw), clampPitch(mc.thePlayer.rotationPitch + MathHelper.wrapAngleTo180_float((float) (-(Math.atan2(deltaY, MathHelper.sqrt_double(deltaX * deltaX + deltaZ * deltaZ)) * 57.295780181884766)) - mc.thePlayer.rotationPitch) + 3.0f)};
    }

    public static float[] getRotationsToPoint(double x, double y, double z) {
        return getRotationsToPoint(x, y, z, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    }

    /**
     * Base-aware overload for silent rotation paths. When the target is directly above/below
     * (horizDist near zero), preserves baseYaw to avoid atan2(0,0) -> -90 degenerate yaw.
     */
    public static float[] getRotationsToPoint(double x, double y, double z, float baseYaw, float basePitch) {
        double deltaX = x - mc.thePlayer.posX;
        double deltaZ = z - mc.thePlayer.posZ;
        double deltaY = y - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double horizDistSq = deltaX * deltaX + deltaZ * deltaZ;

        float yaw;
        float targetPitch;
        if (horizDistSq < 1.0E-12) {
            yaw = baseYaw;
            targetPitch = (float) (-(Math.atan2(deltaY, 0) * 57.295780181884766));
        } else {
            float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * 57.295780181884766) - 90.0f;
            yaw = baseYaw + MathHelper.wrapAngleTo180_float(targetYaw - baseYaw);
            double horizDist = MathHelper.sqrt_double(horizDistSq);
            targetPitch = (float) (-(Math.atan2(deltaY, horizDist) * 57.295780181884766));
        }

        float pitch = basePitch + MathHelper.wrapAngleTo180_float(targetPitch - basePitch) + 3.0f;
        return new float[] { yaw, clampPitch(pitch) };
    }

    public static float[] getRotations(final Entity entity, double horizontalMultipoint, double verticalMultipoint, final float baseYaw, final float basePitch) {
        Vec3 aimPoint = getAimPoint(entity, horizontalMultipoint, verticalMultipoint);
        if (aimPoint == null) {
            return null;
        }
        return getRotationsToPoint(aimPoint.xCoord, aimPoint.yCoord, aimPoint.zCoord, baseYaw, basePitch);
    }

    /**
     * Returns the aim point Vec3 for the given entity and multipoint settings.
     * Extracted from getRotations logic for backup-point fallback.
     */
    public static Vec3 getAimPoint(Entity entity, double horizontalMultipoint, double verticalMultipoint) {
        if (entity == null || mc.thePlayer == null) return null;
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB bb = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        double centerX = (bb.minX + bb.maxX) / 2.0;
        double centerY;
        if (entity instanceof EntityLivingBase) {
            centerY = entity.posY + ((EntityLivingBase) entity).getEyeHeight();
        } else {
            centerY = (bb.minY + bb.maxY) / 2.0;
        }
        double centerZ = (bb.minZ + bb.maxZ) / 2.0;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        if (bb.isVecInside(eye)) {
            return new Vec3(centerX, eye.yCoord, centerZ);
        }
        Vec3 cl = closestPointOnAabb(bb, eye);
        double tH = Math.max(0.0, Math.min(1.0, horizontalMultipoint / 100.0));
        double tV = Math.max(0.0, Math.min(1.0, verticalMultipoint / 100.0));
        double targetX = centerX + (cl.xCoord - centerX) * tH;
        double targetY = centerY + (cl.yCoord - centerY) * tV;
        double targetZ = centerZ + (cl.zCoord - centerZ) * tH;
        return new Vec3(targetX, targetY, targetZ);
    }

    public static Vec3 closestPointOnAabb(AxisAlignedBB box, Vec3 point) {
        double x = Math.max(box.minX, Math.min(box.maxX, point.xCoord));
        double y = Math.max(box.minY, Math.min(box.maxY, point.yCoord));
        double z = Math.max(box.minZ, Math.min(box.maxZ, point.zCoord));
        return new Vec3(x, y, z);
    }

    private static final double BACKUP_FACE_INSET = 0.05;
    private static final int BACKUP_TARGET_TOTAL = 30;

    public static List<Vec3> buildBackupPoints(Entity entity, Vec3 eye) {
        if (entity == null || mc.thePlayer == null) return new ArrayList<>();
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB bb = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);

        double sizeX = bb.maxX - bb.minX;
        double sizeY = bb.maxY - bb.minY;
        double sizeZ = bb.maxZ - bb.minZ;

        boolean xPos = eye.xCoord > bb.maxX;
        boolean xNeg = eye.xCoord < bb.minX;
        boolean yPos = eye.yCoord > bb.maxY;
        boolean yNeg = eye.yCoord < bb.minY;
        boolean zPos = eye.zCoord > bb.maxZ;
        boolean zNeg = eye.zCoord < bb.minZ;

        int visibleFaceCount = (xPos || xNeg ? 1 : 0) + (yPos || yNeg ? 1 : 0) + (zPos || zNeg ? 1 : 0);
        if (visibleFaceCount == 0) return new ArrayList<>();

        int pointsPerFace = BACKUP_TARGET_TOTAL / visibleFaceCount;
        List<Vec3> points = new ArrayList<>(BACKUP_TARGET_TOTAL + 6);

        if (xPos || xNeg) {
            double fixedX = xPos ? bb.maxX - BACKUP_FACE_INSET : bb.minX + BACKUP_FACE_INSET;
            addFaceGrid(points, 0, fixedX,
                    bb.minY + BACKUP_FACE_INSET, bb.maxY - BACKUP_FACE_INSET,
                    bb.minZ + BACKUP_FACE_INSET, bb.maxZ - BACKUP_FACE_INSET,
                    pointsPerFace, sizeY, sizeZ);
        }

        if (yPos || yNeg) {
            double fixedY = yPos ? bb.maxY - BACKUP_FACE_INSET : bb.minY + BACKUP_FACE_INSET;
            addFaceGrid(points, 1, fixedY,
                    bb.minX + BACKUP_FACE_INSET, bb.maxX - BACKUP_FACE_INSET,
                    bb.minZ + BACKUP_FACE_INSET, bb.maxZ - BACKUP_FACE_INSET,
                    pointsPerFace, sizeX, sizeZ);
        }

        if (zPos || zNeg) {
            double fixedZ = zPos ? bb.maxZ - BACKUP_FACE_INSET : bb.minZ + BACKUP_FACE_INSET;
            addFaceGrid(points, 2, fixedZ,
                    bb.minX + BACKUP_FACE_INSET, bb.maxX - BACKUP_FACE_INSET,
                    bb.minY + BACKUP_FACE_INSET, bb.maxY - BACKUP_FACE_INSET,
                    pointsPerFace, sizeX, sizeY);
        }

        return points;
    }

    private static void addFaceGrid(List<Vec3> out, int fixedAxis, double fixedVal,
                                     double uMin, double uMax, double vMin, double vMax,
                                     int targetPoints, double dimU, double dimV) {
        if (dimU < 1e-4 || dimV < 1e-4) {
            double uMid = (uMin + uMax) / 2.0;
            double vMid = (vMin + vMax) / 2.0;
            switch (fixedAxis) {
                case 0: out.add(new Vec3(fixedVal, uMid, vMid)); break;
                case 1: out.add(new Vec3(uMid, fixedVal, vMid)); break;
                case 2: out.add(new Vec3(uMid, vMid, fixedVal)); break;
            }
            return;
        }

        double ratio = dimU / dimV;
        int gridU = Math.max(2, (int) Math.round(Math.sqrt(targetPoints * ratio)));
        int gridV = Math.max(2, (int) Math.round(Math.sqrt(targetPoints / ratio)));

        for (int i = 0; i < gridU; i++) {
            double u = uMin + (uMax - uMin) * i / (gridU - 1);
            for (int j = 0; j < gridV; j++) {
                double v = vMin + (vMax - vMin) * j / (gridV - 1);
                switch (fixedAxis) {
                    case 0: out.add(new Vec3(fixedVal, u, v)); break;
                    case 1: out.add(new Vec3(u, fixedVal, v)); break;
                    case 2: out.add(new Vec3(u, v, fixedVal)); break;
                }
            }
        }
    }

    /**
     * Returns squared distance from player eye to the closest point on the entity's expanded AABB.
     */
    public static double distanceSqFromEyeToClosestOnAABB(Entity entity) {
        if (entity == null || mc.thePlayer == null) return Double.MAX_VALUE;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB bb = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        Vec3 closest = closestPointOnAabb(bb, eye);
        double dx = eye.xCoord - closest.xCoord;
        double dy = eye.yCoord - closest.yCoord;
        double dz = eye.zCoord - closest.zCoord;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Returns distance from player eye to the closest point on the entity's expanded AABB.
     */
    public static double distanceFromEyeToClosestOnAABB(Entity entity) {
        double dSq = distanceSqFromEyeToClosestOnAABB(entity);
        return dSq == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(dSq);
    }

    public static boolean canAimAtPoint(Vec3 eye, Vec3 point, Entity target, double range) {
        return canAimAtPoint(eye, point, target, range, false, true);
    }

    public static boolean canAimAtPoint(Vec3 eye, Vec3 point, Entity target, double range, boolean allowThroughBlocks, boolean allowThroughEntities) {
        if (target == null) return false;
        double dx = point.xCoord - eye.xCoord;
        double dy = point.yCoord - eye.yCoord;
        double dz = point.zCoord - eye.zCoord;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return false;
        double scale = range / len;
        Vec3 end = new Vec3(eye.xCoord + dx * scale, eye.yCoord + dy * scale, eye.zCoord + dz * scale);

        float borderSize = target.getCollisionBorderSize();
        AxisAlignedBB aabb = target.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        MovingObjectPosition entityHit = aabb.calculateIntercept(eye, end);
        if (entityHit == null) return false;

        double entityDistSq = eye.squareDistanceTo(entityHit.hitVec);
        if (!allowThroughBlocks) {
            MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eye, end, false, false, false);
            if (blockHit != null && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                double blockDistSq = eye.squareDistanceTo(blockHit.hitVec);
                if (blockDistSq < entityDistSq) return false;
            }
        }
        if (!allowThroughEntities && hasEntityBlockingPath(eye, end, target, entityDistSq)) {
            return false;
        }
        return true;
    }

    private static boolean hasEntityBlockingPath(Vec3 eye, Vec3 end, Entity target, double targetDistSq) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        Vec3 delta = end.subtract(eye);
        AxisAlignedBB searchBox = mc.thePlayer.getEntityBoundingBox()
                .addCoord(delta.xCoord, delta.yCoord, delta.zCoord)
                .expand(1.0, 1.0, 1.0);
        List<Entity> entities = mc.theWorld.getEntitiesInAABBexcluding(mc.thePlayer, searchBox, Predicates.and(EntitySelectors.NOT_SPECTATING, Entity::canBeCollidedWith));
        for (Entity entity : entities) {
            if (entity == null || entity == target || entity.isDead) {
                continue;
            }
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB bb = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition hit = bb.calculateIntercept(eye, end);
            if (bb.isVecInside(eye)) {
                return true;
            }
            if (hit != null) {
                double entityDistSq = eye.squareDistanceTo(hit.hitVec);
                if (entityDistSq < targetDistSq - 1.0E-7) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isPathBlockedByEntity(Vec3 eye, Vec3 hitVec, Entity target) {
        if (eye == null || hitVec == null || target == null) return false;
        double targetDistSq = eye.squareDistanceTo(hitVec);
        return hasEntityBlockingPath(eye, hitVec, target, targetDistSq);
    }

    public static boolean isEyeInsideEntityAABB(Entity entity) {
        if (entity == null || mc.thePlayer == null) return false;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB bb = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        return bb.isVecInside(eye);
    }

    private static boolean mainRayHitsTargetAABB(Vec3 eye, Vec3 point, Entity target, double range) {
        double dx = point.xCoord - eye.xCoord;
        double dy = point.yCoord - eye.yCoord;
        double dz = point.zCoord - eye.zCoord;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return false;
        double scale = range / len;
        Vec3 end = new Vec3(eye.xCoord + dx * scale, eye.yCoord + dy * scale, eye.zCoord + dz * scale);
        float borderSize = target.getCollisionBorderSize();
        AxisAlignedBB aabb = target.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        return aabb.calculateIntercept(eye, end) != null;
    }

    public static boolean hasValidAimPoint(Entity entity, double hMult, double vMult, double range) {
        return hasValidAimPoint(entity, hMult, vMult, range, false, true);
    }

    public static boolean hasValidAimPoint(Entity entity, double hMult, double vMult, double range, boolean allowThroughBlocks, boolean allowThroughEntities) {
        if (entity == null || mc.thePlayer == null) return false;
        Vec3 mainPoint = getAimPoint(entity, hMult, vMult);
        if (mainPoint == null) return false;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        if (eye.squareDistanceTo(mainPoint) < 1e-6) return true;

        if (!mainRayHitsTargetAABB(eye, mainPoint, entity, range)) {
            return false;
        }

        if (canAimAtPoint(eye, mainPoint, entity, range, allowThroughBlocks, allowThroughEntities)) {
            return true;
        }

        List<Vec3> backups = buildBackupPoints(entity, eye);
        Collections.sort(backups, Comparator.comparingDouble(p -> {
            double dx = p.xCoord - eye.xCoord;
            double dy = p.yCoord - eye.yCoord;
            double dz = p.zCoord - eye.zCoord;
            return dx * dx + dy * dy + dz * dz;
        }));
        for (Vec3 p : backups) {
            if (canAimAtPoint(eye, p, entity, range, allowThroughBlocks, allowThroughEntities)) {
                return true;
            }
        }
        return false;
    }

    public static float[] getRotationsWithBackup(Entity entity, double horizontalMultipoint, double verticalMultipoint, float baseYaw, float basePitch, double range) {
        return getRotationsWithBackup(entity, horizontalMultipoint, verticalMultipoint, baseYaw, basePitch, range, false, true);
    }

    public static float[] getRotationsWithBackup(Entity entity, double horizontalMultipoint, double verticalMultipoint, float baseYaw, float basePitch, double range, boolean allowThroughBlocks, boolean allowThroughEntities) {
        if (entity == null || mc.thePlayer == null) return null;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB bb = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        if (bb.isVecInside(eye)) {
            double centerX = (bb.minX + bb.maxX) / 2.0;
            double centerZ = (bb.minZ + bb.maxZ) / 2.0;
            return getRotationsToPoint(centerX, eye.yCoord, centerZ, baseYaw, basePitch);
        }
        Vec3 mainPoint = getAimPoint(entity, horizontalMultipoint, verticalMultipoint);
        if (mainPoint == null) return null;
        if (eye.squareDistanceTo(mainPoint) < 1e-6) return null;

        if (!mainRayHitsTargetAABB(eye, mainPoint, entity, range)) {
            return getRotationsToPoint(mainPoint.xCoord, mainPoint.yCoord, mainPoint.zCoord, baseYaw, basePitch);
        }

        if (canAimAtPoint(eye, mainPoint, entity, range, allowThroughBlocks, allowThroughEntities)) {
            return getRotationsToPoint(mainPoint.xCoord, mainPoint.yCoord, mainPoint.zCoord, baseYaw, basePitch);
        }

        List<Vec3> backups = buildBackupPoints(entity, eye);
        Collections.sort(backups, Comparator.comparingDouble(p -> {
            double dx = p.xCoord - eye.xCoord;
            double dy = p.yCoord - eye.yCoord;
            double dz = p.zCoord - eye.zCoord;
            return dx * dx + dy * dy + dz * dz;
        }));

        for (Vec3 p : backups) {
            if (canAimAtPoint(eye, p, entity, range, allowThroughBlocks, allowThroughEntities)) {
                return getRotationsToPoint(p.xCoord, p.yCoord, p.zCoord, baseYaw, basePitch);
            }
        }
        return null;
    }

    public static float[] getRotationsPredicated(final Entity entity, final int ticks) {
        if (entity == null) {
            return null;
        }
        if (ticks == 0) {
            return getRotations(entity);
        }
        double posX = entity.posX;
        final double posY = entity.posY;
        double posZ = entity.posZ;
        final double n2 = posX - entity.lastTickPosX;
        final double n3 = posZ - entity.lastTickPosZ;
        for (int i = 0; i < ticks; ++i) {
            posX += n2;
            posZ += n3;
        }
        final double n4 = posX - mc.thePlayer.posX;
        double n5;
        if (entity instanceof EntityLivingBase) {
            n5 = posY + entity.getEyeHeight() * 0.9 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        }
        else {
            n5 = (entity.getEntityBoundingBox().minY + entity.getEntityBoundingBox().maxY) / 2.0 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        }
        final double n6 = posZ - mc.thePlayer.posZ;
        return new float[] { applyVanilla(mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float((float)(Math.atan2(n6, n4) * 57.295780181884766) - 90.0f - mc.thePlayer.rotationYaw)), clampPitch(mc.thePlayer.rotationPitch + MathHelper.wrapAngleTo180_float((float)(-(Math.atan2(n5, MathHelper.sqrt_double(n4 * n4 + n6 * n6)) * 57.295780181884766)) - mc.thePlayer.rotationPitch) + 3.0f) };
    }

    private static final float FAR_THRESHOLD = 180f;

    /**
     * Smoothly interpolates from base to target rotation using linear step model.
     * Steps along the combined (yaw, pitch) direction so both axes move together proportionally,
     * simulating human mouse movement (one fluid motion).
     * @param speed 0 = no movement, 30 = practically instant
     */
    public static float[] smoothRotation(float baseYaw, float basePitch,
                                          float targetYaw, float targetPitch,
                                          int speed) {
        return smoothRotation(baseYaw, basePitch, targetYaw, targetPitch, speed, 0f);
    }

    /**
     * Overload with configurable randomization (0-100%). Higher randomization varies step size
     * per tick to bypass anticheat pattern analysis (consistent deltas, constant acceleration).
     * @param speed 0 = no movement, 30 = practically instant
     */
    public static float[] smoothRotation(float baseYaw, float basePitch,
                                          float targetYaw, float targetPitch,
                                          int speed, float randomizationPercent) {
        if (speed <= 0) {
            return new float[] { baseYaw, clampPitch(basePitch) };
        }
        if (speed >= 30) {
            return new float[] { targetYaw, clampPitch(targetPitch) };
        }
        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - baseYaw);
        float deltaPitch = targetPitch - basePitch;
        float magnitude = (float) MathHelper.sqrt_double(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (magnitude < 0.001f) {
            return new float[] { targetYaw, clampPitch(targetPitch) };
        }
        float t = speed / 30f;
        float stepSize = t * t * 180f;
        float range = 0.6f * (float)(randomizationPercent / 100.0);
        float multiplier = (range <= 0.001f) ? 1.0f : (1.0f - range/2f + (float)(Math.random() * range));
        stepSize *= multiplier;
        float proximityFactor = Math.min(1f, magnitude / FAR_THRESHOLD);
        proximityFactor = (float) Math.pow(proximityFactor, 0.7);
        float maxSlowdown = (float)(randomizationPercent / 100.0);
        // Cap proximity slowdown at 20% (min 80% speed) so high randomization doesn't kill aim assist
        float proximityMult = Math.max(0.8f, 1.0f - maxSlowdown * (1.0f - proximityFactor));
        stepSize *= proximityMult;
        float stepLength = Math.min(stepSize, magnitude);
        float scale = stepLength / magnitude;
        float stepYaw = deltaYaw * scale;
        float stepPitch = deltaPitch * scale;
        float yaw = baseYaw + stepYaw;
        float pitch = basePitch + stepPitch;
        return new float[] { yaw, clampPitch(pitch) };
    }

    public static float clampPitch(final float n) {
        return MathHelper.clamp_float(n, -90.0f, 90.0f);
    }

    // TODO remove calls to this from the util as it's done globally in RotationHelper
    public static float[] fixRotation(float targetYaw, float targetPitch, final float yaw, final float pitch) {
        targetYaw = RotationHelper.unwrapYaw(targetYaw, yaw);
        float n5 = targetYaw - yaw;
        final float abs = Math.abs(n5);
        final float n7 = targetPitch - pitch;
        final float n8 = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        final double n9 = n8 * n8 * n8 * 1.2;
        final float n10 = (float) (Math.round((double) n5 / n9) * n9);
        final float n11 = (float) (Math.round((double) n7 / n9) * n9);
        targetYaw = yaw + n10;
        targetPitch = pitch + n11;
        return new float[] { targetYaw, clampPitch(targetPitch) };
    }

    public static float angle(final double n, final double n2) {
        return (float) (Math.atan2(n - mc.thePlayer.posX, n2 - mc.thePlayer.posZ) * 57.295780181884766 * -1.0);
    }

    public static float deltaAngle(final double n, final double n2) {
        return (float) (Math.atan2(n, n2) * 57.295780181884766 * -1.0);
    }

    public static MovingObjectPosition rayCast(double distance, float yaw, float pitch, boolean collisionCheck) {
        final Vec3 getPositionEyes = mc.thePlayer.getPositionEyes(1.0f);
        final float n4 = -yaw * 0.017453292f;
        final float n5 = -pitch * 0.017453292f;
        final float cos = MathHelper.cos(n4 - 3.1415927f);
        final float sin = MathHelper.sin(n4 - 3.1415927f);
        final float n6 = -MathHelper.cos(n5);
        final Vec3 vec3 = new Vec3(sin * n6, MathHelper.sin(n5), cos * n6);
        return mc.theWorld.rayTraceBlocks(getPositionEyes, getPositionEyes.addVector(vec3.xCoord * distance, vec3.yCoord * distance, vec3.zCoord * distance), true, collisionCheck, true);
    }

    public static MovingObjectPosition rayCastBlock(final double distance, final float yaw, final float pitch) {
        Vec3 eyeVec = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 lookVec = Utils.getLookVec(yaw, pitch);
        Vec3 sumVec = eyeVec.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyeVec, sumVec, false, false, false);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        return mop;
    }

    public static MovingObjectPosition rayTraceCustom(double blockReachDistance, float yaw, float pitch) {
        final Vec3 vec3 = mc.thePlayer.getPositionEyes(1.0F);
        final Vec3 vec31 = getVectorForRotation(pitch, yaw);
        final Vec3 vec32 = vec3.addVector(vec31.xCoord * blockReachDistance, vec31.yCoord * blockReachDistance, vec31.zCoord * blockReachDistance);
        return mc.theWorld.rayTraceBlocks(vec3, vec32, false, false, true);
    }

    /**
     * Raytraces for a block using the given yaw/pitch, but returns null if an entity is closer (so the block is "behind" an entity).
     */
    public static MovingObjectPosition rayTraceBlockIfNoEntityInFront(double reach, float yaw, float pitch) {
        if (mc.thePlayer == null || mc.theWorld == null) return null;
        MovingObjectPosition blockHit = rayTraceCustom(reach, yaw, pitch);
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double blockDist = reach;
        if (blockHit != null && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            blockDist = blockHit.hitVec.distanceTo(eyes);
        } else {
            return null;
        }
        Vec3 lookVec = getVectorForRotation(pitch, yaw);
        Vec3 end = eyes.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);
        List<Entity> entities = mc.theWorld.getEntitiesInAABBexcluding(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().addCoord(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach).expand(1.0F, 1.0F, 1.0F), Predicates.and(EntitySelectors.NOT_SPECTATING, Entity::canBeCollidedWith));
        for (Entity entity : entities) {
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB aabb = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition entityHit = aabb.calculateIntercept(eyes, end);
            if (aabb.isVecInside(eyes)) {
                return null;
            }
            if (entityHit != null) {
                double entityDist = eyes.distanceTo(entityHit.hitVec);
                if (entityDist < blockDist) {
                    return null;
                }
            }
        }
        return blockHit;
    }

    public static Vec3 getVectorForRotation(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * ((float)Math.PI / 180F) - (float)Math.PI);
        float f1 = MathHelper.sin(-yaw * ((float)Math.PI / 180F) - (float)Math.PI);
        float f2 = -MathHelper.cos(-pitch * ((float)Math.PI / 180F));
        float f3 = MathHelper.sin(-pitch * ((float)Math.PI / 180F));
        return new Vec3(f1 * f2, f3, f * f2);
    }

    public static float applyVanilla(float yaw, boolean stop) {
        if (stop) {
            return yaw;
        }
        int scaleFactor = (int) Math.floor(serverRotations[0] / 360);
        float unwrappedYaw = yaw + 360 * scaleFactor;
        if (unwrappedYaw < serverRotations[0] - 180) {
            unwrappedYaw += 360;
        }
        else if (unwrappedYaw > serverRotations[0] + 180) {
            unwrappedYaw -= 360;
        }

        float deltaYaw = unwrappedYaw - serverRotations[0];
        return serverRotations[0] + deltaYaw;
    }

    public static MovingObjectPosition rayTrace(double range, float partialTicks, float[] rotations, EntityLivingBase ignoreCollision) {
        if (ignoreCollision != null) {
            MovingObjectPosition target = rayTraceIgnore(range, partialTicks, rotations, ignoreCollision);
            if (target != null) {
                return target;
            }
        }
        Entity targetEntity = null;
        MovingObjectPosition hitObject;
        double d0 = range;
        if (rotations == null) {
            rotations = new float[] { mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch };
        }
        hitObject = rayTraceCustom(d0, rotations[0], rotations[1]);
        double distanceTo = d0;
        Vec3 vec3 = mc.thePlayer.getPositionEyes(partialTicks);
        if (mc.playerController.extendedReach()) {
            d0 = 6.0;
            distanceTo = 6.0;
        }

        if (hitObject != null) {
            distanceTo = hitObject.hitVec.distanceTo(vec3);
        }

        Vec3 vec31 = RotationUtils.getVectorForRotation(rotations[1], rotations[0]);
        Vec3 vec32 = vec3.addVector(vec31.xCoord * d0, vec31.yCoord * d0, vec31.zCoord * d0);
        Vec3 vec33 = null;
        float f = 1.0F;
        List<Entity> list = mc.theWorld.getEntitiesInAABBexcluding(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().addCoord(vec31.xCoord * d0, vec31.yCoord * d0, vec31.zCoord * d0).expand(f, f, f), Predicates.and(EntitySelectors.NOT_SPECTATING, Entity::canBeCollidedWith));
        double d2 = distanceTo;

        for(int j = 0; j < list.size(); ++j) {
            Entity entity1 = list.get(j);
            float f1 = entity1.getCollisionBorderSize();
            AxisAlignedBB axisalignedbb = entity1.getEntityBoundingBox().expand(f1, f1, f1);
            MovingObjectPosition movingobjectposition = axisalignedbb.calculateIntercept(vec3, vec32);
            if (axisalignedbb.isVecInside(vec3)) {
                if (d2 >= 0.0) {
                    targetEntity = entity1;
                    vec33 = movingobjectposition == null ? vec3 : movingobjectposition.hitVec;
                    d2 = 0.0;
                }
            }
            else if (movingobjectposition != null) {
                double d3 = vec3.distanceTo(movingobjectposition.hitVec);
                if (d3 < d2 || d2 == 0.0) {
                    if (entity1 == mc.thePlayer.ridingEntity && !mc.thePlayer.canRiderInteract()) {
                        if (d2 == 0.0) {
                            targetEntity = entity1;
                            vec33 = movingobjectposition.hitVec;
                        }
                    }
                    else {
                        targetEntity = entity1;
                        vec33 = movingobjectposition.hitVec;
                        d2 = d3;
                    }
                }
            }
        }

        if (targetEntity != null && d2 < distanceTo) {
            return new MovingObjectPosition(targetEntity, vec33);
        }
        return null;
    }

    public static MovingObjectPosition rayTraceIgnore(double range, float partialTicks, float[] rotations, EntityLivingBase ignoreCollision) {
        MovingObjectPosition blockHit = rayTraceCustom(range,
                rotations[0],
                rotations[1]);

        Vec3 start = mc.thePlayer.getPositionEyes(partialTicks);
        double blockDistance = range;
        if (blockHit != null) {
            blockDistance = blockHit.hitVec.distanceTo(start);
        }

        if (ignoreCollision != null) {
            if (rotations == null) {
                rotations = new float[]{
                        mc.thePlayer.rotationYaw,
                        mc.thePlayer.rotationPitch
                };
            }
            Vec3 lookVec = RotationUtils.getVectorForRotation(
                    rotations[1],  // pitch
                    rotations[0]   // yaw
            );
            Vec3 end = start.addVector(
                    lookVec.xCoord * range,
                    lookVec.yCoord * range,
                    lookVec.zCoord * range
            );

            float f1 = ignoreCollision.getCollisionBorderSize();
            AxisAlignedBB aabb = ignoreCollision.getEntityBoundingBox()
                    .expand(f1, f1, f1);
            MovingObjectPosition ignoreMOP = aabb.calculateIntercept(start, end);

            if (aabb.isVecInside(start)) {
                return new MovingObjectPosition(ignoreCollision, start);
            }
            if (ignoreMOP != null) {
                double ignoreDist = start.distanceTo(ignoreMOP.hitVec);
                if (ignoreDist < blockDistance) {
                    return new MovingObjectPosition(
                            ignoreCollision,
                            ignoreMOP.hitVec
                    );
                }
            }
        }
        if (blockHit != null) {
            return blockHit;
        }
        return null;
    }

    public static float applyVanilla(float yaw) {
        return applyVanilla(yaw, false);
    }

    public static float[] getRotationsFromEye(Vec3 eye, double tx, double ty, double tz) {
        double dx = tx - eye.xCoord;
        double dy = ty - eye.yCoord;
        double dz = tz - eye.zCoord;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, pitch};
    }

    public static enum PLAYER_OFFSETS {
        EYE,
        CHEST,
        FOOT,
        NONE;

        public double getHeightOffset(Entity entity) {
            switch (this) {
                case NONE:
                case EYE:
                    return entity.getEyeHeight();
                case CHEST:
                    return entity.height / 2;
                case FOOT:
                    return 0;
            }
            return entity.getEyeHeight();
        }
    }
}
