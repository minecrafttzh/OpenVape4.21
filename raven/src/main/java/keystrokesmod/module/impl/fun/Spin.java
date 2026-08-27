package keystrokesmod.module.impl.fun;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;

public class Spin extends Module {
    public SliderSetting rotation;
    public SliderSetting speed;
    private float yaw;

    public Spin() {
        super("Spin", category.fun);
        this.registerSetting(rotation = new SliderSetting("Rotation", 360.0D, 30.0D, 360.0D, 1.0D));
        this.registerSetting(speed = new SliderSetting("Speed", 25.0D, 1.0D, 60.0D, 1.0D));
    }

    @Override
    public void onEnable() {
        this.yaw = mc.thePlayer.rotationYaw;
    }

    @Override
    public void onDisable() {
        this.yaw = 0.0F;
    }

    @Override
    public void onUpdate() {
        double left = (double) this.yaw + rotation.getInput() - (double) mc.thePlayer.rotationYaw;
        if (left < speed.getInput()) {
            mc.thePlayer.rotationYaw = (float) ((double) mc.thePlayer.rotationYaw + left);
            this.disable();
        }
        else {
            mc.thePlayer.rotationYaw = (float) ((double) mc.thePlayer.rotationYaw + speed.getInput());
            if ((double) mc.thePlayer.rotationYaw >= (double) this.yaw + rotation.getInput()) {
                this.disable();
            }
        }

    }
}