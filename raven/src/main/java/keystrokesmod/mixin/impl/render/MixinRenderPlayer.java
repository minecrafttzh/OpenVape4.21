package keystrokesmod.mixin.impl.render;

import keystrokesmod.utility.BlockAnimationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.entity.RenderPlayer;

@Mixin(RenderPlayer.class)
public class MixinRenderPlayer {
    @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V", at = @At("HEAD"))
    private void onDoRenderHead(AbstractClientPlayer entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        BlockAnimationUtils.beginRender(entity);
    }

    @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V", at = @At("RETURN"))
    private void onDoRenderReturn(AbstractClientPlayer entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        BlockAnimationUtils.endRender(entity);
    }

    @Redirect(method = "setModelVisibilities(Lnet/minecraft/client/entity/AbstractClientPlayer;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/InventoryPlayer;getCurrentItem()Lnet/minecraft/item/ItemStack;"))
    private ItemStack redirectGetCurrentItem(InventoryPlayer inventory) {
        if (Minecraft.getMinecraft().gameSettings.thirdPersonView == 0 && inventory.player == Minecraft.getMinecraft().thePlayer) {
            return Utils.getSpoofedItem(inventory.getCurrentItem());
        }
        else {
            return inventory.getCurrentItem();
        }
    }

    @Redirect(method = "setModelVisibilities(Lnet/minecraft/client/entity/AbstractClientPlayer;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;getItemInUseCount()I"))
    private int redirectGetItemInUseCount(AbstractClientPlayer clientPlayer) {
        int actualCount = clientPlayer.getItemInUseCount();
        if (actualCount > 0) {
            return actualCount;
        }

        ItemStack itemStack = Minecraft.getMinecraft().gameSettings.thirdPersonView == 0
            ? Utils.getSpoofedItem(clientPlayer.inventory.getCurrentItem())
            : clientPlayer.inventory.getCurrentItem();

        return BlockAnimationUtils.shouldForceBlockAnimation(clientPlayer, itemStack) ? 1 : actualCount;
    }
}
