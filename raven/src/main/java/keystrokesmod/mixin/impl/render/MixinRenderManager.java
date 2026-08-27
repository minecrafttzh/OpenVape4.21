package keystrokesmod.mixin.impl.render;

import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.render.Freelook;
import keystrokesmod.utility.RotationUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(RenderManager.class)
public class MixinRenderManager {
    @Shadow
    private float playerViewX;
    @Shadow
    private float playerViewY;

    @Unique
    private float cachedPrevRotationPitch;
    @Unique
    private float cachedRotationPitch;

    @Inject(method = "renderEntityStatic", at = @At("HEAD"))
    public void renderEntityStaticPre(final Entity entity, final float n, final boolean b, final CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (entity instanceof EntityPlayerSP && PreMotionEvent.setRenderYaw()) {
            final EntityPlayerSP player = (EntityPlayerSP)entity;
            cachedRotationPitch = player.rotationPitch;
            cachedPrevRotationPitch = player.prevRotationPitch;
            player.prevRotationPitch = RotationUtils.prevRenderPitch;
            player.rotationPitch = RotationUtils.renderPitch;
        }
    }

    @Inject(method = "renderEntityStatic", at = @At("RETURN"))
    public void renderEntityStaticPost(final Entity entity, final float n, final boolean b, final CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (entity instanceof EntityPlayerSP && PreMotionEvent.setRenderYaw()) {
            final EntityPlayerSP player = (EntityPlayerSP)entity;
            player.prevRotationPitch = this.cachedPrevRotationPitch;
            player.rotationPitch = this.cachedRotationPitch;
        }
    }

    @Redirect(method = "cacheActiveRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RenderManager;playerViewX:F", opcode = Opcodes.PUTFIELD))
    private void freelookRedirectPlayerViewX(RenderManager rm, float value) {
        this.playerViewX = (ModuleManager.freelook != null && ModuleManager.freelook.isEnabled() && Freelook.perspectiveToggled) ? Freelook.cameraPitch : value;
    }

    @Redirect(method = "cacheActiveRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RenderManager;playerViewY:F", opcode = Opcodes.PUTFIELD))
    private void freelookRedirectPlayerViewY(RenderManager rm, float value) {
        this.playerViewY = (ModuleManager.freelook != null && ModuleManager.freelook.isEnabled() && Freelook.perspectiveToggled) ? Freelook.cameraYaw : value;
    }
}
