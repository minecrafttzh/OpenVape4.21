package keystrokesmod.module.impl.player;

import java.util.Set;

import keystrokesmod.Raven;
import keystrokesmod.event.GameTickEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

public class FakeLag extends Module {

    private static final String[] MODE_LABELS = new String[] { "Inbound", "Outbound", "Both" };

    private final SliderSetting mode;
    private final SliderSetting packetDelaySlider;
    private int appliedMode = -1;
    private long appliedDelayMs = -1;
    private @Nullable LagRequest activeLagRequest;

    public FakeLag() {
        super("Fake Lag", category.player, 0);
        this.registerSetting(mode = new SliderSetting("Mode", 1, MODE_LABELS));
        this.registerSetting(packetDelaySlider = new SliderSetting("Packet delay", "ms", 0.0, 0.0, 1500.0, 20.0));
    }

    @Override
    public String getInfo() {
        return (int) packetDelaySlider.getInput() + "ms";
    }

    @Override
    public void guiUpdate() {
        if (!isEnabled()) {
            return;
        }
        if (packetDelaySlider.getInput() <= 0) {
            disable();
            return;
        }
        int m = (int) mode.getInput();
        long d = (long) packetDelaySlider.getInput();
        if (m != appliedMode || d != appliedDelayMs) {
            appliedMode = m;
            appliedDelayMs = d;
            rebindLagRequest();
        }
    }

    private void rebindLagRequest() {
        if (activeLagRequest != null) {
            activeLagRequest.getTimeout().forceTimeOut();
        }
        activeLagRequest = new LagRequest(lagDirectionsForMode(), new ModuleBackedTimeout(this));
        Raven.lagHandler.requestLag(activeLagRequest);
    }

    private Set<EnumLagDirection> lagDirectionsForMode() {
        switch ((int) mode.getInput()) {
            case 0:
                return EnumLagDirection.ONLY_INBOUND;
            case 2:
                return EnumLagDirection.BIDIRECTIONAL;
            case 1:
            default:
                return EnumLagDirection.ONLY_OUTBOUND;
        }
    }

    @Override
    public void onEnable() {
        if (mc.isSingleplayer()) {
            Utils.sendMessage("&cFake lag cannot be enabled in singleplayer.");
            this.disable();
            return;
        }
        if (ModuleManager.blink.isEnabled()) {
            Utils.sendMessage("&cCannot use fake lag with blink!");
            this.disable();
            return;
        }
        appliedMode = (int) mode.getInput();
        appliedDelayMs = (long) packetDelaySlider.getInput();
        rebindLagRequest();
    }

    @Override
    public void onDisable() {
        if (activeLagRequest != null) {
            activeLagRequest.getTimeout().forceTimeOut();
            activeLagRequest = null;
        }
        appliedMode = -1;
        appliedDelayMs = -1;
    }

    @SubscribeEvent
    public void onGameTick(GameTickEvent e) {
        if (!isEnabled()) {
            return;
        }
        if (!Utils.nullCheck() || mc.theWorld == null) {
            this.disable();
            return;
        }
        long delayMs = (long) packetDelaySlider.getInput();
        if (delayMs <= 0) {
            return;
        }
        Set<EnumLagDirection> directions = lagDirectionsForMode();
        if (directions.contains(EnumLagDirection.INBOUND)) {
            Raven.lagHandler.releaseExpiredPackets(EnumLagDirection.INBOUND, delayMs);
        }
        if (directions.contains(EnumLagDirection.OUTBOUND)) {
            Raven.lagHandler.releaseExpiredPackets(EnumLagDirection.OUTBOUND, delayMs);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        if (mc.theWorld == null && isEnabled()) {
            this.disable();
        }
    }
}
