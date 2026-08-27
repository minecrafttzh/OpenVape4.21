package keystrokesmod.mixin.impl.render;

import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.utility.Utils;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer implements IMixinItemRenderer {
    private ItemStack originalItemToRender;
    @Shadow
    private ItemStack itemToRender;
    public boolean cancelUpdate = false;
    public boolean cancelReset = false;
    private boolean renderItemInUse;
    @Shadow
    private float equippedProgress;
    @Shadow
    private float prevEquippedProgress;

    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"))
    private void modifyRenderItemPre(float p_renderItemInFirstPerson_1_, CallbackInfo info) {
        originalItemToRender = itemToRender;
        itemToRender = Utils.getSpoofedItem(originalItemToRender);
    }

    @Inject(method = "renderItemInFirstPerson", at = @At("RETURN"))
    private void modifyRenderItemPost(float p_renderItemInFirstPerson_1_, CallbackInfo info) {
        itemToRender = originalItemToRender;
    }

    @Redirect(method = "renderItemInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;getItemInUseCount()I"))
    private int getItemInUseCountForRender(AbstractClientPlayer player) {
        int actualCount = player.getItemInUseCount();
        if (actualCount > 0 || !renderItemInUse || itemToRender == null) {
            return actualCount;
        }

        return itemToRender.getItemUseAction() == EnumAction.BLOCK ? 1 : actualCount;
    }

    @Inject(method = "updateEquippedItem", at = @At("HEAD"), cancellable = true)
    private void onUpdateEquippedItem(CallbackInfo ci) {
        if (cancelUpdate) {
            cancelUpdate = false;
            equippedProgress = 1.0F;
            prevEquippedProgress = 1.0f;
            ci.cancel();
        }
    }

    @Inject(method = "resetEquippedProgress", at = @At("HEAD"), cancellable = true)
    public void injectResetEquippedProgress(CallbackInfo ci) {
        if (cancelReset) {
            cancelReset = false;
            equippedProgress = 1.0F;
            prevEquippedProgress = 1.0f;
            ci.cancel();
        }
    }

    @Inject(method = "resetEquippedProgress2", at = @At("HEAD"), cancellable = true)
    public void injectResetEquippedProgress2(CallbackInfo ci) {
        if (cancelReset) {
            cancelReset = false;
            equippedProgress = 1.0F;
            prevEquippedProgress = 1.0f;
            ci.cancel();
        }
    }

    @Override
    public void setCancelUpdate(boolean cancel) {
        this.cancelUpdate = cancel;
    }

    @Override
    public void setCancelReset(boolean reset) {
        this.cancelReset = reset;
    }

    @Override
    public boolean isRenderItemInUse() {
        return renderItemInUse;
    }

    @Override
    public void setRenderItemInUse(boolean renderItemInUse) {
        this.renderItemInUse = renderItemInUse;
    }
}
