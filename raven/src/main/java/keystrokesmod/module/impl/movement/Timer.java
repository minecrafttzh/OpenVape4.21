package keystrokesmod.module.impl.movement;

import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;

public class Timer extends Module {
    private SliderSetting speed;

    public Timer() {
        super("Timer", category.movement);
        this.registerSetting(speed = new SliderSetting("Speed", 1.0D, 0.0D, 2.0D, 0.1D));
    }

    @Override
    public String getInfo() {
        return Utils.asWholeNum(speed.getInput());
    }

    public float getConfiguredSpeed() {
        return (float) speed.getInput();
    }

    @Override
    public void onEnable() {
        // If we start in local-freeze mode (speed == 0), keep world timer normal.
        if (Utils.nullCheck() && speed.getInput() <= 0.0D) {
            Utils.resetTimer();
        }
    }

    @Override
    public void onDisable() {
        Utils.resetTimer();
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            return;
        }

        float configuredSpeed = (float) speed.getInput();
        if (configuredSpeed > 0.0F) {
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = configuredSpeed;
        } else {
            // 0-speed mode uses local update skipping in MixinEntityPlayerSP.
            // Keep global timer at normal speed so world/entities continue updating.
            Utils.resetTimer();
        }
    }

    public static int consumeExtraLocalUpdatesForBaseTick() {
        return 0;
    }

    public static boolean shouldSkipBaseLocalUpdate() {
        Timer timer = keystrokesmod.module.ModuleManager.timer;
        if (timer == null || !timer.isEnabled()) {
            return false;
        }
        if (!Utils.nullCheck()) {
            return false;
        }
        return timer.speed.getInput() <= 0.0D;
    }
}
