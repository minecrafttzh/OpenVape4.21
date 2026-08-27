package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.AntiKnockback;
import keystrokesmod.module.impl.combat.Velocity;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FontManager;
import keystrokesmod.utility.font.RavenFontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;

import java.awt.Color;
import java.io.IOException;

public class HUD extends Module {
    private static final String[] COLOR_MODES = new String[] { "Static", "Gradient", "Rainbow" };
    private static final String[] WAVE_AXES = new String[] { "Vertical", "Horizontal" };
    private static final String[] VERTICAL_WAVE_DIRECTIONS = new String[] { "Down", "Up" };
    private static final String[] HORIZONTAL_WAVE_DIRECTIONS = new String[] { "Left", "Right" };
    /** Horizontal wave: scales screen X (center of row) into phase; larger = faster change across X. */
    private static final double HUD_WAVE_HORIZONTAL_X_SCALE = 0.35;
    private static final long HUD_RAINBOW_PERIOD_MS = 7500L;
    private static final double HUD_WAVE_ANGLE_SCALE = 0.12;

    public static SliderSetting colorMode;
    public static ColorSetting hudColor;
    public static ColorSetting hudColor2;
    public static SliderSetting waveAxis;
    public static SliderSetting verticalWaveDirection;
    public static SliderSetting horizontalWaveDirection;
    public static SliderSetting waveSpeed;
    public static SliderSetting waveLength;
    public static SliderSetting font;
    public static SliderSetting fontSize;
    private static SliderSetting outline;
    public static ButtonSetting alphabeticalSort;
    private static ButtonSetting drawBackground;
    private static ButtonSetting textShadow;
    private static ButtonSetting alignRight;
    private static ButtonSetting lowercase;
    public static ButtonSetting showInfo;
    private static final float DEFAULT_POS_X = 5.0f;
    private static final float DEFAULT_POS_Y = 70.0f;
    public static float posX = DEFAULT_POS_X;
    public static float posY = DEFAULT_POS_Y;
    private static float relativePosX = Float.NaN;
    private static float relativePosY = Float.NaN;

    private static final String[] OUTLINE_MODES = new String[] { "None", "Full", "Side" };
    private static final String[] HUD_FONT_OPTIONS = FontManager.getHudFontOptions();
    private static final int BACKGROUND_COLOR = new Color(0, 0, 0, 110).getRGB();

    private boolean isAlphabeticalSort;
    private boolean canShowInfo;
    private String lastHudFontName = "";
    private float lastHudFontScale = -1.0f;

    public HUD() {
        super("HUD", Module.category.render);
        this.registerSetting(colorMode = new SliderSetting("Color mode", 0, COLOR_MODES));
        this.registerSetting(hudColor = new ColorSetting("Color", 255, 255, 255));
        this.registerSetting(hudColor2 = new ColorSetting("Color 2", 85, 85, 255));
        this.registerSetting(waveAxis = new SliderSetting("Wave axis", 0, WAVE_AXES));
        this.registerSetting(verticalWaveDirection = new SliderSetting("Wave direction", 0, VERTICAL_WAVE_DIRECTIONS));
        this.registerSetting(horizontalWaveDirection = new SliderSetting("Wave direction", 0, HORIZONTAL_WAVE_DIRECTIONS));
        this.registerSetting(waveSpeed = new SliderSetting("Wave speed", 1.0, 0.1, 5.0, 0.1));
        this.registerSetting(waveLength = new SliderSetting("Wave length", 1.0, 0.5, 5.0, 0.1));
        this.registerSetting(font = new SliderSetting("Font", 0, HUD_FONT_OPTIONS));
        this.registerSetting(fontSize = new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.1));
        this.registerSetting(outline = new SliderSetting("Outline", 0, OUTLINE_MODES));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.displayGuiScreen(new EditScreen())));
        this.registerSetting(alignRight = new ButtonSetting("Align right", false));
        this.registerSetting(alphabeticalSort = new ButtonSetting("Alphabetical sort", false));
        this.registerSetting(drawBackground = new ButtonSetting("Draw background", false));
        this.registerSetting(textShadow = new ButtonSetting("Text shadow", true));
        this.registerSetting(lowercase = new ButtonSetting("Lowercase", false));
        this.registerSetting(showInfo = new ButtonSetting("Show module info", true));
    }

    @Override
    public void guiUpdate() {
        int mode = colorMode == null ? 0 : (int) colorMode.getInput();
        if (hudColor != null) {
            hudColor.setVisible(mode == 0 || mode == 1, this);
        }
        if (hudColor2 != null) {
            hudColor2.setVisible(mode == 1, this);
        }
        boolean showWaveSettings = mode == 1 || mode == 2;
        boolean verticalAxis = hudWaveIsVertical();
        if (waveAxis != null) {
            waveAxis.setVisible(showWaveSettings, this);
        }
        if (verticalWaveDirection != null) {
            verticalWaveDirection.setVisible(showWaveSettings && verticalAxis, this);
        }
        if (horizontalWaveDirection != null) {
            horizontalWaveDirection.setVisible(showWaveSettings && !verticalAxis, this);
        }
        if (waveSpeed != null) {
            waveSpeed.setVisible(showWaveSettings, this);
        }
        if (waveLength != null) {
            waveLength.setVisible(showWaveSettings, this);
        }
    }

    @Override
    public void onEnable() {
        guiUpdate();
        ModuleManager.sort();
    }

    @Override
    public void guiButtonToggled(ButtonSetting buttonSetting) {
        if (buttonSetting == alphabeticalSort || buttonSetting == showInfo) {
            ModuleManager.sort();
        }
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }

        if (isAlphabeticalSort != alphabeticalSort.isToggled()) {
            isAlphabeticalSort = alphabeticalSort.isToggled();
            ModuleManager.sort();
        }

        if (canShowInfo != showInfo.isToggled()) {
            canShowInfo = showInfo.isToggled();
            ModuleManager.sort();
        }

        String currentFontName = getSelectedFontName();
        float currentFontScale = getSelectedFontScale();
        if (!currentFontName.equals(lastHudFontName) || Float.compare(currentFontScale, lastHudFontScale) != 0) {
            lastHudFontName = currentFontName;
            lastHudFontScale = currentFontScale;
            ModuleManager.sort();
        }

        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }

        syncPositionToResolution();

        for (Module module : ModuleManager.organizedModules) {
            module.getInfoUpdate();
            if (Module.sort) {
                break;
            }
        }

        if (Module.sort) {
            ModuleManager.sort();
        }
        Module.sort = false;

        RavenFontRenderer hudFont = getHudFontRenderer();
        int textTopOffset = hudFont.getTextTopOffset();
        int textBottomOffset = hudFont.getTextBottomOffset();
        int horizontalTextPadding = getHudHorizontalTextPadding();
        int textTopPadding = getHudTextTopPadding();
        int textBottomPadding = getHudTextBottomPadding();
        int outlineThickness = getHudOutlineThickness();
        int rowHeight = getHudRowHeight(textTopOffset, textBottomOffset, textTopPadding, textBottomPadding);
        float yPos = posY;
        double verticalWaveAccum = 0.0;
        boolean firstVisibleRow = true;
        String previousModule = "";
        double lastOutlineLeft = 0.0;
        double lastOutlineRight = 0.0;
        double lastBackgroundBottom = 0.0;
        boolean removeVelocity = ModuleManager.antiKnockback.isEnabled();

        try {
            for (Module module : ModuleManager.organizedModules) {
                if (!module.isEnabled() || module == this || shouldSkipModule(module, removeVelocity)) {
                    continue;
                }

                String moduleName = getHudRenderText(module);
                int moduleWidth = hudFont.getStringWidth(moduleName);
                float xPos = posX;
                float textY = getHudTextY(yPos, textTopOffset, textTopPadding);
                double backgroundLeft = xPos - horizontalTextPadding;
                double backgroundRight = xPos + moduleWidth + horizontalTextPadding;
                double backgroundTop = yPos;
                double backgroundBottom = yPos + rowHeight;
                double outlineLeft = backgroundLeft - outlineThickness;
                double outlineRight = backgroundRight + outlineThickness;
                double outlineTop = backgroundTop - outlineThickness;

                if (alignRight.isToggled()) {
                    xPos -= moduleWidth;
                    backgroundLeft = xPos - horizontalTextPadding;
                    backgroundRight = xPos + moduleWidth + horizontalTextPadding;
                    outlineLeft = backgroundLeft - outlineThickness;
                    outlineRight = backgroundRight + outlineThickness;
                }

                double rowCenterX = (backgroundLeft + backgroundRight) * 0.5;
                double wavePhase = hudWavePhase(verticalWaveAccum, rowCenterX);
                int color = getHudColor(wavePhase);

                if (drawBackground.isToggled()) {
                    RenderUtils.drawRect(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, BACKGROUND_COLOR);
                }

                if (outline.getInput() == 1 && firstVisibleRow) {
                    RenderUtils.drawRect(outlineLeft, outlineTop, outlineRight, backgroundTop, color);
                }

                if (hudWaveIsVertical()) {
                    verticalWaveAccum += getVerticalWaveStep();
                }
                firstVisibleRow = false;

                if (outline.getInput() == 1 && !previousModule.isEmpty()) {
                    double difference = hudFont.getStringWidth(previousModule) - moduleWidth;
                    if (alphabeticalSort.isToggled() && difference < 0) {
                        RenderUtils.drawRect(outlineLeft, outlineTop, xPos - difference + horizontalTextPadding + outlineThickness, backgroundTop, color);
                    }
                    else if (alignRight.isToggled()) {
                        RenderUtils.drawRect(xPos - difference - horizontalTextPadding - outlineThickness, outlineTop, backgroundLeft, backgroundTop, color);
                    }
                    else {
                        RenderUtils.drawRect(backgroundRight, outlineTop, xPos + difference + moduleWidth + horizontalTextPadding + outlineThickness, backgroundTop, color);
                    }
                }

                if (outline.getInput() > 0) {
                    if (alignRight.isToggled()) {
                        RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                    }
                    else {
                        RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                    }
                }

                if (outline.getInput() == 1) {
                    if (alignRight.isToggled()) {
                        RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                    }
                    else {
                        RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                    }
                }

                drawHudText(hudFont, moduleName, xPos, textY, color);
                previousModule = moduleName;
                lastOutlineLeft = outlineLeft;
                lastOutlineRight = outlineRight;
                lastBackgroundBottom = backgroundBottom;
                yPos += rowHeight;
            }
        }
        catch (Exception exception) {
            Utils.sendMessage("&cAn error occurred rendering HUD. check your logs");
            exception.printStackTrace();
        }

        if (outline.getInput() == 1 && !previousModule.isEmpty()) {
            double bottomCenterX = (lastOutlineLeft + lastOutlineRight) * 0.5;
            double bottomPhase = hudWavePhase(verticalWaveAccum, bottomCenterX);
            RenderUtils.drawRect(lastOutlineLeft, lastBackgroundBottom, lastOutlineRight, lastBackgroundBottom + outlineThickness, getHudColor(bottomPhase));
        }
    }

    public static int getLongestModule() {
        RavenFontRenderer hudFont = getHudFontRenderer();
        int length = 0;

        for (Module module : ModuleManager.organizedModules) {
            if (module.isEnabled()) {
                length = Math.max(length, hudFont.getStringWidth(getHudRenderText(module)));
            }
        }

        return length;
    }

    private static boolean shouldSkipModule(Module module, boolean removeVelocity) {
        if (module.isHidden()) {
            return true;
        }
        if (module == ModuleManager.commandLine) {
            return true;
        }
        return module instanceof Velocity && removeVelocity;
    }

    private static boolean isLastVisibleModule(Module currentModule, boolean removeVelocity) {
        boolean foundCurrent = false;

        for (Module module : ModuleManager.organizedModules) {
            if (!foundCurrent) {
                if (module == currentModule) {
                    foundCurrent = true;
                }
                continue;
            }

            if (module.isEnabled() && !(module instanceof HUD) && !shouldSkipModule(module, removeVelocity)) {
                return false;
            }
        }

        return true;
    }

    static class EditScreen extends GuiScreen {
        private static final String EXAMPLE = "This is an-Example-HUD";

        private GuiButtonExt resetPosition;
        private boolean dragging = false;
        private float minX = 0.0f;
        private float minY = 0.0f;
        private float maxX = 0.0f;
        private float maxY = 0.0f;
        private float actualX = 5.0f;
        private float actualY = 70.0f;
        private float lastActualX = 0.0f;
        private float lastActualY = 0.0f;
        private int lastMouseX = 0;
        private int lastMouseY = 0;
        private float clickMinX = 0.0f;

        @Override
        public void initGui() {
            super.initGui();
            this.buttonList.add(this.resetPosition = new GuiButtonExt(1, this.width - 90, this.height - 25, 85, 20, "Reset position"));
            HUD.syncPositionToResolution(new ScaledResolution(this.mc));
            this.actualX = HUD.posX;
            this.actualY = HUD.posY;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            ScaledResolution resolution = new ScaledResolution(this.mc);
            if (!this.dragging) {
                HUD.syncPositionToResolution(resolution);
                this.actualX = HUD.posX;
                this.actualY = HUD.posY;
            }
            drawRect(0, 0, this.width, this.height, -1308622848);
            float previewX = this.actualX;
            float previewY = this.actualY;
            float previewMaxX = previewX + 50.0f;
            float previewMaxY = previewY + 32.0f;
            float[] clickPos = this.getPreviewBounds(EXAMPLE);

            this.minX = previewX;
            this.minY = previewY;

            if (clickPos == null) {
                this.maxX = previewMaxX;
                this.maxY = previewMaxY;
                this.clickMinX = previewX;
            }
            else {
                this.maxX = clickPos[0];
                this.maxY = clickPos[1];
                this.clickMinX = clickPos[2];
            }

            HUD.setAbsolutePosition(previewX, previewY, resolution);

            int textX = resolution.getScaledWidth() / 2 - 84;
            int textY = resolution.getScaledHeight() / 2 - 20;
            RenderUtils.drawColoredString("Edit the HUD position by dragging.", '-', textX, textY, 2L, 0L, true, this.mc.fontRendererObj);

            try {
                this.handleInput();
            }
            catch (IOException ignored) {
            }

            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        private float[] getPreviewBounds(String text) {
            RavenFontRenderer hudFont = HUD.getHudFontRenderer();

            if (empty()) {
                float x = this.minX;
                float y = this.minY;
                String[] lines = text.split("-");
                int localTextTopPadding = getHudTextTopPadding();
                int localTextBottomPadding = getHudTextBottomPadding();
                int localRowHeight = getHudRowHeight(hudFont.getTextTopOffset(), hudFont.getTextBottomOffset(), localTextTopPadding, localTextBottomPadding);

                for (String line : lines) {
                    if (HUD.alignRight.isToggled()) {
                        x += hudFont.getStringWidth(lines[0]) - hudFont.getStringWidth(line);
                    }
                    float textY = getHudTextY(y, hudFont.getTextTopOffset(), localTextTopPadding);
                    drawHudText(hudFont, line, x, textY, Color.white.getRGB());
                    y += localRowHeight;
                }
                return null;
            }

            int longestModule = getLongestModule();
            float y = this.minY;
            double verticalWaveAccum = 0.0;
            boolean firstVisibleRow = true;
            String previousModule = "";
            double lastOutlineLeft = 0.0;
            double lastOutlineRight = 0.0;
            double lastBackgroundBottom = 0.0;
            boolean removeVelocity = ModuleManager.antiKnockback.isEnabled();
            int textTopOffset = hudFont.getTextTopOffset();
            int textBottomOffset = hudFont.getTextBottomOffset();
            int horizontalTextPadding = getHudHorizontalTextPadding();
            int textTopPadding = getHudTextTopPadding();
            int textBottomPadding = getHudTextBottomPadding();
            int outlineThickness = getHudOutlineThickness();
            int rowHeight = getHudRowHeight(textTopOffset, textBottomOffset, textTopPadding, textBottomPadding);

            try {
                for (Module module : ModuleManager.organizedModules) {
                    if (!module.isEnabled() || module instanceof HUD || shouldSkipModule(module, removeVelocity)) {
                        continue;
                    }

                    String moduleName = getHudRenderText(module);
                    int moduleWidth = hudFont.getStringWidth(moduleName);
                    float xPos = posX;
                    float textY = getHudTextY(y, textTopOffset, textTopPadding);
                    double backgroundLeft = xPos - horizontalTextPadding;
                    double backgroundRight = xPos + moduleWidth + horizontalTextPadding;
                    double backgroundTop = y;
                    double backgroundBottom = y + rowHeight;
                    double outlineLeft = backgroundLeft - outlineThickness;
                    double outlineRight = backgroundRight + outlineThickness;
                    double outlineTop = backgroundTop - outlineThickness;

                    if (alignRight.isToggled()) {
                        xPos -= moduleWidth;
                        backgroundLeft = xPos - horizontalTextPadding;
                        backgroundRight = xPos + moduleWidth + horizontalTextPadding;
                        outlineLeft = backgroundLeft - outlineThickness;
                        outlineRight = backgroundRight + outlineThickness;
                    }

                    double rowCenterX = (backgroundLeft + backgroundRight) * 0.5;
                    double wavePhase = hudWavePhase(verticalWaveAccum, rowCenterX);
                    int color = getHudColor(wavePhase);

                    if (outline.getInput() == 1 && firstVisibleRow) {
                        RenderUtils.drawRect(outlineLeft, outlineTop, outlineRight, backgroundTop, color);
                    }

                    if (hudWaveIsVertical()) {
                        verticalWaveAccum += getVerticalWaveStep();
                    }
                    firstVisibleRow = false;

                    if (drawBackground.isToggled()) {
                        RenderUtils.drawRect(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, BACKGROUND_COLOR);
                    }

                    if (outline.getInput() == 1 && !previousModule.isEmpty()) {
                        double difference = hudFont.getStringWidth(previousModule) - moduleWidth;
                        if (alphabeticalSort.isToggled() && difference < 0) {
                            RenderUtils.drawRect(outlineLeft, outlineTop, xPos - difference + horizontalTextPadding + outlineThickness, backgroundTop, color);
                        }
                        else if (alignRight.isToggled()) {
                            RenderUtils.drawRect(xPos - difference - horizontalTextPadding - outlineThickness, outlineTop, backgroundLeft, backgroundTop, color);
                        }
                        else {
                            RenderUtils.drawRect(backgroundRight, outlineTop, xPos + difference + moduleWidth + horizontalTextPadding + outlineThickness, backgroundTop, color);
                        }
                    }

                    if (outline.getInput() > 0) {
                        if (alignRight.isToggled()) {
                            RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                        }
                        else {
                            RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                        }
                    }

                    if (outline.getInput() == 1) {
                        if (alignRight.isToggled()) {
                            RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                        }
                        else {
                            RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                        }
                    }

                    drawHudText(hudFont, moduleName, xPos, textY, color);
                    previousModule = moduleName;
                    lastOutlineLeft = outlineLeft;
                    lastOutlineRight = outlineRight;
                    lastBackgroundBottom = backgroundBottom;
                    y += rowHeight;
                }
            }
            catch (Exception exception) {
                Utils.sendMessage("&cAn error occurred rendering HUD. check your logs");
                exception.printStackTrace();
            }

            if (outline.getInput() == 1 && !previousModule.isEmpty()) {
                double bottomCenterX = (lastOutlineLeft + lastOutlineRight) * 0.5;
                double bottomPhase = hudWavePhase(verticalWaveAccum, bottomCenterX);
                RenderUtils.drawRect(lastOutlineLeft, lastBackgroundBottom, lastOutlineRight, lastBackgroundBottom + outlineThickness, getHudColor(bottomPhase));
            }

            return new float[]{this.minX + longestModule, (float) Math.ceil(Math.max(y, lastBackgroundBottom)), this.minX - longestModule};
        }

        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
            super.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);

            if (button != 0) {
                return;
            }

            if (this.dragging) {
                this.actualX = this.lastActualX + (mouseX - this.lastMouseX);
                this.actualY = this.lastActualY + (mouseY - this.lastMouseY);
            }
            else if (mouseX > this.clickMinX && mouseX < this.maxX && mouseY > this.minY && mouseY < this.maxY) {
                this.dragging = true;
                this.lastMouseX = mouseX;
                this.lastMouseY = mouseY;
                this.lastActualX = this.actualX;
                this.lastActualY = this.actualY;
            }
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) {
                this.dragging = false;
            }
        }

        @Override
        public void actionPerformed(GuiButton button) {
            if (button == this.resetPosition) {
                HUD.resetPosition(new ScaledResolution(this.mc));
                this.actualX = HUD.posX;
                this.actualY = HUD.posY;
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }

        private boolean empty() {
            for (Module module : ModuleManager.organizedModules) {
                if (module.isEnabled() && !module.getName().equals("HUD")) {
                    if (module.isHidden()) {
                        continue;
                    }
                    if (module == ModuleManager.commandLine) {
                        continue;
                    }
                    return false;
                }
            }
            return true;
        }
    }

    public static RavenFontRenderer getHudFontRenderer() {
        return FontManager.getHudRenderer(getSelectedFontName(), getSelectedFontScale());
    }

    public static String getHudText(Module module) {
        String moduleName = module instanceof AntiKnockback ? "Velocity" : module.getNameInHud();
        if (lowercase != null && lowercase.isToggled()) {
            moduleName = moduleName.toLowerCase();
        }
        return moduleName;
    }

    public static String getHudRenderText(Module module) {
        String moduleName = getHudText(module);
        if (showInfo != null && showInfo.isToggled() && !module.getInfo().isEmpty()) {
            moduleName += " \u00a77" + module.getInfo();
        }
        if (lowercase != null && lowercase.isToggled()) {
            moduleName = moduleName.toLowerCase();
        }
        return moduleName;
    }

    public static String getSelectedFontName() {
        if (font == null) {
            return HUD_FONT_OPTIONS[0];
        }
        int index = (int) Math.max(0, Math.min(font.getOptions().length - 1, font.getInput()));
        return font.getOptions()[index];
    }

    public static float getSelectedFontScale() {
        if (fontSize == null) {
            return 1.0f;
        }
        return (float) fontSize.getInput();
    }

    public static float getRelativePosX() {
        syncPositionToResolution();
        return relativePosX;
    }

    public static float getRelativePosY() {
        syncPositionToResolution();
        return relativePosY;
    }

    public static void setRelativePosition(float normalizedX, float normalizedY) {
        relativePosX = normalizedX;
        relativePosY = normalizedY;
        syncPositionToResolution();
    }

    public static void setAbsolutePosition(float absoluteX, float absoluteY) {
        setAbsolutePosition(absoluteX, absoluteY, new ScaledResolution(mc));
    }

    public static void resetPosition() {
        resetPosition(new ScaledResolution(mc));
    }

    private static void syncPositionToResolution() {
        syncPositionToResolution(new ScaledResolution(mc));
    }

    private static void syncPositionToResolution(ScaledResolution resolution) {
        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());

        if (Float.isNaN(relativePosX) || Float.isNaN(relativePosY)) {
            relativePosX = posX / scaledWidth;
            relativePosY = posY / scaledHeight;
        }

        posX = relativePosX * scaledWidth;
        posY = relativePosY * scaledHeight;
    }

    private static void setAbsolutePosition(float absoluteX, float absoluteY, ScaledResolution resolution) {
        posX = absoluteX;
        posY = absoluteY;

        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());
        relativePosX = absoluteX / scaledWidth;
        relativePosY = absoluteY / scaledHeight;
    }

    private static void resetPosition(ScaledResolution resolution) {
        setAbsolutePosition(DEFAULT_POS_X, DEFAULT_POS_Y, resolution);
    }

    private static int getHudHorizontalTextPadding() {
        return getScaledHudPixels(2.0f);
    }

    private static int getHudTextTopPadding() {
        return getScaledHudPixels(2.0f);
    }

    private static int getHudTextBottomPadding() {
        return 0;
    }

    private static int getHudOutlineThickness() {
        return getScaledHudPixels(1.0f);
    }

    private static int getHudRowHeight(int textTopOffset, int textBottomOffset, int textTopPadding, int textBottomPadding) {
        int textBoxHeight = Math.max(1, textBottomOffset - textTopOffset);
        return Math.max(1, textBoxHeight + textTopPadding + textBottomPadding);
    }

    private static float getHudTextY(float rowTop, int textTopOffset, int textTopPadding) {
        return rowTop + textTopPadding - textTopOffset;
    }

    private static int getScaledHudPixels(float basePixels) {
        return Math.max(1, Math.round(basePixels * getSelectedFontScale()));
    }

    private static boolean shouldDrawTextShadow() {
        return textShadow == null || textShadow.isToggled();
    }

    private static boolean hudWaveIsVertical() {
        return waveAxis == null || (int) waveAxis.getInput() == 0;
    }

    private static double hudWavePhase(double verticalAccum, double rowCenterX) {
        if (hudWaveIsVertical()) {
            return verticalAccum;
        }
        return rowCenterX * (HUD_WAVE_HORIZONTAL_X_SCALE / getWaveLengthMultiplier()) * getHorizontalWaveDirectionSign();
    }

    private static void drawHudText(RavenFontRenderer hudFont, String moduleName, float xPos, float textY, int fallbackColor) {
        if (!shouldUseHorizontalWaveText()) {
            hudFont.drawString(moduleName, xPos, textY, fallbackColor, shouldDrawTextShadow());
            return;
        }

        hudFont.drawGlyphString(moduleName, xPos, textY, (character, xOffset, width, formattingColor) -> {
            if (formattingColor != null) {
                return formattingColor;
            }
            return getHudColor(hudWavePhase(0.0, xPos + xOffset + width * 0.5f));
        }, shouldDrawTextShadow());
    }

    private static boolean shouldUseHorizontalWaveText() {
        return colorMode != null && (int) colorMode.getInput() != 0 && !hudWaveIsVertical();
    }

    private static double getVerticalWaveStep() {
        return (12.0 / getWaveLengthMultiplier()) * getVerticalWaveDirectionSign();
    }

    private static int getVerticalWaveDirectionSign() {
        return verticalWaveDirection == null || (int) verticalWaveDirection.getInput() == 0 ? -1 : 1;
    }

    private static int getHorizontalWaveDirectionSign() {
        return horizontalWaveDirection == null || (int) horizontalWaveDirection.getInput() == 0 ? -1 : 1;
    }

    /**
     * Accent color for HUD rows/outlines. Other modules can match HUD when enabled.
     */
    public static int getHudColor(double gradientOffset) {
        if (colorMode == null || hudColor == null) {
            return 0xFFFFFF;
        }
        int mode = (int) colorMode.getInput();
        if (mode == 2) {
            return getRainbowWaveColor(gradientOffset);
        }
        if (mode == 1 && hudColor2 != null) {
            java.awt.Color c1 = new java.awt.Color(hudColor.getRed(), hudColor.getGreen(), hudColor.getBlue());
            java.awt.Color c2 = new java.awt.Color(hudColor2.getRed(), hudColor2.getGreen(), hudColor2.getBlue());
            return getGradientWaveColor(c1, c2, gradientOffset);
        }
        return hudColor.getRGB();
    }

    private static int getGradientWaveColor(java.awt.Color c1, java.awt.Color c2, double gradientOffset) {
        double animationProgress = (Math.sin(getAnimatedWaveAngle(gradientOffset)) + 1.0) * 0.5;
        return Theme.convert(c1, c2, animationProgress).getRGB();
    }

    private static int getRainbowWaveColor(double gradientOffset) {
        double hue = getAnimatedWaveAngle(gradientOffset) / (Math.PI * 2.0);
        hue -= Math.floor(hue);
        return Color.getHSBColor((float) hue, 1.0F, 1.0F).getRGB();
    }

    private static double getAnimatedWaveAngle(double gradientOffset) {
        return System.currentTimeMillis() / (double) HUD_RAINBOW_PERIOD_MS * (Math.PI * 2.0) * getWaveSpeedMultiplier()
                + gradientOffset * HUD_WAVE_ANGLE_SCALE;
    }

    private static double getWaveSpeedMultiplier() {
        return waveSpeed == null ? 1.0 : Math.max(0.1, waveSpeed.getInput());
    }

    private static double getWaveLengthMultiplier() {
        return waveLength == null ? 1.0 : Math.max(0.5, waveLength.getInput());
    }

}
