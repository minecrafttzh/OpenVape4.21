package keystrokesmod.module.impl.render;

import keystrokesmod.Raven;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;

public class Tracers extends Module {
    public ButtonSetting showInvis;
    public ColorSetting color;
    public ButtonSetting rainbow;
    public SliderSetting lineWidth;

    private boolean viewBobbingEnabled;
    private final ArrayList<Entity> trackedEntities = new ArrayList<>();
    private int trackedEntityCount = 0;

    public Tracers() {
        super("Tracers", category.render);
        this.registerSetting(showInvis = new ButtonSetting("Show invis", true));
        this.registerSetting(lineWidth = new SliderSetting("Line Width", 1.0D, 1.0D, 5.0D, 1.0D));
        this.registerSetting(color = new ColorSetting("Color", 0, 255, 0));
        this.registerSetting(rainbow = new ButtonSetting("Rainbow", false));
    }

    @Override
    public void onEnable() {
        this.viewBobbingEnabled = mc.gameSettings.viewBobbing;
        if (this.viewBobbingEnabled) {
            mc.gameSettings.viewBobbing = false;
        }
    }

    @Override
    public void onDisable() {
        mc.gameSettings.viewBobbing = this.viewBobbingEnabled;
    }

    @Override
    public void onUpdate() {
        if (mc.gameSettings.viewBobbing) {
            mc.gameSettings.viewBobbing = false;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        updateTrackedEntities();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || trackedEntityCount == 0) {
            return;
        }
        int rgb = rainbow.isToggled() ? Utils.getChroma(2L, 0L) : color.getColor();
        for (int i = 0; i < trackedEntityCount; i++) {
            Entity entity = trackedEntities.get(i);
            if (entity == null) {
                continue;
            }
            RenderUtils.drawTracerLine(entity, rgb, (float) lineWidth.getInput(), e.partialTicks);
        }
    }

    private void updateTrackedEntities() {
        trackedEntityCount = 0;
        if (!Utils.nullCheck() || mc.theWorld == null) {
            return;
        }

        if (Raven.DEBUG) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (entity instanceof EntityLivingBase && entity != mc.thePlayer) {
                    addTrackedEntity(entity);
                }
            }
            return;
        }

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer) {
                continue;
            }
            if (player.deathTime != 0) {
                continue;
            }
            if (!showInvis.isToggled() && player.isInvisible()) {
                continue;
            }
            if (AntiBot.isBot(player)) {
                continue;
            }
            addTrackedEntity(player);
        }
    }

    private void addTrackedEntity(Entity entity) {
        if (trackedEntityCount >= trackedEntities.size()) {
            trackedEntities.add(entity);
        }
        else {
            trackedEntities.set(trackedEntityCount, entity);
        }
        trackedEntityCount++;
    }
}
