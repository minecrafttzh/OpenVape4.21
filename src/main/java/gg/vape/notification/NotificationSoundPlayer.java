package gg.vape.notification;

import gg.vape.Vape;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class NotificationSoundPlayer {
    private static int[] controlFlowMarker;
    /**
     * 使用有界队列（128 条）。用户短时间内疯狂触发通知时，超出上限会通过
     * offer() 静默丢弃，避免 backlog 无限增长。旧实现用单槽 AtomicReference，
     * 快速触发会导致后者覆盖前者（丢音），或同一槽位被复用时相互打断。
     */
    private final LinkedBlockingQueue<SoundClip> pendingQueue = new LinkedBlockingQueue<SoundClip>(128);

    public NotificationSoundPlayer() {
        this.startSoundThread();
    }

    static {
        if (NotificationSoundPlayer.getControlFlowMarker() == null) {
            NotificationSoundPlayer.setControlFlowMarker(new int[3]);
        }
    }

    /**
     * 取出当前队列里所有待播放的声音并逐个启动播放。
     * 注意：SoundClip.play() 现在是完全非阻塞的——每个声音创建独立 Clip，
     * 因此 drainTo 之后 N 个声音会同时在混音器上并发播放（即自然重叠）。
     */
    public void playPendingSound() {
        if (this.isMuted()) {
            // 静音时只清队列，不再播放。
            this.pendingQueue.clear();
            return;
        }
        List<SoundClip> batch = new ArrayList<SoundClip>();
        this.pendingQueue.drainTo(batch);
        if (batch.isEmpty()) {
            return;
        }
        float volume = this.getVolumePercent();
        for (SoundClip clip : batch) {
            try {
                clip.play(volume);
            }
            catch (Exception exception) {
                Vape.logThrowable(exception);
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
        if (sound == null) {
            return;
        }
        // offer 失败（队列已满）时静默丢弃，避免触发线程被 put 阻塞。
        this.pendingQueue.offer(sound);
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
