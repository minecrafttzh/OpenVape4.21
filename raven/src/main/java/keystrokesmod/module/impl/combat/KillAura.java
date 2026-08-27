package keystrokesmod.module.impl.combat;

import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.minigames.SkyWars;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityGiantZombie;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.*;

public class KillAura extends Module {
    private SliderSetting targetCPS;
    private SliderSetting fov;
    private SliderSetting attackRange;
    private SliderSetting swingRange;
    private SliderSetting aimRange;
    public SliderSetting rotationMode;
    private SliderSetting speed;
    private SliderSetting sortMode;
    private SliderSetting switchDelay;
    private SliderSetting targets;
    private ButtonSetting attackMobs;
    private ButtonSetting targetInvis;
    private ButtonSetting disableInInventory;
    private ButtonSetting disableWhileMining;
    private ButtonSetting aimThroughBlocks;
    private ButtonSetting aimThroughEntities;
    private ButtonSetting ignoreTeammates;
    private ButtonSetting prioritizeEnemies;
    private ButtonSetting notUsingItem;
    private ButtonSetting requireMouseDown;
    private ButtonSetting weaponOnly;

    private String[] rotationModes = new String[]{"Silent", "Lock view", "None"};
    private String[] sortModes = new String[]{"Distance", "Health", "Hurt time", "Yaw"};

    public static EntityLivingBase target;
    public static EntityLivingBase attackingEntity;

    public boolean isRequireMouseDown() {
        return requireMouseDown.isToggled();
    }

    private HashMap<Integer, Integer> hitMap = new HashMap<>();
    private List<Entity> hostileMobs = new ArrayList<>();
    private Map<Integer, Boolean> golems = new HashMap<>();

    private long nextClickTime;
    private Random rand;
    private double targetDistance = Double.MAX_VALUE;

    public KillAura() {
        super("Kill Aura", category.combat);
        this.registerSetting(targetCPS = new SliderSetting("Target CPS", 10.0, 1.0, 20.0, 0.5));
        this.registerSetting(fov = new SliderSetting("FOV", "°", 360.0, 30.0, 360.0, 4.0));
        this.registerSetting(attackRange = new SliderSetting("Range (attack)", 3.0, 3.0, 6.0, 0.05));
        this.registerSetting(swingRange = new SliderSetting("Range (swing)", 4.5, 3.0, 8.0, 0.05));
        this.registerSetting(aimRange = new SliderSetting("Range (aim)", 4.5, 3.0, 8.0, 0.05));
        this.registerSetting(rotationMode = new SliderSetting("Rotation mode", 0, rotationModes));
        this.registerSetting(speed = new SliderSetting("Speed", 10, 1, 30, 1));
        this.registerSetting(sortMode = new SliderSetting("Sort mode", 0, sortModes));
        this.registerSetting(switchDelay = new SliderSetting("Switch delay", "ms", 200.0, 50.0, 1000.0, 25.0));
        this.registerSetting(targets = new SliderSetting("Targets", 3.0, 1.0, 10.0, 1.0));
        this.registerSetting(targetInvis = new ButtonSetting("Target invis", true));
        this.registerSetting(attackMobs = new ButtonSetting("Attack mobs", false));
        this.registerSetting(aimThroughBlocks = new ButtonSetting("Hit through walls", false));
        this.registerSetting(aimThroughEntities = new ButtonSetting("Hit through entities", false));
        this.registerSetting(disableInInventory = new ButtonSetting("Disable in inventory", true));
        this.registerSetting(disableWhileMining = new ButtonSetting("Disable while mining", false));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(notUsingItem = new ButtonSetting("Not using item", false));
        this.registerSetting(prioritizeEnemies = new ButtonSetting("Prioritize enemies", false));
        this.registerSetting(requireMouseDown = new ButtonSetting("Require mouse down", false));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
    }

    @Override
    public String getInfo() {
        if (rotationMode.getInput() == 2) {
            return (int) this.fov.getInput() + fov.getSuffix();
        }
        return rotationModes[(int) rotationMode.getInput()];
    }

    @Override
    public void onEnable() {
        rand = new Random();
        nextClickTime = 0L;
    }

    @Override
    public void onDisable() {
        hitMap.clear();
        setTarget(null);
        nextClickTime = 0L;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onClientRotation(ClientRotationEvent e) {
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            return;
        }
        if (!basicCondition() || !settingCondition()) {
            setTarget(null);
            return;
        }
        handleTarget();
        if (target == null) {
            return;
        }
        targetDistance = RotationUtils.distanceFromEyeToClosestOnAABB(target);
        if (rotationMode.getInput() == 0) {
            double aimRangeVal = aimRange.getInput();
            if (targetDistance <= aimRangeVal) {
                int speedVal = (int) speed.getInput();
                boolean useBackup = !aimThroughBlocks.isToggled() || !aimThroughEntities.isToggled();
                float[] rot = RotationHelper.get().getRotationsToTarget(target, e, speedVal, 100, 100, 0f, useBackup, aimRangeVal, aimThroughBlocks.isToggled(), aimThroughEntities.isToggled());
                if (rot != null) {
                    e.yaw = rot[0];
                    e.pitch = rot[1];
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        if (rotationMode.getInput() == 1 && target != null) {
            double aimRangeVal = aimRange.getInput();
            if (targetDistance <= aimRangeVal) {
                int speedVal = (int) speed.getInput();
                boolean useBackup = !aimThroughBlocks.isToggled() || !aimThroughEntities.isToggled();
                float[] rot = RotationHelper.get().getRotationsToTarget(target, speedVal, 100, 100, 0f, useBackup, aimRangeVal, aimThroughBlocks.isToggled(), aimThroughEntities.isToggled());
                if (rot != null) {
                    mc.thePlayer.rotationYaw = rot[0];
                    mc.thePlayer.rotationPitch = rot[1];
                }
            }
        }

        if (target != null && targetDistance <= attackRange.getInput()) {
            attackingEntity = target;
        } else {
            attackingEntity = null;
        }
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (!Utils.nullCheck()) return;
        if (target == null) return;
        if (targetDistance > swingRange.getInput()) return;

        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        long now = System.currentTimeMillis();
        if (nextClickTime == 0) {
            nextClickTime = now;
        }
        int clicks = 0;
        while (nextClickTime <= now) {
            clicks++;
            nextClickTime += nextDelay();
        }

        if (!basicCondition() || !settingCondition()) return;
        if (notUsingItem.isToggled() && mc.thePlayer.isUsingItem()) return;

        for (int i = 0; i < clicks; i++) {
            KeyBinding.onTick(key);
            ReflectionUtils.setButton(0, true);
        }
    }

    @SubscribeEvent
    public void onSetAttackTarget(LivingSetAttackTargetEvent e) {
        if (e.entity != null && !hostileMobs.contains(e.entity)) {
            if (!(e.target instanceof EntityPlayer) || !e.target.getName().equals(mc.thePlayer.getName())) {
                return;
            }
            if (Utils.getBedwarsStatus() == 2 && e.entity instanceof EntityPigZombie) {
                return;
            }
            hostileMobs.add(e.entity);
        }
        if (e.target == null && hostileMobs.contains(e.entity)) {
            hostileMobs.remove(e.entity);
        }
    }

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            hitMap.clear();
            hostileMobs.clear();
            golems.clear();
        }
    }

    private void setTarget(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) {
            target = null;
            attackingEntity = null;
            targetDistance = Double.MAX_VALUE;
            nextClickTime = 0L;
        } else {
            target = (EntityLivingBase) entity;
        }
    }

    private void handleTarget() {
        double maxRange = Math.max(attackRange.getInput(), aimRange.getInput());
        float fovValue = (float) fov.getInput();

        List<KillAuraTarget> candidates = new ArrayList<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            Candidate candidate = getCandidateTarget(entity, maxRange, fovValue);
            if (candidate == null) {
                continue;
            }

            KillAuraTarget auraTarget = buildKillAuraTarget(candidate.entity, candidate.distance, maxRange);
            if (auraTarget != null) {
                candidates.add(auraTarget);
            }
        }

        if (prioritizeEnemies.isToggled()) {
            List<KillAuraTarget> enemies = new ArrayList<>();
            for (KillAuraTarget candidate : candidates) {
                if (candidate.isEnemy) {
                    enemies.add(candidate);
                }
            }
            if (!enemies.isEmpty()) {
                candidates = enemies;
            }
        }

        candidates.sort(getTargetComparator().thenComparingDouble(c -> c.distance));

        double attackRangeValue = attackRange.getInput();
        List<KillAuraTarget> attackTargets = new ArrayList<>();
        for (KillAuraTarget candidate : candidates) {
            if (candidate.distance <= attackRangeValue) {
                attackTargets.add(candidate);
            }
        }

        if (!attackTargets.isEmpty()) {
            KillAuraTarget selectedAttackTarget = selectAttackTarget(attackTargets);
            if (selectedAttackTarget != null) {
                setTarget(selectedAttackTarget.entity);
                return;
            }
            return;
        }

        if (!candidates.isEmpty()) {
            setTarget(candidates.get(0).entity);
            return;
        }

        setTarget(null);
    }

    private Candidate getCandidateTarget(Entity entity, double maxRange, float fovValue) {
        if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer || entity.isDead) {
            return null;
        }

        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (Utils.isFriended(player) || player.deathTime != 0) {
                return null;
            }
            if (AntiBot.isBot(entity) || (ignoreTeammates.isToggled() && Utils.isTeammate(entity))) {
                return null;
            }
        } else if (entity instanceof EntityCreature && attackMobs.isToggled()) {
            EntityCreature creature = (EntityCreature) entity;
            if (creature.tasks == null || creature.isAIDisabled() || creature.deathTime != 0) {
                return null;
            }

            String canonicalName = entity.getClass().getCanonicalName();
            if (canonicalName == null || !canonicalName.startsWith("net.minecraft.entity.monster.")) {
                return null;
            }
        } else {
            return null;
        }

        if (entity.isInvisible() && !targetInvis.isToggled()) {
            return null;
        }

        if (fovValue != 360.0f && !Utils.inFov(fovValue, entity)) {
            return null;
        }

        double distance = RotationUtils.distanceFromEyeToClosestOnAABB(entity);
        if (distance > maxRange) {
            return null;
        }

        return new Candidate((EntityLivingBase) entity, distance);
    }

    private KillAuraTarget buildKillAuraTarget(EntityLivingBase entity, double distanceToBoundingBox, double maxRange) {
        if (entity instanceof EntityCreature && attackMobs.isToggled() && !isHostile((EntityCreature) entity)) {
            return null;
        }

        double multipointH = 100;
        double multipointV = 100;
        if (!RotationUtils.hasValidAimPoint(entity, multipointH, multipointV, maxRange, aimThroughBlocks.isToggled(), aimThroughEntities.isToggled())) {
            return null;
        }

        boolean isEnemyPlayer = entity instanceof EntityPlayer && Utils.isEnemy((EntityPlayer) entity);
        return new KillAuraTarget(
                entity,
                distanceToBoundingBox,
                entity.getHealth(),
                entity.hurtTime,
                RotationUtils.distanceFromYaw(entity, false),
                entity.getEntityId(),
                isEnemyPlayer
        );
    }

    private Comparator<KillAuraTarget> getTargetComparator() {
        switch ((int) sortMode.getInput()) {
            case 1:
                return Comparator.comparingDouble(target -> target.health);
            case 2:
                return Comparator.comparingInt(target -> target.hurttime);
            case 3:
                return Comparator.comparingDouble(target -> target.yawDelta);
            case 0:
            default:
                return Comparator.comparingDouble(target -> target.distance);
        }
    }

    private KillAuraTarget selectAttackTarget(List<KillAuraTarget> attackTargets) {
        int ticksExisted = mc.thePlayer.ticksExisted;
        int switchDelayTicks = (int) (switchDelay.getInput() / 50);
        long noHitTicks = (long) Math.min(attackTargets.size(), targets.getInput()) * switchDelayTicks;

        for (KillAuraTarget candidate : attackTargets) {
            Integer firstHitTick = hitMap.get(candidate.entityId);
            if (firstHitTick == null || ticksExisted - firstHitTick >= switchDelayTicks) {
                continue;
            }
            return candidate;
        }

        for (KillAuraTarget candidate : attackTargets) {
            Integer firstHitTick = hitMap.get(candidate.entityId);
            if (firstHitTick == null || ticksExisted >= firstHitTick + noHitTicks) {
                hitMap.put(candidate.entityId, ticksExisted);
                return candidate;
            }
        }

        return null;
    }

    private boolean isHostile(EntityCreature entityCreature) {
        if (SkyWars.onlyAuraHostiles()) {
            if (entityCreature instanceof EntityGiantZombie) {
                return false;
            }
            return !ModuleManager.skyWars.spawnedMobs.contains(entityCreature.getEntityId());
        } else if (entityCreature instanceof EntitySilverfish) {
            String teamColor = Utils.getFirstColorCode(entityCreature.getCustomNameTag());
            String teamColorSelf = Utils.getFirstColorCode(mc.thePlayer.getDisplayName().getFormattedText());
            return teamColor.isEmpty() || (!teamColorSelf.equals(teamColor) && !Utils.isTeammate(entityCreature));
        } else if (entityCreature instanceof EntityIronGolem) {
            if (Utils.getBedwarsStatus() != 2) {
                return true;
            }
            if (!golems.containsKey(entityCreature.getEntityId())) {
                double nearestDistance = -1;
                EntityArmorStand nearestArmorStand = null;
                for (Entity entity : mc.theWorld.loadedEntityList) {
                    if (!(entity instanceof EntityArmorStand)) {
                        continue;
                    }
                    String stripped = Utils.stripString(entity.getDisplayName().getFormattedText());
                    if (stripped.contains("[") && stripped.endsWith("]")) {
                        double distanceSq = entity.getDistanceSq(entityCreature.posX, entityCreature.posY, entityCreature.posZ);
                        if (distanceSq < nearestDistance || nearestDistance == -1) {
                            nearestDistance = distanceSq;
                            nearestArmorStand = (EntityArmorStand) entity;
                        }
                    }
                }
                if (nearestArmorStand != null) {
                    String teamColor = Utils.getFirstColorCode(nearestArmorStand.getDisplayName().getFormattedText());
                    String teamColorSelf = Utils.getFirstColorCode(mc.thePlayer.getDisplayName().getFormattedText());
                    boolean isTeam = !teamColor.isEmpty() && (teamColorSelf.equals(teamColor) || Utils.isTeammate(nearestArmorStand));
                    golems.put(entityCreature.getEntityId(), isTeam);
                    return !isTeam;
                }
                return !ModuleManager.bedwars.spawnedMobs.contains(entityCreature.getEntityId());
            } else {
                return !golems.getOrDefault(entityCreature.getEntityId(), false);
            }
        } else if (entityCreature instanceof EntityPigZombie && Utils.getBedwarsStatus() != 2) {
            return false;
        }
        return hostileMobs.contains(entityCreature);
    }

    private boolean basicCondition() {
        if (!Utils.nullCheck()) {
            return false;
        }
        return !mc.thePlayer.isDead;
    }

    private boolean settingCondition() {
        if (requireMouseDown.isToggled() && !Mouse.isButtonDown(0)) {
            return false;
        } else if (weaponOnly.isToggled() && !Utils.holdingWeapon()) {
            return false;
        } else if (disableWhileMining.isToggled() && Utils.isMining()) {
            return false;
        } else if (disableInInventory.isToggled() && mc.currentScreen != null) {
            return false;
        }
        return true;
    }

    private long nextDelay() {
        int cps = Math.max(1, (int) targetCPS.getInput());
        int baseDelay = 1000 / cps;
        int finalDelay = baseDelay + (rand.nextInt(21) - 10);
        return Math.max(33, Math.min(180, finalDelay));
    }

    public SliderSetting getAttackRangeSetting() {
        return attackRange;
    }

    public SliderSetting getSwingRangeSetting() {
        return swingRange;
    }

    public SliderSetting getAimRangeSetting() {
        return aimRange;
    }

    public boolean shouldOverrideMouseOver() {
        return this.isEnabled()
                && Utils.nullCheck()
                && attackingEntity != null
                && target == attackingEntity
                && basicCondition()
                && targetDistance <= swingRange.getInput();
    }

    public void modifyMouseOverFromGetMouseOver(float partialTicks) {
        if (!shouldOverrideMouseOver()) {
            return;
        }

        Entity viewEntity = mc.getRenderViewEntity();
        if (viewEntity == null) {
            return;
        }

        Vec3 eyes = viewEntity.getPositionEyes(partialTicks);
        Vec3 look = viewEntity.getLook(partialTicks);
        double reach = attackRange.getInput();
        Vec3 rayEnd = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);

        float border = attackingEntity.getCollisionBorderSize();
        AxisAlignedBB bb = attackingEntity.getEntityBoundingBox().expand(border, border, border);
        MovingObjectPosition intercept = bb.calculateIntercept(eyes, rayEnd);
        boolean inside = bb.isVecInside(eyes);
        if (!inside && intercept == null) {
            return;
        }

        Vec3 hitVec = inside ? (intercept == null ? eyes : intercept.hitVec) : intercept.hitVec;
        if (!aimThroughBlocks.isToggled()) {
            MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyes, hitVec, false, false, true);
            if (blockHit != null && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                return;
            }
        }
        if (!aimThroughEntities.isToggled() && RotationUtils.isPathBlockedByEntity(eyes, hitVec, attackingEntity)) {
            return;
        }

        mc.objectMouseOver = new MovingObjectPosition(attackingEntity, hitVec);
        mc.pointedEntity = attackingEntity;

        EntityRenderer renderer = mc.entityRenderer;
        if (renderer instanceof IAccessorEntityRenderer) {
            ((IAccessorEntityRenderer) renderer).setPointedEntity(attackingEntity);
        }
    }

    private static final class Candidate {
        final EntityLivingBase entity;
        final double distance;

        Candidate(EntityLivingBase entity, double distance) {
            this.entity = entity;
            this.distance = distance;
        }
    }

    static class KillAuraTarget {
        final EntityLivingBase entity;
        final double distance;
        final float health;
        final int hurttime;
        final double yawDelta;
        final int entityId;
        final boolean isEnemy;

        public KillAuraTarget(EntityLivingBase entity, double distance, float health, int hurttime, double yawDelta, int entityId, boolean isEnemy) {
            this.entity = entity;
            this.distance = distance;
            this.health = health;
            this.hurttime = hurttime;
            this.yawDelta = yawDelta;
            this.entityId = entityId;
            this.isEnemy = isEnemy;
        }
    }
}
