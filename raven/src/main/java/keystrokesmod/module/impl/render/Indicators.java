package keystrokesmod.module.impl.render;

import keystrokesmod.mixin.impl.accessor.IAccessorEntityArrow;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.FireballSimulator;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FontManager;
import keystrokesmod.utility.font.RavenFontRenderer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Indicators extends Module {
    private static final class ProjectileTrajectoryProps {
        final double gravity;
        final double drag;
        final double dragInWater;
        final double hitboxRadius;
        final double width;
        final double height;
        final boolean ignoreBlockWithoutBoundingBox;
        final int ignoreOwnerTicks;

        private ProjectileTrajectoryProps(double gravity, double drag, double dragInWater, double hitboxRadius,
                                          double width, double height, boolean ignoreBlockWithoutBoundingBox,
                                          int ignoreOwnerTicks) {
            this.gravity = gravity;
            this.drag = drag;
            this.dragInWater = dragInWater;
            this.hitboxRadius = hitboxRadius;
            this.width = width;
            this.height = height;
            this.ignoreBlockWithoutBoundingBox = ignoreBlockWithoutBoundingBox;
            this.ignoreOwnerTicks = ignoreOwnerTicks;
        }
    }

    private static final class FluidState {
        final boolean inWater;
        final Vec3 flowDirection;

        private FluidState(boolean inWater, Vec3 flowDirection) {
            this.inWater = inWater;
            this.flowDirection = flowDirection;
        }
    }

    private static final class BlockCollisionResult {
        final MovingObjectPosition hit;
        final double distanceSq;

        private BlockCollisionResult(MovingObjectPosition hit, double distanceSq) {
            this.hit = hit;
            this.distanceSq = distanceSq;
        }
    }

    private static final class TrajectoryPrediction {
        final List<Vec3> points;
        final Vec3 impactPosition;
        final BlockPos hitBlockPos;
        final double width;
        final double height;

        private TrajectoryPrediction(List<Vec3> points, Vec3 impactPosition, BlockPos hitBlockPos,
                                     double width, double height) {
            this.points = points;
            this.impactPosition = impactPosition;
            this.hitBlockPos = hitBlockPos;
            this.width = width;
            this.height = height;
        }
    }

    private GroupSetting items;
    private ButtonSetting renderArrows;
    private ButtonSetting renderPearls;
    private ButtonSetting renderFireballs;
    private ButtonSetting drawFireballTrajectory;
    private ButtonSetting drawArrowTrajectory;
    private ButtonSetting drawPearlTrajectory;
    private ButtonSetting renderEggs;
    private ButtonSetting renderSnowballs;

    private SliderSetting arrow;
    private SliderSetting radius;
    private SliderSetting font;
    private ButtonSetting itemColors;
    private ButtonSetting renderItem;
    private ButtonSetting renderDistance;
    private ButtonSetting onlyWhenApproaching;
    private ButtonSetting renderOnlyOffScreen;

    private static final int APPROACH_INTERVAL_TICKS = 5;
    private static final double MIN_NET_TOWARD_BLOCKS = 1.0;
    private static final float FIREBALL_TRAJECTORY_LINE_WIDTH = 2.0F;
    private static final float FIREBALL_TRAJECTORY_SHADE_ALPHA = 0.18F;
    private static final double ARROW_GRAVITY = 0.05D;
    private static final double THROWABLE_GRAVITY = 0.03D;
    private static final double PROJECTILE_DRAG = 0.99D;
    private static final double ARROW_WATER_DRAG = 0.6D;
    private static final double THROWABLE_WATER_DRAG = 0.8D;
    private static final double WATER_FLOW_ACCELERATION = 0.014D;
    private static final double WATER_CHECK_EXPAND_Y = -0.4000000059604645D;
    private static final double WATER_CHECK_CONTRACT = 0.001D;
    private static final double COLLISION_EPSILON_SQ = 1.0E-7D;
    private static final double ENTITY_HIT_EXPANSION = 0.3D;
    private static final int PROJECTILE_TRAJECTORY_MAX_TICKS = 120;
    private static final int PROJECTILE_TRAJECTORY_SUBDIVISIONS = 4;
    private static final int PROJECTILE_IGNORE_OWNER_TICKS = 5;
    private static final double ARROW_TRAJECTORY_MARKER_BACK_OFFSET = 0.12D;
    private static final double ARROW_TRAJECTORY_MARKER_INNER_RADIUS = 0.02D;
    private static final double ARROW_TRAJECTORY_MARKER_OUTER_RADIUS = 0.15D;
    private static final float ARROW_TRAJECTORY_MARKER_LINE_WIDTH = 2.0F;
    private static final float ARROW_TRAJECTORY_MARKER_ALPHA = 1.0F;
    private static final double ARROW_TRAJECTORY_MARKER_SPIN_SPEED = 0.35D;
    private static final double ARROW_TRAJECTORY_MARKER_INNER_BACK_SWEEP = 0.01D;
    private static final double ARROW_TRAJECTORY_MARKER_OUTER_BACK_SWEEP = 0.05D;
    private static final String[] FONT_OPTIONS = FontManager.getHudFontOptions();
    private int tickCounter;
    private final Map<Entity, Vec3> lastPosition = new HashMap<>();
    private final Set<Entity> entitiesToRender = new HashSet<>();

    private String[] arrowTypes = new String[] { "Caret", "Greater than", "Triangle" };

    public Indicators() {
        super("Indicators", category.render);
        this.registerSetting(items = new GroupSetting("Items"));
        this.registerSetting(renderArrows = new ButtonSetting(items, "Render arrows", true));
        this.registerSetting(renderPearls = new ButtonSetting(items, "Render ender pearls", true));
        this.registerSetting(renderFireballs = new ButtonSetting(items, "Render fireballs", true));
        this.registerSetting(drawArrowTrajectory = new ButtonSetting(items, "Draw arrow trajectory", false));
        this.registerSetting(drawPearlTrajectory = new ButtonSetting(items, "Draw pearl trajectory", false));
        this.registerSetting(drawFireballTrajectory = new ButtonSetting(items, "Draw fireball trajectory", true));
        this.registerSetting(renderEggs = new ButtonSetting(items, "Render eggs", false));
        this.registerSetting(renderSnowballs = new ButtonSetting(items, "Render snowballs", false));
        this.registerSetting(arrow = new SliderSetting("Arrow", 0, arrowTypes));
        this.registerSetting(radius = new SliderSetting("Circle radius", 50, 30, 200, 5));
        this.registerSetting(font = new SliderSetting("Font", 0, FONT_OPTIONS));
        this.registerSetting(itemColors = new ButtonSetting("Item colors", true));
        this.registerSetting(renderItem = new ButtonSetting("Render item", true));
        this.registerSetting(renderDistance = new ButtonSetting("Render distance", true));
        this.registerSetting(onlyWhenApproaching = new ButtonSetting("Only when approaching", false));
        this.registerSetting(renderOnlyOffScreen = new ButtonSetting("Render only offscreen", false));
        updateTrajectoryVisibility();
    }

    @Override
    public void onUpdate() {
        updateTrajectoryVisibility();
    }

    @Override
    public void guiButtonToggled(ButtonSetting buttonSetting) {
        if (buttonSetting == renderFireballs || buttonSetting == renderArrows || buttonSetting == renderPearls
                || buttonSetting == drawFireballTrajectory) {
            updateTrajectoryVisibility();
        }
    }

    public void onDisable() {
        lastPosition.clear();
        entitiesToRender.clear();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        tickCounter++;
        if (tickCounter % APPROACH_INTERVAL_TICKS != 0) {
            return;
        }
        Set<Entity> seen = new HashSet<>();
        entitiesToRender.clear();
        double px = mc.thePlayer.posX, py = mc.thePlayer.posY, pz = mc.thePlayer.posZ;
        for (Entity en : mc.theWorld.loadedEntityList) {
            if (en == null || en == mc.thePlayer) {
                continue;
            }
            ItemStack itemStack = getTrackedItemStack(en);
            if (itemStack == null || !canRender(en)) {
                continue;
            }
            seen.add(en);
            Vec3 posThen = lastPosition.get(en);
            if (onlyWhenApproaching.isToggled()) {
                if (posThen == null) {
                    lastPosition.put(en, new Vec3(en.posX, en.posY, en.posZ));
                    continue;
                }
                double distanceThen = Math.sqrt(
                        (px - posThen.xCoord) * (px - posThen.xCoord) +
                                (py - posThen.yCoord) * (py - posThen.yCoord) +
                                (pz - posThen.zCoord) * (pz - posThen.zCoord));
                double distanceNow = mc.thePlayer.getDistanceToEntity(en);
                if (distanceThen - distanceNow <= MIN_NET_TOWARD_BLOCKS) {
                    lastPosition.put(en, new Vec3(en.posX, en.posY, en.posZ));
                    continue;
                }
            }
            entitiesToRender.add(en);
            lastPosition.put(en, new Vec3(en.posX, en.posY, en.posZ));
        }
        lastPosition.keySet().retainAll(seen);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (mc.currentScreen != null || !Utils.nullCheck()) {
            return;
        }
        try {
            for (Entity en : entitiesToRender) {
                ItemStack itemStack = getTrackedItemStack(en);
                if (itemStack == null) {
                    continue;
                }
                this.renderIndicatorFor(en, itemStack, event.renderTickTime);
            }
        }
        catch (Exception e) {}
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!Utils.nullCheck() || mc.theWorld == null || !hasEnabledWorldTrajectories()) {
            return;
        }

        try {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (!canRender(entity)) {
                    continue;
                }

                if (entity instanceof EntityLargeFireball && drawFireballTrajectory.isToggled()) {
                    renderFireballTrajectory((EntityLargeFireball) entity, event.partialTicks);
                }
                else if (entity instanceof EntityArrow && drawArrowTrajectory.isToggled()
                        && !((IAccessorEntityArrow) entity).getInGround()) {
                    renderArrowTrajectory((EntityArrow) entity, event.partialTicks);
                }
                else if (entity instanceof EntityEnderPearl && drawPearlTrajectory.isToggled()) {
                    renderPearlTrajectory((EntityEnderPearl) entity, event.partialTicks);
                }
            }
        }
        catch (Exception e) {}
    }

    private ItemStack getTrackedItemStack(Entity en) {
        if (en == null) {
            return null;
        }
        if (en instanceof EntityArrow) {
            if (((IAccessorEntityArrow) en).getInGround()) {
                return null;
            }
            return new ItemStack(Items.arrow);
        }
        if (en instanceof EntityFireball) {
            return new ItemStack(Items.fire_charge);
        }
        if (en instanceof EntityEnderPearl) {
            return new ItemStack(Items.ender_pearl);
        }
        if (en instanceof EntityEgg) {
            return new ItemStack(Items.egg);
        }
        if (en instanceof EntitySnowball) {
            return new ItemStack(Items.snowball);
        }
        return null;
    }

    private boolean canRender(Entity entity) {
        if (entity instanceof EntityArrow && !((IAccessorEntityArrow) entity).getInGround() && renderArrows.isToggled()) {
            return true;
        }
        else if (entity instanceof EntityLargeFireball && renderFireballs.isToggled()) {
            return true;
        }
        else if (entity instanceof EntityEnderPearl && renderPearls.isToggled()) {
            return true;
        }
        else if (entity instanceof EntityEgg && renderEggs.isToggled()) {
            return true;
        }
        else if (entity instanceof EntitySnowball && renderSnowballs.isToggled()) {
            return true;
        }
        return false;
    }

    private void renderIndicatorFor(Entity en, ItemStack itemStack, float partialTicks) {
        if (!this.canRender(en)) {
            return;
        }
        if (!this.shouldRender(en, itemStack)) {
            return;
        }
        if (renderOnlyOffScreen.isToggled() && RenderUtils.isInViewFrustum(en)) {
            return;
        }
        Color colorForStack = getColorForItem(itemStack);
        int color = itemColors.isToggled() ? colorForStack.getRGB() : -1;

        double x = en.lastTickPosX + (en.posX - en.lastTickPosX) * partialTicks - mc.getRenderManager().viewerPosX;
        double y = en.lastTickPosY + (en.posY - en.lastTickPosY) * partialTicks - mc.getRenderManager().viewerPosY + en.height / 2;
        double z = en.lastTickPosZ + (en.posZ - en.lastTickPosZ) * partialTicks - mc.getRenderManager().viewerPosZ;

        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(((IAccessorMinecraft) mc).getTimer().renderPartialTicks, 0);

        ScaledResolution scaledResolution = new ScaledResolution(mc);
        Vec3 vec = RenderUtils.convertTo2D(scaledResolution.getScaleFactor(), x, y, z);

        if (vec != null) {
            mc.entityRenderer.setupOverlayRendering();
            ScaledResolution res = new ScaledResolution(mc);

            double dx = vec.xCoord - res.getScaledWidth() / 2.0;
            double dy = vec.yCoord - res.getScaledHeight() / 2.0;
            boolean inFrustum = vec.zCoord < 1.0003684;

            if (!inFrustum) {
                dx *= -1.0;
                dy *= -1.0;
            }

            double angle1 = Math.atan2(dx, dy);
            double angle2 = Math.atan2(dy, dx) * 57.295780181884766 + 90.0;
            double hypotenuse = Math.hypot(dx, dy);
            double radiusInput = radius.getInput();

            if (renderItem.isToggled()) {
                radiusInput += 20.0;
            }

            if (inFrustum && hypotenuse < radiusInput + 15.0) {
                return;
            }

            double baseX = res.getScaledWidth() / 2.0;
            double baseY = res.getScaledHeight() / 2.0;
            double sinAng = Math.sin(angle1);
            double cosAng = Math.cos(angle1);
            double renderX = baseX + radiusInput * sinAng;
            double renderY = baseY + radiusInput * cosAng;

            GlStateManager.pushMatrix();
            GlStateManager.translate(renderX, renderY, 0.0);
            GlStateManager.rotate((float) angle2, 0.0f, 0.0f, 1.0f);
            GlStateManager.scale(1.0f, 1.0f, 1.0f);

            int arrowInput = (int) arrow.getInput();

            if (arrowInput == 0) {
                if (color == -1) {
                    GL11.glColor3d(1.0, 1.0, 1.0);
                }
                else {
                    GL11.glColor3d(colorForStack.getRed(), colorForStack.getGreen(), colorForStack.getBlue());
                }

                GL11.glEnable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glEnable(GL11.GL_LINE_SMOOTH);

                double halfAngle = 0.6108652353286743;
                double size = 9.0;
                double offsetY = 5.0;
                GL11.glLineWidth(3.0f);
                GL11.glBegin(GL11.GL_LINE_STRIP);
                GL11.glVertex2d(Math.sin(-halfAngle) * size, Math.cos(-halfAngle) * size - offsetY);
                GL11.glVertex2d(0.0, -offsetY);
                GL11.glVertex2d(Math.sin(halfAngle) * size, Math.cos(halfAngle) * size - offsetY);
                GL11.glEnd();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_LINE_SMOOTH);
            }
            else if (arrowInput == 1) {
                GlStateManager.rotate(-90.0f, 0.0f, 0.0f, 1.0f);
                GlStateManager.scale(1.5, 1.5, 1.5);
                RavenFontRenderer fr = getIndicatorFontRenderer();
                fr.drawString(">", -2.0f, -4.0f, color, false);
            }
            else if (arrowInput == 2) {
                RenderUtils.draw2DPolygon(0.0, 0.0, 5.0, 3, Utils.mergeAlpha(color, 255));
            }

            GlStateManager.popMatrix();

            renderX = baseX + (radiusInput - 13.0) * sinAng;
            renderY = baseY + (radiusInput - 13.0) * cosAng;

            GlStateManager.pushMatrix();
            GlStateManager.translate(renderX, renderY, 0.0);
            GlStateManager.scale(0.8, 0.8, 0.8);

            if (renderDistance.isToggled()) {
                String text = (int) mc.thePlayer.getDistanceToEntity(en) + "m";
                RavenFontRenderer fr = getIndicatorFontRenderer();
                fr.drawString(text, (float) (-fr.getStringWidth(text) / 2), -4.0f, -1, true);
            }

            GlStateManager.popMatrix();

            if (renderItem.isToggled() && itemStack != null) {
                GlStateManager.pushMatrix();
                if (itemStack.getItem() == Items.arrow) {
                    renderX = baseX + (radiusInput - 26.0) * sinAng;
                    renderY = baseY + (radiusInput - 26.0) * cosAng;
                    GlStateManager.translate(renderX, renderY, 0.0);
                    GlStateManager.scale(1.0f, 1.0f, 1.0f);
                    GlStateManager.rotate((float) angle2 - 45.0f, 0.0f, 0.0f, 1.0f);
                    mc.getRenderItem().renderItemIntoGUI(itemStack, -12, -4);
                }
                else {
                    renderX = baseX + (radiusInput - 29.0) * sinAng;
                    renderY = baseY + (radiusInput - 29.0) * cosAng;
                    GlStateManager.translate(renderX, renderY, 0.0);
                    GlStateManager.scale(1.0f, 1.0f, 1.0f);
                    mc.getRenderItem().renderItemIntoGUI(itemStack, -8, -9);
                }
                GlStateManager.popMatrix();
            }
        }
    }

    private Color getColorForItem(ItemStack itemStack) {
        if (itemStack == null) {
            return Color.WHITE;
        }
        if (itemStack.getItem() == Items.ender_pearl) {
            return new Color(210, 0, 255);
        }
        else if (itemStack.getItem() == Items.fire_charge) {
            return new Color(255, 150, 0);
        }
        else if (itemStack.getItem() == Items.egg) {
            return new Color(255, 238, 154);
        }
        else {
            return Color.WHITE;
        }
    }

    private boolean shouldRender(Entity en, ItemStack stack) {
        return true;
    }

    private boolean hasEnabledWorldTrajectories() {
        return (renderFireballs.isToggled() && drawFireballTrajectory.isToggled())
                || (renderArrows.isToggled() && drawArrowTrajectory.isToggled())
                || (renderPearls.isToggled() && drawPearlTrajectory.isToggled());
    }

    private void updateTrajectoryVisibility() {
        if (drawFireballTrajectory != null) {
            drawFireballTrajectory.setVisible(renderFireballs != null && renderFireballs.isToggled(), this);
        }
        if (drawArrowTrajectory != null) {
            drawArrowTrajectory.setVisible(renderArrows != null && renderArrows.isToggled(), this);
        }
        if (drawPearlTrajectory != null) {
            drawPearlTrajectory.setVisible(renderPearls != null && renderPearls.isToggled(), this);
        }
    }

    private void renderFireballTrajectory(EntityLargeFireball fireball, float partialTicks) {
        FireballSimulator.Result result = FireballSimulator.simulate(fireball);
        Vec3 impactPosition = result.getImpactPosition();

        if (impactPosition == null) {
            return;
        }

        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;
        double startX = fireball.lastTickPosX + (fireball.posX - fireball.lastTickPosX) * partialTicks;
        double startY = fireball.lastTickPosY + (fireball.posY - fireball.lastTickPosY) * partialTicks + fireball.height * 0.5D;
        double startZ = fireball.lastTickPosZ + (fireball.posZ - fireball.lastTickPosZ) * partialTicks;
        double endY = impactPosition.yCoord + fireball.height * 0.5D;
        Color fireballColor = itemColors.isToggled() ? getColorForItem(new ItemStack(Items.fire_charge)) : Color.WHITE;
        float red = fireballColor.getRed() / 255.0F;
        float green = fireballColor.getGreen() / 255.0F;
        float blue = fireballColor.getBlue() / 255.0F;

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        GL11.glLineWidth(FIREBALL_TRAJECTORY_LINE_WIDTH);
        GL11.glColor4f(red, green, blue, 1.0F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(startX - viewerX, startY - viewerY, startZ - viewerZ);
        GL11.glVertex3d(impactPosition.xCoord - viewerX, endY - viewerY, impactPosition.zCoord - viewerZ);
        GL11.glEnd();

        AxisAlignedBB impactBox = getImpactBox(fireball, impactPosition);
        RenderUtils.drawOutlinedBox(impactBox, viewerX, viewerY, viewerZ);
        RenderUtils.drawBoundingBox(impactBox.offset(-viewerX, -viewerY, -viewerZ), red, green, blue, FIREBALL_TRAJECTORY_SHADE_ALPHA);

        GL11.glLineWidth(1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private void renderArrowTrajectory(EntityArrow arrowEntity, float partialTicks) {
        ProjectileTrajectoryProps props = new ProjectileTrajectoryProps(
                ARROW_GRAVITY,
                PROJECTILE_DRAG,
                ARROW_WATER_DRAG,
                Math.max(arrowEntity.width * 0.5D, 0.25D),
                arrowEntity.width,
                arrowEntity.height,
                true,
                PROJECTILE_IGNORE_OWNER_TICKS
        );
        renderPredictedTrajectory(
                arrowEntity,
                partialTicks,
                predictProjectileTrajectory(arrowEntity, props, arrowEntity.shootingEntity),
                Color.WHITE
        );
    }

    private void renderPearlTrajectory(EntityEnderPearl pearlEntity, float partialTicks) {
        ProjectileTrajectoryProps props = new ProjectileTrajectoryProps(
                THROWABLE_GRAVITY,
                PROJECTILE_DRAG,
                THROWABLE_WATER_DRAG,
                Math.max(pearlEntity.width * 0.5D, 0.125D),
                pearlEntity.width,
                pearlEntity.height,
                false,
                PROJECTILE_IGNORE_OWNER_TICKS
        );
        Color pearlColor = itemColors.isToggled() ? getColorForItem(new ItemStack(Items.ender_pearl)) : Color.WHITE;
        renderPredictedTrajectory(
                pearlEntity,
                partialTicks,
                predictProjectileTrajectory(pearlEntity, props, pearlEntity.getThrower()),
                pearlColor
        );
    }

    private TrajectoryPrediction predictProjectileTrajectory(Entity projectile, ProjectileTrajectoryProps props, Entity owner) {
        double posX = projectile.posX;
        double posY = projectile.posY;
        double posZ = projectile.posZ;
        double motionX = projectile.motionX;
        double motionY = projectile.motionY;
        double motionZ = projectile.motionZ;
        int ticksInAir = Math.max(0, projectile.ticksExisted);

        List<Vec3> points = new ArrayList<>();
        points.add(new Vec3(posX, posY, posZ));

        for (int tick = 0; tick < PROJECTILE_TRAJECTORY_MAX_TICKS; tick++) {
            FluidState fluidState = sampleFluidState(posX, posY, posZ, props.width, props.height, projectile);
            double[] motion = new double[] { motionX, motionY, motionZ };
            applyWaterFlowAcceleration(fluidState, motion);
            motionX = motion[0];
            motionY = motion[1];
            motionZ = motion[2];

            double nextX = posX + motionX;
            double nextY = posY + motionY;
            double nextZ = posZ + motionZ;

            Vec3 start = new Vec3(posX, posY, posZ);
            Vec3 end = new Vec3(nextX, nextY, nextZ);
            BlockCollisionResult blockCollision = getNearestBlockCollision(start, end, props);
            MovingObjectPosition blockHit = blockCollision.hit;
            double bestDistanceSq = blockCollision.distanceSq;
            Vec3 clampedEnd = blockHit != null ? blockHit.hitVec : end;

            AxisAlignedBB broadBox = new AxisAlignedBB(
                    posX - props.hitboxRadius,
                    posY - props.hitboxRadius,
                    posZ - props.hitboxRadius,
                    posX + props.hitboxRadius,
                    posY + props.hitboxRadius,
                    posZ + props.hitboxRadius
            ).addCoord(motionX, motionY, motionZ).expand(1.0D, 1.0D, 1.0D);

            Vec3 bestEntityHit = null;
            for (Entity entity : mc.theWorld.getEntitiesWithinAABBExcludingEntity(projectile, broadBox)) {
                if (!(entity instanceof EntityLivingBase)) {
                    continue;
                }
                if (entity instanceof EntityArmorStand) {
                    continue;
                }
                if (!entity.canBeCollidedWith()) {
                    continue;
                }
                if (((EntityLivingBase) entity).deathTime != 0) {
                    continue;
                }
                if (entity instanceof EntityPlayer && AntiBot.isBot(entity)) {
                    continue;
                }
                if (owner != null && entity.isEntityEqual(owner) && ticksInAir < props.ignoreOwnerTicks) {
                    continue;
                }

                AxisAlignedBB expandedBox = entity.getEntityBoundingBox().expand(
                        ENTITY_HIT_EXPANSION,
                        ENTITY_HIT_EXPANSION,
                        ENTITY_HIT_EXPANSION
                );
                MovingObjectPosition entityHit = expandedBox.calculateIntercept(start, clampedEnd);
                if (entityHit == null) {
                    continue;
                }

                double distanceSq = start.squareDistanceTo(entityHit.hitVec);
                if (distanceSq + COLLISION_EPSILON_SQ < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    bestEntityHit = entityHit.hitVec;
                }
            }

            if (bestEntityHit != null) {
                addHitSegmentPoints(points, posX, posY, posZ, motionX, motionY, motionZ, bestEntityHit);
                return new TrajectoryPrediction(points, bestEntityHit, null, props.width, props.height);
            }

            if (blockHit != null) {
                addHitSegmentPoints(points, posX, posY, posZ, motionX, motionY, motionZ, blockHit.hitVec);
                return new TrajectoryPrediction(points, blockHit.hitVec, blockHit.getBlockPos(), props.width, props.height);
            }

            addFullSegmentPoints(points, posX, posY, posZ, motionX, motionY, motionZ);
            posX = nextX;
            posY = nextY;
            posZ = nextZ;

            motionX *= fluidState.inWater ? props.dragInWater : props.drag;
            motionY *= fluidState.inWater ? props.dragInWater : props.drag;
            motionZ *= fluidState.inWater ? props.dragInWater : props.drag;
            motionY -= props.gravity;
            ticksInAir++;

            if (posY < -64.0D) {
                break;
            }
        }

        return new TrajectoryPrediction(points, null, null, props.width, props.height);
    }

    private void addFullSegmentPoints(List<Vec3> points, double posX, double posY, double posZ,
                                      double motionX, double motionY, double motionZ) {
        for (int step = 1; step <= PROJECTILE_TRAJECTORY_SUBDIVISIONS; step++) {
            double t = (double) step / (double) PROJECTILE_TRAJECTORY_SUBDIVISIONS;
            points.add(new Vec3(
                    posX + motionX * t,
                    posY + motionY * t,
                    posZ + motionZ * t
            ));
        }
    }

    private void addHitSegmentPoints(List<Vec3> points, double posX, double posY, double posZ,
                                     double motionX, double motionY, double motionZ, Vec3 hitVec) {
        double segmentLengthSq = motionX * motionX + motionY * motionY + motionZ * motionZ;
        double hitDx = hitVec.xCoord - posX;
        double hitDy = hitVec.yCoord - posY;
        double hitDz = hitVec.zCoord - posZ;
        double hitDistanceSq = hitDx * hitDx + hitDy * hitDy + hitDz * hitDz;
        double hitT = segmentLengthSq > 0.0D ? Math.sqrt(hitDistanceSq / segmentLengthSq) : 0.0D;
        hitT = Math.max(0.0D, Math.min(1.0D, hitT));
        int subCount = (int) Math.ceil(hitT * PROJECTILE_TRAJECTORY_SUBDIVISIONS);

        for (int step = 1; step < subCount; step++) {
            double t = (double) step / (double) PROJECTILE_TRAJECTORY_SUBDIVISIONS;
            points.add(new Vec3(
                    posX + motionX * t,
                    posY + motionY * t,
                    posZ + motionZ * t
            ));
        }

        points.add(hitVec);
    }

    private void renderPredictedTrajectory(Entity projectile, float partialTicks, TrajectoryPrediction prediction, Color color) {
        if (prediction.points.size() < 2) {
            return;
        }

        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;
        double startX = projectile.lastTickPosX + (projectile.posX - projectile.lastTickPosX) * partialTicks;
        double startY = projectile.lastTickPosY + (projectile.posY - projectile.lastTickPosY) * partialTicks;
        double startZ = projectile.lastTickPosZ + (projectile.posZ - projectile.lastTickPosZ) * partialTicks;
        float red = color.getRed() / 255.0F;
        float green = color.getGreen() / 255.0F;
        float blue = color.getBlue() / 255.0F;

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        GL11.glLineWidth(FIREBALL_TRAJECTORY_LINE_WIDTH);
        GL11.glColor4f(red, green, blue, 1.0F);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(startX - viewerX, startY - viewerY, startZ - viewerZ);
        for (int i = 1; i < prediction.points.size(); i++) {
            Vec3 point = prediction.points.get(i);
            GL11.glVertex3d(point.xCoord - viewerX, point.yCoord - viewerY, point.zCoord - viewerZ);
        }
        GL11.glEnd();

        if (prediction.impactPosition != null) {
            AxisAlignedBB impactBox = getPredictedImpactBox(projectile, prediction);
            RenderUtils.drawOutlinedBox(impactBox, viewerX, viewerY, viewerZ);
            RenderUtils.drawBoundingBox(impactBox.offset(-viewerX, -viewerY, -viewerZ), red, green, blue, FIREBALL_TRAJECTORY_SHADE_ALPHA);
        }

        if (projectile instanceof EntityArrow) {
            renderArrowTrajectoryStartMarker(prediction, projectile, partialTicks, startX, startY, startZ, viewerX, viewerY, viewerZ, red, green, blue);
        }

        GL11.glLineWidth(1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private void renderArrowTrajectoryStartMarker(TrajectoryPrediction prediction, Entity projectile, float partialTicks,
                                                  double startX, double startY, double startZ,
                                                  double viewerX, double viewerY, double viewerZ,
                                                  float red, float green, float blue) {
        Vec3 direction = getArrowTrajectoryMarkerDirection(prediction, projectile, startX, startY, startZ);
        if (direction == null) {
            return;
        }

        Vec3 armAxisA = getPerpendicularUnitVector(direction);
        if (armAxisA == null) {
            return;
        }

        Vec3 armAxisB = normalizeVec3(cross(direction, armAxisA));
        if (armAxisB == null) {
            return;
        }

        double centerX = startX - direction.xCoord * ARROW_TRAJECTORY_MARKER_BACK_OFFSET;
        double centerY = startY - direction.yCoord * ARROW_TRAJECTORY_MARKER_BACK_OFFSET;
        double centerZ = startZ - direction.zCoord * ARROW_TRAJECTORY_MARKER_BACK_OFFSET;
        double spinAngle = (projectile.ticksExisted + partialTicks) * ARROW_TRAJECTORY_MARKER_SPIN_SPEED;
        double cosine = Math.cos(spinAngle);
        double sine = Math.sin(spinAngle);
        Vec3 rotatedArmAxisA = rotateMarkerAxis(armAxisA, armAxisB, cosine, sine);
        Vec3 rotatedArmAxisB = rotateMarkerAxis(armAxisB, armAxisA, cosine, -sine);

        GL11.glLineWidth(ARROW_TRAJECTORY_MARKER_LINE_WIDTH);
        GL11.glColor4f(red, green, blue, ARROW_TRAJECTORY_MARKER_ALPHA);
        GL11.glBegin(GL11.GL_LINES);
        addMarkerSegment(centerX, centerY, centerZ, rotatedArmAxisA, direction, viewerX, viewerY, viewerZ);
        addMarkerSegment(centerX, centerY, centerZ, new Vec3(-rotatedArmAxisA.xCoord, -rotatedArmAxisA.yCoord, -rotatedArmAxisA.zCoord), direction, viewerX, viewerY, viewerZ);
        addMarkerSegment(centerX, centerY, centerZ, rotatedArmAxisB, direction, viewerX, viewerY, viewerZ);
        addMarkerSegment(centerX, centerY, centerZ, new Vec3(-rotatedArmAxisB.xCoord, -rotatedArmAxisB.yCoord, -rotatedArmAxisB.zCoord), direction, viewerX, viewerY, viewerZ);
        GL11.glEnd();
    }

    private void addMarkerSegment(double centerX, double centerY, double centerZ, Vec3 axis, Vec3 direction,
                                  double viewerX, double viewerY, double viewerZ) {
        GL11.glVertex3d(
                centerX + axis.xCoord * ARROW_TRAJECTORY_MARKER_INNER_RADIUS - direction.xCoord * ARROW_TRAJECTORY_MARKER_INNER_BACK_SWEEP - viewerX,
                centerY + axis.yCoord * ARROW_TRAJECTORY_MARKER_INNER_RADIUS - direction.yCoord * ARROW_TRAJECTORY_MARKER_INNER_BACK_SWEEP - viewerY,
                centerZ + axis.zCoord * ARROW_TRAJECTORY_MARKER_INNER_RADIUS - direction.zCoord * ARROW_TRAJECTORY_MARKER_INNER_BACK_SWEEP - viewerZ
        );
        GL11.glVertex3d(
                centerX + axis.xCoord * ARROW_TRAJECTORY_MARKER_OUTER_RADIUS - direction.xCoord * ARROW_TRAJECTORY_MARKER_OUTER_BACK_SWEEP - viewerX,
                centerY + axis.yCoord * ARROW_TRAJECTORY_MARKER_OUTER_RADIUS - direction.yCoord * ARROW_TRAJECTORY_MARKER_OUTER_BACK_SWEEP - viewerY,
                centerZ + axis.zCoord * ARROW_TRAJECTORY_MARKER_OUTER_RADIUS - direction.zCoord * ARROW_TRAJECTORY_MARKER_OUTER_BACK_SWEEP - viewerZ
        );
    }

    private Vec3 getArrowTrajectoryMarkerDirection(TrajectoryPrediction prediction, Entity projectile,
                                                   double startX, double startY, double startZ) {
        Vec3 direction = normalizeVec3(new Vec3(projectile.motionX, projectile.motionY, projectile.motionZ));
        if (direction != null) {
            return direction;
        }

        if (prediction.points.size() < 2) {
            return null;
        }

        Vec3 nextPoint = prediction.points.get(1);
        return normalizeVec3(new Vec3(
                nextPoint.xCoord - startX,
                nextPoint.yCoord - startY,
                nextPoint.zCoord - startZ
        ));
    }

    private Vec3 getPerpendicularUnitVector(Vec3 direction) {
        Vec3 reference = Math.abs(direction.yCoord) < 0.9D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        return normalizeVec3(cross(direction, reference));
    }

    private Vec3 rotateMarkerAxis(Vec3 primaryAxis, Vec3 secondaryAxis, double primaryWeight, double secondaryWeight) {
        return new Vec3(
                primaryAxis.xCoord * primaryWeight + secondaryAxis.xCoord * secondaryWeight,
                primaryAxis.yCoord * primaryWeight + secondaryAxis.yCoord * secondaryWeight,
                primaryAxis.zCoord * primaryWeight + secondaryAxis.zCoord * secondaryWeight
        );
    }

    private Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(
                a.yCoord * b.zCoord - a.zCoord * b.yCoord,
                a.zCoord * b.xCoord - a.xCoord * b.zCoord,
                a.xCoord * b.yCoord - a.yCoord * b.xCoord
        );
    }

    private Vec3 normalizeVec3(Vec3 vector) {
        double lengthSq = vector.xCoord * vector.xCoord + vector.yCoord * vector.yCoord + vector.zCoord * vector.zCoord;
        if (lengthSq <= 1.0E-6D) {
            return null;
        }

        double invLength = 1.0D / Math.sqrt(lengthSq);
        return new Vec3(vector.xCoord * invLength, vector.yCoord * invLength, vector.zCoord * invLength);
    }

    private AxisAlignedBB getPredictedImpactBox(Entity projectile, TrajectoryPrediction prediction) {
        if (projectile instanceof EntityArrow) {
            return getProjectileImpactBox(prediction);
        }

        if (prediction.hitBlockPos != null) {
            AxisAlignedBB selectionBox = BlockUtils.getBlockSelectionBox(prediction.hitBlockPos);
            if (selectionBox != null) {
                return selectionBox;
            }

            return new AxisAlignedBB(
                    prediction.hitBlockPos.getX(),
                    prediction.hitBlockPos.getY(),
                    prediction.hitBlockPos.getZ(),
                    prediction.hitBlockPos.getX() + 1.0D,
                    prediction.hitBlockPos.getY() + 1.0D,
                    prediction.hitBlockPos.getZ() + 1.0D
            );
        }

        return getProjectileImpactBox(prediction);
    }

    private AxisAlignedBB getProjectileImpactBox(TrajectoryPrediction prediction) {
        double halfWidth = prediction.width * 0.5D;
        return new AxisAlignedBB(
                prediction.impactPosition.xCoord - halfWidth,
                prediction.impactPosition.yCoord,
                prediction.impactPosition.zCoord - halfWidth,
                prediction.impactPosition.xCoord + halfWidth,
                prediction.impactPosition.yCoord + prediction.height,
                prediction.impactPosition.zCoord + halfWidth
        );
    }

    private AxisAlignedBB getImpactBox(EntityLargeFireball fireball, Vec3 impactPosition) {
        double halfWidth = fireball.width * 0.5D;
        return new AxisAlignedBB(
                impactPosition.xCoord - halfWidth,
                impactPosition.yCoord,
                impactPosition.zCoord - halfWidth,
                impactPosition.xCoord + halfWidth,
                impactPosition.yCoord + fireball.height,
                impactPosition.zCoord + halfWidth
        );
    }

    private FluidState sampleFluidState(double posX, double posY, double posZ, double width, double height, Entity projectile) {
        AxisAlignedBB entityBox = new AxisAlignedBB(
                posX - width * 0.5D,
                posY,
                posZ - width * 0.5D,
                posX + width * 0.5D,
                posY + height,
                posZ + width * 0.5D
        );
        AxisAlignedBB waterCheckBox = entityBox.expand(0.0D, WATER_CHECK_EXPAND_Y, 0.0D)
                .contract(WATER_CHECK_CONTRACT, WATER_CHECK_CONTRACT, WATER_CHECK_CONTRACT);

        int minX = MathHelper.floor_double(waterCheckBox.minX);
        int maxX = MathHelper.floor_double(waterCheckBox.maxX + 1.0D);
        int minY = MathHelper.floor_double(waterCheckBox.minY);
        int maxY = MathHelper.floor_double(waterCheckBox.maxY + 1.0D);
        int minZ = MathHelper.floor_double(waterCheckBox.minZ);
        int maxZ = MathHelper.floor_double(waterCheckBox.maxZ + 1.0D);

        if (!mc.theWorld.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new FluidState(false, new Vec3(0.0D, 0.0D, 0.0D));
        }

        boolean inWater = false;
        Vec3 flowDirection = new Vec3(0.0D, 0.0D, 0.0D);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x < maxX; ++x) {
            for (int y = minY; y < maxY; ++y) {
                for (int z = minZ; z < maxZ; ++z) {
                    mutablePos.set(x, y, z);
                    IBlockState blockState = mc.theWorld.getBlockState(mutablePos);

                    if (blockState.getBlock().getMaterial() != Material.water) {
                        continue;
                    }

                    double liquidSurfaceY = (double) ((float) (y + 1)
                            - BlockLiquid.getLiquidHeightPercent((Integer) blockState.getValue(BlockLiquid.LEVEL)));
                    if ((double) maxY >= liquidSurfaceY) {
                        inWater = true;
                        flowDirection = blockState.getBlock().modifyAcceleration(mc.theWorld, mutablePos, projectile, flowDirection);
                    }
                }
            }
        }

        if (flowDirection.lengthVector() > 0.0D) {
            flowDirection = flowDirection.normalize();
        }

        return new FluidState(inWater, flowDirection);
    }

    private void applyWaterFlowAcceleration(FluidState fluidState, double[] motion) {
        if (!fluidState.inWater || fluidState.flowDirection.lengthVector() <= 0.0D) {
            return;
        }

        motion[0] += fluidState.flowDirection.xCoord * WATER_FLOW_ACCELERATION;
        motion[1] += fluidState.flowDirection.yCoord * WATER_FLOW_ACCELERATION;
        motion[2] += fluidState.flowDirection.zCoord * WATER_FLOW_ACCELERATION;
    }

    private AxisAlignedBB getBlockSweepBounds(Vec3 start, Vec3 end) {
        return new AxisAlignedBB(
                Math.min(start.xCoord, end.xCoord),
                Math.min(start.yCoord, end.yCoord),
                Math.min(start.zCoord, end.zCoord),
                Math.max(start.xCoord, end.xCoord),
                Math.max(start.yCoord, end.yCoord),
                Math.max(start.zCoord, end.zCoord)
        );
    }

    private AxisAlignedBB getVanillaProjectileBounds(Block block, BlockPos pos) {
        block.setBlockBoundsBasedOnState(mc.theWorld, pos);
        return block.getSelectedBoundingBox(mc.theWorld, pos);
    }

    private AxisAlignedBB intersectBoxes(AxisAlignedBB first, AxisAlignedBB second) {
        double minX = Math.max(first.minX, second.minX);
        double minY = Math.max(first.minY, second.minY);
        double minZ = Math.max(first.minZ, second.minZ);
        double maxX = Math.min(first.maxX, second.maxX);
        double maxY = Math.min(first.maxY, second.maxY);
        double maxZ = Math.min(first.maxZ, second.maxZ);

        if (maxX - minX <= 1.0E-7D || maxY - minY <= 1.0E-7D || maxZ - minZ <= 1.0E-7D) {
            return null;
        }

        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private BlockCollisionResult rayTraceBlockCollisionBoxes(Vec3 start, Vec3 end, ProjectileTrajectoryProps props) {
        AxisAlignedBB sweepBounds = getBlockSweepBounds(start, end);
        int minX = MathHelper.floor_double(sweepBounds.minX);
        int maxX = MathHelper.floor_double(sweepBounds.maxX + 1.0D);
        int minY = MathHelper.floor_double(sweepBounds.minY);
        int maxY = MathHelper.floor_double(sweepBounds.maxY + 1.0D);
        int minZ = MathHelper.floor_double(sweepBounds.minZ);
        int maxZ = MathHelper.floor_double(sweepBounds.maxZ + 1.0D);

        if (!mc.theWorld.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new BlockCollisionResult(null, Double.MAX_VALUE);
        }

        List<AxisAlignedBB> collisionBoxes = new ArrayList<>();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        MovingObjectPosition bestHit = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (int x = minX; x < maxX; ++x) {
            for (int y = minY; y < maxY; ++y) {
                for (int z = minZ; z < maxZ; ++z) {
                    mutablePos.set(x, y, z);
                    IBlockState blockState = mc.theWorld.getBlockState(mutablePos);
                    Block block = blockState.getBlock();

                    if ((!props.ignoreBlockWithoutBoundingBox
                            || block.getCollisionBoundingBox(mc.theWorld, mutablePos, blockState) != null)
                            && block.canCollideCheck(blockState, false)) {
                        collisionBoxes.clear();
                        AxisAlignedBB vanillaProjectileBounds = getVanillaProjectileBounds(block, mutablePos);
                        block.addCollisionBoxesToList(mc.theWorld, mutablePos, blockState, sweepBounds, collisionBoxes, null);

                        for (AxisAlignedBB collisionBox : collisionBoxes) {
                            AxisAlignedBB projectileCollisionBox = intersectBoxes(collisionBox, vanillaProjectileBounds);
                            if (projectileCollisionBox == null) {
                                continue;
                            }

                            MovingObjectPosition hit = projectileCollisionBox.calculateIntercept(start, end);
                            if (hit == null) {
                                continue;
                            }

                            double distanceSq = start.squareDistanceTo(hit.hitVec);
                            if (distanceSq + COLLISION_EPSILON_SQ < bestDistanceSq) {
                                bestDistanceSq = distanceSq;
                                bestHit = new MovingObjectPosition(hit.hitVec, hit.sideHit, new BlockPos(mutablePos));
                            }
                        }
                    }
                }
            }
        }

        return new BlockCollisionResult(bestHit, bestDistanceSq);
    }

    private BlockCollisionResult getNearestBlockCollision(Vec3 start, Vec3 end, ProjectileTrajectoryProps props) {
        MovingObjectPosition vanillaHit = mc.theWorld.rayTraceBlocks(start, end, false, props.ignoreBlockWithoutBoundingBox, false);
        double vanillaDistanceSq = vanillaHit != null ? start.squareDistanceTo(vanillaHit.hitVec) : Double.MAX_VALUE;

        BlockCollisionResult collisionBoxHit = rayTraceBlockCollisionBoxes(start, end, props);
        if (collisionBoxHit.hit != null && collisionBoxHit.distanceSq + COLLISION_EPSILON_SQ < vanillaDistanceSq) {
            return collisionBoxHit;
        }

        return new BlockCollisionResult(vanillaHit, vanillaDistanceSq);
    }

    private String getSelectedFontName() {
        if (font == null) {
            return FONT_OPTIONS[0];
        }
        int index = (int) Math.max(0, Math.min(font.getOptions().length - 1, font.getInput()));
        return font.getOptions()[index];
    }

    private RavenFontRenderer getIndicatorFontRenderer() {
        return FontManager.getNametagRenderer(getSelectedFontName());
    }
}
