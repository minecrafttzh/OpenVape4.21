package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class HitParticles extends Module {

    private static final int PARTICLE_CRIT = 4;
    private static final int PARTICLE_CRIT_MAGIC = 13;
    private static final double ARROW_SCAN_EXPAND = 96.0;
    private static final int[] ARGS_NONE = new int[0];
    private static final Random RANDOM = new Random();

    private final ButtonSetting onMelee;
    private final ButtonSetting onRanged;
    private final SliderSetting meleeParticle;
    private final SliderSetting rangedParticle;
    private final SliderSetting meleeMultiplier;
    private final SliderSetting rangedMultiplier;

    private final Map<Integer, Integer> rangedSpawnForArrow = new HashMap<>();

    private static final String[] PARTICLE_NAMES = new String[] {
            "Angry Villager",
            "Blood",
            "Cloud",
            "Confetti",
            "Critical",
            "Crit/Magic Crit",
            "Enchantment",
            "Explosion",
            "Flame",
            "Happy Villager",
            "Heart",
            "Instant Spell",
            "Lava",
            "Magic Critical",
            "Mob Spell",
            "Music Note",
            "Portal",
            "Slime",
            "Smoke",
            "Snow",
            "Spark",
            "Spell",
            "Splash",
            "Witch"
    };

    private static final int[] PARTICLE_IDS = new int[] {
            20, 37, 32, 30, 9, -1, 25, 1, 26, 21, 34, 14, 27, 10, 15, 23, 24, 33, 11, 31, 3, 13, 5, 17
    };

    private static final int[] PARTICLE_COUNTS = new int[] {
            8, 32, 16, 16, 32, -1, 16, 1, 16, 16, 8, 16, 16, 32, 16, 8, 32, 16, 16, 16, 16, 16, 32, 16
    };

    private static final float[] PARTICLE_OFFSETS = new float[] {
            2.5f, 1.2f, 0.2f, 3.0f, 1.0f, -1.0f, 1.0f, 1.0f, 0.05f, 2.5f, 2.0f, 0.7f, 1.0f, 1.0f, 1.0f, 3.0f, 1.0f, 0.5f, 0.08f, 1.0f, 0.1f, 1.0f, 1.0f, 0.5f
    };

    private static final boolean[] PARTICLE_IGNORE_DIST = new boolean[] {
            true, true, false, true, false, false, true, true, false, true, true, false, false, false, false, true, true, false, true, true, false, false, false, false
    };

    private static final int[][] PARTICLE_ARGS = new int[][] {
            ARGS_NONE,
            new int[] { Block.getIdFromBlock(Blocks.netherrack) },
            ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE,
            ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE,
            ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE, ARGS_NONE
    };

    public HitParticles() {
        super("Hit Particles", category.render);
        this.registerSetting(new DescriptionSetting("Melee"));
        this.registerSetting(onMelee = new ButtonSetting("Melee hits", true));
        this.registerSetting(meleeParticle = new SliderSetting("Melee particle", 4, PARTICLE_NAMES));
        this.registerSetting(meleeMultiplier = new SliderSetting("Melee multiplier", 1.0, 1.0, 8.0, 1.0));

        this.registerSetting(new DescriptionSetting("Ranged"));
        this.registerSetting(onRanged = new ButtonSetting("Arrow hits", true));
        this.registerSetting(rangedParticle = new SliderSetting("Ranged particle", 4, PARTICLE_NAMES));
        this.registerSetting(rangedMultiplier = new SliderSetting("Ranged multiplier", 1.0, 1.0, 8.0, 1.0));
    }

    @Override
    public void onDisable() {
        rangedSpawnForArrow.clear();
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!isEnabled() || !onMelee.isToggled() || !Utils.nullCheck()) {
            return;
        }
        if (event.entityPlayer != mc.thePlayer || !(event.target instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase target = (EntityLivingBase) event.target;
        if (target.hurtTime > 5 || target.hurtResistantTime > 0) {
            return;
        }
        int idx = (int) meleeParticle.getInput();
        spawnParticleType(idx, target, (int) meleeMultiplier.getInput());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!isEnabled() || !onRanged.isToggled() || event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }

        pruneArrowDedupeMap();

        AxisAlignedBB scan = mc.thePlayer.getEntityBoundingBox().expand(ARROW_SCAN_EXPAND, ARROW_SCAN_EXPAND, ARROW_SCAN_EXPAND);
        @SuppressWarnings("unchecked")
        List<EntityArrow> arrows = mc.theWorld.getEntitiesWithinAABB(EntityArrow.class, scan);
        for (int i = 0, n = arrows.size(); i < n; i++) {
            EntityArrow arrow = arrows.get(i);
            if (arrow.shootingEntity != mc.thePlayer) {
                continue;
            }
            EntityLivingBase target = getCollisionEntity(arrow);
            if (target == null || target.hurtTime > 0) {
                continue;
            }
            int aid = arrow.getEntityId();
            int tid = target.getEntityId();
            Integer prev = rangedSpawnForArrow.get(aid);
            if (prev != null && prev == tid) {
                continue;
            }
            rangedSpawnForArrow.put(aid, tid);
            int idx = (int) rangedParticle.getInput();
            spawnParticleType(idx, target, (int) rangedMultiplier.getInput());
        }
    }

    private void pruneArrowDedupeMap() {
        Iterator<Map.Entry<Integer, Integer>> it = rangedSpawnForArrow.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            if (mc.theWorld.getEntityByID(entry.getKey()) == null) {
                it.remove();
            }
        }
    }

    private void spawnParticleType(int index, Entity entity, int multiplier) {
        if (index < 0 || index >= PARTICLE_IDS.length) {
            return;
        }
        int id = PARTICLE_IDS[index];
        if (id == -1) {
            spawnParticleRaw(9, entity, multiplier, PARTICLE_COUNTS[PARTICLE_CRIT], PARTICLE_OFFSETS[PARTICLE_CRIT], PARTICLE_IGNORE_DIST[PARTICLE_CRIT], PARTICLE_ARGS[PARTICLE_CRIT]);
            spawnParticleRaw(10, entity, multiplier, PARTICLE_COUNTS[PARTICLE_CRIT_MAGIC], PARTICLE_OFFSETS[PARTICLE_CRIT_MAGIC], PARTICLE_IGNORE_DIST[PARTICLE_CRIT_MAGIC], PARTICLE_ARGS[PARTICLE_CRIT_MAGIC]);
        }
        else {
            spawnParticleRaw(id, entity, multiplier, PARTICLE_COUNTS[index], PARTICLE_OFFSETS[index], PARTICLE_IGNORE_DIST[index], PARTICLE_ARGS[index]);
        }
    }

    private static void spawnParticleRaw(int id, Entity entity, int multiplier, int count, float offset, boolean ignoreDistance, int[] args) {
        for (int i = 0; i < count * multiplier; i++) {
            double xOffset = RANDOM.nextFloat() * (offset * 2.0f) - offset;
            double yOffset = RANDOM.nextFloat() * (offset * 2.0f) - offset;
            double zOffset = RANDOM.nextFloat() * (offset * 2.0f) - offset;
            if (ignoreDistance || xOffset * xOffset + yOffset * yOffset + zOffset * zOffset <= 1.0) {
                double x = entity.posX + xOffset * entity.width / 4.0;
                double y = entity.getEntityBoundingBox().minY + entity.height / 2.0f + yOffset * entity.height / 4.0;
                double z = entity.posZ + zOffset * entity.width / 4.0;
                mc.effectRenderer.spawnEffectParticle(id, x, y, z, xOffset, yOffset, zOffset, args);
            }
        }
    }

    private static EntityLivingBase getCollisionEntity(EntityArrow arrow) {
        World world = arrow.worldObj;
        Vec3 pos = new Vec3(arrow.posX, arrow.posY, arrow.posZ);
        Vec3 motionEnd = new Vec3(arrow.posX + arrow.motionX, arrow.posY + arrow.motionY, arrow.posZ + arrow.motionZ);
        MovingObjectPosition rayTrace = world.rayTraceBlocks(pos, motionEnd, false, true, false);
        Vec3 traceEnd = motionEnd;
        if (rayTrace != null) {
            traceEnd = rayTrace.hitVec;
        }

        EntityLivingBase target = null;
        double closestSq = 0.0;
        AxisAlignedBB search = arrow.getEntityBoundingBox()
                .addCoord(arrow.motionX, arrow.motionY, arrow.motionZ)
                .expand(1.0, 1.0, 1.0);
        @SuppressWarnings("unchecked")
        List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(arrow, search);

        for (Entity entity : entities) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!living.canBeCollidedWith()) {
                continue;
            }
            if (living == arrow.shootingEntity) {
                continue;
            }
            AxisAlignedBB collisionBox = entity.getEntityBoundingBox().expand(0.3, 0.3, 0.3);
            MovingObjectPosition collision = collisionBox.calculateIntercept(pos, traceEnd);
            if (collision == null) {
                continue;
            }
            double distSq = pos.squareDistanceTo(collision.hitVec);
            if (distSq >= closestSq && closestSq != 0.0) {
                continue;
            }
            target = living;
            closestSq = distSq;
        }

        return target;
    }
}
