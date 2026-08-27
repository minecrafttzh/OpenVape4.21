package keystrokesmod.module.impl.combat;

import keystrokesmod.Raven;
import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.Blink;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.CombatTargeting;
import keystrokesmod.utility.Utils;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

public class KnockbackDelay extends Module {

    private final SliderSetting distanceToTarget;
    private final SliderSetting chance;
    private final SliderSetting maximumDelay;
    private final ButtonSetting inAir;
    private final ButtonSetting lookingAtPlayer;
    private final ButtonSetting requireLeftMouse;
    private final ButtonSetting onlyWhitelistedItem;
    private final ItemListSetting whitelistedItems;

    private LagRequest inboundLagRequest;

    public KnockbackDelay() {
        super("Knockback Delay", category.combat);
        this.registerSetting(distanceToTarget = new SliderSetting("Distance to target", 6.0, 3.0, 12.0, 0.1));
        this.registerSetting(chance = new SliderSetting("Chance", "%", 100.0, 0.0, 100.0, 1.0));
        this.registerSetting(maximumDelay = new SliderSetting("Maximum delay", "ms", 200.0, 50.0, 1000.0, 10.0));
        this.registerSetting(new DescriptionSetting("Conditions"));
        this.registerSetting(inAir = new ButtonSetting("In air", true));
        this.registerSetting(lookingAtPlayer = new ButtonSetting("Looking at player", false));
        this.registerSetting(requireLeftMouse = new ButtonSetting("Require Left mouse", false, "Require mouse down"));
        this.registerSetting(onlyWhitelistedItem = new ButtonSetting(
                "Restrict held item",
                false,
                "Item whitelist",
                "Restrict to listed items"));
        this.registerSetting(whitelistedItems = new ItemListSetting("Whitelisted items"));
        this.closetModule = true;
    }

    @Override
    public void guiUpdate() {
        whitelistedItems.setVisible(onlyWhitelistedItem.isToggled(), this);
    }

    @Override
    public void onEnable() {
        if (blinksInbound()) {
            Utils.sendMessage("&cKnockback Delay conflicts with Blink inbound / both. Disable Blink or use outbound-only.");
            disable();
            return;
        }
        inboundLagRequest = null;
    }

    @Override
    public void onDisable() {
        flushInboundLagAndClear();
    }

    @Override
    public String getInfo() {
        return (int) maximumDelay.getInput() + "ms";
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onReceivePacketHigh(ReceivePacketEvent e) {
        if (!isEnabled() || e.isCanceled()) {
            return;
        }

        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            flushInboundLagAndClear();
            return;
        }

        if (!(e.getPacket() instanceof S12PacketEntityVelocity)) {
            return;
        }

        if (!Utils.nullCheck() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) e.getPacket();
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) {
            return;
        }

        if (conditionsFailureReason() != null) {
            return;
        }

        if (chance.getInput() < 100.0 && Math.random() * 100.0 >= chance.getInput()) {
            return;
        }

        if (isInboundSessionActive()) {
            return;
        }

        inboundLagRequest = new LagRequest(EnumLagDirection.ONLY_INBOUND, new ModuleBackedTimeout(this));
        Raven.lagHandler.requestLag(inboundLagRequest);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGameTick(GameTickEvent e) {
        if (!isEnabled()) {
            return;
        }

        if (!Utils.nullCheck() || mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
            flushInboundLagAndClear();
            return;
        }

        if (!isInboundSessionActive()) {
            return;
        }

        if (conditionsFailureReason() != null) {
            flushInboundLagAndClear();
            return;
        }

        Raven.lagHandler.releaseExpiredPackets(EnumLagDirection.INBOUND, (long) maximumDelay.getInput());
    }

    private boolean isInboundSessionActive() {
        return inboundLagRequest != null && !inboundLagRequest.getTimeout().isTimedOut();
    }

    private void flushInboundLagAndClear() {
        if (inboundLagRequest != null) {
            inboundLagRequest.getTimeout().forceTimeOut();
            inboundLagRequest = null;
        }
    }

    private String conditionsFailureReason() {
        double maxSq = distanceToTarget.getInput() * distanceToTarget.getInput();
        if (CombatTargeting.findTarget(maxSq) == null) {
            return "no target in range";
        }

        if (inAir.isToggled() && mc.thePlayer.onGround) {
            return "not in air";
        }

        if (lookingAtPlayer.isToggled() && CombatTargeting.getMouseOverTarget(maxSq) == null) {
            return "not looking at player";
        }

        if (requireLeftMouse.isToggled() && !Mouse.isButtonDown(0)) {
            return "LMB not held";
        }

        if (onlyWhitelistedItem.isToggled()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held == null || !whitelistedItems.matches(held)) {
                return "held item not whitelisted";
            }
        }

        return null;
    }

    private static boolean blinksInbound() {
        Blink blink = ModuleManager.blink;
        return blink != null && blink.isEnabled() && blink.delaysInboundPackets();
    }
}
