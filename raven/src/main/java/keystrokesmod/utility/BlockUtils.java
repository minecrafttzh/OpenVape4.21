package keystrokesmod.utility;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class BlockUtils implements IMinecraftInstance {
    public static boolean isSamePos(BlockPos blockPos, BlockPos blockPos2) {
        return blockPos == blockPos2 || (blockPos.getX() == blockPos2.getX() && blockPos.getY() == blockPos2.getY() && blockPos.getZ() == blockPos2.getZ());
    }

    public static boolean notFull(Block block) {
        return block instanceof BlockFenceGate || block instanceof BlockLadder || block instanceof BlockFlowerPot || block instanceof BlockBasePressurePlate || isFluid(block) || block instanceof BlockFence || block instanceof BlockAnvil || block instanceof BlockEnchantmentTable || block instanceof BlockChest;
    }

    public static boolean isNormalBlock(final Block block) {
        return block == Blocks.glass || (block.isFullBlock() && block != Blocks.gravel && block != Blocks.sand && block != Blocks.soul_sand && block != Blocks.tnt && block != Blocks.crafting_table && block != Blocks.furnace && block != Blocks.dispenser && block != Blocks.dropper && block != Blocks.noteblock && block != Blocks.command_block);
    }


    public static BlockPos pos(final double x, final double y, final double z) {
        return new BlockPos(x, y, z);
    }

    public static boolean isBlockPosEqual(final BlockPos pos1, final BlockPos pos2) {
        return pos1 == pos2 || (pos1.getX() == pos2.getX() && pos1.getY() == pos2.getY() && pos1.getZ() == pos2.getZ());
    }

    public static BlockPos offsetPos(MovingObjectPosition mop) {
        return mop.getBlockPos().offset(mop.sideHit);
    }

    public static boolean isFluid(Block block) {
        return block.getMaterial() == Material.lava || block.getMaterial() == Material.water;
    }

    public static boolean isInteractable(Block block) {
        return block instanceof BlockTrapDoor || block instanceof BlockDoor || block instanceof BlockContainer || block instanceof BlockJukebox || block instanceof BlockFenceGate || block instanceof BlockChest || block instanceof BlockEnderChest || block instanceof BlockEnchantmentTable || block instanceof BlockBrewingStand || block instanceof BlockBed || block instanceof BlockDropper || block instanceof BlockDispenser || block instanceof BlockHopper || block instanceof BlockAnvil || block instanceof BlockNote || block instanceof BlockWorkbench;
    }

    public static boolean isInteractable(MovingObjectPosition mv) {
        if (mv == null || mv.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mv.getBlockPos() == null) {
            return false;
        }
        if (!mc.thePlayer.isSneaking() || mc.thePlayer.getHeldItem() == null) {
            return isInteractable(BlockUtils.getBlock(mv.getBlockPos()));
        }
        return false;
    }

    public static float getBlockHardness(final Block block, final ItemStack itemStack, boolean ignoreSlow, boolean ignoreGround) {
        final float getBlockHardness = block.getBlockHardness(mc.theWorld, null);
        if (getBlockHardness < 0.0f) {
            return 0.0f;
        }
        return (block.getMaterial().isToolNotRequired() || (itemStack != null && itemStack.canHarvestBlock(block))) ? (getToolDigEfficiency(itemStack, block, ignoreSlow, ignoreGround) / getBlockHardness / 30.0f) : (getToolDigEfficiency(itemStack, block, ignoreSlow, ignoreGround) / getBlockHardness / 100.0f);
    }

    public static float maxDigRateAcrossSlots(Block block, int slotCount) {
        if (mc.thePlayer == null || slotCount <= 0) {
            return 0f;
        }
        int n = Math.min(slotCount, mc.thePlayer.inventory.getSizeInventory());
        float best = 0f;
        for (int i = 0; i < n; i++) {
            float h = getBlockHardness(block, mc.thePlayer.inventory.getStackInSlot(i), false, false);
            if (h > best) {
                best = h;
            }
        }
        return best;
    }

    public static float getToolDigEfficiency(ItemStack itemStack, Block block, boolean ignoreSlow, boolean ignoreGround) {
        float n = (itemStack == null) ? 1.0f : itemStack.getItem().getStrVsBlock(itemStack, block);
        if (n > 1.0f) {
            final int getEnchantmentLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemStack);
            if (getEnchantmentLevel > 0 && itemStack != null) {
                n += getEnchantmentLevel * getEnchantmentLevel + 1;
            }
        }
        if (mc.thePlayer.isPotionActive(Potion.digSpeed)) {
            n *= 1.0f + (mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2f;
        }
        if (!ignoreSlow) {
            if (mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
                float n2;
                switch (mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
                    case 0: {
                        n2 = 0.3f;
                        break;
                    }
                    case 1: {
                        n2 = 0.09f;
                        break;
                    }
                    case 2: {
                        n2 = 0.0027f;
                        break;
                    }
                    default: {
                        n2 = 8.1E-4f;
                        break;
                    }
                }
                n *= n2;
            }
            if (mc.thePlayer.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(mc.thePlayer)) {
                n /= 5.0f;
            }
            if (!mc.thePlayer.onGround && !ignoreGround) {
                n /= 5.0f;
            }
        }
        return n;
    }

    public static Block getBlock(BlockPos blockPos) {
        return getBlockState(blockPos).getBlock();
    }

    public static Block getBlock(double x, double y, double z) {
        return getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    public static Block getBlock(Vec3 position) {
        return getBlockState(new BlockPos(position.xCoord, position.yCoord, position.zCoord)).getBlock();
    }

    public static IBlockState getBlockState(BlockPos blockPos) {
        if (mc.theWorld == null || blockPos == null) {
            return Blocks.air.getDefaultState();
        }
        return mc.theWorld.getBlockState(blockPos);
    }

    public static AxisAlignedBB getBlockSelectionBox(BlockPos pos) {
        if (mc.theWorld == null || pos == null) return null;
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        block.setBlockBoundsBasedOnState(mc.theWorld, pos);
        AxisAlignedBB box = block.getSelectedBoundingBox(mc.theWorld, pos);
        if (box == null) {
            box = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        }
        return box;
    }

    public static AxisAlignedBB getCollisionOrSelectionBox(BlockPos pos) {
        if (mc.theWorld == null || pos == null) {
            return null;
        }
        IBlockState st = mc.theWorld.getBlockState(pos);
        Block block = st.getBlock();
        AxisAlignedBB bb = block.getCollisionBoundingBox(mc.theWorld, pos, st);
        if (bb == null) {
            bb = block.getSelectedBoundingBox(mc.theWorld, pos);
        }
        if (bb == null) {
            bb = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        }
        return bb;
    }

    public static AxisAlignedBB getCollisionOrSelectedOnly(BlockPos pos) {
        if (mc.theWorld == null || pos == null) {
            return null;
        }
        IBlockState st = mc.theWorld.getBlockState(pos);
        Block block = st.getBlock();
        AxisAlignedBB bb = block.getCollisionBoundingBox(mc.theWorld, pos, st);
        if (bb == null) {
            bb = block.getSelectedBoundingBox(mc.theWorld, pos);
        }
        return bb;
    }

    public static AxisAlignedBB unionBlockBounds(BlockPos a, BlockPos b) {
        AxisAlignedBB ua = getCollisionOrSelectionBox(a);
        AxisAlignedBB ub = getCollisionOrSelectionBox(b);
        return ua.union(ub);
    }

    public static EnumFacing facingFromBlockCenterToPoint(BlockPos pos, Vec3 hit) {
        double px = hit.xCoord - (pos.getX() + 0.5);
        double py = hit.yCoord - (pos.getY() + 0.5);
        double pz = hit.zCoord - (pos.getZ() + 0.5);
        double ax = Math.abs(px);
        double ay = Math.abs(py);
        double az = Math.abs(pz);
        if (ax > ay && ax > az) {
            return px > 0 ? EnumFacing.EAST : EnumFacing.WEST;
        }
        if (ay > az) {
            return py > 0 ? EnumFacing.UP : EnumFacing.DOWN;
        }
        return pz > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }

    public static boolean check(final BlockPos blockPos, final Block block) {
        return getBlock(blockPos) == block;
    }

    public static boolean replaceable(BlockPos blockPos) {
        if (!Utils.nullCheck()) {
            return true;
        }
        return getBlock(blockPos).isReplaceable(mc.theWorld, blockPos);
    }

    public static boolean canSeeVecBlock(final BlockPos pos, final Vec3 vecPlayer, final Vec3 vecBlockPoint) {
        final MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(vecPlayer, vecBlockPoint, false, false, false);
        if (mop == null) {
            return true;
        }
        if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            final BlockPos mopPos = mop.getBlockPos();
            if (mopPos.getX() == pos.getX() && mopPos.getY() == pos.getY() && mopPos.getZ() == pos.getZ()) {
                return true;
            }
        }
        return false;
    }

    public static boolean canBlockBeSeen(final BlockPos pos) {
        final Vec3 vecPlayer = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        for (double offsetY = 0.0; offsetY <= 0.5; offsetY += 0.5) {
            final double y = pos.getY() + offsetY;
            Vec3 vecBlockPoint = new Vec3(pos.getX() + 1, y, pos.getZ() + 0.5);
            if (canSeeVecBlock(pos, vecPlayer, vecBlockPoint)) {
                return true;
            }
            vecBlockPoint = new Vec3(pos.getX(), y, pos.getZ() + 0.5);
            if (canSeeVecBlock(pos, vecPlayer, vecBlockPoint)) {
                return true;
            }
            vecBlockPoint = new Vec3(pos.getX() + 0.5, y, (double)(pos.getZ() + 1));
            if (canSeeVecBlock(pos, vecPlayer, vecBlockPoint)) {
                return true;
            }
            vecBlockPoint = new Vec3(pos.getX() + 0.5, y, (double)pos.getZ());
            if (canSeeVecBlock(pos, vecPlayer, vecBlockPoint)) {
                return true;
            }
        }
        return false;
    }

    public static EnumDyeColor getWoolColor(final IBlockState state) {
        return (EnumDyeColor)state.getProperties().get(BlockColored.COLOR);
    }

    public static EnumFacing[] getVisibleFaces(Vec3 eye, BlockPos block) {
        EnumFacing yFace = Math.abs(eye.yCoord - (block.getY() + 1)) < Math.abs(eye.yCoord - block.getY())
                ? EnumFacing.UP : EnumFacing.DOWN;
        EnumFacing zFace = Math.abs(eye.zCoord - (block.getZ() + 1)) < Math.abs(eye.zCoord - block.getZ())
                ? EnumFacing.SOUTH : EnumFacing.NORTH;
        EnumFacing xFace = Math.abs(eye.xCoord - (block.getX() + 1)) < Math.abs(eye.xCoord - block.getX())
                ? EnumFacing.EAST : EnumFacing.WEST;
        return new EnumFacing[]{yFace, zFace, xFace};
    }

    public static boolean containsFace(EnumFacing[] faces, EnumFacing face) {
        for (EnumFacing f : faces) if (f == face) return true;
        return false;
    }

    public static Vec3 getFaceCenter(BlockPos block, EnumFacing face) {
        double eps = 1e-3;
        double cx = block.getX() + 0.5;
        double cy = block.getY() + 0.5;
        double cz = block.getZ() + 0.5;
        switch (face) {
            case UP:    return new Vec3(cx, block.getY() + 1 - eps, cz);
            case DOWN:  return new Vec3(cx, block.getY() + eps, cz);
            case NORTH: return new Vec3(cx, cy, block.getZ() + eps);
            case SOUTH: return new Vec3(cx, cy, block.getZ() + 1 - eps);
            case EAST:  return new Vec3(block.getX() + 1 - eps, cy, cz);
            case WEST:  return new Vec3(block.getX() + eps, cy, cz);
            default:    return new Vec3(cx, cy, cz);
        }
    }

    public static double dist2PointAABB(Vec3 p, BlockPos b) {
        double cx = Math.max(b.getX(), Math.min(b.getX() + 1, p.xCoord));
        double cy = Math.max(b.getY(), Math.min(b.getY() + 1, p.yCoord));
        double cz = Math.max(b.getZ(), Math.min(b.getZ() + 1, p.zCoord));
        double dx = p.xCoord - cx, dy = p.yCoord - cy, dz = p.zCoord - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    public static boolean canPlaceBlockOnSide(ItemStack stack, BlockPos pos, EnumFacing side) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
        return ((ItemBlock) stack.getItem()).canPlaceBlockOnSide(
                mc.theWorld, pos, side, mc.thePlayer, stack);
    }

    public static float getFistBreakTicks(Block block) {
        float hardness = block.getBlockHardness(mc.theWorld, null);
        if (hardness < 0) return Float.MAX_VALUE;
        if (hardness == 0) return 0;
        return hardness * (block.getMaterial().isToolNotRequired() ? 30f : 100f);
    }

    public static boolean hasAirNeighbor(BlockPos pos, BlockPos... exclude) {
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos n = pos.offset(f);
            if (mc.theWorld.getBlockState(n).getBlock() != Blocks.air) continue;
            boolean excluded = false;
            for (BlockPos ex : exclude) {
                if (n.equals(ex)) { excluded = true; break; }
            }
            if (!excluded) return true;
        }
        return false;
    }

    public static boolean isAdjacentToBed(BlockPos pos) {
        for (EnumFacing face : EnumFacing.values()) {
            if (getBlock(pos.offset(face)) instanceof BlockBed) return true;
        }
        return false;
    }

    public static MovingObjectPosition traverseBlocksAlongRay(Vec3 start, Vec3 end,
            boolean wantBed, boolean wantAdjacent) {
        if (mc.theWorld == null) return null;
        if (Double.isNaN(start.xCoord) || Double.isNaN(start.yCoord) || Double.isNaN(start.zCoord)) return null;
        if (Double.isNaN(end.xCoord) || Double.isNaN(end.yCoord) || Double.isNaN(end.zCoord)) return null;

        int destX = MathHelper.floor_double(end.xCoord);
        int destY = MathHelper.floor_double(end.yCoord);
        int destZ = MathHelper.floor_double(end.zCoord);
        int curX = MathHelper.floor_double(start.xCoord);
        int curY = MathHelper.floor_double(start.yCoord);
        int curZ = MathHelper.floor_double(start.zCoord);

        MovingObjectPosition firstHit = null;

        MovingObjectPosition candidate = getBlockCollisionHit(curX, curY, curZ, start, end);
        if (candidate != null) {
            if (isBedOrAdjacentMatch(candidate.getBlockPos(), wantBed, wantAdjacent)) return candidate;
            firstHit = candidate;
        }

        Vec3 tracePos = start;
        int remaining = 200;

        while (remaining-- >= 0) {
            if (Double.isNaN(tracePos.xCoord) || Double.isNaN(tracePos.yCoord) || Double.isNaN(tracePos.zCoord))
                return firstHit;
            if (curX == destX && curY == destY && curZ == destZ)
                return firstHit;

            boolean crossX = true, crossY = true, crossZ = true;
            double boundX = 999.0, boundY = 999.0, boundZ = 999.0;
            if (destX > curX) boundX = (double) curX + 1.0;
            else if (destX < curX) boundX = (double) curX;
            else crossX = false;
            if (destY > curY) boundY = (double) curY + 1.0;
            else if (destY < curY) boundY = (double) curY;
            else crossY = false;
            if (destZ > curZ) boundZ = (double) curZ + 1.0;
            else if (destZ < curZ) boundZ = (double) curZ;
            else crossZ = false;

            double dx = end.xCoord - tracePos.xCoord;
            double dy = end.yCoord - tracePos.yCoord;
            double dz = end.zCoord - tracePos.zCoord;
            double tX = 999.0, tY = 999.0, tZ = 999.0;
            if (crossX) tX = (boundX - tracePos.xCoord) / dx;
            if (crossY) tY = (boundY - tracePos.yCoord) / dy;
            if (crossZ) tZ = (boundZ - tracePos.zCoord) / dz;
            if (tX == -0.0) tX = -1.0E-4;
            if (tY == -0.0) tY = -1.0E-4;
            if (tZ == -0.0) tZ = -1.0E-4;

            EnumFacing face;
            if (tX < tY && tX < tZ) {
                face = destX > curX ? EnumFacing.WEST : EnumFacing.EAST;
                tracePos = new Vec3(boundX, tracePos.yCoord + dy * tX, tracePos.zCoord + dz * tX);
            } else if (tY < tZ) {
                face = destY > curY ? EnumFacing.DOWN : EnumFacing.UP;
                tracePos = new Vec3(tracePos.xCoord + dx * tY, boundY, tracePos.zCoord + dz * tY);
            } else {
                face = destZ > curZ ? EnumFacing.NORTH : EnumFacing.SOUTH;
                tracePos = new Vec3(tracePos.xCoord + dx * tZ, tracePos.yCoord + dy * tZ, boundZ);
            }

            curX = MathHelper.floor_double(tracePos.xCoord) - (face == EnumFacing.EAST ? 1 : 0);
            curY = MathHelper.floor_double(tracePos.yCoord) - (face == EnumFacing.UP ? 1 : 0);
            curZ = MathHelper.floor_double(tracePos.zCoord) - (face == EnumFacing.SOUTH ? 1 : 0);

            candidate = getBlockCollisionHit(curX, curY, curZ, start, end);
            if (candidate != null) {
                if (isBedOrAdjacentMatch(candidate.getBlockPos(), wantBed, wantAdjacent)) return candidate;
                if (firstHit == null) firstHit = candidate;
            }
        }
        return firstHit;
    }

    private static MovingObjectPosition getBlockCollisionHit(int x, int y, int z, Vec3 start, Vec3 end) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (!block.canCollideCheck(state, false)) return null;
        return block.collisionRayTrace(mc.theWorld, pos, start, end);
    }

    private static boolean isBedOrAdjacentMatch(BlockPos pos, boolean wantBed, boolean wantAdjacent) {
        Block block = getBlock(pos);
        boolean isBed = block instanceof BlockBed;
        if (wantBed && isBed) return true;
        if (wantAdjacent && !isBed && isAdjacentToBed(pos)) return true;
        return false;
    }
}