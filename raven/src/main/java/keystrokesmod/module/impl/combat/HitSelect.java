package keystrokesmod.module.impl.combat;

import keystrokesmod.event.PreAttackEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.CombatTargeting;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HitSelect extends Module {
    private static final double HIT_RANGE = 3.0D;
    private static final double HIT_RANGE_SQ = HIT_RANGE * HIT_RANGE;
    private static final int HURT_WINDOW_TICKS = 10;
    private static final int SERVER_CONFIRM_COOLDOWN_TICKS = HURT_WINDOW_TICKS;
    private static final int SERVER_CONFIRM_TIMEOUT_TICKS = 30;

    private static final int BLOCK_WAIT_FIRST = 1;
    private static final int BLOCK_SERVER_COOLDOWN = 1 << 3;
    private static final int BLOCK_PREDICTED_BURST = 1 << 4;
    private static final int BLOCK_CRITICALS = 1 << 5;

    private final SliderSetting pauseDuration;
    private final SliderSetting mode;
    private final SliderSetting waitForFirstHit;
    private final ButtonSetting disableDuringKnockback;
    private final ButtonSetting onlyWhileDamaged;
    private final ButtonSetting useServerAttackTime;
    private final ButtonSetting fakeSwing;
    private final SliderSetting inCombatCancelRate;
    private final SliderSetting missedSwingsCancelRate;

    private final String[] modes = new String[] { "Burst", "Criticals" };

    private EntityPlayer currentTarget;
    private final Map<Integer, TargetState> targetStates = new HashMap<>();
    private int lastSelfHurtTime;
    private boolean takingKnockback;
    private boolean waitFirstTracking;
    private int waitFirstStartTick = -1;
    private boolean waitFirstUnlocked;

    private int tickCounter;

    public HitSelect() {
        super("Hit Select", category.combat);

        this.registerSetting(new DescriptionSetting("Filters unnecessary clicks."));
        this.registerSetting(pauseDuration = new SliderSetting("Pause duration", "ms", 500.0D, 0.0D, 500.0D, 50.0D));
        this.registerSetting(mode = new SliderSetting("Mode", 0, modes));
        this.registerSetting(waitForFirstHit = new SliderSetting("Wait for first hit", "ms", 0.0D, 0.0D, 500.0D, 50.0D));
        this.registerSetting(disableDuringKnockback = new ButtonSetting("Disable during knockback", false));
        this.registerSetting(onlyWhileDamaged = new ButtonSetting("Only while damaged", false));
        this.registerSetting(useServerAttackTime = new ButtonSetting("Use server attack time", false));
        this.registerSetting(fakeSwing = new ButtonSetting("Fake swing", false));
        this.registerSetting(new DescriptionSetting("Cancel rate"));
        this.registerSetting(inCombatCancelRate = new SliderSetting("In combat", "%", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(missedSwingsCancelRate = new SliderSetting("Missed swings", "%", 0.0D, 0.0D, 100.0D, 1.0D));
        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        return modes[(int) mode.getInput()];
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        resetAllState();
    }

    @Override
    public void onDisable() {
        resetAllState();
    }

    private static int msToTicks(double ms) {
        if (ms <= 0.0) {
            return 0;
        }
        return (int) Math.ceil(ms / 50.0);
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent event) {
        if (!Utils.nullCheck() || mc.thePlayer.isDead || mc.theWorld == null) {
            resetAllState();
            return;
        }

        tickCounter++;
        int currentTick = tickCounter;
        pruneTargetStates();

        EntityPlayer nextTarget = CombatTargeting.findTarget(HIT_RANGE_SQ);
        updateCurrentTarget(nextTarget, currentTick);
        updateSelfDamage(currentTick);
        updateTargetDamage(currentTick);
    }

    @SubscribeEvent
    public void onPreAttack(PreAttackEvent event) {
        if (!canProcessClicks()) {
            return;
        }

        int currentTick = tickCounter;
        ClickType clickType = classifyClick(event.objectMouseOver);

        if (clickType == ClickType.BLOCK_INTERACTION) {
            return;
        }

        if (clickType == ClickType.MISSED_SWING) {
            if (shouldCancel(missedSwingsCancelRate.getInput())) {
                cancelClick(event);
            }
            return;
        }

        EntityPlayer clickedTarget = CombatTargeting.asValidPlayer(event.objectMouseOver == null ? null : event.objectMouseOver.entityHit, HIT_RANGE_SQ);
        if (clickedTarget == null) {
            return;
        }

        updateCurrentTarget(clickedTarget, currentTick);

        TargetState state = getTargetState(clickedTarget, currentTick);
        int blockMask = getValidHitBlockMask(state, currentTick);
        boolean shouldBlock = (blockMask & BLOCK_WAIT_FIRST) != 0
                || (blockMask & BLOCK_PREDICTED_BURST) != 0
                || applyPauseDuration(state, blockMask & ~BLOCK_PREDICTED_BURST, currentTick);
        if (shouldBlock && shouldCancel(inCombatCancelRate.getInput())) {
            cancelClick(event);
            return;
        }

        recordPassedValidHit(clickedTarget, currentTick);
    }

    private boolean canProcessClicks() {
        return Utils.nullCheck() && mc.theWorld != null && mc.thePlayer != null && !mc.thePlayer.isDead;
    }

    private ClickType classifyClick(MovingObjectPosition objectMouseOver) {
        if (objectMouseOver == null) {
            return ClickType.MISSED_SWING;
        }

        if (objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return ClickType.BLOCK_INTERACTION;
        }

        if (objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            Entity entityHit = objectMouseOver.entityHit;
            return CombatTargeting.asValidPlayer(entityHit, HIT_RANGE_SQ) != null ? ClickType.VALID_HIT : ClickType.MISSED_SWING;
        }

        return ClickType.MISSED_SWING;
    }

    private void cancelClick(PreAttackEvent event) {
        if (fakeSwing.isToggled() && Utils.nullCheck()) {
            Utils.setSwinging();
        }

        event.setCanceled(true);
    }

    private void updateCurrentTarget(EntityPlayer nextTarget, int currentTick) {
        if (sameTarget(nextTarget)) {
            if (nextTarget != null) {
                currentTarget = nextTarget;
                getTargetState(nextTarget, currentTick);
            }
            return;
        }

        currentTarget = nextTarget;

        if (nextTarget == null) {
            resetWaitFirstState();
        } else if (!waitFirstTracking) {
            waitFirstTracking = true;
            waitFirstStartTick = currentTick;
            waitFirstUnlocked = false;
        }

        if (nextTarget != null) {
            getTargetState(nextTarget, currentTick);
        }
    }

    private void updateSelfDamage(int currentTick) {
        int hurtTime = mc.thePlayer.hurtTime;
        boolean hurtAgain = hurtTime > lastSelfHurtTime;

        if (hurtAgain) {
            if (waitFirstTracking && !waitFirstUnlocked) {
                waitFirstUnlocked = true;
            }

            if (!takingKnockback) {
                takingKnockback = true;
            }

            if (currentTarget != null) {
                TargetState state = getTargetState(currentTarget, currentTick);
                state.firstSelfHitSeen = true;
            }
        }

        if (takingKnockback && mc.thePlayer.onGround && !hurtAgain) {
            takingKnockback = false;
        }

        lastSelfHurtTime = hurtTime;
    }

    private void updateTargetDamage(int currentTick) {
        if (currentTarget == null || !useServerAttackTime.isToggled()) {
            return;
        }

        TargetState state = getTargetState(currentTarget, currentTick);
        int targetHurtTime = currentTarget.hurtTime;
        if (state.pendingServerConfirmationTick >= 0 && currentTick - state.pendingServerConfirmationTick > SERVER_CONFIRM_TIMEOUT_TICKS) {
            state.pendingServerConfirmationTick = -1;
        }

        if (state.pendingServerConfirmationTick >= 0 && targetHurtTime > state.lastObservedTargetHurtTime) {
            state.pendingServerConfirmationTick = -1;
            state.lastConfirmedTargetDamageTick = currentTick;
            state.rawBlockMask = BLOCK_SERVER_COOLDOWN;
            state.rawBlockStartTick = currentTick;
        }

        state.lastObservedTargetHurtTime = targetHurtTime;
    }

    private int getValidHitBlockMask(TargetState state, int currentTick) {
        if (currentTarget == null) {
            return 0;
        }

        if (disableDuringKnockback.isToggled() && isTakingKnockback()) {
            return 0;
        }

        int blockMask = 0;

        if (isWaitingForFirstHit(currentTick)) {
            blockMask |= BLOCK_WAIT_FIRST;
        }

        blockMask |= getBurstBlockMask(state, currentTick);

        if (isCriticalsBlocked(state, currentTick)) {
            blockMask |= BLOCK_CRITICALS;
        }

        return blockMask;
    }

    private int getBurstBlockMask(TargetState state, int currentTick) {
        if (useServerAttackTime.isToggled()) {
            if (state.lastConfirmedTargetDamageTick >= 0 && currentTick - state.lastConfirmedTargetDamageTick < SERVER_CONFIRM_COOLDOWN_TICKS) {
                return BLOCK_SERVER_COOLDOWN;
            }

            return 0;
        }

        if (!isPredictedBurstWindowActive(state, currentTick)) {
            return 0;
        }

        int pauseTicks = msToTicks(pauseDuration.getInput());
        return pauseTicks > 0 && currentTick - state.predictedBurstWindowStartTick < pauseTicks
                ? BLOCK_PREDICTED_BURST
                : 0;
    }

    private boolean isCriticalsBlocked(TargetState state, int currentTick) {
        if ((int) mode.getInput() != 1) {
            return false;
        }

        if (mc.thePlayer.onGround) {
            return false;
        }

        if (onlyWhileDamaged.isToggled() && !state.firstSelfHitSeen) {
            return false;
        }

        if (disableDuringKnockback.isToggled() && isTakingKnockback()) {
            return false;
        }

        return !canCriticalHit();
    }

    private boolean isWaitingForFirstHit(int currentTick) {
        if (waitForFirstHit.getInput() <= 0.0D
                || currentTarget == null
                || !waitFirstTracking
                || waitFirstUnlocked
                || waitFirstStartTick < 0) {
            return false;
        }

        int requiredTicks = msToTicks(waitForFirstHit.getInput());
        return requiredTicks > 0 && currentTick - waitFirstStartTick < requiredTicks;
    }

    private boolean canCriticalHit() {
        return mc.thePlayer.fallDistance > 0.0F
                && !mc.thePlayer.onGround
                && !mc.thePlayer.isOnLadder()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isPotionActive(Potion.blindness)
                && mc.thePlayer.ridingEntity == null;
    }

    private boolean isTakingKnockback() {
        return takingKnockback || mc.thePlayer.hurtTime > 0;
    }

    private boolean applyPauseDuration(TargetState state, int blockMask, int currentTick) {
        if (blockMask == 0) {
            state.rawBlockMask = 0;
            state.rawBlockStartTick = -1;
            return false;
        }

        if (pauseDuration.getInput() <= 0.0D) {
            state.rawBlockMask = blockMask;
            state.rawBlockStartTick = currentTick;
            return false;
        }

        if (blockMask != state.rawBlockMask) {
            state.rawBlockMask = blockMask;
            state.rawBlockStartTick = currentTick;
        } else if (state.rawBlockStartTick < 0) {
            state.rawBlockStartTick = currentTick;
        }

        int requiredTicks = msToTicks(pauseDuration.getInput());
        return requiredTicks > 0 && currentTick - state.rawBlockStartTick < requiredTicks;
    }

    private void recordPassedValidHit(EntityPlayer target, int currentTick) {
        if (target == null) {
            return;
        }

        updateCurrentTarget(target, currentTick);
        TargetState state = getTargetState(target, currentTick);

        if (useServerAttackTime.isToggled()) {
            state.pendingServerConfirmationTick = currentTick;
            state.lastConfirmedTargetDamageTick = -1;
            return;
        }

        if (!isPredictedBurstWindowActive(state, currentTick)) {
            startPredictedBurstWindow(state, currentTick, HURT_WINDOW_TICKS);
        }
    }

    private boolean shouldCancel(double chance) {
        if (chance <= 0.0D) {
            return false;
        }

        if (chance >= 100.0D) {
            return true;
        }

        return Math.random() * 100.0D < chance;
    }

    private boolean sameTarget(EntityPlayer nextTarget) {
        if (currentTarget == null || nextTarget == null) {
            return currentTarget == nextTarget;
        }

        return currentTarget.getEntityId() == nextTarget.getEntityId();
    }

    private void resetWaitFirstState() {
        waitFirstTracking = false;
        waitFirstStartTick = -1;
        waitFirstUnlocked = false;
    }

    private int getHurtWindowTicks(EntityPlayer target) {
        if (target == null || target.maxHurtTime <= 0) {
            return HURT_WINDOW_TICKS;
        }

        return Math.max(HURT_WINDOW_TICKS, target.maxHurtTime);
    }

    private boolean isPredictedBurstWindowActive(TargetState state, int currentTick) {
        return state.predictedBurstWindowEndTick >= 0 && currentTick < state.predictedBurstWindowEndTick;
    }

    private void startPredictedBurstWindow(TargetState state, int startTick, int windowTicks) {
        int hurtWindowTicks = Math.max(1, windowTicks);
        state.predictedBurstWindowStartTick = startTick;
        state.predictedBurstWindowEndTick = startTick + hurtWindowTicks;
    }

    private void clearPredictedBurstWindow(TargetState state) {
        state.predictedBurstWindowStartTick = -1;
        state.predictedBurstWindowEndTick = -1;
    }

    private void syncPredictedBurstWindow(TargetState state, EntityPlayer target, int currentTick) {
        if (state.predictedBurstWindowEndTick >= 0 && currentTick >= state.predictedBurstWindowEndTick) {
            clearPredictedBurstWindow(state);
        }

        if (target == null || target.hurtTime <= 0) {
            return;
        }

        int hurtWindowTicks = Math.max(getHurtWindowTicks(target), target.hurtTime);
        int elapsedWindowTicks = hurtWindowTicks - target.hurtTime;
        int estimatedStartTick = currentTick - Math.max(0, elapsedWindowTicks);
        if (!isPredictedBurstWindowActive(state, currentTick) || estimatedStartTick > state.predictedBurstWindowStartTick) {
            startPredictedBurstWindow(state, estimatedStartTick, hurtWindowTicks);
        }
    }

    private TargetState getTargetState(EntityPlayer target, int currentTick) {
        TargetState state = targetStates.get(target.getEntityId());
        if (state == null) {
            state = new TargetState();
            if (useServerAttackTime.isToggled()) {
                state.lastObservedTargetHurtTime = target.hurtTime;
            }
            targetStates.put(target.getEntityId(), state);
        }
        return state;
    }

    private void pruneTargetStates() {
        if (mc.theWorld == null) {
            targetStates.clear();
            return;
        }

        Iterator<Map.Entry<Integer, TargetState>> iterator = targetStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TargetState> entry = iterator.next();
            Entity entity = mc.theWorld.getEntityByID(entry.getKey());
            if (!(entity instanceof EntityPlayer) || entity.isDead || ((EntityPlayer) entity).deathTime != 0) {
                iterator.remove();
            }
        }
    }

    private void resetAllState() {
        currentTarget = null;
        targetStates.clear();
        lastSelfHurtTime = 0;
        takingKnockback = false;
        resetWaitFirstState();
    }

    private enum ClickType {
        VALID_HIT,
        BLOCK_INTERACTION,
        MISSED_SWING
    }

    private static class TargetState {
        boolean firstSelfHitSeen;
        int lastConfirmedTargetDamageTick = -1;
        int pendingServerConfirmationTick = -1;
        int predictedBurstWindowStartTick = -1;
        int predictedBurstWindowEndTick = -1;
        int lastObservedTargetHurtTime;
        int rawBlockStartTick = -1;
        int rawBlockMask;
    }
}
