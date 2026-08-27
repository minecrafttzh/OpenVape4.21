package keystrokesmod.module.impl.combat;

import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class WTap extends Module {
    private SliderSetting delayBetweenReset;
    private SliderSetting delayUntilReset;
    private SliderSetting chance;
    private ButtonSetting playersOnly;

    private long pendingResetAtMs;
    private long lastResetStartMs;
    private boolean waitingForSprintRestart;
    private boolean wasSprinting;

    public static boolean stopSprint = false;

    public WTap() {
        super("WTap", category.combat);
        this.registerSetting(chance = new SliderSetting("Chance", "%", 100, 0, 100, 1));
        this.registerSetting(delayBetweenReset = new SliderSetting("Delay between reset", "ms", 300, 0, 1000, 10));
        this.registerSetting(delayUntilReset = new SliderSetting("Delay until reset", "ms", 150, 0, 1000, 10));
        this.registerSetting(playersOnly = new ButtonSetting("Players only", true));
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        pendingResetAtMs = 0L;
        lastResetStartMs = 0L;
        waitingForSprintRestart = false;
        wasSprinting = false;
        stopSprint = false;
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck() || mc.thePlayer.isDead) {
            pendingResetAtMs = 0L;
            waitingForSprintRestart = false;
            wasSprinting = false;
            stopSprint = false;
            return;
        }

        long now = System.currentTimeMillis();
        boolean sprintingNow = mc.thePlayer.isSprinting();

        if (waitingForSprintRestart && sprintingNow && !wasSprinting) {
            lastResetStartMs = now;
            waitingForSprintRestart = false;
        }

        if (pendingResetAtMs > 0L && now >= pendingResetAtMs) {
            stopSprint = true;
            pendingResetAtMs = 0L;
            waitingForSprintRestart = true;
        }

        wasSprinting = sprintingNow;
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!Utils.nullCheck() || event.entityPlayer != mc.thePlayer || !mc.thePlayer.isSprinting()) {
            return;
        }
        if (chance.getInput() == 0) {
            return;
        }
        if (playersOnly.isToggled()) {
            if (!(event.target instanceof EntityPlayer)) {
                return;
            }
            if (AntiBot.isBot(event.target)) {
                return;
            }
        }
        else if (!(event.target instanceof EntityLivingBase)) {
            return;
        }
        if (((EntityLivingBase)event.target).deathTime != 0) {
            return;
        }

        if (pendingResetAtMs > 0L) {
            return;
        }

        long currentMs = System.currentTimeMillis();
        long betweenResetDelay = (long) delayBetweenReset.getInput();
        if (lastResetStartMs > 0L && currentMs - lastResetStartMs < betweenResetDelay) {
            return;
        }

        if (chance.getInput() != 100.0D) {
            double ch = Math.random();
            if (ch >= chance.getInput() / 100.0D) {
                return;
            }
        }

        pendingResetAtMs = currentMs + (long) delayUntilReset.getInput();
    }

    @Override
    public void onDisable() {
        pendingResetAtMs = 0L;
        lastResetStartMs = 0L;
        waitingForSprintRestart = false;
        wasSprinting = false;
        stopSprint = false;
    }
}