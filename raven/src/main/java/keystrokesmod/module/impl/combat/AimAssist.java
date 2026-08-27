package keystrokesmod.module.impl.combat;

import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AimAssist extends Module {

    private SliderSetting mode;
    private SliderSetting speed;
    private SliderSetting multipointHorizontal;
    private SliderSetting multipointVertical;
    private SliderSetting randomization;
    private SliderSetting fov;
    private SliderSetting range;
    private SliderSetting sortMode;

    private ButtonSetting aimInvis;
    private ButtonSetting clickAim;
    private ButtonSetting ignoreTeammates;
    private ButtonSetting ignoreBehindWalls;
    private ButtonSetting ignoreBehindEntities;
    private ButtonSetting stopWhenBreaking;
    private ButtonSetting keepMoveDirection;
    private SliderSetting hoverDelay;
    private ButtonSetting weaponOnly;

    private long miningStartTime = -1;

    private String[] AIM_MODES = new String[]{"Normal", "Silent"};
    private String[] SORT_MODES = new String[]{"Health", "Angle", "Hurt time", "Distance"};

    public AimAssist() {
        super("Aim Assist", category.combat);
        this.registerSetting(mode = new SliderSetting("Mode", 0, AIM_MODES));
        this.registerSetting(speed = new SliderSetting("Speed", 10, 1, 30, 1));
        this.registerSetting(multipointHorizontal = new SliderSetting("Multipoint horizontal", "%", 0, 0, 100, 1));
        this.registerSetting(multipointVertical = new SliderSetting("Multipoint vertical", "%", 0, 0, 100, 1));
        this.registerSetting(randomization = new SliderSetting("Randomization", "%", 50, 0, 100, 1));
        this.registerSetting(fov = new SliderSetting("FOV", 90.0D, 15.0D, 360.0D, 1.0D));
        this.registerSetting(range = new SliderSetting("Range", 4.5D, 0.0D, 5.0D, 0.1D));
        this.registerSetting(sortMode = new SliderSetting("Sort", 1, SORT_MODES));
        this.registerSetting(ignoreBehindWalls = new ButtonSetting("Ignore behind walls", false));
        this.registerSetting(ignoreBehindEntities = new ButtonSetting("Ignore behind entities", false));
        this.registerSetting(aimInvis = new ButtonSetting("Aim invis", false));
        this.registerSetting(clickAim = new ButtonSetting("Require mouse", true));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(stopWhenBreaking = new ButtonSetting("Stop when breaking", false));
        this.registerSetting(keepMoveDirection = new ButtonSetting("Keep move direction", true));
        this.registerSetting(hoverDelay = new SliderSetting("Hover delay", " ms", 100, 0, 500, 10));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
    }

    @Override
    public String getInfo() {
        return AIM_MODES[(int) mode.getInput()];
    }

    @Override
    public void guiUpdate() {
        hoverDelay.setVisible(stopWhenBreaking.isToggled(), this);
        keepMoveDirection.setVisible(mode.getInput() == 1, this);
    }

    @Override
    public void onDisable() {
        miningStartTime = -1;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onClientRotation(ClientRotationEvent e) {
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            return;
        }
        if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target != null) return;
        if (mode.getInput() == 0 || !conditionsMet()) {
            return;
        }
        Entity en = getEnemy(true);
        if (en == null) {
            return;
        }

        int speedVal = (int) speed.getInput();
        double multipointH = multipointHorizontal.getInput();
        double multipointV = multipointVertical.getInput();
        float randomizationPercent = (float) randomization.getInput();
        boolean useBackup = ignoreBehindWalls.isToggled() || ignoreBehindEntities.isToggled();
        float[] rot = RotationHelper.get().getRotationsToTarget(en, e, speedVal, multipointH, multipointV, randomizationPercent, useBackup, range.getInput(), !ignoreBehindWalls.isToggled(), !ignoreBehindEntities.isToggled());
        if (rot == null) return;
        RotationHelper.get().forceMovementFix = true;
        RotationHelper.get().setServerRelativeMovementInputs(!keepMoveDirection.isToggled());
        e.yaw = rot[0];
        e.pitch = rot[1];
    }

    @Override
    public void onUpdate() {
        if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target != null) return;
        if (mode.getInput() == 1 || !conditionsMet()) {
            return;
        }
        Entity en = getEnemy(false);
        if (en == null) {
            return;
        }

        int speedVal = (int) speed.getInput();
        double multipointH = multipointHorizontal.getInput();
        double multipointV = multipointVertical.getInput();
        float randomizationPercent = (float) randomization.getInput();
        boolean useBackup = ignoreBehindWalls.isToggled() || ignoreBehindEntities.isToggled();
        float[] rot = RotationHelper.get().getRotationsToTarget(en, speedVal, multipointH, multipointV, randomizationPercent, useBackup, range.getInput(), !ignoreBehindWalls.isToggled(), !ignoreBehindEntities.isToggled());
        if (rot == null) return;
        mc.thePlayer.rotationYaw = rot[0];
        mc.thePlayer.rotationPitch = rot[1];
    }

    private Entity getEnemy(boolean silentMode) {
        final int fovVal = (int) this.fov.getInput();
        float viewYaw = mc.thePlayer.rotationYaw;
        if (silentMode) {
            Float serverYaw = RotationHelper.get().getServerYaw();
            if (serverYaw != null) {
                viewYaw = serverYaw;
            }
        }

        List<EntityPlayer> candidates = new ArrayList<>();
        for (EntityPlayer entityPlayer : mc.theWorld.playerEntities) {
            if (entityPlayer == mc.thePlayer || entityPlayer.deathTime != 0) {
                continue;
            }
            if (Utils.isFriended(entityPlayer)) {
                continue;
            }
            if (ignoreTeammates.isToggled() && Utils.isTeammate(entityPlayer)) {
                continue;
            }
            if (!aimInvis.isToggled() && entityPlayer.isInvisible()) {
                continue;
            }
            if (RotationUtils.distanceSqFromEyeToClosestOnAABB(entityPlayer) > range.getInput() * range.getInput()) {
                continue;
            }
            if (AntiBot.isBot(entityPlayer)) {
                continue;
            }
            if (fovVal != 360) {
                float angleToEntity = RotationUtils.angle(entityPlayer.posX, entityPlayer.posZ);
                if (!Utils.inFov(viewYaw, (float) fovVal, angleToEntity)) {
                    continue;
                }
            }
            candidates.add(entityPlayer);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Comparator<EntityPlayer> primary;
        switch ((int) sortMode.getInput()) {
            case 0: // Health (lower first)
                primary = Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorptionAmount());
                break;
            case 1: // Angle (smaller first) - use local player view, not server rotations
                primary = Comparator.comparingDouble(p -> {
                    double yawDelta = Math.abs(Utils.aimDifference(p, false));
                    double pitchDelta = Math.abs(Utils.pitchDifference(p, false));
                    return yawDelta + pitchDelta;
                });
                break;
            case 2: // Hurt time (lower first)
                primary = Comparator.<EntityPlayer>comparingInt(p -> p.hurtTime);
                break;
            case 3: // Distance (closer first)
                primary = Comparator.comparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p));
                break;
            default:
                primary = Comparator.comparingDouble(p -> {
                    double yawDelta = Math.abs(Utils.aimDifference(p, false));
                    double pitchDelta = Math.abs(Utils.pitchDifference(p, false));
                    return yawDelta + pitchDelta;
                });
        }
        candidates.sort(primary.thenComparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p)));

        if (ignoreBehindWalls.isToggled() || ignoreBehindEntities.isToggled()) {
            double multipointH = multipointHorizontal.getInput();
            double multipointV = multipointVertical.getInput();
            double rangeVal = range.getInput();
            boolean allowThroughBlocks = !ignoreBehindWalls.isToggled();
            boolean allowThroughEntities = !ignoreBehindEntities.isToggled();
            for (EntityPlayer candidate : candidates) {
                if (RotationUtils.hasValidAimPoint(candidate, multipointH, multipointV, rangeVal, allowThroughBlocks, allowThroughEntities)) {
                    return candidate;
                }
            }
            return null;
        }

        return candidates.get(0);
    }

    private boolean conditionsMet() {
        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            return false;
        }
        if (weaponOnly.isToggled() && !Utils.holdingWeapon()) {
            return false;
        }
        if (clickAim.isToggled() && !Mouse.isButtonDown(0)) {
            return false;
        }
        if (stopWhenBreaking.isToggled() && Utils.isMining()) {
            if (miningStartTime == -1) {
                miningStartTime = System.currentTimeMillis();
            }
            long elapsed = System.currentTimeMillis() - miningStartTime;
            if (elapsed >= hoverDelay.getInput()) {
                return false;
            }
        } else {
            miningStartTime = -1;
        }
        return true;
    }

}
