package keystrokesmod.utility;

import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

public final class StairsUtils {

    public interface BlockFaceDrawer {
        void draw(AxisAlignedBB box, EnumFacing face, int overlayStart, int overlayEnd, int outlineStart, int outlineEnd, boolean overlay, boolean outline);
    }

    public static void drawStairs(BlockPos blockPos, IBlockState blockState, AxisAlignedBB box, EnumFacing side, double viewerX, double viewerY, double viewerZ, int overlayStartColor, int overlayEndColor, int outlineStartColor, int outlineEndColor, boolean overlay, boolean outline, BlockFaceDrawer drawer) {
        EnumFacing blockFacing = blockState.getValue(BlockStairs.FACING);
        BlockStairs.EnumHalf blockHalf = blockState.getValue(BlockStairs.HALF);
        int blockX = blockPos.getX();
        int blockY = blockPos.getY();
        int blockZ = blockPos.getZ();
        int angleX = (blockHalf == BlockStairs.EnumHalf.TOP) ? 270 : 0;
        int angleY = 0;
        switch (blockFacing) {
            case NORTH: angleY = 180; break;
            case EAST: angleY = 90; break;
            case WEST: angleY = 270; break;
            default: break;
        }
        GL11.glPushMatrix();
        GL11.glTranslated(-viewerX, -viewerY, -viewerZ);
        GL11.glTranslated(blockX + 0.5, blockY, blockZ + 0.5);
        GL11.glRotated(angleY, 0.0, 1.0, 0.0);
        GL11.glTranslated(0.0, 0.5, 0.0);
        GL11.glRotated(angleX, 1.0, 0.0, 0.0);
        GL11.glTranslated(-blockX - 0.5, -blockY - 0.5, -blockZ - 0.5);
        if (side == null) {
            drawStairsFull(box, overlayStartColor, overlayEndColor, outlineStartColor, outlineEndColor, overlay, outline, drawer);
        } else {
            drawStairsSide(box, blockHalf, blockFacing, side, overlayStartColor, overlayEndColor, outlineStartColor, outlineEndColor, overlay, outline, drawer);
        }
        GL11.glPopMatrix();
    }

    private static void drawStairsFull(AxisAlignedBB box, int overlayStart, int overlayEnd, int outlineStart, int outlineEnd, boolean overlay, boolean outline, BlockFaceDrawer drawer) {
        drawStairsTop(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline, drawer);
        drawStairsBottom(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline, drawer);
        drawStairsNorth(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline, drawer);
        drawStairsEast(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline, drawer);
        drawStairsSouth(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline, drawer);
        drawStairsWest(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline, drawer);
    }

    private static void drawStairsSide(AxisAlignedBB box, BlockStairs.EnumHalf blockHalf, EnumFacing blockFacing, EnumFacing side, int overlayStart, int overlayEnd, int outlineStart, int outlineEnd, boolean overlay, boolean outline, BlockFaceDrawer drawer) {
        EnumFacing mapped = getSide(blockHalf, blockFacing, side);
        switch (mapped) {
            case UP: drawStairsTop(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline, drawer); break;
            case DOWN: drawStairsBottom(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline, drawer); break;
            case NORTH: drawStairsNorth(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline, drawer); break;
            case EAST: drawStairsEast(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline, drawer); break;
            case SOUTH: drawStairsSouth(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline, drawer); break;
            case WEST: drawStairsWest(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline, drawer); break;
        }
    }

    private static void drawStairsTop(AxisAlignedBB box, int os, int oe, int ls, int le, boolean overlay, boolean outline, BlockFaceDrawer drawer) {
        AxisAlignedBB b1 = box.contract(0.0, 0.0, 0.25).offset(0.0, 0.0, 0.25);
        AxisAlignedBB b2 = box.contract(0.0, 0.0, 0.25).offset(0.0, -0.5, -0.25);
        drawer.draw(b1, EnumFacing.UP, os, oe, ls, le, overlay, outline);
        drawer.draw(b2, EnumFacing.UP, os, oe, ls, le, overlay, outline);
    }

    private static void drawStairsBottom(AxisAlignedBB box, int os, int oe, int ls, int le, boolean overlay, boolean outline, BlockFaceDrawer drawer) {
        drawer.draw(box, EnumFacing.DOWN, os, oe, ls, le, overlay, outline);
    }

    private static void drawStairsNorth(AxisAlignedBB box, int os, int oe, int ls, int le, boolean overlay, boolean outline, BlockFaceDrawer drawer) {
        drawer.draw(box.contract(0.0, 0.252, 0.0).offset(0.0, 0.252, 0.5), EnumFacing.NORTH, os, oe, ls, le, overlay, outline);
        drawer.draw(box.contract(0.0, 0.25, 0.0).offset(0.0, -0.25, 0.0), EnumFacing.NORTH, os, oe, ls, le, overlay, outline);
    }

    private static void drawStairsEast(AxisAlignedBB box, int os, int oe, int ls, int le, boolean overlay, boolean outline, BlockFaceDrawer drawer) {
        drawer.draw(box.contract(0.0, 0.252, 0.25).offset(0.0, 0.252, 0.25), EnumFacing.EAST, os, oe, ls, le, overlay, outline);
        drawer.draw(box.contract(0.0, 0.25, 0.0).offset(0.0, -0.25, 0.0), EnumFacing.EAST, os, oe, ls, le, overlay, outline);
    }

    private static void drawStairsSouth(AxisAlignedBB box, int os, int oe, int ls, int le, boolean overlay, boolean outline, BlockFaceDrawer drawer) {
        drawer.draw(box, EnumFacing.SOUTH, os, oe, ls, le, overlay, outline);
    }

    private static void drawStairsWest(AxisAlignedBB box, int os, int oe, int ls, int le, boolean overlay, boolean outline, BlockFaceDrawer drawer) {
        drawer.draw(box.contract(0.0, 0.252, 0.25).offset(0.0, 0.252, 0.25), EnumFacing.WEST, os, oe, ls, le, overlay, outline);
        drawer.draw(box.contract(0.0, 0.25, 0.0).offset(0.0, -0.25, 0.0), EnumFacing.WEST, os, oe, ls, le, overlay, outline);
    }

    private static EnumFacing getSide(BlockStairs.EnumHalf blockHalf, EnumFacing blockFacing, EnumFacing side) {
        if (blockHalf == BlockStairs.EnumHalf.TOP) {
            switch (blockFacing) {
                case NORTH:
                    side = side.rotateAround(EnumFacing.Axis.X);
                    side = side.rotateAround(EnumFacing.Axis.Y);
                    side = side.rotateAround(EnumFacing.Axis.Y);
                    break;
                case EAST:
                    side = side.rotateAround(EnumFacing.Axis.Z);
                    side = side.rotateAround(EnumFacing.Axis.Y);
                    break;
                case SOUTH:
                    side = side.rotateAround(EnumFacing.Axis.X);
                    side = side.rotateAround(EnumFacing.Axis.X);
                    side = side.rotateAround(EnumFacing.Axis.X);
                    break;
                case WEST:
                    side = side.rotateAround(EnumFacing.Axis.Z);
                    side = side.rotateAround(EnumFacing.Axis.Y);
                    side = side.rotateAround(EnumFacing.Axis.Z);
                    side = side.rotateAround(EnumFacing.Axis.Z);
                    break;
                default: break;
            }
        } else if (side != EnumFacing.UP && side != EnumFacing.DOWN) {
            switch (blockFacing) {
                case NORTH: side = side.getOpposite(); break;
                case EAST: side = side.rotateYCCW(); break;
                case WEST: side = side.rotateYCCW(); break;
                default: break;
            }
        }
        return side;
    }

    private StairsUtils() {}
}
