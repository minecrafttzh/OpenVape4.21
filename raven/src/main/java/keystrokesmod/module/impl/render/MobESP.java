package keystrokesmod.module.impl.render;

import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.shader.GlowShader;
import keystrokesmod.utility.shader.OutlineShader;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class MobESP extends Module {

    public static boolean renderingOutlinePass = false;
    private static final ThreadLocal<Boolean> MOB_CHAMS_ACTIVE = ThreadLocal.withInitial(() -> false);

    public GroupSetting espTypes;
    public ButtonSetting twoD;
    public ButtonSetting box;
    public ButtonSetting outline;
    public ButtonSetting chams;
    public ButtonSetting ring;
    public ButtonSetting shaded;
    public ButtonSetting healthBar;
    public ButtonSetting redOnDamage;
    public ButtonSetting showInvis;

    private final SliderSetting maxDistance;

    private final List<MobEntry> mobEntries = new ArrayList<>();
    private final Map<EntityLivingBase, Integer> renderAsTwoD = new HashMap<>();

    private Framebuffer outlineFramebuffer;
    private final OutlineShader outlineShader = new OutlineShader();
    private final GlowShader glowShader = new GlowShader();

    private static final class MobEntry {
        final Class<? extends EntityLivingBase> type;
        final Predicate<EntityLivingBase> refine;
        final ButtonSetting enable;
        final ColorSetting color;

        MobEntry(Class<? extends EntityLivingBase> type, Predicate<EntityLivingBase> refine, ButtonSetting enable, ColorSetting color) {
            this.type = type;
            this.refine = refine;
            this.enable = enable;
            this.color = color;
        }

        boolean matches(EntityLivingBase entity) {
            if (!type.isInstance(entity)) {
                return false;
            }
            return refine == null || refine.test(entity);
        }
    }

    public MobESP() {
        super("MobESP", category.render);
        this.registerSetting(espTypes = new GroupSetting("Types"));
        this.registerSetting(twoD = new ButtonSetting(espTypes, "2D", false));
        this.registerSetting(box = new ButtonSetting(espTypes, "Box", false));
        this.registerSetting(outline = new ButtonSetting(espTypes, "Outline", false));
        this.registerSetting(chams = new ButtonSetting(espTypes, "Chams", false));
        this.registerSetting(ring = new ButtonSetting(espTypes, "Ring", false));
        this.registerSetting(shaded = new ButtonSetting(espTypes, "Shaded", false));
        this.registerSetting(healthBar = new ButtonSetting(espTypes, "Health bar", false));
        this.registerSetting(redOnDamage = new ButtonSetting("Red on damage", true));
        this.registerSetting(showInvis = new ButtonSetting("Show invis", true));
        this.registerSetting(maxDistance = new SliderSetting("Max distance", 128.0, 32.0, 256.0, 8.0));

        registerMobGroups();
    }

    private void registerMobGroups() {
        registerMob("Armor Stand", EntityArmorStand.class, false, 200, 200, 200);
        registerMob("Baby Zombie", EntityZombie.class, e -> !((EntityZombie) e).isVillager() && ((EntityZombie) e).isChild(), true, 0, 120, 255);
        registerMob("Bat", EntityBat.class, false, 80, 80, 80);
        registerMob("Blaze", EntityBlaze.class, true, 255, 165, 0);
        registerMob("Cave Spider", EntityCaveSpider.class, true, 64, 64, 64);
        registerMob("Chicken", EntityChicken.class, false, 255, 255, 255);
        registerMob("Cow", EntityCow.class, false, 120, 80, 60);
        registerMob("Creeper", EntityCreeper.class, true, 0, 255, 0);
        registerMob("Donkey", EntityHorse.class, e -> ((EntityHorse) e).getHorseType() == 1, false, 120, 120, 120);
        registerMob("Elder Guardian", EntityGuardian.class, e -> ((EntityGuardian) e).isElder(), false, 0, 90, 140);
        registerMob("Ender Dragon", EntityDragon.class, false, 200, 0, 200);
        registerMob("Enderman", EntityEnderman.class, true, 0, 0, 0);
        registerMob("Endermite", EntityEndermite.class, false, 80, 0, 120);
        registerMob("Ghast", EntityGhast.class, true, 255, 255, 255);
        registerMob("Giant", EntityGiantZombie.class, false, 0, 0, 139);
        registerMob("Guardian", EntityGuardian.class, g -> !((EntityGuardian) g).isElder(), false, 0, 150, 180);
        registerMob("Horse", EntityHorse.class, e -> ((EntityHorse) e).getHorseType() == 0, false, 150, 100, 60);
        registerMob("Iron Golem", EntityIronGolem.class, false, 200, 200, 200);
        registerMob("Magma Cube", EntityMagmaCube.class, false, 200, 60, 0);
        registerMob("Mooshroom", EntityMooshroom.class, false, 200, 0, 0);
        registerMob("Mule", EntityHorse.class, e -> ((EntityHorse) e).getHorseType() == 2, false, 130, 110, 80);
        registerMob("Ocelot", EntityOcelot.class, false, 200, 150, 100);
        registerMob("Pig", EntityPig.class, false, 255, 150, 200);
        registerMob("Rabbit", EntityRabbit.class, false, 180, 140, 100);
        registerMob("Sheep", EntitySheep.class, false, 255, 255, 255);
        registerMob("Silverfish", EntitySilverfish.class, true, 128, 128, 128);
        registerMob("Skeleton", EntitySkeleton.class, e -> ((EntitySkeleton) e).getSkeletonType() == 0, true, 255, 255, 255);
        registerMob("Skeleton Horse", EntityHorse.class, e -> ((EntityHorse) e).getHorseType() == 4, false, 220, 220, 220);
        registerMob("Slime", EntitySlime.class, true, 0, 255, 0);
        registerMob("Snow Golem", EntitySnowman.class, false, 255, 255, 255);
        registerMob("Spider", EntitySpider.class, true, 0, 0, 0);
        registerMob("Squid", EntitySquid.class, false, 30, 60, 180);
        registerMob("Villager", EntityVillager.class, false, 180, 150, 120);
        registerMob("Witch", EntityWitch.class, false, 90, 50, 120);
        registerMob("Wither", EntityWither.class, false, 64, 64, 64);
        registerMob("Wither Skeleton", EntitySkeleton.class, e -> ((EntitySkeleton) e).getSkeletonType() == 1, true, 55, 55, 55);
        registerMob("Wolf", EntityWolf.class, false, 200, 200, 200);
        registerMob("Zombie", EntityZombie.class, e -> !((EntityZombie) e).isVillager() && !((EntityZombie) e).isChild(), true, 0, 0, 255);
        registerMob("Zombie Horse", EntityHorse.class, e -> ((EntityHorse) e).getHorseType() == 3, false, 80, 100, 70);
        registerMob("Zombie Pigman", EntityPigZombie.class, true, 255, 192, 203);
        registerMob("Zombie Villager", EntityZombie.class, e -> ((EntityZombie) e).isVillager(), true, 100, 140, 90);
    }

    private void registerMob(String name, Class<? extends EntityLivingBase> clazz, boolean defaultOn, int r, int g, int b) {
        registerMob(name, clazz, null, defaultOn, r, g, b);
    }

    private void registerMob(String name, Class<? extends EntityLivingBase> clazz, Predicate<EntityLivingBase> refine, boolean defaultOn, int r, int g, int b) {
        GroupSetting group = new GroupSetting(name);
        this.registerSetting(group);
        ButtonSetting enable = new ButtonSetting(group, "Enable", defaultOn);
        this.registerSetting(enable);
        ColorSetting color = new ColorSetting(group, "Color", r, g, b);
        this.registerSetting(color);
        mobEntries.add(new MobEntry(clazz, refine, enable, color));
    }

    private MobEntry resolveEntry(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            return null;
        }
        MobEntry best = null;
        for (MobEntry entry : mobEntries) {
            if (!entry.enable.isToggled() || !entry.matches(entity)) {
                continue;
            }
            if (best == null || best.type.isAssignableFrom(entry.type)) {
                best = entry;
            }
        }
        return best;
    }

    private int rgbFor(EntityLivingBase ent, MobEntry entry) {
        int rgb = Utils.mergeAlpha(entry.color.getRGB(), 255);
        if (redOnDamage.isToggled() && ent.hurtTime != 0) {
            rgb = 0xFFFF0000;
        }
        return rgb;
    }

    public static void onRenderMobPre(EntityLivingBase entity) {
        MobESP mod = getMobEspModule();
        if (mod == null || !mod.shouldApplyChamsTo(entity)) {
            return;
        }
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(1.0f, -1_100_000.0f);
        MOB_CHAMS_ACTIVE.set(true);
    }

    public static void onRenderMobPost() {
        if (!Boolean.TRUE.equals(MOB_CHAMS_ACTIVE.get())) {
            return;
        }
        MOB_CHAMS_ACTIVE.set(false);
        GL11.glPolygonOffset(1.0f, 1_100_000.0f);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    private static MobESP getMobEspModule() {
        Module module = ModuleManager.getModule(MobESP.class);
        return module instanceof MobESP && module.isEnabled() ? (MobESP) module : null;
    }

    private boolean shouldApplyChamsTo(EntityLivingBase entity) {
        if (!chams.isToggled() || !Utils.nullCheck() || entity == null || entity == mc.thePlayer) {
            return false;
        }
        if (entity.deathTime != 0) {
            return false;
        }
        if (!showInvis.isToggled() && entity.isInvisible()) {
            return false;
        }
        double maxDistSq = maxDistance.getInput() * maxDistance.getInput();
        if (!RenderUtils.isWithinDistanceSqToRenderView(entity, maxDistSq)) {
            return false;
        }
        return resolveEntry(entity) != null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        this.renderAsTwoD.clear();
        if (!Utils.nullCheck() || !this.isEnabled()) {
            return;
        }
        double maxDistSq = maxDistance.getInput() * maxDistance.getInput();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.deathTime != 0) {
                continue;
            }
            if (!showInvis.isToggled() && living.isInvisible()) {
                continue;
            }
            if (!RenderUtils.isWithinDistanceSqToRenderView(living, maxDistSq)) {
                continue;
            }
            MobEntry entry = resolveEntry(living);
            if (entry == null) {
                continue;
            }
            int rgb = rgbFor(living, entry);
            this.renderAsTwoD.put(living, rgb);
            this.render(living, rgb);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderWorldLast2D(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || !this.isEnabled()) {
            return;
        }
        if (outline.isToggled()) {
            runOutlinePass(e.partialTicks);
        }
        if (twoD.isToggled()) {
            for (Map.Entry<EntityLivingBase, Integer> entry : renderAsTwoD.entrySet()) {
                int col = redOnDamage.isToggled() && entry.getKey().hurtTime != 0 ? 0xFFFF0000 : entry.getValue();
                renderTwoD(entry.getKey(), col, 0, e.partialTicks);
            }
        }
    }

    private void runOutlinePass(float partialTicks) {
        if (!outlineShader.isValid() || !glowShader.isValid() || renderAsTwoD.isEmpty()) {
            return;
        }
        boolean anyVisible = false;
        for (EntityLivingBase ent : renderAsTwoD.keySet()) {
            if (RenderUtils.isInViewFrustum(ent)) {
                anyVisible = true;
                break;
            }
        }
        if (!anyVisible) {
            return;
        }
        outlineFramebuffer = RenderUtils.createFrameBuffer(outlineFramebuffer, false);
        if (outlineFramebuffer == null) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        outlineFramebuffer.bindFramebuffer(false);
        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(partialTicks, 0);
        boolean shadows = mc.gameSettings.entityShadows;
        mc.gameSettings.entityShadows = false;
        renderingOutlinePass = true;

        glowShader.use();
        for (Map.Entry<EntityLivingBase, Integer> e : renderAsTwoD.entrySet()) {
            EntityLivingBase ent = e.getKey();
            if (!RenderUtils.isInViewFrustum(ent)) {
                continue;
            }
            int col = redOnDamage.isToggled() && ent.hurtTime != 0 ? 0xFFFF0000 : e.getValue();
            glowShader.setColor((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, (col >> 24) & 0xFF);
            boolean invis = ent.isInvisible();
            if (showInvis.isToggled()) {
                ent.setInvisible(false);
            }
            mc.getRenderManager().renderEntityStatic(ent, partialTicks, true);
            ent.setInvisible(invis);
        }
        glowShader.stop();
        renderingOutlinePass = false;

        mc.gameSettings.entityShadows = shadows;
        mc.entityRenderer.disableLightmap();
        mc.entityRenderer.setupOverlayRendering();
        mc.getFramebuffer().bindFramebuffer(false);
        outlineShader.use();
        RenderUtils.drawFramebufferFullscreen(outlineFramebuffer);
        outlineShader.stop();
        outlineFramebuffer.framebufferClear();
        mc.getFramebuffer().bindFramebuffer(false);
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    private void render(Entity en, int rgb) {
        if (!box.isToggled() && !shaded.isToggled() && !healthBar.isToggled() && !ring.isToggled()) {
            return;
        }
        if (!RenderUtils.isInViewFrustum(en)) {
            return;
        }
        if (box.isToggled()) {
            RenderUtils.renderEntity(en, 1, 0, 0, rgb, redOnDamage.isToggled());
        }
        if (shaded.isToggled()) {
            if (ModuleManager.murderMystery == null || !ModuleManager.murderMystery.isEnabled() || ModuleManager.murderMystery.isEmpty()) {
                RenderUtils.renderEntity(en, 2, 0, 0, rgb, redOnDamage.isToggled());
            }
        }
        if (healthBar.isToggled()) {
            RenderUtils.renderEntity(en, 4, 0, 0, rgb, redOnDamage.isToggled());
        }
        if (ring.isToggled()) {
            RenderUtils.renderEntity(en, 6, 0, 0, rgb, redOnDamage.isToggled());
        }
    }

    private void renderTwoD(EntityLivingBase en, int rgb, double expand, float partialTicks) {
        if (!RenderUtils.isInViewFrustum(en)) {
            return;
        }
        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(((IAccessorMinecraft) mc).getTimer().renderPartialTicks, 0);

        double playerX = en.lastTickPosX + (en.posX - en.lastTickPosX) * partialTicks - mc.getRenderManager().viewerPosX;
        double playerY = en.lastTickPosY + (en.posY - en.lastTickPosY) * partialTicks - mc.getRenderManager().viewerPosY;
        double playerZ = en.lastTickPosZ + (en.posZ - en.lastTickPosZ) * partialTicks - mc.getRenderManager().viewerPosZ;

        AxisAlignedBB bbox = en.getEntityBoundingBox().expand(0.1D + expand, 0.1D + expand, 0.1D + expand);
        AxisAlignedBB axis = new AxisAlignedBB(
                bbox.minX - en.posX + playerX,
                bbox.minY - en.posY + playerY,
                bbox.minZ - en.posZ + playerZ,
                bbox.maxX - en.posX + playerX,
                bbox.maxY - en.posY + playerY,
                bbox.maxZ - en.posZ + playerZ
        );

        Vec3[] corners = new Vec3[8];
        corners[0] = new Vec3(axis.minX, axis.minY, axis.minZ);
        corners[1] = new Vec3(axis.minX, axis.minY, axis.maxZ);
        corners[2] = new Vec3(axis.minX, axis.maxY, axis.minZ);
        corners[3] = new Vec3(axis.minX, axis.maxY, axis.maxZ);
        corners[4] = new Vec3(axis.maxX, axis.minY, axis.minZ);
        corners[5] = new Vec3(axis.maxX, axis.minY, axis.maxZ);
        corners[6] = new Vec3(axis.maxX, axis.maxY, axis.minZ);
        corners[7] = new Vec3(axis.maxX, axis.maxY, axis.maxZ);

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        boolean isInView = false;
        ScaledResolution scaledResolution = new ScaledResolution(mc);

        for (Vec3 corner : corners) {
            Vec3 screenVec = RenderUtils.convertTo2D(scaledResolution.getScaleFactor(), corner.xCoord, corner.yCoord, corner.zCoord);
            if (screenVec != null) {
                if (screenVec.zCoord >= 1.0003684 || screenVec.zCoord <= 0) {
                    continue;
                }
                isInView = true;
                double screenX = screenVec.xCoord;
                double screenY = screenVec.yCoord;
                if (screenX < minX) {
                    minX = screenX;
                }
                if (screenY < minY) {
                    minY = screenY;
                }
                if (screenX > maxX) {
                    maxX = screenX;
                }
                if (screenY > maxY) {
                    maxY = screenY;
                }
            }
        }

        if (!isInView) {
            return;
        }

        mc.entityRenderer.setupOverlayRendering();

        ScaledResolution res = new ScaledResolution(mc);
        int screenWidth = res.getScaledWidth();
        int screenHeight = res.getScaledHeight();

        minX = Math.max(0, minX);
        minY = Math.max(0, minY);
        maxX = Math.min(screenWidth, maxX);
        maxY = Math.min(screenHeight, maxY);

        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0F);

        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.4F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2d(minX, minY);
        GL11.glVertex2d(maxX, minY);
        GL11.glVertex2d(maxX, maxY);
        GL11.glVertex2d(minX, maxY);
        GL11.glEnd();

        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.4F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2d(minX + 1.0, minY + 1.0);
        GL11.glVertex2d(maxX - 1.0, minY + 1.0);
        GL11.glVertex2d(maxX - 1.0, maxY - 1.0);
        GL11.glVertex2d(minX + 1.0, maxY - 1.0);
        GL11.glEnd();

        GL11.glColor4f(red, green, blue, 1.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2d(minX + 0.5, minY + 0.5);
        GL11.glVertex2d(maxX - 0.5, minY + 0.5);
        GL11.glVertex2d(maxX - 0.5, maxY - 0.5);
        GL11.glVertex2d(minX + 0.5, maxY - 0.5);
        GL11.glEnd();

        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
    }
}
