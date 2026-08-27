package keystrokesmod.module.impl.movement;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;

public class Sprint extends Module {

    private final ButtonSetting allowUsingItem;
    private final ButtonSetting allowBackwards;
    private final ButtonSetting allowSideways;
    private final ButtonSetting allowInInventory;

    public Sprint() {
        super("Sprint", category.movement, 0);
        this.registerSetting(new DescriptionSetting("Allow while"));
        this.registerSetting(allowUsingItem = new ButtonSetting("Using item", false));
        this.registerSetting(allowBackwards = new ButtonSetting("Backwards", false));
        this.registerSetting(allowSideways = new ButtonSetting("Sideways", false));
        this.registerSetting(allowInInventory = new ButtonSetting("In inventory", false));
        this.closetModule = true;
    }

    @Override
    public void onDisable() {
        if (Utils.nullCheck()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            return;
        }
        boolean inGame = mc.inGameHasFocus;
        boolean inInv = allowInInventory.isToggled() && (mc.currentScreen instanceof GuiInventory || mc.currentScreen instanceof GuiChest);
        if (!inGame && !inInv) {
            return;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
    }

    public boolean allowWhileUsingItem() {
        return this.isEnabled() && allowUsingItem.isToggled();
    }

    public boolean allowWhileBackwards() {
        return this.isEnabled() && allowBackwards.isToggled();
    }

    public boolean allowWhileSideways() {
        return this.isEnabled() && allowSideways.isToggled();
    }
}
