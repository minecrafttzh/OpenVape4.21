package gg.vape.notification;

import gg.vape.Vape;

/**
 * Tracks module on/off state changes so that a single keypress which toggles
 * multiple modules produces exactly ONE enable or disable sound playback
 * instead of one per module.
 *
 * <p>When not inside a batch (e.g. module toggled via the click-GUI) the state
 * change is played immediately via the shared NotificationSoundPlayer.</p>
 */
public final class ModuleToggleSoundTracker {
    private static final ThreadLocal<BatchState> BATCH = ThreadLocal.withInitial(BatchState::new);

    private ModuleToggleSoundTracker() {
    }

    /**
     * Marks the start of a synchronous dispatch batch, e.g. a single
     * EventKeyPress that may toggle any number of modules. State changes
     * recorded during the batch are aggregated into a single sound.
     */
    public static void startBatch() {
        BatchState state = BATCH.get();
        state.depth++;
        if (state.depth == 1) {
            state.anyEnabled = false;
            state.anyDisabled = false;
        }
    }

    /**
     * Ends a dispatch batch. If any state changes were aggregated the
     * corresponding sound is queued now.
     */
    public static void endBatch() {
        BatchState state = BATCH.get();
        if (state.depth == 0) {
            return;
        }
        state.depth--;
        if (state.depth == 0) {
            try {
                playAggregated(state);
            } finally {
                state.anyEnabled = false;
                state.anyDisabled = false;
            }
        }
    }

    /**
     * Records a concrete module state transition. If called outside a batch
     * the corresponding sound is queued immediately.
     *
     * @param nowEnabled true if the module became enabled, false for disabled
     */
    public static void recordStateChange(boolean nowEnabled) {
        BatchState state = BATCH.get();
        if (state.depth > 0) {
            if (nowEnabled) {
                state.anyEnabled = true;
            } else {
                state.anyDisabled = true;
            }
            return;
        }
        NotificationSoundPlayer player = Vape.INSTANCE.getNotificationSoundPlayer();
        if (player == null) {
            return;
        }
        player.queue(nowEnabled ? NotificationSounds.MODULE_ENABLE : NotificationSounds.MODULE_DISABLE);
    }

    private static void playAggregated(BatchState state) {
        if (!state.anyEnabled && !state.anyDisabled) {
            return;
        }
        NotificationSoundPlayer player = Vape.INSTANCE.getNotificationSoundPlayer();
        if (player == null) {
            return;
        }
        // If any module was enabled in this batch prefer the enable sound.
        // When only disable transitions happened play the disable sound.
        SoundClip clip = state.anyEnabled ? NotificationSounds.MODULE_ENABLE : NotificationSounds.MODULE_DISABLE;
        player.queue(clip);
    }

    private static final class BatchState {
        int depth;
        boolean anyEnabled;
        boolean anyDisabled;
    }
}
