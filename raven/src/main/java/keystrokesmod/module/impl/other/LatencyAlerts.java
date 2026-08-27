package keystrokesmod.module.impl.other;

import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.event.HoverEvent;
import net.minecraft.network.Packet;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class LatencyAlerts extends Module {
    private SliderSetting interval;
    private SliderSetting highLatency;
    private ButtonSetting ignoreLimbo;

    private long lastPacketTime = 0L;
    private long lastAlert = 0L;

    private Packet<?> lastPacket = null;

    public LatencyAlerts() {
        super("Latency Alerts", category.other);
        this.registerSetting(new DescriptionSetting("Detects packet loss."));
        this.registerSetting(interval = new SliderSetting("Alert interval", " second", 3.0, 0.0, 5.0, 0.1));
        this.registerSetting(highLatency = new SliderSetting("High latency", " second", 0.5, 0.1, 5.0, 0.1));
        this.registerSetting(ignoreLimbo = new ButtonSetting("Ignore limbo", true));
        this.closetModule = true;
    }

    @Override
    public void onDisable() {
        this.lastPacketTime = 0;
        this.lastAlert = 0;
        this.lastPacket = null;
    }

    @SubscribeEvent
    public void onPacketReceive(ReceivePacketEvent e) {
        this.lastPacketTime = System.currentTimeMillis();
        this.lastPacket = e.getPacket();
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            this.lastPacketTime = System.currentTimeMillis();
            this.lastAlert = System.currentTimeMillis();
            this.lastPacket = null;
            return;
        }

        if (mc.isSingleplayer() || (this.ignoreLimbo.isToggled() && inLimbo())) {
            this.lastPacketTime = System.currentTimeMillis();
            this.lastAlert = System.currentTimeMillis();
            return;
        }
        long currentMs = System.currentTimeMillis();
        if (currentMs - this.lastPacketTime >= this.highLatency.getInput() * 1000 && currentMs - this.lastAlert >= this.interval.getInput() * 1000) {
            String msSinceLastPacket = String.valueOf(Math.abs(System.currentTimeMillis() - this.lastPacketTime));

            ChatComponentText component = new ChatComponentText(Utils.formatColor("&7[&dR&7]&r &7Packet loss detected: "));
            ChatStyle style = new ChatStyle();

            String packetName = this.lastPacket == null ? "Unknown" : this.lastPacket.getClass().getSimpleName();
            String contents = "&7Last packet: &b" + packetName;

            String timeString = new SimpleDateFormat("h:mm:ss a").format(new Date(this.lastPacketTime));
            contents += "\n&7Received at: &b" + timeString;

            style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(Utils.formatColor(contents))));
            component.appendSibling(new ChatComponentText(Utils.formatColor("&c" + msSinceLastPacket)).setChatStyle(style));
            component.appendSibling(new ChatComponentText(Utils.formatColor("&7ms")));
            mc.thePlayer.addChatMessage(component);

            this.lastAlert = System.currentTimeMillis();
        }
    }

    @Override
    public void onEnable() {
        this.lastPacketTime = System.currentTimeMillis();
    }

    public boolean inLimbo() {
        if (!Utils.nullCheck()) {
            return false;
        }
        List<String> scoreboard = Utils.getSidebarLines();
        if (scoreboard.isEmpty()) {
            return mc.theWorld.provider.getDimensionName().equals("The End");
        }
        return false;
    }
}
