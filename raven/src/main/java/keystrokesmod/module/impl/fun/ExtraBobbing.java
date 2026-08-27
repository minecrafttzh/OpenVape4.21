package keystrokesmod.module.impl.fun;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;

public class ExtraBobbing extends Module {
    public SliderSetting level;

    private boolean viewBobbingEnabled;

    public ExtraBobbing() {
        super("Extra Bobbing", category.fun);
        this.registerSetting(level = new SliderSetting("Level", 1.0D, 0.0D, 8.0D, 0.1D));
    }

    @Override
    public void onEnable() {
        this.viewBobbingEnabled = mc.gameSettings.viewBobbing;
        if (!this.viewBobbingEnabled) {
            mc.gameSettings.viewBobbing = true;
        }

    }

    @Override
    public void onDisable() {
        mc.gameSettings.viewBobbing = this.viewBobbingEnabled;
    }

    @Override
    public void onUpdate() {
        if (!mc.gameSettings.viewBobbing) {
            mc.gameSettings.viewBobbing = true;
        }

        if (mc.thePlayer.movementInput.moveForward != 0.0F || mc.thePlayer.movementInput.moveStrafe != 0.0F) {
            mc.thePlayer.cameraYaw = (float) ((double) mc.thePlayer.cameraYaw + level.getInput() / 2.0D);
        }
    }
}