package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.PotionListSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FontManager;
import keystrokesmod.utility.font.RavenFontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PotionHUD extends Module {
    private static final String[] TIME_FORMAT_OPTIONS = new String[]{ "1m20s", "01:20" };
    private static final String[] SORT_OPTIONS = new String[]{ "Duration", "Length" };
    private static final String[] SORT_DIRECTION_OPTIONS = new String[]{ "Descending", "Ascending" };
    private static final String[] ALIGNMENT_OPTIONS = new String[]{ "Left", "Center", "Right" };
    private static final String[] VERTICAL_ALIGNMENT_OPTIONS = new String[]{ "Top", "Center", "Bottom" };
    private static final String[] FONT_OPTIONS = FontManager.getHudFontOptions();

    private static final float DEFAULT_RELATIVE_X = 0.5f;
    private static final float DEFAULT_RELATIVE_Y = 0.5f;
    private static final int TIMER_COLOR = 0xFFAAAAAA;
    private static final int EDIT_OUTLINE_COLOR = 0xFFFFFFFF;
    private static final int BACKGROUND_COLOR = new Color(0, 0, 0, 110).getRGB();
    private static final String PLACEHOLDER_TEXT = "No Potions Active";

    private final SliderSetting timeFormat;
    private final SliderSetting sortMode;
    private final SliderSetting sortDirection;
    private final SliderSetting horizontalAlignment;
    private final SliderSetting verticalAlignment;
    private final SliderSetting font;
    private final SliderSetting scale;
    private final ButtonSetting excludePermanent;
    private final ButtonSetting drawBackground;
    private final ButtonSetting textShadow;
    private final PotionListSetting potionBlacklist;

    private float posX = Float.NaN;
    private float posY = Float.NaN;
    private float relativePosX = Float.NaN;
    private float relativePosY = Float.NaN;
    public PotionHUD() {
        super("Potion HUD", category.render);
        this.registerSetting(timeFormat = new SliderSetting("Time Format", 0, TIME_FORMAT_OPTIONS));
        this.registerSetting(sortMode = new SliderSetting("Sort By", 0, SORT_OPTIONS));
        this.registerSetting(sortDirection = new SliderSetting("Sort Direction", 0, SORT_DIRECTION_OPTIONS));
        this.registerSetting(horizontalAlignment = new SliderSetting("Horizontal Alignment", 0, ALIGNMENT_OPTIONS, "Alignment"));
        this.registerSetting(verticalAlignment = new SliderSetting("Vertical Alignment", 0, VERTICAL_ALIGNMENT_OPTIONS, "Direction"));
        this.registerSetting(font = new SliderSetting("Font", 0, FONT_OPTIONS));
        this.registerSetting(scale = new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.1));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.displayGuiScreen(new EditScreen())));
        this.registerSetting(excludePermanent = new ButtonSetting("Exclude permanent", false));
        this.registerSetting(drawBackground = new ButtonSetting("Draw background", false));
        this.registerSetting(textShadow = new ButtonSetting("Text shadow", true));
        this.registerSetting(potionBlacklist = new PotionListSetting("Blacklisted potions", "Potions", "Potion blacklist"));
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }

        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }

        render(false);
    }

    public float getPosX() {
        syncPositionToResolution();
        return posX;
    }

    public float getPosY() {
        syncPositionToResolution();
        return posY;
    }

    public float getRelativePosX() {
        syncPositionToResolution();
        return relativePosX;
    }

    public float getRelativePosY() {
        syncPositionToResolution();
        return relativePosY;
    }

    public void setRelativePosition(float normalizedX, float normalizedY) {
        relativePosX = normalizedX;
        relativePosY = normalizedY;
        syncPositionToResolution();
    }

    public void setAbsolutePosition(float absoluteX, float absoluteY) {
        setAbsolutePosition(absoluteX, absoluteY, new ScaledResolution(mc));
    }

    public void resetPosition() {
        setRelativePosition(DEFAULT_RELATIVE_X, DEFAULT_RELATIVE_Y);
    }

    private void render(boolean editing) {
        ScaledResolution resolution = new ScaledResolution(mc);
        syncPositionToResolution(resolution);

        RenderState state = buildRenderState(editing);
        if (state.entries.isEmpty()) {
            return;
        }

        adjustAnchorForLayoutChanges(resolution, state);
        Bounds bounds = renderState(state);
        if (editing) {
            drawBounds(bounds);
        }
    }

    private RenderState buildRenderState(boolean includePlaceholder) {
        RavenFontRenderer renderer = getFontRenderer();
        LayoutMetrics metrics = LayoutMetrics.from(renderer, getSelectedScale());
        ArrayList<PotionEntry> entries = new ArrayList<PotionEntry>();
        Collection<PotionEffect> activeEffects = mc.thePlayer.getActivePotionEffects();

        if (activeEffects != null) {
            for (PotionEffect effect : activeEffects) {
                PotionEntry entry = buildEntry(effect, renderer);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        sortEntries(entries);

        if (entries.isEmpty() && includePlaceholder) {
            entries.add(PotionEntry.placeholder(renderer));
        }

        int maxWidth = 0;
        for (PotionEntry entry : entries) {
            maxWidth = Math.max(maxWidth, entry.totalWidth);
        }

        return new RenderState(renderer, metrics, entries, maxWidth);
    }

    private PotionEntry buildEntry(PotionEffect effect, RavenFontRenderer renderer) {
        if (effect == null) {
            return null;
        }

        int duration = effect.getDuration();
        if (excludePermanent.isToggled() && duration > 32000) {
            return null;
        }

        Potion potion = Potion.potionTypes[effect.getPotionID()];
        if (potion == null) {
            return null;
        }

        if (potionBlacklist.containsPotion(potion.getName())) {
            return null;
        }

        String label = getPotionLabel(effect, potion);
        String durationText = formatDuration(duration);
        int labelWidth = renderer.getStringWidth(label);
        int durationWidth = durationText.isEmpty() ? 0 : renderer.getStringWidth(durationText);
        int gapWidth = durationWidth > 0 ? renderer.getStringWidth(" ") : 0;
        return new PotionEntry(label, durationText, labelWidth, durationWidth, gapWidth, duration, potion.getLiquidColor());
    }

    private void sortEntries(List<PotionEntry> entries) {
        final int directionMultiplier = (int) sortDirection.getInput() == 1 ? 1 : -1;

        if ((int) sortMode.getInput() == 0) {
            entries.sort(new Comparator<PotionEntry>() {
                @Override
                public int compare(PotionEntry first, PotionEntry second) {
                    return Integer.compare(first.durationTicks, second.durationTicks) * directionMultiplier;
                }
            });
            return;
        }

        entries.sort(new Comparator<PotionEntry>() {
            @Override
            public int compare(PotionEntry first, PotionEntry second) {
                return Integer.compare(first.totalWidth, second.totalWidth) * directionMultiplier;
            }
        });
    }

    private Bounds renderState(RenderState state) {
        int horizontalAlignMode = (int) horizontalAlignment.getInput();
        int verticalAlignMode = (int) verticalAlignment.getInput();
        float stackHeight = state.entries.size() * state.metrics.rowHeight;
        float firstRowTop = verticalAlignMode == 0
                ? posY
                : verticalAlignMode == 1 ? posY - stackHeight / 2.0f : posY - stackHeight;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int i = 0; i < state.entries.size(); i++) {
            PotionEntry entry = state.entries.get(i);
            float rowTop = firstRowTop + i * state.metrics.rowHeight;
            float rowLeft = horizontalAlignMode == 0
                    ? posX
                    : horizontalAlignMode == 2 ? posX - entry.totalWidth : posX - entry.totalWidth / 2.0f;
            float backgroundLeft = rowLeft - state.metrics.horizontalTextPadding;
            float backgroundRight = rowLeft + entry.totalWidth + state.metrics.horizontalTextPadding;
            float backgroundBottom = rowTop + state.metrics.rowHeight;
            float textY = rowTop + state.metrics.textTopPadding - state.metrics.textTopOffset;
            float timerX = rowLeft + entry.labelWidth + entry.gapWidth;

            if (drawBackground.isToggled()) {
                RenderUtils.drawRect(backgroundLeft, rowTop, backgroundRight, backgroundBottom, BACKGROUND_COLOR);
            }

            state.renderer.drawString(entry.label, rowLeft, textY, entry.color, textShadow.isToggled());
            if (!entry.durationText.isEmpty()) {
                state.renderer.drawString(entry.durationText, timerX, textY, TIMER_COLOR, textShadow.isToggled());
            }

            minX = Math.min(minX, backgroundLeft);
            minY = Math.min(minY, rowTop);
            maxX = Math.max(maxX, backgroundRight);
            maxY = Math.max(maxY, backgroundBottom);
        }

        return new Bounds(minX, minY, maxX, maxY);
    }

    private void drawBounds(Bounds bounds) {
        float left = bounds.left - 1.0f;
        float top = bounds.top - 1.0f;
        float right = bounds.right + 1.0f;
        float bottom = bounds.bottom + 1.0f;
        RenderUtils.drawRect(left, top, right, top + 1.0f, EDIT_OUTLINE_COLOR);
        RenderUtils.drawRect(left, bottom - 1.0f, right, bottom, EDIT_OUTLINE_COLOR);
        RenderUtils.drawRect(left, top, left + 1.0f, bottom, EDIT_OUTLINE_COLOR);
        RenderUtils.drawRect(right - 1.0f, top, right, bottom, EDIT_OUTLINE_COLOR);
    }

    private void adjustAnchorForLayoutChanges(ScaledResolution resolution, RenderState state) {
        syncPositionToResolution(resolution);
    }

    private void syncPositionToResolution() {
        syncPositionToResolution(new ScaledResolution(mc));
    }

    private void syncPositionToResolution(ScaledResolution resolution) {
        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());

        if (Float.isNaN(relativePosX) || Float.isNaN(relativePosY)) {
            if (Float.isNaN(posX) || Float.isNaN(posY)) {
                relativePosX = DEFAULT_RELATIVE_X;
                relativePosY = DEFAULT_RELATIVE_Y;
            }
            else {
                relativePosX = posX / scaledWidth;
                relativePosY = posY / scaledHeight;
            }
        }

        posX = relativePosX * scaledWidth;
        posY = relativePosY * scaledHeight;
    }

    private void setAbsolutePosition(float absoluteX, float absoluteY, ScaledResolution resolution) {
        posX = absoluteX;
        posY = absoluteY;
        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());
        relativePosX = absoluteX / scaledWidth;
        relativePosY = absoluteY / scaledHeight;
    }

    private RavenFontRenderer getFontRenderer() {
        return FontManager.getHudRenderer(getSelectedFontName(), getSelectedScale());
    }

    private String getSelectedFontName() {
        int index = (int) Math.max(0, Math.min(font.getOptions().length - 1, font.getInput()));
        return font.getOptions()[index];
    }

    private float getSelectedScale() {
        return (float) scale.getInput();
    }

    private String getPotionLabel(PotionEffect effect, Potion potion) {
        String label = StatCollector.translateToLocal(effect.getEffectName());
        if (effect.getAmplifier() >= 1) {
            label += " " + toRomanNumeral(effect.getAmplifier() + 1);
        }
        return label.isEmpty() ? StatCollector.translateToLocal(potion.getName()) : label;
    }

    private String formatDuration(int durationTicks) {
        int totalSeconds = Math.max(0, durationTicks / 20);
        if ((int) timeFormat.getInput() == 1) {
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
        }

        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes > 0 && seconds == 0) {
            return minutes + "m";
        }
        return (minutes > 0 ? minutes + "m" : "") + seconds + "s";
    }

    private static final class PotionEntry {
        private final String label;
        private final String durationText;
        private final int labelWidth;
        private final int durationWidth;
        private final int gapWidth;
        private final int totalWidth;
        private final int durationTicks;
        private final int color;

        private PotionEntry(String label, String durationText, int labelWidth, int durationWidth, int gapWidth, int durationTicks, int color) {
            this.label = label;
            this.durationText = durationText;
            this.labelWidth = labelWidth;
            this.durationWidth = durationWidth;
            this.gapWidth = gapWidth;
            this.totalWidth = labelWidth + gapWidth + durationWidth;
            this.durationTicks = durationTicks;
            this.color = color;
        }

        private static PotionEntry placeholder(RavenFontRenderer renderer) {
            String text = PLACEHOLDER_TEXT;
            return new PotionEntry(text, "", renderer.getStringWidth(text), 0, 0, 0, 0xFFFFFFFF);
        }
    }

    private static final class LayoutMetrics {
        private final int textTopOffset;
        private final int textTopPadding;
        private final int horizontalTextPadding;
        private final int rowHeight;

        private LayoutMetrics(int textTopOffset, int textTopPadding, int horizontalTextPadding, int rowHeight) {
            this.textTopOffset = textTopOffset;
            this.textTopPadding = textTopPadding;
            this.horizontalTextPadding = horizontalTextPadding;
            this.rowHeight = rowHeight;
        }

        private static LayoutMetrics from(RavenFontRenderer renderer, float fontScale) {
            int textTopOffset = renderer.getTextTopOffset();
            int textBottomOffset = renderer.getTextBottomOffset();
            int textTopPadding = getScaledHudPixels(2.0f, fontScale);
            int textBottomPadding = 0;
            int horizontalTextPadding = getScaledHudPixels(2.0f, fontScale);
            int textBoxHeight = Math.max(1, textBottomOffset - textTopOffset);
            int rowHeight = Math.max(1, textBoxHeight + textTopPadding + textBottomPadding);
            return new LayoutMetrics(textTopOffset, textTopPadding, horizontalTextPadding, rowHeight);
        }
    }

    private static final class RenderState {
        private final RavenFontRenderer renderer;
        private final LayoutMetrics metrics;
        private final List<PotionEntry> entries;
        private final int maxWidth;

        private RenderState(RavenFontRenderer renderer, LayoutMetrics metrics, List<PotionEntry> entries, int maxWidth) {
            this.renderer = renderer;
            this.metrics = metrics;
            this.entries = entries;
            this.maxWidth = maxWidth;
        }
    }

    private static final class Bounds {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private static int getScaledHudPixels(float basePixels, float fontScale) {
        return Math.max(1, Math.round(basePixels * fontScale));
    }

    private static String toRomanNumeral(int value) {
        if (value <= 0) {
            return Integer.toString(value);
        }

        int[] values = new int[]{ 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        String[] numerals = new String[]{ "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder builder = new StringBuilder();
        int remaining = value;

        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                builder.append(numerals[i]);
                remaining -= values[i];
            }
        }

        return builder.toString();
    }

    private class EditScreen extends GuiScreen {
        private GuiButtonExt resetPosition;
        private boolean dragging;
        private float minX;
        private float minY;
        private float maxX;
        private float maxY;
        private float actualX;
        private float actualY;
        private float lastActualX;
        private float lastActualY;
        private int lastMouseX;
        private int lastMouseY;

        @Override
        public void initGui() {
            super.initGui();
            this.buttonList.add(this.resetPosition = new GuiButtonExt(1, this.width - 90, this.height - 25, 85, 20, "Reset position"));
            syncPositionToResolution(new ScaledResolution(this.mc));
            this.actualX = posX;
            this.actualY = posY;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            ScaledResolution resolution = new ScaledResolution(this.mc);
            if (!this.dragging) {
                syncPositionToResolution(resolution);
                this.actualX = posX;
                this.actualY = posY;
            }

            drawRect(0, 0, this.width, this.height, -1308622848);
            setAbsolutePosition(this.actualX, this.actualY, resolution);

            RenderState state = buildRenderState(true);
            adjustAnchorForLayoutChanges(resolution, state);
            Bounds bounds = renderState(state);
            drawBounds(bounds);

            this.minX = bounds.left;
            this.minY = bounds.top;
            this.maxX = bounds.right;
            this.maxY = bounds.bottom;
            this.actualX = posX;
            this.actualY = posY;

            String message = "Edit the HUD position by dragging.";
            int textX = resolution.getScaledWidth() / 2 - this.fontRendererObj.getStringWidth(message) / 2;
            int textY = resolution.getScaledHeight() / 2 - 20;
            RenderUtils.drawColoredString(message, '-', textX, textY, 2L, 0L, true, this.mc.fontRendererObj);

            try {
                this.handleInput();
            }
            catch (IOException ignored) {
            }

            super.drawScreen(mouseX, mouseY, partialTicks);
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
            else if (mouseX >= this.minX && mouseX <= this.maxX && mouseY >= this.minY && mouseY <= this.maxY) {
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
                resetPosition();
                this.actualX = posX;
                this.actualY = posY;
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
