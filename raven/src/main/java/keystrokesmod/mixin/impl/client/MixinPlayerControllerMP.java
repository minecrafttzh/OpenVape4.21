package keystrokesmod.mixin.impl.client;

import keystrokesmod.event.AttackEvent;
import keystrokesmod.event.UseItemEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.BedAura;
import keystrokesmod.module.impl.player.FastMine;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerControllerMP.class)
public class MixinPlayerControllerMP {

    @Shadow
    private int blockHitDelay;

    @Inject(method = "sendUseItem(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"))
    public void injectUseItemEvent(EntityPlayer p_sendUseItem_1_, World p_sendUseItem_2_, ItemStack p_sendUseItem_3_, CallbackInfoReturnable<Boolean> ci) {
        UseItemEvent event = new UseItemEvent(p_sendUseItem_3_);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.setReturnValue(false);
        }
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void injectAttackEntity(EntityPlayer playerIn, Entity targetEntity, CallbackInfo callbackInfo) {
        AttackEvent event = new AttackEvent(targetEntity, playerIn, true);

        MinecraftForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            callbackInfo.cancel();
        }
    }

    @Redirect(
        method = "onPlayerDamageBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;getPlayerRelativeBlockHardness(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;)F"
        )
    )
    private float fastMineScaleHardness(Block block, EntityPlayer player, World worldIn, BlockPos pos) {
        float hardness = block.getPlayerRelativeBlockHardness(player, worldIn, pos);
        BedAura bedAura = ModuleManager.bedAura;
        if (bedAura != null && bedAura.shouldOverrideFastMine()) {
            return hardness * bedAura.getBreakSpeedMultiplier();
        }
        FastMine fm = ModuleManager.fastMine;
        if (fm == null) {
            return hardness;
        }
        return hardness * fm.getBreakSpeedMultiplier();
    }

    /**
     * Vanilla sets {@code blockHitDelay = 5} after creative click, creative mining tick, and survival block break
     * ({@code PlayerControllerMP}). Override at assignment time when FastMine break delay &lt; 5 ticks.
     */
    @Unique
    private void raven$fastMineApplyBreakDelaySlider() {
        BedAura bedAura = ModuleManager.bedAura;
        if (bedAura != null && bedAura.shouldOverrideFastMine()) {
            int delay = bedAura.getBreakDelayTicks();
            if (delay < 5) {
                this.blockHitDelay = delay;
            }
            return;
        }
        FastMine fm = ModuleManager.fastMine;
        if (fm == null) {
            return;
        }
        int o = fm.getBlockHitDelayOverrideOrMinusOne();
        if (o >= 0) {
            this.blockHitDelay = o;
        }
    }

    @Inject(
        method = "clickBlock",
        at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;blockHitDelay:I", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void raven$fastMineAfterClickBlockSetDelay(BlockPos loc, EnumFacing face, CallbackInfoReturnable<Boolean> cir) {
        raven$fastMineApplyBreakDelaySlider();
    }

    @Inject(
        method = "onPlayerDamageBlock",
        at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;blockHitDelay:I", ordinal = 1, shift = At.Shift.AFTER)
    )
    private void raven$fastMineAfterCreativeMiningSetDelay(BlockPos posBlock, EnumFacing directionFacing, CallbackInfoReturnable<Boolean> cir) {
        raven$fastMineApplyBreakDelaySlider();
    }

    @Inject(
        method = "onPlayerDamageBlock",
        at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;blockHitDelay:I", ordinal = 2, shift = At.Shift.AFTER)
    )
    private void raven$fastMineAfterBreakBlockSetDelay(BlockPos posBlock, EnumFacing directionFacing, CallbackInfoReturnable<Boolean> cir) {
        raven$fastMineApplyBreakDelaySlider();
    }
}
