package keystrokesmod.clickgui;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.clickgui.components.FocusableTextComponent;
import keystrokesmod.clickgui.components.impl.BindComponent;
import keystrokesmod.clickgui.components.impl.CategoryComponent;
import keystrokesmod.clickgui.components.impl.ModuleComponent;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.CommandLine;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.utility.CommandHandler;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.shader.BlurUtils;
import keystrokesmod.utility.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ClickGui extends GuiScreen {
    private ScheduledFuture sf;
    private Timer logoSmoothWidth;
    private Timer logoSmoothLength;
    private Timer smoothEntity;
    private Timer backgroundFade;
    private Timer blurSmooth;
    private ScaledResolution sr;
    private GuiButtonExt commandLineSend;
    private GuiTextField commandLineInput;
    public static ArrayList<CategoryComponent> categories;
    private int actualScreenWidth;
    private int actualScreenHeight;
    private double previousScale;
    private static boolean isNotFirstOpen;
    private boolean pendingScaleRefresh;

    public ClickGui() {
        categories = new ArrayList();
        int y = 5;
        Module.category[] values;
        int length = (values = Module.category.values()).length;

        for (int i = 0; i < length; ++i) {
            Module.category c = values[i];
            CategoryComponent categoryComponent = new CategoryComponent(c);
            categoryComponent.setY(y, false);
            categories.add(categoryComponent);
            y += 20;
        }
    }

    public void initMain() {
        (this.logoSmoothWidth = this.smoothEntity = this.blurSmooth = this.backgroundFade = new Timer(500.0F)).start();
        this.sf = Raven.getScheduledExecutor().schedule(() -> {
            (this.logoSmoothLength = new Timer(650.0F)).start();
        }, 650L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void initGui() {
        super.initGui();
        double configuredScale = getConfiguredGuiScale();
        if (!isNotFirstOpen) {
            isNotFirstOpen = true;
            this.previousScale = configuredScale;
        }
        for (CategoryComponent categoryComponent : categories) {
            categoryComponent.setScreenSize(this.width, this.height);
        }
        if (Double.compare(this.previousScale, configuredScale) != 0) {
            for (CategoryComponent categoryComponent : categories) {
                categoryComponent.limitPositions();
            }
        }
        for (CategoryComponent categoryComponent : categories) {
            if (categoryComponent.category == Module.category.profiles) {
                categoryComponent.reloadModules(true);
            } else if (categoryComponent.category == Module.category.scripts) {
                categoryComponent.reloadModules(false);
            } else {
                categoryComponent.reloadModules();
            }
        }
        (this.commandLineInput = new GuiTextField(1, this.mc.fontRendererObj, 22, this.height - 100, 150, 20)).setMaxStringLength(256);
        this.buttonList.add(this.commandLineSend = new GuiButtonExt(2, 22, this.height - 70, 150, 20, "Send"));
        this.commandLineSend.visible = CommandLine.opened;
        this.previousScale = configuredScale;
    }

    /** Categories in render order: least recently interacted first (so most recent drawn on top). */
    private List<CategoryComponent> getCategoriesInRenderOrder() {
        List<CategoryComponent> renderOrder = new ArrayList<>(categories);
        renderOrder.sort(Comparator.comparingLong(c -> c.lastInteractedTime));
        return renderOrder;
    }

    /** Returns the topmost CategoryComponent under the cursor, or null. */
    private CategoryComponent getTopmostUnderCursor(List<CategoryComponent> renderOrder, int x, int y) {
        for (int i = renderOrder.size() - 1; i >= 0; i--) {
            if (renderOrder.get(i).overRect(x, y)) {
                return renderOrder.get(i);
            }
        }
        return null;
    }

    public void drawScreen(int x, int y, float p) {
        int logicalMouseX = toLogicalCoordinate(x);
        int logicalMouseY = toLogicalCoordinate(y);

        if (Gui.backgroundBlur.getInput() != 0) {
            BlurUtils.prepareBlur();
            RoundedUtils.drawRound(0, 0, this.actualScreenWidth, this.actualScreenHeight, 0.0f, true, Color.black);
            float inputToRange = (float) (3 * ((Gui.backgroundBlur.getInput() + 35) / 100));
            BlurUtils.blurEnd(2, this.blurSmooth.getValueFloat(0, inputToRange, 1));
        }
        if (Gui.darkBackground.isToggled()) {
            drawRect(0, 0, this.actualScreenWidth, this.actualScreenHeight, (int) (this.backgroundFade.getValueFloat(0.0F, 0.7F, 2) * 255.0F) << 24);
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(getRenderScale(), getRenderScale(), 1.0D);

        int r;
        if (!Gui.removeWatermark.isToggled()) {
            int h = this.height / 4;
            int wd = this.width / 2;
            int w_c = 30 - this.logoSmoothWidth.getValueInt(0, 30, 3);
            this.drawCenteredString(this.fontRendererObj, "r", wd + 1 - w_c, h - 25, Utils.getChroma(2L, 1500L));
            this.drawCenteredString(this.fontRendererObj, "a", wd - w_c, h - 15, Utils.getChroma(2L, 1200L));
            this.drawCenteredString(this.fontRendererObj, "v", wd - w_c, h - 5, Utils.getChroma(2L, 900L));
            this.drawCenteredString(this.fontRendererObj, "e", wd - w_c, h + 5, Utils.getChroma(2L, 600L));
            this.drawCenteredString(this.fontRendererObj, "n", wd - w_c, h + 15, Utils.getChroma(2L, 300L));
            this.drawCenteredString(this.fontRendererObj, "bS", wd + 1 + w_c, h + 30, Utils.getChroma(2L, 0L));
            this.drawVerticalLine(wd - 10 - w_c, h - 30, h + 43, Color.white.getRGB());
            this.drawVerticalLine(wd + 10 + w_c, h - 30, h + 43, Color.white.getRGB());
            if (this.logoSmoothLength != null) {
                r = this.logoSmoothLength.getValueInt(0, 20, 2);
                this.drawHorizontalLine(wd - 10, wd - 10 + r, h - 29, -1);
                this.drawHorizontalLine(wd + 10, wd + 10 - r, h + 42, -1);
            }
        }

        List<CategoryComponent> renderOrder = getCategoriesInRenderOrder();
        CategoryComponent topmostUnderCursor = getTopmostUnderCursor(renderOrder, logicalMouseX, logicalMouseY);
        for (CategoryComponent c : renderOrder) {
            c.render(this.fontRendererObj);
            c.mousePosition(logicalMouseX, logicalMouseY, c == topmostUnderCursor);

            for (Component m : c.getModules()) {
                m.drawScreen(logicalMouseX, logicalMouseY);
            }
        }

        GL11.glColor3f(1.0f, 1.0f, 1.0f);
        if (!Gui.removePlayerModel.isToggled()) {
            GlStateManager.pushMatrix();
            GlStateManager.disableBlend();
            GuiInventory.drawEntityOnScreen(this.width + 15 - this.smoothEntity.getValueInt(0, 40, 2), this.height - 10, 40, (float) (this.width - 25 - logicalMouseX), (float) (this.height - 50 - logicalMouseY), this.mc.thePlayer);
            GlStateManager.enableBlend();
            GlStateManager.popMatrix();
        }


        if (CommandLine.opened) {
            if (!this.commandLineSend.visible) {
                this.commandLineSend.visible = true;
            }

            r = CommandLine.animate.isToggled() ? CommandLine.animation.getValueInt(0, 200, 2) : 200;
            if (CommandLine.closed) {
                r = 200 - r;
                if (r == 0) {
                    CommandLine.closed = false;
                    CommandLine.opened = false;
                    this.commandLineSend.visible = false;
                }
            }
            drawRect(0, 0, r, this.height, -1089466352);
            this.drawHorizontalLine(0, r - 1, (this.height - 345), -1);
            this.drawHorizontalLine(0, r - 1, (this.height - 115), -1);
            drawRect(r - 1, 0, r, this.height, -1);
            CommandHandler.renderCommandOutput(this.fontRendererObj, this.height, r, this.sr.getScaleFactor());
            int x2 = r - 178;
            this.commandLineInput.xPosition = x2;
            this.commandLineSend.xPosition = x2;
            this.commandLineInput.drawTextBox();
            super.drawScreen(logicalMouseX, logicalMouseY, p);
        }
        else if (CommandLine.closed) {
            CommandLine.closed = false;
        }

        GlStateManager.popMatrix();
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        List<CategoryComponent> inputOrder = new ArrayList<>(categories);
        inputOrder.sort((a, b) -> Long.compare(b.lastInteractedTime, a.lastInteractedTime));
        CategoryComponent topmostCategory = null;
        for (CategoryComponent category : inputOrder) {
            if (category.overRect(mouseX, mouseY)) {
                topmostCategory = category;
                break;
            }
        }

        if (topmostCategory != null) {
            topmostCategory.markInteracted();
        }

        if (mouseButton == 0) {
            for (CategoryComponent category : categories) {
                category.overTitle(false);
            }
            if (topmostCategory != null && topmostCategory.draggable(mouseX, mouseY)) {
                topmostCategory.overTitle(true);
                topmostCategory.xx = mouseX - topmostCategory.getX();
                topmostCategory.yy = mouseY - topmostCategory.getY();
                topmostCategory.dragging = true;
            }
        }

        if (mouseButton == 1 && topmostCategory != null && topmostCategory.overTitle(mouseX, mouseY)) {
            topmostCategory.mouseClicked(!topmostCategory.isOpened());
        }

        if (topmostCategory != null && topmostCategory.isOpened() && !topmostCategory.getModules().isEmpty() && !topmostCategory.overTitle(mouseX, mouseY)) {
            for (ModuleComponent component : topmostCategory.getModules()) {
                if (component.onClick(mouseX, mouseY, mouseButton)) {
                    break;
                }
            }
        }

        if (CommandLine.opened) {
            this.commandLineInput.mouseClicked(mouseX, mouseY, mouseButton);
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (mouseButton == 0 || mouseButton == 1) {
            FocusableTextComponent focusedComponent = findFocusedTextComponentAt(mouseX, mouseY);
            enforceSingleFocusedTextInput(focusedComponent);
        }
    }


    public void mouseReleased(int x, int y, int button) {
        if (button == 0) {
            for (CategoryComponent category : categories) {
                category.overTitle(false);
                if (category.isOpened() && !category.getModules().isEmpty()) {
                    for (Component module : category.getModules()) {
                        module.mouseReleased(x, y, button);
                    }
                }
            }
        }
        if (pendingScaleRefresh) {
            pendingScaleRefresh = false;
            refreshLayoutForConfiguredScale();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheelInput = Mouse.getDWheel();
        if (wheelInput != 0) {
            int mouseX = Mouse.getEventX() * this.width / mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / mc.displayHeight - 1;
            for (CategoryComponent category : categories) {
                category.onScroll(wheelInput, mouseX, mouseY);
            }
        }
    }

    /**
     * Refreshes the ClickGui for the newly loaded profile's Gui scale. Call after
     * all module settings (including Gui.guiScale) are loaded. Recomputes the
     * ClickGui layout using the profile's configured internal scale.
     */
    public void refreshAfterProfileLoad() {
        if (mc == null) {
            mc = Minecraft.getMinecraft();
        }
        refreshLayoutForConfiguredScale();
    }

    @Override
    public void setWorldAndResolution(Minecraft p_setWorldAndResolution_1_, final int p_setWorldAndResolution_2_, final int p_setWorldAndResolution_3_) {
        this.mc = p_setWorldAndResolution_1_;
        this.itemRender = p_setWorldAndResolution_1_.getRenderItem();
        this.fontRendererObj = p_setWorldAndResolution_1_.fontRendererObj;
        refreshScaledResolution();
        if (!MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.InitGuiEvent.Pre(this, this.buttonList))) {
            this.buttonList.clear();
            this.initGui();
        }
        MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.InitGuiEvent.Post(this, this.buttonList));
    }

    @Override
    public void keyTyped(char t, int k) {
        FocusableTextComponent activeTextInput = getActiveFocusedTextInput();
        if (k == Keyboard.KEY_ESCAPE) {
            if (activeTextInput != null) {
                activeTextInput.unfocusTextInput();
                return;
            }
            if (!binding()) {
                this.mc.displayGuiScreen(null);
                return;
            }
        }

        if (activeTextInput != null) {
            for (CategoryComponent category : categories) {
                if (!category.isOpened() || category.getModules().isEmpty()) {
                    continue;
                }
                for (Component module : category.getModules()) {
                    module.keyTyped(t, k);
                }
            }
            return;
        }

        for (CategoryComponent category : categories) {
            if (category.isOpened() && !category.getModules().isEmpty()) {
                for (Component module : category.getModules()) {
                    module.keyTyped(t, k);
                }
            }
        }
        if (CommandLine.opened) {
            String cm = this.commandLineInput.getText();
            if (k == 28 && !cm.isEmpty()) {
                CommandHandler.runCommand(this.commandLineInput.getText());
                this.commandLineInput.setText("");
                return;
            }
            this.commandLineInput.textboxKeyTyped(t, k);
        }
    }

    public void actionPerformed(GuiButton b) {
        if (b == this.commandLineSend) {
            CommandHandler.runCommand(this.commandLineInput.getText());
            this.commandLineInput.setText("");
        }
    }

    @Override
    public void onGuiClosed() {
        this.logoSmoothLength = null;
        if (this.sf != null) {
            this.sf.cancel(true);
            this.sf = null;
        }
        for (CategoryComponent c : categories) {
            c.dragging = false;
            c.onGuiClosed();
            for (Component m : c.getModules()) {
                m.onGuiClosed();
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean binding() {
        for (CategoryComponent c : categories) {
            for (ModuleComponent m : c.getModules()) {
                for (Component component : m.settings) {
                    if (component instanceof BindComponent && ((BindComponent) component).isBinding) {
                        return true;
                    }
                    if (component instanceof FocusableTextComponent && ((FocusableTextComponent) component).isTextInputFocused()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean unfocusFocusedTextInput() {
        for (CategoryComponent category : categories) {
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent textComponent = (FocusableTextComponent) component;
                        if (textComponent.isTextInputFocused()) {
                            textComponent.unfocusTextInput();
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private FocusableTextComponent getActiveFocusedTextInput() {
        FocusableTextComponent activeComponent = null;
        for (CategoryComponent category : categories) {
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent textComponent = (FocusableTextComponent) component;
                        if (textComponent.isTextInputFocused()) {
                            if (activeComponent == null) {
                                activeComponent = textComponent;
                            }
                            else {
                                textComponent.unfocusTextInput();
                            }
                        }
                    }
                }
            }
        }
        return activeComponent;
    }

    private FocusableTextComponent findFocusedTextComponentAt(int mouseX, int mouseY) {
        List<CategoryComponent> inputOrder = new ArrayList<>(categories);
        inputOrder.sort((a, b) -> Long.compare(b.lastInteractedTime, a.lastInteractedTime));

        for (CategoryComponent category : inputOrder) {
            if (!category.isOpened() || !category.overRect(mouseX, mouseY)) {
                continue;
            }

            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent textComponent = (FocusableTextComponent) component;
                        if (textComponent.isTextInputFocused() && textComponent.containsClick(mouseX, mouseY)) {
                            return textComponent;
                        }
                    }
                }
            }
        }

        return null;
    }

    private void enforceSingleFocusedTextInput(FocusableTextComponent focusedComponentToKeep) {
        for (CategoryComponent category : categories) {
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent textComponent = (FocusableTextComponent) component;
                        if (textComponent != focusedComponentToKeep && textComponent.isTextInputFocused()) {
                            textComponent.unfocusTextInput();
                        }
                    }
                }
            }
        }
    }

    public void onSliderChange() {
        for (CategoryComponent c : categories) {
            for (ModuleComponent m : c.getModules()) {
                m.onSliderChange();
            }
        }
    }

    public void requestScaleRefresh() {
        this.pendingScaleRefresh = true;
    }

    private void refreshLayoutForConfiguredScale() {
        refreshScaledResolution();
        for (CategoryComponent categoryComponent : categories) {
            categoryComponent.setScreenSize(this.width, this.height);
            categoryComponent.limitPositions();
        }
        this.buttonList.clear();
        initGui();
    }

    private void refreshScaledResolution() {
        this.sr = new ScaledResolution(mc);
        this.actualScreenWidth = this.sr.getScaledWidth();
        this.actualScreenHeight = this.sr.getScaledHeight();

        double targetScaleFactor = getTargetGuiScaleFactor();
        this.width = Math.max(1, MathHelper.ceiling_double_int((double) mc.displayWidth / targetScaleFactor));
        this.height = Math.max(1, MathHelper.ceiling_double_int((double) mc.displayHeight / targetScaleFactor));
    }

    private int getMaximumGuiScaleFactor() {
        int scaleFactor = 1;
        while (mc.displayWidth / (scaleFactor + 1) >= 320 && mc.displayHeight / (scaleFactor + 1) >= 240) {
            ++scaleFactor;
        }

        if (mc.isUnicode() && scaleFactor % 2 != 0 && scaleFactor != 1) {
            --scaleFactor;
        }

        return scaleFactor;
    }

    private double getTargetGuiScaleFactor() {
        // Old "Normal" mode forced Minecraft guiScale=2, so treat 1.0x as that baseline.
        return Math.max(1.0D, Math.min(getMaximumGuiScaleFactor(), getConfiguredGuiScale() * 2.0D));
    }

    private int toLogicalCoordinate(int coordinate) {
        return (int) Math.floor(coordinate / getRenderScale());
    }

    private double getRenderScale() {
        return actualScreenWidth <= 0 || width <= 0 ? 1.0D : (double) actualScreenWidth / (double) width;
    }

    public static double getActiveRenderScale() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.currentScreen instanceof ClickGui ? ((ClickGui) minecraft.currentScreen).getRenderScale() : 1.0D;
    }

    private double getConfiguredGuiScale() {
        return Gui.getClickGuiScale();
    }
}
