package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.render.DamageTint;
import keystrokesmod.module.impl.render.MobESP;
import keystrokesmod.module.impl.other.NameHider;
import keystrokesmod.module.impl.render.PlayerESP;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

@SideOnly(Side.CLIENT)
@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> extends Render<T> {  // credit: pablolnmak
    @Shadow
    protected boolean renderOutlines;

    @Shadow
    protected abstract void unsetBrightness();

    protected MixinRendererLivingEntity(RenderManager renderManager) {
        super(renderManager);
    }

    @Unique
    private boolean shouldRender() {
        return ModuleManager.playerESP != null && ModuleManager.playerESP.isEnabled() && ModuleManager.playerESP.outline.isToggled();
    }

    @Redirect(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;setScoreTeamColor(Lnet/minecraft/entity/EntityLivingBase;)Z"))
    private boolean setOutlineColor(RendererLivingEntity instance, T entityLivingBaseIn) {
        int i = 16777215;
        boolean drawOutline = shouldRender() && ((entityLivingBaseIn != Minecraft.getMinecraft().thePlayer && !AntiBot.isBot(entityLivingBaseIn)) || (entityLivingBaseIn == Minecraft.getMinecraft().thePlayer && ModuleManager.playerESP.renderSelf.isToggled()));

        if (!drawOutline || ModuleManager.playerESP.teamColor.isToggled())
        {
            if (entityLivingBaseIn instanceof EntityPlayer)
            {
                ScorePlayerTeam scoreplayerteam = (ScorePlayerTeam)entityLivingBaseIn.getTeam();

                if (scoreplayerteam != null)
                {
                    String s = FontRenderer.getFormatFromString(scoreplayerteam.getColorPrefix());

                    if (s.length() >= 2)
                    {
                        i = this.getFontRendererFromRenderManager().getColorCode(s.charAt(1));
                    }
                }
            }
        }
        else if (ModuleManager.playerESP.rainbow.isToggled()) {
            i = Utils.getChroma(2L, 0L);
        }
        else {
            i = ModuleManager.playerESP.color.getColor();
        }

        if (drawOutline && ModuleManager.playerESP.redOnDamage.isToggled() && entityLivingBaseIn.hurtTime != 0) {
            i = Color.RED.getRGB();
        }

        if (drawOutline) {
            return false;
        }

        float f1 = (float)(i >> 16 & 255) / 255.0F;
        float f2 = (float)(i >> 8 & 255) / 255.0F;
        float f = (float)(i & 255) / 255.0F;
        GlStateManager.disableLighting();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.color(f1, f2, f, 1.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        return true;
    }

    @ModifyVariable(method = "renderModel", at = @At(value = "STORE"), ordinal = 0)
    private boolean modifyInvisibleFlag(boolean flag) {
        return flag || (this.renderOutlines && shouldRender() && ModuleManager.playerESP.showInvis.isToggled());
    }

    @Inject(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At("HEAD"), cancellable = true)
    private void suppressNameDuringOutlinePass(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (PlayerESP.renderingOutlinePass || MobESP.renderingOutlinePass) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    private void mobEsp$chamsPre(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if (!(entity instanceof EntityPlayer)) {
            MobESP.onRenderMobPre(entity);
        }
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    private void mobEsp$chamsPost(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if (!(entity instanceof EntityPlayer)) {
            MobESP.onRenderMobPost();
        }
    }

    @Unique
    private EntityLivingBase nameHider$renderNameEntity;

    @Inject(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("HEAD"))
    private void nameHider$captureRenderEntity(T entity, double x, double y, double z, CallbackInfo ci) {
        this.nameHider$renderNameEntity = entity;
    }

    @ModifyVariable(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At(value = "STORE"), ordinal = 0)
    private String nameHider$hideRenderedName(String displayName) {
        if (displayName == null || ModuleManager.nameHider == null || !ModuleManager.nameHider.isEnabled()) {
            return displayName;
        }

        if (this.nameHider$renderNameEntity instanceof EntityPlayer) {
            EntityPlayer entityPlayer = (EntityPlayer) this.nameHider$renderNameEntity;
            return NameHider.getPlayerDisplayName(entityPlayer, entityPlayer.getDisplayName()).getFormattedText();
        }

        return NameHider.getFakeName(displayName);
    }

    @Inject(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("RETURN"))
    private void nameHider$clearRenderEntity(T entity, double x, double y, double z, CallbackInfo ci) {
        this.nameHider$renderNameEntity = null;
    }

    @Unique
    private EntityLivingBase damageTint$entity;

    @Inject(method = "setBrightness", at = @At("HEAD"))
    private void damageTint$captureEntity(T entitylivingbaseIn, float partialTicks, boolean combineTextures, CallbackInfoReturnable<Boolean> cir) {
        this.damageTint$entity = entitylivingbaseIn;
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 0))
    private float damageTint$modifyRed(float f) {
        if (DamageTint.instance != null) {
            return DamageTint.instance.color.getRed() / 255.0f;
        }
        return f;
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 1))
    private float damageTint$modifyGreen(float f) {
        if (DamageTint.instance != null) {
            return DamageTint.instance.color.getGreen() / 255.0f;
        }
        return f;
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 2))
    private float damageTint$modifyBlue(float f) {
        if (DamageTint.instance != null) {
            return DamageTint.instance.color.getBlue() / 255.0f;
        }
        return f;
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 3))
    private float damageTint$modifyAlpha(float f) {
        if (DamageTint.instance != null) {
            return DamageTint.computeAlpha(this.damageTint$entity);
        }
        return f;
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    private void damageTint$restoreRenderState(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if (DamageTint.instance == null) {
            return;
        }

        // Damage tint piggybacks on RendererLivingEntity#setBrightness, so restore the
        // same texture combiner state vanilla expects after a brightness pass.
        this.unsetBrightness();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.damageTint$entity = null;
    }
}
