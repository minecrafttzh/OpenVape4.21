package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class Holdlook extends Module {
    private final KeySetting rearCamKey;
    private final KeySetting frontCamKey;

    private boolean rearActive;
    private boolean frontActive;
    private int savedPerspective;

    public Holdlook() {
        super("Hold Look", category.render);
        this.registerSetting(rearCamKey = new KeySetting("Rear cam", 0));
        this.registerSetting(frontCamKey = new KeySetting("Front cam", 0));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !Utils.nullCheck()) return;

        if (mc.currentScreen != null) {
            if (rearActive || frontActive) {
                applyThirdPersonView(0);
                rearActive = false;
                frontActive = false;
            }
            return;
        }

        boolean rearDown = rearCamKey.isPressed();
        boolean frontDown = frontCamKey.isPressed();

        if (rearDown && !rearActive) {
            savedPerspective = mc.gameSettings.thirdPersonView;
            applyThirdPersonView(1);
            rearActive = true;
        } else if (!rearDown && rearActive) {
            applyThirdPersonView(frontActive ? 2 : 0);
            rearActive = false;
        }

        if (frontDown && !frontActive) {
            if (!rearActive) {
                savedPerspective = mc.gameSettings.thirdPersonView;
            }
            applyThirdPersonView(2);
            frontActive = true;
        } else if (!frontDown && frontActive) {
            applyThirdPersonView(rearActive ? 1 : 0);
            frontActive = false;
        }
    }

    @Override
    public void onDisable() {
        if (rearActive || frontActive) {
            applyThirdPersonView(0);
            rearActive = false;
            frontActive = false;
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
