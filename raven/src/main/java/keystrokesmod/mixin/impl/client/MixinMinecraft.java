package keystrokesmod.mixin.impl.client;

import keystrokesmod.event.*;
import net.minecraft.util.MovingObjectPosition;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.BedAura;
import keystrokesmod.module.impl.render.Freelook;
import keystrokesmod.module.impl.player.FastMine;
import org.objectweb.asm.Opcodes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V", shift = At.Shift.BEFORE))
    public void onBeforeGetMouseOver(CallbackInfo ci) {
        RotationHelper.get().updateServerRotations();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V", shift = At.Shift.AFTER))
    public void onRunTickMouseOver(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PostMouseSelectionEvent());
    }

    @Inject(method = "runTick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/settings/GameSettings;chatVisibility:Lnet/minecraft/entity/player/EntityPlayer$EnumChatVisibility;"))
    private void injectBeforeChatVisibility(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PrePlayerInteractEvent());
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", ordinal = 2))
    private void onRunTick(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PreInputEvent());
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    public void onRunGameLoop(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new RunGameLoopEvent());
    }

    @Inject(method = "clickMouse", at = @At("HEAD"), cancellable = true)
    public void injectClickMouse(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        MovingObjectPosition mop = mc.objectMouseOver;
        PreAttackEvent preAttack = new PreAttackEvent(mop);
        MinecraftForge.EVENT_BUS.post(preAttack);
        if (preAttack.isCanceled()) {
            ci.cancel();
            return;
        }
        MinecraftForge.EVENT_BUS.post(new ClickMouseEvent());
    }

    @Inject(method = "rightClickMouse", at = @At("HEAD"), cancellable = true)
    public void injectRightClickMouse(CallbackInfo ci) {
        RightClickMouseEvent event = new RightClickMouseEvent();
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    public void onRunTickStart(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new GameTickEvent());
    }

    @Inject(
        method = "runTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/profiler/Profiler;startSection(Ljava/lang/String;)V",
            ordinal = 0,
            shift = At.Shift.BEFORE
        )
    )
    public void onRunTickAfterRightClickDelay(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new RightClickDelayTickEvent());
    }

    @Inject(
        method = "runTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;sendClickBlockToController(Z)V",
            shift = At.Shift.AFTER
        )
    )
    private void raven$fastMinePassiveBlockHitDelay(CallbackInfo ci) {
        BedAura bedAura = ModuleManager.bedAura;
        if (bedAura != null && bedAura.shouldOverrideFastMine()) {
            return;
        }
        FastMine fm = ModuleManager.fastMine;
        if (fm != null) {
            fm.tickPassiveBlockHitDecay((Minecraft) (Object) this);
        }
    }

    @Inject(method = "displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V", at = @At("HEAD"))
    public void onDisplayGuiScreen(GuiScreen guiScreen, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        GuiScreen previousGui = mc.currentScreen;
        GuiScreen setGui = guiScreen;
        boolean opened = setGui != null;
        if (!opened) {
            setGui = previousGui;
        }

        GuiUpdateEvent event = new GuiUpdateEvent(setGui, opened);
        MinecraftForge.EVENT_BUS.post(event);
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/InventoryPlayer;changeCurrentItem(I)V"))
    public void changeCurrentItem(InventoryPlayer inventoryPlayer, int slot) {
        PreSlotScrollEvent event = new PreSlotScrollEvent(slot, inventoryPlayer.currentItem);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return;
        }
        inventoryPlayer.changeCurrentItem(slot);
    }

    @Redirect(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;thirdPersonView:I", opcode = Opcodes.PUTFIELD))
    private void onSetThirdPersonView(GameSettings gameSettings, int value) {
        if (ModuleManager.freelook != null && Freelook.perspectiveToggled) {
            ModuleManager.freelook.resetPerspective();
        } else {
            gameSettings.thirdPersonView = value;
        }
    }

    @Redirect(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I", opcode = Opcodes.PUTFIELD))
    private void onSetCurrentItem(InventoryPlayer inventoryPlayer, int slot) {
        SlotUpdateEvent e = new SlotUpdateEvent(slot);
        MinecraftForge.EVENT_BUS.post(e);
        if (e.isCanceled()) {
            return;
        }
        inventoryPlayer.currentItem = slot;
    }

}
