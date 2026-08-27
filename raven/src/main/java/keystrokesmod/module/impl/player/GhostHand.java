package keystrokesmod.module.impl.player;

import com.google.common.base.Predicates;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.*;
import org.lwjgl.input.Mouse;

import java.util.List;

public class GhostHand extends Module {

    private GroupSetting interactGroup;
    private ButtonSetting throughNonPlayer;
    private ButtonSetting throughBots;
    private ButtonSetting throughFriendlies;
    private ButtonSetting throughEnemies;

    private GroupSetting priorityGroup;
    private ButtonSetting priorityEverything;
    private ButtonSetting priorityBed;
    private ButtonSetting priorityBedAdjacent;

    private GroupSetting useGroup;
    private GroupSetting conditionsGroup;
    private ButtonSetting useSword;
    private ButtonSetting useTool;
    private ButtonSetting useFists;
    private ButtonSetting useBucket;
    private ButtonSetting useFlintSteel;
    private ButtonSetting useCobweb;
    private ButtonSetting useOther;

    private ButtonSetting requireLmb;
    private ButtonSetting requireRmb;
    private ButtonSetting notSword;

    public GhostHand() {
        super("Ghost Hand", category.player);
        this.registerSetting(interactGroup = new GroupSetting("Interact through"));
        this.registerSetting(throughNonPlayer = new ButtonSetting(interactGroup, "Non-player entities", true));
        this.registerSetting(throughBots = new ButtonSetting(interactGroup, "Bots", false));
        this.registerSetting(throughFriendlies = new ButtonSetting(interactGroup, "Friendlies", false));
        this.registerSetting(throughEnemies = new ButtonSetting(interactGroup, "Enemies", true));

        this.registerSetting(priorityGroup = new GroupSetting("Preferred targets"));
        this.registerSetting(priorityEverything = new ButtonSetting(priorityGroup, "Everything", true));
        this.registerSetting(priorityBed = new ButtonSetting(priorityGroup, "Bed", false));
        this.registerSetting(priorityBedAdjacent = new ButtonSetting(priorityGroup, "Next to bed", false));

        this.registerSetting(useGroup = new GroupSetting("Allow while using"));
        this.registerSetting(useSword = new ButtonSetting(useGroup, "Sword", false));
        this.registerSetting(useTool = new ButtonSetting(useGroup, "Tool", true));
        this.registerSetting(useFists = new ButtonSetting(useGroup, "Fists", true));
        this.registerSetting(useBucket = new ButtonSetting(useGroup, "Bucket", true));
        this.registerSetting(useFlintSteel = new ButtonSetting(useGroup, "Flint and steel", true));
        this.registerSetting(useCobweb = new ButtonSetting(useGroup, "Cobweb", true));
        this.registerSetting(useOther = new ButtonSetting(useGroup, "Other", true));

        this.registerSetting(conditionsGroup = new GroupSetting("Conditions"));
        this.registerSetting(requireLmb = new ButtonSetting(conditionsGroup, "Require Left mouse", false));
        this.registerSetting(requireRmb = new ButtonSetting(conditionsGroup, "Require right mouse", false));
        this.registerSetting(notSword = new ButtonSetting(conditionsGroup, "Not holding a sword", false));
    }

    public boolean shouldOverrideMouseOver() {
        if (!this.isEnabled()) return false;
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return false;
        if (mc.getRenderViewEntity() == null) return false;
        if (notSword.isToggled() && Utils.holdingSword()) return false;
        if (requireLmb.isToggled() && !Mouse.isButtonDown(0)) return false;
        if (requireRmb.isToggled() && !Mouse.isButtonDown(1)) return false;
        return heldItemAllowed();
    }

    public void modifyMouseOverFromGetMouseOver(float partialTicks) {
        if (!shouldOverrideMouseOver()) return;

        Entity viewEntity = mc.getRenderViewEntity();

        double reach = mc.playerController.getBlockReachDistance();
        if (mc.playerController.extendedReach()) reach = 6.0;

        MovingObjectPosition blockHit = findPrioritizedBlock(viewEntity, reach, partialTicks);
        if (blockHit == null) return;

        BlockPos hitPos = blockHit.getBlockPos();
        Block hitBlock = BlockUtils.getBlock(hitPos);
        boolean isBed = hitBlock instanceof BlockBed;
        boolean isAdjacent = !isBed && BlockUtils.isAdjacentToBed(hitPos);
        boolean priorityOverride = (priorityBed.isToggled() && isBed)
                || (priorityBedAdjacent.isToggled() && isAdjacent);

        if (!priorityEverything.isToggled() && !priorityOverride) return;

        if (!priorityOverride) {
            Vec3 eyes = viewEntity.getPositionEyes(partialTicks);
            Vec3 blockHitVec = blockHit.hitVec;
            double blockDist = eyes.distanceTo(blockHitVec);

            AxisAlignedBB scanBox = new AxisAlignedBB(
                    Math.min(eyes.xCoord, blockHitVec.xCoord) - 1.0,
                    Math.min(eyes.yCoord, blockHitVec.yCoord) - 1.0,
                    Math.min(eyes.zCoord, blockHitVec.zCoord) - 1.0,
                    Math.max(eyes.xCoord, blockHitVec.xCoord) + 1.0,
                    Math.max(eyes.yCoord, blockHitVec.yCoord) + 1.0,
                    Math.max(eyes.zCoord, blockHitVec.zCoord) + 1.0);

            List<Entity> candidates = mc.theWorld.getEntitiesInAABBexcluding(
                    viewEntity, scanBox, Predicates.and(EntitySelectors.NOT_SPECTATING, Entity::canBeCollidedWith));

            Entity closest = null;
            double closestDist = Double.MAX_VALUE;

            for (Entity e : candidates) {
                if (e == viewEntity) continue;
                float cb = e.getCollisionBorderSize();
                AxisAlignedBB bb = e.getEntityBoundingBox().expand(cb, cb, cb);
                MovingObjectPosition intercept = bb.calculateIntercept(eyes, blockHitVec);
                boolean inside = bb.isVecInside(eyes);
                if (!inside && intercept == null) continue;
                double dist = inside ? 0.0 : eyes.distanceTo(intercept.hitVec);
                if (dist >= blockDist) continue;
                if (e == viewEntity.ridingEntity && !viewEntity.canRiderInteract()) continue;
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = e;
                }
            }

            if (closest == null || !obstructionAllowed(closest)) return;
        }

        mc.objectMouseOver = blockHit;
        mc.pointedEntity = null;

        EntityRenderer renderer = mc.entityRenderer;
        if (renderer instanceof IAccessorEntityRenderer) {
            ((IAccessorEntityRenderer) renderer).setPointedEntity(null);
        }
    }

    private MovingObjectPosition findPrioritizedBlock(Entity viewEntity, double reach, float partialTicks) {
        Vec3 eyes = viewEntity.getPositionEyes(partialTicks);
        Vec3 look = viewEntity.getLook(partialTicks);
        Vec3 rayEnd = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        return BlockUtils.traverseBlocksAlongRay(eyes, rayEnd, priorityBed.isToggled(), priorityBedAdjacent.isToggled());
    }

    private boolean heldItemAllowed() {
        ItemStack held = mc.thePlayer.getHeldItem();
        switch (classify(held)) {
            case SWORD: return useSword.isToggled();
            case TOOL: return useTool.isToggled();
            case FISTS: return useFists.isToggled();
            case BUCKET: return useBucket.isToggled();
            case FLINT_STEEL: return useFlintSteel.isToggled();
            case COBWEB: return useCobweb.isToggled();
            default: return useOther.isToggled();
        }
    }

    private boolean obstructionAllowed(Entity e) {
        if (!(e instanceof EntityPlayer)) return throughNonPlayer.isToggled();
        EntityPlayer player = (EntityPlayer) e;
        if (AntiBot.isBot(player)) return throughBots.isToggled();
        if (Utils.isFriended(player) || Utils.isTeammate(player)) return throughFriendlies.isToggled();
        return throughEnemies.isToggled();
    }

    private enum HeldCategory { SWORD, TOOL, FISTS, BUCKET, FLINT_STEEL, COBWEB, OTHER }

    private static HeldCategory classify(ItemStack held) {
        if (held == null) return HeldCategory.FISTS;
        Item item = held.getItem();
        if (item instanceof ItemSword) return HeldCategory.SWORD;
        if (item instanceof ItemTool || item instanceof ItemHoe || item instanceof ItemShears) return HeldCategory.TOOL;
        if (item instanceof ItemBucket) return HeldCategory.BUCKET;
        if (item instanceof ItemFlintAndSteel) return HeldCategory.FLINT_STEEL;
        if (item instanceof ItemBlock && ((ItemBlock) item).getBlock() == Blocks.web) return HeldCategory.COBWEB;
        return HeldCategory.OTHER;
    }
}
