package keystrokesmod.module.impl.player;

import keystrokesmod.Raven;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;
import java.util.Set;

public class Blink extends Module {

    private static final String[] MODE_LABELS = new String[] { "Inbound", "Outbound", "Both" };

    private SliderSetting mode;
    private SliderSetting disableAfterMs;
    private ButtonSetting maxDuration;
    private ButtonSetting disableOnAttack;
    private ButtonSetting initialPosition;

    private Vec3 pos;
    private int color = new Color(0, 255, 0, 120).getRGB();
    private int blinkTicks;
    private long enableTime;

    public Blink() {
        super("Blink", category.player);
        this.registerSetting(mode = new SliderSetting("Mode", 1, MODE_LABELS));
        this.registerSetting(maxDuration = new ButtonSetting("Max duration", false));
        this.registerSetting(disableAfterMs = new SliderSetting("Disable after", "ms", 500.0, 50.0, 20000.0, 50.0));
        this.registerSetting(new DescriptionSetting("Disable on"));
        this.registerSetting(disableOnAttack = new ButtonSetting("Attack", false));
        this.registerSetting(initialPosition = new ButtonSetting("Show initial position", true));
    }

    @Override
    public void guiUpdate() {
        disableAfterMs.setVisible(maxDuration.isToggled(), this);
    }

    @Override
    public void onEnable() {
        pos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        blinkTicks = 0;
        enableTime = System.currentTimeMillis();
        Raven.lagHandler.requestLag(
                new LagRequest(
                        lagDirectionsForMode(),
                        new ModuleBackedTimeout(this)
                )
        );
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

    public boolean delaysInboundPackets() {
        int m = (int) mode.getInput();
        return m == 0 || m == 2;
    }

    @Override
    public String getInfo() {
        return String.valueOf(blinkTicks);
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        ++blinkTicks;
        if (!maxDuration.isToggled()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - enableTime;
        if (elapsed >= (int) disableAfterMs.getInput()) {
            this.disable();
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!this.isEnabled() || !disableOnAttack.isToggled() || !Utils.nullCheck()) {
            return;
        }
        if (event.entityPlayer != mc.thePlayer) {
            return;
        }
        this.disable();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || pos == null || !initialPosition.isToggled()) {
            return;
        }
        RenderUtils.drawPlayerBoundingBox(pos, color);
    }
}