package keystrokesmod.module.impl.movement;

import keystrokesmod.event.PostPlayerInputEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.ModuleUtils;
import keystrokesmod.utility.Utils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class BHop extends Module {
    public SliderSetting mode;
    public static SliderSetting speedSetting;
    private ButtonSetting liquidDisable;
    private ButtonSetting sneakDisable, jumpMoving;
    public ButtonSetting rotateYawOption, damageBoost, damageBoostRequireKey;
    public GroupSetting damageBoostGroup;
    public KeySetting damageBoostKey;
    public String[] modes = new String[]{"Strafe", "Ground", "9 tick", "8 tick", "7 tick"};
    public boolean hopping, lowhop, didMove, setRotation;

    public BHop() {
        super("BHop", Module.category.movement);
        this.registerSetting(mode = new SliderSetting("Mode", 0, modes));
        this.registerSetting(speedSetting = new SliderSetting("Speed", 2.0, 0.8, 1.2, 0.01));
        this.registerSetting(liquidDisable = new ButtonSetting("Disable in liquid", true));
        this.registerSetting(sneakDisable = new ButtonSetting("Disable while sneaking", true));
        this.registerSetting(jumpMoving = new ButtonSetting("Only jump when moving", true));
        this.registerSetting(rotateYawOption = new ButtonSetting("Rotate yaw", false));
        this.registerSetting(damageBoostGroup = new GroupSetting("Damage boost"));
        this.registerSetting(damageBoost = new ButtonSetting(damageBoostGroup, "Enable", false));
        this.registerSetting(damageBoostRequireKey = new ButtonSetting(damageBoostGroup,"Require key", false));
        this.registerSetting(damageBoostKey = new KeySetting(damageBoostGroup,"Enable key", 51));
    }

    public void guiUpdate() {
        this.damageBoostKey.setVisible(damageBoostRequireKey.isToggled(), this);
    }

    @Override
    public String getInfo() {
        return modes[(int) mode.getInput()];
    }

    @SubscribeEvent
    public void onPostPlayerInput(PostPlayerInputEvent e) {
        if (!mc.thePlayer.onGround || mc.thePlayer.capabilities.isFlying) {
            return;
        }
        if (hopping) {
            mc.thePlayer.movementInput.jump = false;
        }
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        if (((mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) && liquidDisable.isToggled()) || (mc.thePlayer.isSneaking() && sneakDisable.isToggled())) {
            return;
        }
        if (ModuleManager.longJump.function) {
            return;
        }
        if (mode.getInput() >= 1) {
            if (mc.thePlayer.onGround && (!jumpMoving.isToggled() || Utils.isMoving())) {
                if (mc.thePlayer.moveForward <= -0.5 && mc.thePlayer.moveStrafing == 0 && KillAura.target == null && !Utils.noSlowingBackWithBow() && !mc.thePlayer.isCollidedHorizontally) {
                    setRotation = true;
                }
                mc.thePlayer.jump();
                double speed = (speedSetting.getInput() - 0.52);
                double speedModifier = speed;
                final int speedAmplifier = Utils.getSpeedAmplifier();
                switch (speedAmplifier) {
                    case 1:
                        speedModifier = speed + 0.02;
                        break;
                    case 2:
                        speedModifier = speed + 0.04;
                        break;
                    case 3:
                        speedModifier = speed + 0.1;
                        break;
                }

                if (Utils.isMoving() && !Utils.noSlowingBackWithBow()) {
                    Utils.setSpeed(speedModifier - Utils.randomizeDouble(0.0003, 0.0001));
                    didMove = true;
                }
                hopping = true;
            }
            if (mc.thePlayer.moveForward <= 0.5 && hopping) {
                ModuleUtils.handleSlow();
            }
            if (!mc.thePlayer.onGround) {
                hopping = false;
            }
        }
        switch ((int) mode.getInput()) {
            case 0:
                if (Utils.isMoving()) {
                    if (mc.thePlayer.onGround) {
                        mc.thePlayer.jump();
                    }
                    mc.thePlayer.setSprinting(true);
                    Utils.setSpeed(Utils.getHorizontalSpeed() + 0.005 * speedSetting.getInput());
                    hopping = true;
                    break;
                }
                break;
        }
    }

    public void onDisable() {
        hopping = false;
    }
}