package keystrokesmod.module.impl.combat;

import keystrokesmod.Raven;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.event.RightClickMouseEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.event.UseItemEvent;
import keystrokesmod.utility.CombatTargeting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

public class Autoblock extends Module {
    private final SliderSetting range;
    private final SliderSetting maxHurtTimeMs;
    private final SliderSetting maxHoldMs;

    private final ButtonSetting requireLmb;
    private final ButtonSetting requireRmb;
    private final ButtonSetting onlyWhenDamaged;
    private final ButtonSetting ignoreTeammates;

    private final SliderSetting lagChance;
    private final SliderSetting lagMaxDuration;
    private final ButtonSetting preventDelayAttacks;
    private final ButtonSetting blockAgainImmediately;
    private final ButtonSetting forceBlockAnimation;

    private boolean isBlocking;
    private boolean manualBlock;
    private int blockStartTick = -1;
    private EntityPlayer currentTarget;
    private int lastSelfHurtTime;

    private boolean isLagging;
    private int lagStartTick = -1;
    private LagRequest outboundLag;

    private int tickCounter;

    public Autoblock() {
        super("Auto Block", category.combat);

        this.registerSetting(range = new SliderSetting("Range", 4.0, 2.0, 6.0, 0.1));
        this.registerSetting(maxHurtTimeMs = new SliderSetting("Maximum Hurt Time", "ms", 200, 50, 500, 50));
        this.registerSetting(maxHoldMs = new SliderSetting("Maximum Hold Time", "ms", 150, 50, 500, 50));

        this.registerSetting(new DescriptionSetting("Lag"));
        this.registerSetting(lagChance = new SliderSetting("Lag Chance", "%", 100, 0, 100, 5));
        this.registerSetting(lagMaxDuration = new SliderSetting("Lag Max Duration", "ms", 200, 50, 500, 50));
        this.registerSetting(preventDelayAttacks = new ButtonSetting("Prevent delaying attacks", true));
        this.registerSetting(blockAgainImmediately = new ButtonSetting("Block again immediately", true));
        this.registerSetting(forceBlockAnimation = new ButtonSetting("Force block animation", true));

        this.registerSetting(new DescriptionSetting("Conditions"));
        this.registerSetting(requireLmb = new ButtonSetting("Require Left mouse", true));
        this.registerSetting(requireRmb = new ButtonSetting("Require right mouse", false));
        this.registerSetting(onlyWhenDamaged = new ButtonSetting("Damaged", false));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        resetState(false);
    }

    private static int msToTicks(double ms) {
        if (ms <= 0.0) return 0;
        return (int) Math.ceil(ms / 50.0);
    }

    @Override
    public void onDisable() {
        resetState(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if (!Utils.nullCheck() || !Utils.holdingSword()) return;
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) return;
        if (e.button == 1) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickMouse(RightClickMouseEvent e) {
        if (shouldBlockVanillaUse()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onUseItem(UseItemEvent e) {
        if (shouldBlockVanillaUse()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        if (!Utils.nullCheck()) return;
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) return;
        if (mc.currentScreen != null && (isBlocking || isLagging)) {
            resetState(true);
            return;
        }
        if (!forceBlockAnimation.isToggled() || !Utils.holdingSword()) return;
        ReflectionUtils.setItemInUse(isBlocking || isLagging);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent e) {
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) {
            releaseLag();
            return;
        }
        if (!isLagging || !preventDelayAttacks.isToggled()) return;
        if (!(e.getPacket() instanceof C02PacketUseEntity)) return;
        if (((C02PacketUseEntity) e.getPacket()).getAction() != C02PacketUseEntity.Action.ATTACK) return;

        releaseLag();
        if (blockAgainImmediately.isToggled() && Utils.holdingSword()) {
            startBlocking(tickCounter);
        }
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (!Utils.nullCheck() || mc.thePlayer.isDead || mc.currentScreen != null) {
            resetState(true);
            return;
        }

        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) {
            resetState(true);
            return;
        }

        int selfHurtTime = mc.thePlayer.hurtTime;
        boolean hurtAgain = selfHurtTime > lastSelfHurtTime;
        lastSelfHurtTime = selfHurtTime;

        if (!Utils.holdingSword()) {
            resetState(false);
            return;
        }

        tickCounter++;
        int currentTick = tickCounter;

        currentTarget = CombatTargeting.findTarget(range.getInput() * range.getInput(), ignoreTeammates.isToggled());
        boolean killAuraAttacking = ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && !ModuleManager.killAura.isRequireMouseDown() && currentTarget != null;
        boolean rmbDown = Mouse.isButtonDown(1);
        boolean lmbDown = Mouse.isButtonDown(0) || killAuraAttacking;

        if (!rmbDown) {
            resetState(true);
            return;
        }

        if (!lmbDown) {
            if (isLagging) releaseLag();
            if (!isBlocking) {
                startBlocking(currentTick);
                manualBlock = true;
            }
            return;
        }

        if (manualBlock) {
            stopBlocking(true);
            manualBlock = false;
        }

        boolean hasTarget = currentTarget != null;
        boolean conditionsMet = hasTarget && checkConditions(lmbDown, rmbDown);

        if (isLagging) {
            int lagMaxTicks = msToTicks(lagMaxDuration.getInput());
            boolean lagExpired = lagMaxTicks > 0 && lagStartTick >= 0 && currentTick - lagStartTick >= lagMaxTicks;

            if (lagExpired || !conditionsMet) {
                releaseLag();
                if (lagExpired && blockAgainImmediately.isToggled() && conditionsMet) {
                    startBlocking(currentTick);
                }
            }
        }

        if (!conditionsMet) {
            stopBlocking(true);
            return;
        }

        if (!isBlocking && !isLagging) {
            boolean shouldStart;
            if (onlyWhenDamaged.isToggled()) {
                shouldStart = shouldPredictiveBlock();
            } else {
                shouldStart = true;
            }
            if (shouldStart) {
                startBlocking(currentTick);
            }
        }

        if (isBlocking) {
            int maxHoldTicks = msToTicks(maxHoldMs.getInput());
            boolean timeExpired = maxHoldTicks > 0 && blockStartTick >= 0 && currentTick - blockStartTick >= maxHoldTicks;
            boolean shouldStop = timeExpired;
            if (onlyWhenDamaged.isToggled() && hurtAgain) {
                shouldStop = true;
            }
            if (shouldStop) {
                if (shouldStartLag()) {
                    startLag(currentTick);
                }
                stopBlocking(true);
            }
        }
    }

    private boolean checkConditions(boolean lmbDown, boolean rmbDown) {
        if (requireLmb.isToggled() && !lmbDown) return false;
        if (requireRmb.isToggled() && !rmbDown) return false;
        return true;
    }

    private boolean shouldPredictiveBlock() {
        int ourHurtTime = mc.thePlayer.hurtTime;
        int triggerTick = (int) Math.round(maxHurtTimeMs.getInput() / 50.0);
        triggerTick = Math.max(1, Math.min(10, triggerTick));
        return ourHurtTime == triggerTick;
    }

    private boolean shouldBlockVanillaUse() {
        return isEnabled() && isLagging && Utils.nullCheck() && Utils.holdingSword() && mc.currentScreen == null;
    }

    private void startBlocking(int currentTick) {
        if (!Utils.holdingSword()) return;
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, true);
        KeyBinding.onTick(keyCode);
        isBlocking = true;
        blockStartTick = currentTick;
    }

    private void stopBlocking(boolean forceRelease) {
        if (!isBlocking && !forceRelease) return;
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, false);
        isBlocking = false;
        blockStartTick = -1;
    }

    private boolean shouldStartLag() {
        double chance = lagChance.getInput();
        if (chance <= 0) return false;
        if (chance >= 100) return true;
        return Math.random() * 100 < chance;
    }

    private void startLag(int currentTick) {
        if (isLagging) return;
        int lagReferenceTick = blockStartTick >= 0 ? blockStartTick : currentTick;
        int lagMaxTicks = msToTicks(lagMaxDuration.getInput());
        if (lagMaxTicks > 0 && currentTick - lagReferenceTick >= lagMaxTicks) {
            return;
        }
        outboundLag = new LagRequest(EnumLagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
        Raven.lagHandler.requestLag(outboundLag);
        isLagging = true;
        lagStartTick = lagReferenceTick;
    }

    private void releaseLag() {
        if (!isLagging) return;
        if (outboundLag != null) {
            outboundLag.getTimeout().forceTimeOut();
            outboundLag = null;
        }
        isLagging = false;
        lagStartTick = -1;
    }

    public boolean isActive() {
        return isEnabled() && (isBlocking || isLagging);
    }

    private void resetState(boolean releaseUseKey) {
        boolean wasActive = isBlocking || isLagging;
        releaseLag();
        stopBlocking(releaseUseKey);
        manualBlock = false;
        if (forceBlockAnimation.isToggled() && wasActive) {
            ReflectionUtils.setItemInUse(false);
        }
        if (Mouse.isButtonDown(1) && mc.currentScreen == null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        }
        currentTarget = null;
        lastSelfHurtTime = 0;
    }
}
