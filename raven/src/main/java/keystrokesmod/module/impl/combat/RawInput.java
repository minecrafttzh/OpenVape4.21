package keystrokesmod.module.impl.combat;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.utility.Utils;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Mouse;
import net.minecraft.util.MouseHelper;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RawInput extends Module {
    private MouseHelper originalHelper;
    private RawMouseHelper rawHelper;
    private RawMouseThread pollThread;

    public RawInput() {
        super("Raw Input", category.combat, 0);
        this.registerSetting(new DescriptionSetting("Bypasses OS mouse acceleration."));
    }

    @Override
    public void onEnable() {
        if (!isRawInputSupported()) {
            Utils.sendMessage("&cRaw input libraries not found.");
            this.disable();
            return;
        }

        originalHelper = mc.mouseHelper;
        pollThread = new RawMouseThread();
        pollThread.start();
        rawHelper = new RawMouseHelper(pollThread);
        mc.mouseHelper = rawHelper;
    }

    @Override
    public void onDisable() {
        if (originalHelper != null) {
            mc.mouseHelper = originalHelper;
            originalHelper = null;
        }
        if (pollThread != null) {
            pollThread.running = false;
            pollThread = null;
        }
        rawHelper = null;
    }

    private static boolean isRawInputSupported() {
        String path = System.getProperty("java.library.path");
        if (path == null) return false;
        String separator = File.pathSeparator;
        String mapped = System.mapLibraryName("jinput-raw");
        for (String dir : path.split(separator.isEmpty() ? ";" : separator)) {
            if (new File(dir, mapped).exists()) return true;
        }
        String mappedDx = System.mapLibraryName("jinput-dx8");
        for (String dir : path.split(separator.isEmpty() ? ";" : separator)) {
            if (new File(dir, mappedDx).exists()) return true;
        }
        return false;
    }

    private static String getEnvironmentClass() {
        String path = System.getProperty("java.library.path");
        if (path == null) return null;
        String separator = File.pathSeparator;
        boolean hasDx = false;
        boolean hasRaw = false;
        String mappedDx = System.mapLibraryName("jinput-dx8");
        String mappedRaw = System.mapLibraryName("jinput-raw");
        for (String dir : path.split(separator.isEmpty() ? ";" : separator)) {
            if (new File(dir, mappedDx).exists()) hasDx = true;
            if (new File(dir, mappedRaw).exists()) hasRaw = true;
        }
        if (hasDx && !hasRaw) return "net.java.games.input.DirectInputEnvironmentPlugin";
        if (hasRaw) return "net.java.games.input.DirectAndRawInputEnvironmentPlugin";
        return null;
    }

    static class RawMouseThread extends Thread {
        final AtomicInteger dx = new AtomicInteger(0);
        final AtomicInteger dy = new AtomicInteger(0);
        volatile boolean running = true;
        volatile List<Mouse> mice = new ArrayList<>();

        RawMouseThread() {
            super("Raven Raw Mouse Input");
            setDaemon(true);
        }

        @Override
        public void run() {
            rescan();
            while (running) {
                try {
                    if (!mice.isEmpty()) {
                        for (Mouse m : mice) {
                            if (!m.poll()) {
                                rescan();
                                break;
                            }
                            dx.addAndGet((int) m.getX().getPollData());
                            dy.addAndGet(-(int) m.getY().getPollData());
                        }
                        Thread.sleep(1);
                    } else {
                        Thread.sleep(50);
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                    try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                }
            }
        }

        void rescan() {
            try {
                String envClass = getEnvironmentClass();
                if (envClass == null) return;
                Constructor<?> ctor = Class.forName(envClass).getDeclaredConstructor();
                ctor.setAccessible(true);
                ControllerEnvironment env = (ControllerEnvironment) ctor.newInstance();
                List<Mouse> found = new ArrayList<>();
                for (Controller c : env.getControllers()) {
                    if (c instanceof Mouse) {
                        found.add((Mouse) c);
                    }
                }
                this.mice = found;
            } catch (Exception ignored) {}
        }

        void reset() {
            dx.set(0);
            dy.set(0);
        }
    }

    static class RawMouseHelper extends MouseHelper {
        private final RawMouseThread thread;
        private int fails;

        RawMouseHelper(RawMouseThread thread) {
            this.thread = thread;
        }

        @Override
        public void grabMouseCursor() {
            thread.reset();
            super.grabMouseCursor();
        }

        @Override
        public void mouseXYChange() {
            if (!thread.mice.isEmpty() && thread.isAlive()) {
                boolean movement = false;
                this.deltaX = thread.dx.getAndSet(0);
                this.deltaY = thread.dy.getAndSet(0);
                movement = (this.deltaX != 0 || this.deltaY != 0);
                if ((Math.abs(org.lwjgl.input.Mouse.getDX()) > 5 || Math.abs(org.lwjgl.input.Mouse.getDY()) > 5) && !movement) {
                    if (fails++ > 5) {
                        thread.rescan();
                    }
                } else if (movement) {
                    fails = 0;
                }
            } else {
                super.mouseXYChange();
            }
        }
    }
}
