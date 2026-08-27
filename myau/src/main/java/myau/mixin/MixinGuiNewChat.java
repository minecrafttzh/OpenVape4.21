package myau.mixin;

import myau.module.modules.RenderFixes;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@SideOnly(Side.CLIENT)
@Mixin(value = {GuiNewChat.class}, priority = 9999)
public abstract class MixinGuiNewChat {
    @Shadow
    @Final
    private List<ChatLine> drawnChatLines;

    @Shadow
    private int scrollPos;

    @Shadow
    private boolean isScrolled;

    @Inject(method = {"drawChat"}, at = @At("HEAD"), cancellable = true)
    private void myau$renderModernChat(int updateCounter, CallbackInfo callbackInfo) {
        if (RenderFixes.renderChat((GuiNewChat) (Object) this, updateCounter, this.drawnChatLines, this.scrollPos, this.isScrolled)) {
            callbackInfo.cancel();
        }
    }

    @ModifyVariable(method = {"getChatComponent"}, at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int myau$adjustChatComponentMouseX(int mouseX) {
        return RenderFixes.adjustChatMouseX(mouseX);
    }

    @ModifyVariable(method = {"getChatComponent"}, at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private int myau$adjustChatComponentMouseY(int mouseY) {
        return RenderFixes.adjustChatMouseY(mouseY);
    }
}
