package keystrokesmod.mixin.impl.client;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.SafeWalk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(GameSettings.class)
public class MixinGameSettings {

    /**
     * @author Strangers
     * @reason Overwrites the original isKeyDown method, used to fix not sneaking with SafeWalk on ViaForge
     */
    @Overwrite
    public static boolean isKeyDown(KeyBinding key) {
        SafeWalk safewalk = ModuleManager.safeWalk;
        if (key == Minecraft.getMinecraft().gameSettings.keyBindSneak && safewalk != null && safewalk.isEnabled() && safewalk.sneak.isToggled() && safewalk.isSneaking) {
            return true;
        }
        return key.getKeyCode() != 0 && (key.getKeyCode() < 0 ? Mouse.isButtonDown(key.getKeyCode() + 100) : Keyboard.isKeyDown(key.getKeyCode()));
    }

}