package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class ChestESP extends Module {
    private static final int TRAPPED_CHEST_TYPE = 1;
    private static final ChestKind[] RENDERABLE_CHEST_KINDS = {
            ChestKind.NORMAL,
            ChestKind.TRAPPED,
            ChestKind.ENDER
    };
    private static final ThreadLocal<Boolean> CHEST_CHAMS_ACTIVE = ThreadLocal.withInitial(() -> false);

    private final EnumMap<ChestKind, ChestVisualSettings> chestSettingsByKind = new EnumMap<>(ChestKind.class);
    private final EnumMap<ChestKind, List<BlockPos>> trackedWorldBatches = new EnumMap<>(ChestKind.class);
    private final SliderSetting maxDistance;


    public ChestESP() {
        super("ChestESP", Module.category.render, 0);
        chestSettingsByKind.put(ChestKind.NORMAL, registerChestSettings("Chest", 198, 132, 56));
        chestSettingsByKind.put(ChestKind.TRAPPED, registerChestSettings("Trapped chest", 176, 64, 64));
        chestSettingsByKind.put(ChestKind.ENDER, registerChestSettings("Ender chest", 128, 64, 192));
        this.registerSetting(maxDistance = new SliderSetting("Max distance", 128.0, 32.0, 256.0, 8.0));
    }

    @Override
    public void guiUpdate() {
        for (ChestKind chestKind : RENDERABLE_CHEST_KINDS) {
            getChestSettings(chestKind).updateVisibility(this);
        }
    }

    public static void onRenderChestPre(TileEntity tileEntity) {
        ChestESP mod = getChestEspModule();
        if (mod == null || !mod.shouldApplyChamsTo(tileEntity)) {
            return;
        }
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(1.0f, -1_100_000.0f);
        CHEST_CHAMS_ACTIVE.set(true);
    }

    public static void onRenderChestPost() {
        if (!Boolean.TRUE.equals(CHEST_CHAMS_ACTIVE.get())) {
            return;
        }
        CHEST_CHAMS_ACTIVE.set(false);
        GL11.glPolygonOffset(1.0f, 1_100_000.0f);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    private static ChestESP getChestEspModule() {
        Module m = ModuleManager.getModule(ChestESP.class);
        return m instanceof ChestESP && m.isEnabled() ? (ChestESP) m : null;
    }

    private boolean shouldApplyChamsTo(TileEntity tileEntity) {
        ChestVisualSettings settings = getChestSettings(getChestKind(tileEntity));
        return settings != null
                && settings.isChamsEnabled()
                && isWithinMaxDistance(tileEntity)
                && !shouldSkipOpened(settings, tileEntity);
    }

    private ChestKind getChestKind(TileEntity tileEntity) {
        if (tileEntity instanceof TileEntityEnderChest) {
            return ChestKind.ENDER;
        }
        if (tileEntity instanceof TileEntityChest) {
            return ((TileEntityChest) tileEntity).getChestType() == TRAPPED_CHEST_TYPE ? ChestKind.TRAPPED : ChestKind.NORMAL;
        }
        return ChestKind.NONE;
    }

    private ChestVisualSettings registerChestSettings(String groupName, int red, int green, int blue) {
        GroupSetting group = new GroupSetting(groupName);
        this.registerSetting(group);

        ButtonSetting outline = new ButtonSetting(group, "Outline", false);
        this.registerSetting(outline);

        ColorSetting outlineColor = new ColorSetting(group, "Outline color", red, green, blue, 255);
        this.registerSetting(outlineColor);

        ButtonSetting shade = new ButtonSetting(group, "Shade", false);
        this.registerSetting(shade);

        ColorSetting shadeColor = new ColorSetting(group, "Shade color", red, green, blue, 255);
        this.registerSetting(shadeColor);

        ButtonSetting chams = new ButtonSetting(group, "Chams", false);
        this.registerSetting(chams);

        ButtonSetting disableIfOpened = new ButtonSetting(group, "Disable if opened", false);
        this.registerSetting(disableIfOpened);

        return new ChestVisualSettings(outline, outlineColor, shade, shadeColor, chams, disableIfOpened);
    }

    private ChestVisualSettings getChestSettings(ChestKind chestKind) {
        return chestSettingsByKind.get(chestKind);
    }

    private boolean shouldSkipOpened(ChestVisualSettings settings, TileEntity tileEntity) {
        if (settings == null || !settings.isDisableIfOpened()) {
            return false;
        }
        if (tileEntity instanceof TileEntityChest) {
            return ((TileEntityChest) tileEntity).lidAngle > 0.0f;
        }
        return tileEntity instanceof TileEntityEnderChest && ((TileEntityEnderChest) tileEntity).lidAngle > 0.0f;
    }

    private boolean hasAnyWorldOverlayEnabled() {
        for (ChestKind chestKind : RENDERABLE_CHEST_KINDS) {
            if (getChestSettings(chestKind).hasWorldOverlayEnabled()) {
                return true;
            }
        }
        return false;
    }

    private boolean isWithinMaxDistance(TileEntity tileEntity) {
        double maxDistSq = maxDistance.getInput() * maxDistance.getInput();
        return RenderUtils.isBlockPosWithinDistanceSqToView(tileEntity.getPos(), maxDistSq);
    }

    private AxisAlignedBB getChestBoundingBox(BlockPos pos) {
        return new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        updateTrackedWorldBatches();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent ev) {
        if (!Utils.nullCheck() || trackedWorldBatches.isEmpty()) {
            return;
        }
        for (ChestKind chestKind : RENDERABLE_CHEST_KINDS) {
            List<BlockPos> trackedBatch = trackedWorldBatches.get(chestKind);
            if (trackedBatch == null || trackedBatch.isEmpty()) {
                continue;
            }

            ChestVisualSettings settings = getChestSettings(chestKind);
            if (settings == null || !settings.hasWorldOverlayEnabled()) {
                continue;
            }

            List<BlockPos> visibleBatch = new ArrayList<>();
            for (BlockPos pos : trackedBatch) {
                if (RenderUtils.isInViewFrustum(getChestBoundingBox(pos))) {
                    visibleBatch.add(pos);
                }
            }
            if (visibleBatch.isEmpty()) {
                continue;
            }

            RenderUtils.renderChestBatch(
                    visibleBatch,
                    settings.getOutlineColor(),
                    settings.getShadeColor(),
                    settings.isOutlineEnabled(),
                    settings.isShadeEnabled()
            );
        }
    }

    private void updateTrackedWorldBatches() {
        trackedWorldBatches.clear();
        if (!Utils.nullCheck() || mc.theWorld == null || !hasAnyWorldOverlayEnabled()) {
            return;
        }

        double maxDistSq = maxDistance.getInput() * maxDistance.getInput();
        for (ChestKind chestKind : RENDERABLE_CHEST_KINDS) {
            ChestVisualSettings settings = getChestSettings(chestKind);
            if (settings != null && settings.hasWorldOverlayEnabled()) {
                trackedWorldBatches.put(chestKind, new ArrayList<>());
            }
        }

        for (TileEntity tileEntity : mc.theWorld.loadedTileEntityList) {
            ChestKind chestKind = getChestKind(tileEntity);
            ChestVisualSettings settings = getChestSettings(chestKind);
            if (settings == null || !settings.hasWorldOverlayEnabled() || shouldSkipOpened(settings, tileEntity)) {
                continue;
            }

            BlockPos pos = tileEntity.getPos();
            if (!RenderUtils.isBlockPosWithinDistanceSqToView(pos, maxDistSq)) {
                continue;
            }

            List<BlockPos> trackedBatch = trackedWorldBatches.get(chestKind);
            if (trackedBatch != null) {
                trackedBatch.add(pos);
            }
        }
    }

    private enum ChestKind {
        NONE,
        NORMAL,
        TRAPPED,
        ENDER
    }

    private static final class ChestVisualSettings {
        private final ButtonSetting outline;
        private final ColorSetting outlineColor;
        private final ButtonSetting shade;
        private final ColorSetting shadeColor;
        private final ButtonSetting chams;
        private final ButtonSetting disableIfOpened;

        private ChestVisualSettings(ButtonSetting outline, ColorSetting outlineColor, ButtonSetting shade,
                                    ColorSetting shadeColor, ButtonSetting chams, ButtonSetting disableIfOpened) {
            this.outline = outline;
            this.outlineColor = outlineColor;
            this.shade = shade;
            this.shadeColor = shadeColor;
            this.chams = chams;
            this.disableIfOpened = disableIfOpened;
        }

        private void updateVisibility(Module owner) {
            outlineColor.setVisible(outline.isToggled(), owner);
            shadeColor.setVisible(shade.isToggled(), owner);
        }

        private boolean hasWorldOverlayEnabled() {
            return isOutlineEnabled() || isShadeEnabled();
        }

        private boolean isOutlineEnabled() {
            return outline.isToggled();
        }

        private int getOutlineColor() {
            return outlineColor.getColor();
        }

        private boolean isShadeEnabled() {
            return shade.isToggled();
        }

        private int getShadeColor() {
            return shadeColor.getColor();
        }

        private boolean isChamsEnabled() {
            return chams.isToggled();
        }

        private boolean isDisableIfOpened() {
            return disableIfOpened.isToggled();
        }
    }
}
