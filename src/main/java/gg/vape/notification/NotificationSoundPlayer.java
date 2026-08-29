package gg.vape.notification;

import gg.vape.Vape;
import gg.vape.notification.SoundClip;
import java.util.concurrent.atomic.AtomicReference;

public class NotificationSoundPlayer {
    private static int[] controlFlowMarker;
    private final AtomicReference<SoundClip> pendingSound = new AtomicReference<SoundClip>();

    public NotificationSoundPlayer() {
        this.startSoundThread();
    }

    static {
        if (NotificationSoundPlayer.getControlFlowMarker() == null) {
            NotificationSoundPlayer.setControlFlowMarker(new int[3]);
        }
    }

    public void playPendingSound() {
        if (this.pendingSound.get() != null) {
            SoundClip sound = this.pendingSound.get();
            this.pendingSound.set(null);
            if (!this.isMuted()) {
                sound.play(this.getVolumePercent());
            }
        }
    }

    public boolean isMuted() {
        return Vape.INSTANCE.getPublicProfileSettings().muted.getEffectiveValue();
    }

    public static int[] getControlFlowMarker() {
        return controlFlowMarker;
    }

    public static void setControlFlowMarker(int[] marker) {
        controlFlowMarker = marker;
    }


    public float getVolumePercent() {
        return ((Double)Vape.INSTANCE.getPublicProfileSettings().volume.getValue()).floatValue();
    }

    public void queue(SoundClip sound) {
        this.pendingSound.set(sound);
    }

    public void startSoundThread() {
        new Thread(this::runSoundLoop, "Vape notification sound player").start();
    }

    private void runSoundLoop() {
        // 原实现使用 while (!Vape.INSTANCE.enabled) 作为循环条件，逻辑完全反了：
        // 一旦客户端 enabled 被置 true，线程立刻退出，队列中的声音永远不会被播放。
        // 改为无条件轮询，直到线程中断（JVM 关闭）。
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(100L);
                this.playPendingSound();
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            catch (Exception exception) {
                Vape.logThrowable(exception);
            }
        }
    }
}
