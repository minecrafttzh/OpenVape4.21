package keystrokesmod.module.impl.render;

import keystrokesmod.mixin.impl.accessor.IAccessorMouseHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.Freecam;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.event.world.WorldEvent;

public class Freelook extends Module {
    public static boolean perspectiveToggled;
    public static float cameraYaw;
    public static float cameraPitch;

    private ButtonSetting hold;
    private ButtonSetting invertPitch;
    private ButtonSetting lockPitch;
    private ButtonSetting customFov;
    private SliderSetting fov;
    private KeySetting freelookKey;

    private boolean prevKeyState;
    private int previousPerspective;
    private float lastFov;

    public Freelook() {
        super("Free Look", category.render);
        this.registerSetting(freelookKey = new KeySetting("Key", 56));
        this.registerSetting(hold = new ButtonSetting("Hold", true));
        this.registerSetting(invertPitch = new ButtonSetting("Invert pitch", false));
        this.registerSetting(lockPitch = new ButtonSetting("Lock pitch", true));
        this.registerSetting(customFov = new ButtonSetting("Custom FOV", false));
        this.registerSetting(fov = new SliderSetting("FOV", 90, 10, 150, 1));
    }

    @Override
    public void guiUpdate() {
        fov.setVisible(customFov.isToggled(), this);
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent e) {
        if (e.phase != Phase.END || mc.currentScreen != null || !Utils.nullCheck()) {
            return;
        }
        Module freecamMod = ModuleManager.getModule(Freecam.class);
        if (freecamMod instanceof Freecam && ((Freecam) freecamMod).freeEntity != null) {
            return;
        }
        boolean down = freelookKey.isPressed();
        if (down != prevKeyState) {
            onPressed(down);
            prevKeyState = down;
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui != null && perspectiveToggled && hold.isToggled()) {
            resetPerspective();
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load e) {
        if (perspectiveToggled) {
            resetPerspective();
        }
    }

    private void onPressed(boolean state) {
        if (!isEnabled()) {
            if (perspectiveToggled) {
                resetPerspective();
            }
            return;
        }
        if (state) {
            cameraYaw = mc.thePlayer.rotationYaw;
            cameraPitch = mc.thePlayer.rotationPitch;
            if (perspectiveToggled) {
                resetPerspective();
            } else {
                enterPerspective();
            }
        } else if (hold.isToggled()) {
            resetPerspective();
        }
    }

    private void enterPerspective() {
        perspectiveToggled = true;
        previousPerspective = mc.gameSettings.thirdPersonView;
        applyThirdPersonView(1);
        lastFov = mc.gameSettings.fovSetting;
    }

    public void resetPerspective() {
        perspectiveToggled = false;
        applyThirdPersonView(previousPerspective);
        if (mc.currentScreen == null && mc.inGameHasFocus) {
            mc.mouseHelper.grabMouseCursor();
        }
        if (hold.isToggled() || mc.gameSettings.fovSetting == lastFov || customFov.isToggled()) {
            mc.gameSettings.fovSetting = lastFov;
        }
    }

    public static boolean overrideMouse(Minecraft mc) {
        if (!mc.inGameHasFocus) {
            return false;
        }
        if (ModuleManager.freelook == null || !ModuleManager.freelook.isEnabled() || !perspectiveToggled) {
            return true;
        }
        mc.mouseHelper.mouseXYChange();
        float sens = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        float mult = sens * sens * sens * 8.0f;
        Freelook fl = ModuleManager.freelook;
        if (fl != null) {
            int dx = ((IAccessorMouseHelper) mc.mouseHelper).getDeltaX();
            int dy = ((IAccessorMouseHelper) mc.mouseHelper).getDeltaY();
            float fdx = dx * mult;
            float fdy = dy * mult;
            cameraYaw += fdx * 0.15f;
            if (fl.invertPitch.isToggled()) {
                fdy = -fdy;
            }
            cameraPitch += fdy * 0.15f;
            if (fl.lockPitch.isToggled()) {
                cameraPitch = Math.max(-90f, Math.min(90f, cameraPitch));
            }
            if (fl.customFov.isToggled()) {
                mc.gameSettings.fovSetting = (float) fl.fov.getInput();
            }
        }
        return false;
    }

    @Override
    public void onDisable() {
        if (perspectiveToggled) {
            perspectiveToggled = false;
            applyThirdPersonView(0);
            if (mc.currentScreen == null && mc.inGameHasFocus) {
                mc.mouseHelper.grabMouseCursor();
            }
            mc.gameSettings.fovSetting = lastFov;
        }
    }

    private void applyThirdPersonView(int view) {
        if (view < 0) {
            view = 0;
        } else if (view > 2) {
            view = 2;
        }

        mc.gameSettings.thirdPersonView = view;
        if (mc.entityRenderer != null) {
            if (view == 0) {
                mc.entityRenderer.loadEntityShader(mc.getRenderViewEntity());
            } else if (view == 1) {
                mc.entityRenderer.loadEntityShader((Entity) null);
            }
        }
        if (mc.renderGlobal != null) {
            mc.renderGlobal.setDisplayListEntitiesDirty();
        }
    }
}
