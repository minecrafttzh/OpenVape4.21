package keystrokesmod.module.impl.movement;

import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;

public class Boost extends Module {
    private SliderSetting multiplier;
    private SliderSetting time;

    private int ticks = 0;
    private boolean timerDisabled = false;

    public Boost() {
        super("Boost", category.movement);
        this.registerSetting(new DescriptionSetting("20 ticks are in 1 second"));
        this.registerSetting(multiplier = new SliderSetting("Multiplier", "x", 2.0D, 1.0D, 3.0D, 0.05D));
        this.registerSetting(time = new SliderSetting("Time", " tick", 15.0D, 1.0D, 80.0D, 1.0D));
    }

    public void onEnable() {
        if (ModuleManager.timer.isEnabled()) {
            this.timerDisabled = true;
            ModuleManager.timer.disable();
        }

    }

    @Override
    public void onDisable() {
        this.ticks = 0;
        if (((IAccessorMinecraft) mc).getTimer().timerSpeed != 1.0F) {
            Utils.resetTimer();
        }

        if (this.timerDisabled) {
            ModuleManager.timer.enable();
        }

        this.timerDisabled = false;
    }

    @Override
    public void onUpdate() {
        if (this.ticks == 0) {
            this.ticks = mc.thePlayer.ticksExisted;
        }

        ((IAccessorMinecraft) mc).getTimer().timerSpeed = (float) multiplier.getInput();
        if ((double) this.ticks == (double) mc.thePlayer.ticksExisted - time.getInput()) {
            Utils.resetTimer();
            this.disable();
        }

    }
}