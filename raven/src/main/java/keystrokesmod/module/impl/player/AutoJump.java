package keystrokesmod.module.impl.player;

import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.module.Module;
import keystrokesmod.script.model.SimulatedPlayer;
import keystrokesmod.utility.Utils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class AutoJump extends Module {
    public AutoJump() {
        super("Auto Jump", category.player);
    }

    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!Utils.nullCheck() || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) return;
        if (!mc.thePlayer.onGround) return;

        SimulatedPlayer sim = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput);
        sim.tick();

        if (!sim.onGround && !e.isJump()) {
            e.setJump(true);
        }
    }
}
