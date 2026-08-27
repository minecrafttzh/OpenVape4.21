package keystrokesmod.module.impl.movement;

import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.utility.Utils;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class NullMove extends Module {

    private boolean prevW;
    private boolean prevS;
    private boolean prevA;
    private boolean prevD;

    private int lastForwardSign;
    private int lastStrafeSign;

    public NullMove() {
        super("Null Move", category.movement, 0);
    }

    @Override
    public void onDisable() {
        this.prevW = false;
        this.prevS = false;
        this.prevA = false;
        this.prevD = false;
        this.lastForwardSign = 0;
        this.lastStrafeSign = 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!this.isEnabled() || !Utils.nullCheck()) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }

        boolean w = mc.gameSettings.keyBindForward.isKeyDown();
        boolean s = mc.gameSettings.keyBindBack.isKeyDown();
        boolean a = mc.gameSettings.keyBindLeft.isKeyDown();
        boolean d = mc.gameSettings.keyBindRight.isKeyDown();

        if (w && !this.prevW) {
            this.lastForwardSign = 1;
        }
        if (s && !this.prevS) {
            this.lastForwardSign = -1;
        }
        if (a && !this.prevA) {
            this.lastStrafeSign = 1;
        }
        if (d && !this.prevD) {
            this.lastStrafeSign = -1;
        }

        if (w && s) {
            e.setForward(this.lastForwardSign >= 0 ? 1.0F : -1.0F);
        }
        if (a && d) {
            e.setStrafe(this.lastStrafeSign >= 0 ? 1.0F : -1.0F);
        }

        this.prevW = w;
        this.prevS = s;
        this.prevA = a;
        this.prevD = d;
    }
}
