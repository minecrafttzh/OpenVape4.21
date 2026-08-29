package gg.vape.input;

import gg.vape.config.ClientSettings;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.notification.ModuleToggleSoundTracker;
import gg.vape.wrapper.impl.Minecraft;
import java.util.HashMap;

public class KeyboardInputState {
    private int lastKey;
    private boolean lastKeyDown;
    private long lastChangeTime;
    private HashMap<Integer, Boolean> keyStates = new HashMap();
    private boolean canceled;

    public boolean isLastKeyDown() {
        return this.lastKeyDown;
    }

    public long getLastChangeTime() {
        return this.lastChangeTime;
    }

    public boolean isKeyDown(int keyCode) {
        return this.keyStates.getOrDefault(keyCode, false);
    }

    public boolean isCanceled() {
        return this.canceled;
    }

    private void dispatchChange(int keyCode, boolean keyDown) {
        this.lastChangeTime = System.nanoTime();
        this.lastKeyDown = keyDown;
        this.lastKey = keyCode;
        EventKeyPress event = new EventKeyPress(keyCode, keyDown);
        // 一次按键事件可能触发多个模块 toggle；用批次追踪聚合播放一次声音。
        ModuleToggleSoundTracker.startBatch();
        try {
            event.fire();
        } finally {
            ModuleToggleSoundTracker.endBatch();
        }
        this.canceled = event.isCanceled();
        if (!gg.vape.module.none.ClientSettings.INSTANCE.inputEnabled) {
            int inventoryKeyCode = ClientSettings.getPlatformKeyCode(Minecraft.gameSettings().y$src$Lgg_vape_wrapper_impl_KeyBinding_$1hvjjoh());
            if (keyCode == inventoryKeyCode) {
                return;
            }
            this.canceled = true;
        }
    }

    public int getLastKey() {
        return this.lastKey;
    }

    public KeyboardInputState() {
        this.lastChangeTime = System.nanoTime();
    }


    public void setKeyState(int keyCode, boolean keyDown) {
        boolean previousState = this.keyStates.getOrDefault(keyCode, false);
        if (previousState != keyDown) {
            this.dispatchChange(keyCode, keyDown);
        }
        this.keyStates.put(keyCode, keyDown);
    }

    public void releaseKey(int keyCode) {
        this.keyStates.put(keyCode, false);
    }
}

