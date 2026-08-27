package keystrokesmod.module.impl.player;

import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.impl.StringListSetting;
import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.network.play.server.S2EPacketCloseWindow;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.IOException;

public class HideWindow extends Module {
    private static final String ICON_PATH = "/assets/keystrokesmod/textures/gui/tv_off.png";
    private static final int ICON_BASE_SIZE = 16;
    private static final float DEFAULT_RELATIVE_X = 0.5f;
    private static final float DEFAULT_RELATIVE_Y = 0.05f;
    private static final int EDIT_OUTLINE_COLOR = 0xFFFFFFFF;

    private final ColorSetting iconColor;
    private final SliderSetting iconScale;
    private final ButtonSetting onlyWhileCrouching;
    private final ButtonSetting whitelist;
    private final StringListSetting whitelistEntries;

    private GuiContainer hiddenGui;

    private float posX = Float.NaN;
    private float posY = Float.NaN;
    private float relativePosX = Float.NaN;
    private float relativePosY = Float.NaN;

    public HideWindow() {
        super("Hide Window", category.player);
        this.registerSetting(iconColor = new ColorSetting("Icon color", 255, 255, 255));
        this.registerSetting(iconScale = new SliderSetting("Icon scale", 1.0, 0.5, 3.0, 0.1));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.displayGuiScreen(new EditScreen())));
        this.registerSetting(onlyWhileCrouching = new ButtonSetting("Only while crouching", false));
        this.registerSetting(whitelist = new ButtonSetting("Whitelist", false));
        this.registerSetting(whitelistEntries = new StringListSetting("Whitelist names", "e.g. Upgrades & Traps", 128));
        whitelistEntries.visible = false;
    }

    @Override
    public String getInfo() {
        return hiddenGui != null ? "Hidden" : "";
    }

    @Override
    public void guiUpdate() {
        whitelistEntries.setVisible(whitelist.isToggled(), this);
    }

    @Override
    public void onDisable() {
        if (hiddenGui != null && mc.thePlayer != null) {
            mc.thePlayer.closeScreen();
        }
        hiddenGui = null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui == null || mc.thePlayer == null) {
            return;
        }

        if (event.gui instanceof GuiContainer && !(event.gui instanceof GuiInventory)) {
            if (mc.currentScreen instanceof GuiContainer) {
                hiddenGui = null;
                return;
            }

            GuiContainer gui = (GuiContainer) event.gui;
            if (onlyWhileCrouching.isToggled() && !mc.thePlayer.isSneaking()) {
                return;
            }
            if (whitelist.isToggled() && !matchesWhitelist(gui)) {
                return;
            }

            hiddenGui = gui;
            mc.thePlayer.openContainer = hiddenGui.inventorySlots;
            event.setCanceled(true);
            return;
        }

        if (event.gui instanceof GuiInventory && hiddenGui != null) {
            event.gui = hiddenGui;
            hiddenGui = null;
        }
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof S2EPacketCloseWindow) {
            hiddenGui = null;
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        hiddenGui = null;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (hiddenGui == null || mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }
        renderIcon(false);
    }

    public float getPosX() {
        syncPosition();
        return posX;
    }

    public float getPosY() {
        syncPosition();
        return posY;
    }

    public float getRelativePosX() {
        syncPosition();
        return relativePosX;
    }

    public float getRelativePosY() {
        syncPosition();
        return relativePosY;
    }

    public void setRelativePosition(float normalizedX, float normalizedY) {
        relativePosX = normalizedX;
        relativePosY = normalizedY;
        syncPosition();
    }

    public void setAbsolutePosition(float absoluteX, float absoluteY) {
        setAbsolutePosition(absoluteX, absoluteY, new ScaledResolution(mc));
    }

    public void resetPosition() {
        setRelativePosition(DEFAULT_RELATIVE_X, DEFAULT_RELATIVE_Y);
    }

    private void renderIcon(boolean editing) {
        syncPosition();
        ResourceLocation icon = RenderUtils.getIcon(ICON_PATH);
        if (icon == null) {
            return;
        }

        float scale = (float) iconScale.getInput();
        int size = Math.round(ICON_BASE_SIZE * scale);
        float drawX = posX - size / 2.0f;
        float drawY = posY - size / 2.0f;

        RenderUtils.drawIcon(icon, drawX, drawY, size, 0xFF000000 | iconColor.getRGB());

        if (editing) {
            float outLeft = drawX - 2;
            float outTop = drawY - 2;
            float outRight = drawX + size + 2;
            float outBottom = drawY + size + 2;
            RenderUtils.drawRect(outLeft, outTop, outRight, outTop + 1, EDIT_OUTLINE_COLOR);
            RenderUtils.drawRect(outLeft, outBottom - 1, outRight, outBottom, EDIT_OUTLINE_COLOR);
            RenderUtils.drawRect(outLeft, outTop, outLeft + 1, outBottom, EDIT_OUTLINE_COLOR);
            RenderUtils.drawRect(outRight - 1, outTop, outRight, outBottom, EDIT_OUTLINE_COLOR);
        }
    }

    private void syncPosition() {
        syncPosition(new ScaledResolution(mc));
    }

    private void syncPosition(ScaledResolution resolution) {
        int w = Math.max(1, resolution.getScaledWidth());
        int h = Math.max(1, resolution.getScaledHeight());
        if (Float.isNaN(relativePosX) || Float.isNaN(relativePosY)) {
            if (Float.isNaN(posX) || Float.isNaN(posY)) {
                relativePosX = DEFAULT_RELATIVE_X;
                relativePosY = DEFAULT_RELATIVE_Y;
            } else {
                relativePosX = posX / w;
                relativePosY = posY / h;
            }
        }
        posX = relativePosX * w;
        posY = relativePosY * h;
    }

    private void setAbsolutePosition(float absoluteX, float absoluteY, ScaledResolution resolution) {
        posX = absoluteX;
        posY = absoluteY;
        int w = Math.max(1, resolution.getScaledWidth());
        int h = Math.max(1, resolution.getScaledHeight());
        relativePosX = absoluteX / w;
        relativePosY = absoluteY / h;
    }

    private boolean matchesWhitelist(GuiContainer gui) {
        java.util.List<String> entries = whitelistEntries.getEntries();
        if (entries.isEmpty()) {
            return false;
        }
        String title = getContainerTitle(gui);
        if (title.isEmpty()) {
            return false;
        }
        String lower = title.toLowerCase();
        for (String name : entries) {
            if (lower.contains(name.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String getContainerTitle(GuiContainer gui) {
        if (gui.inventorySlots instanceof ContainerChest) {
            return ((ContainerChest) gui.inventorySlots)
                    .getLowerChestInventory()
                    .getDisplayName()
                    .getUnformattedText();
        }
        return "";
    }

    private class EditScreen extends GuiScreen {
        private GuiButtonExt resetBtn;
        private boolean dragging;
        private float actualX, actualY;
        private float lastActualX, lastActualY;
        private int lastMouseX, lastMouseY;

        @Override
        public void initGui() {
            super.initGui();
            buttonList.add(resetBtn = new GuiButtonExt(1, width - 90, height - 25, 85, 20, "Reset position"));
            syncPosition(new ScaledResolution(mc));
            actualX = posX;
            actualY = posY;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            ScaledResolution resolution = new ScaledResolution(mc);
            if (!dragging) {
                syncPosition(resolution);
                actualX = posX;
                actualY = posY;
            }

            drawRect(0, 0, width, height, 0xB2000000);
            setAbsolutePosition(actualX, actualY, resolution);
            renderIcon(true);
            actualX = posX;
            actualY = posY;

            String message = "Drag the icon to reposition it.";
            int textX = resolution.getScaledWidth() / 2 - fontRendererObj.getStringWidth(message) / 2;
            int textY = resolution.getScaledHeight() / 2 - 10;
            RenderUtils.drawColoredString(message, '-', textX, textY, 2L, 0L, true, mc.fontRendererObj);

            try {
                handleInput();
            } catch (IOException ignored) {
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
            super.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);
            if (button != 0) return;

            if (dragging) {
                actualX = lastActualX + (mouseX - lastMouseX);
                actualY = lastActualY + (mouseY - lastMouseY);
            } else {
                float scale = (float) iconScale.getInput();
                int size = Math.round(ICON_BASE_SIZE * scale);
                float minX = posX - size / 2.0f - 2;
                float minY = posY - size / 2.0f - 2;
                float maxX = posX + size / 2.0f + 2;
                float maxY = posY + size / 2.0f + 2;
                if (mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY) {
                    dragging = true;
                    lastMouseX = mouseX;
                    lastMouseY = mouseY;
                    lastActualX = actualX;
                    lastActualY = actualY;
                }
            }
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) {
                dragging = false;
            }
        }

        @Override
        public void actionPerformed(GuiButton button) {
            if (button == resetBtn) {
                resetPosition();
                actualX = posX;
                actualY = posY;
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
