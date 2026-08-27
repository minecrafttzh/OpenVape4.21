package keystrokesmod.module.impl.render;

import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class Radar extends Module {
    private ButtonSetting tracerLines;
    
    private int scale = 2;
    
    private static final int RECT_COLOR = new Color(0, 0, 0, 125).getRGB();
    
    public Radar() {
        super("Radar", category.render);
        this.registerSetting(tracerLines = new ButtonSetting("Show tracer lines", false));
    }

    @Override
    public void onUpdate() {
        this.scale = new ScaledResolution(mc).getScaleFactor();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        if (mc.currentScreen instanceof ClickGui) {
            return;
        }
        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }
        int x = 5;
        int y = 70;
        int rightX = x + 100;
        int bottomY = y + 100;
        Gui.drawRect(x, y, rightX, bottomY, RECT_COLOR);
        Gui.drawRect(x - 1, y - 1, rightX + 1, y, -1);
        Gui.drawRect(x - 1, bottomY, rightX + 1, bottomY + 1, -1);
        Gui.drawRect(x - 1, y, x, bottomY, -1);
        Gui.drawRect(rightX, y, rightX + 1, bottomY, -1);
        int playerIndicatorX = rightX / 2 + 3;
        int playerIndicatorY = y + 52;
        RenderUtils.drawPolygon(playerIndicatorX, playerIndicatorY, 5.0, 3, -1);
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * this.scale, mc.displayHeight - this.scale * 170, rightX * this.scale - this.scale * 5, this.scale * 100);
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player != mc.thePlayer && player.deathTime == 0) {
                if (AntiBot.isBot(player)) {
                    continue;
                }
                double distanceSquared = player.getDistanceSqToEntity(mc.thePlayer);
                if (distanceSquared > 360.0) {
                    continue;
                }
                double playerAngle = (mc.thePlayer.rotationYaw + Math.atan2(player.posX - mc.thePlayer.posX, player.posZ - mc.thePlayer.posZ) * 57.295780181884766) % 360.0;
                double scaledDistance = distanceSquared / 5.0;
                double xOffset = scaledDistance * Math.sin(Math.toRadians(playerAngle));
                double zOffset = scaledDistance * Math.cos(Math.toRadians(playerAngle));
                if (tracerLines.isToggled()) {
                    GL11.glPushMatrix();
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glEnable(GL11.GL_LINE_SMOOTH);
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glLineWidth(0.5f);
                    GL11.glColor3d(1.0, 1.0, 1.0);
                    GL11.glBegin(GL11.GL_LINES);
                    GL11.glVertex2d(playerIndicatorX, playerIndicatorY);
                    GL11.glVertex2d((double)playerIndicatorX - xOffset, (double)playerIndicatorY - zOffset);
                    GL11.glEnd();
                    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    GL11.glDisable(GL11.GL_LINE_SMOOTH);
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glPopMatrix();
                }
                RenderUtils.drawPolygon((double)playerIndicatorX - xOffset, (double)playerIndicatorY - zOffset, 3.0, 4, Color.red.getRGB());
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glPopMatrix();
    }
}
