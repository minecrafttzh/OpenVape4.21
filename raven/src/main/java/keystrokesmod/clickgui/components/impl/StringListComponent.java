package keystrokesmod.clickgui.components.impl;

import keystrokesmod.clickgui.animation.ScrollOffsetAnimation;
import keystrokesmod.utility.Theme;
import keystrokesmod.module.setting.impl.StringListSetting;
import keystrokesmod.utility.RenderUtils;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class StringListComponent extends AbstractTextInputComponent {
    private static final String CLOSE_ICON_PATH = "/assets/keystrokesmod/textures/gui/close.png";
    private static final float SELECTED_LIST_GAP = 4f;
    private static final int MAX_VISIBLE_SELECTED = 7;
    private static final int CLOSE_SIZE = 6;
    private static final float CLOSE_PAD = 3f;
    private static final float LIST_ROW_TEXT_SCALE = 0.56f;
    private static final float LIST_ROW_TEXT_Y_OFFSET = LIST_ROW_TEXT_SCALE;
    private static final float LIST_ROW_VISUAL_HEIGHT = ROW_HEIGHT - 1f;
    private final StringListSetting setting;
    private final ScrollOffsetAnimation selectedScrollAnim = new ScrollOffsetAnimation(200);

    private float lastMouseX;
    private float lastMouseY;

    public StringListComponent(StringListSetting setting, ModuleComponent moduleComponent, float o) {
        super(moduleComponent, o, setting.getPlaceholder(), setting.getMaxLength());
        this.setting = setting;
    }

    @Override
    public void render() {
        Layout layout = layout(false);
        renderLabel(layout, setting.getName());
        renderTextField(layout);
        renderSelectedEntries(layout);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        super.drawScreen(mouseX, mouseY);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        clampSelectedScroll();
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (!moduleComponent.isOpened || !moduleComponent.isVisible(this)) {
            return false;
        }

        Layout layout = layout(true);
        if (button == 0 && isTextFieldClicked(mouseX, mouseY, layout)) {
            setTextFieldFocused(true);
            return true;
        }

        if (button == 0 && handleSelectedEntryClick(mouseX, mouseY, layout)) {
            return true;
        }

        if (isTextFieldFocused()) {
            getTextField().setText("");
            setTextFieldFocused(false);
        }
        return false;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!moduleComponent.isOpened || !isTextFieldFocused()) {
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            getTextField().setText("");
            setTextFieldFocused(false);
            return;
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            submitText();
            setTextFieldFocused(false);
            return;
        }

        getTextField().textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public void onScroll(int scroll) {
        if (!moduleComponent.isOpened || !moduleComponent.isVisible(this)) {
            return;
        }

        if (!capturesCategoryScroll(lastMouseX, lastMouseY)) {
            return;
        }

        float scrollSpeed = (float) keystrokesmod.module.impl.client.Gui.scrollSpeed.getInput();
        float delta = scrollSpeed * (scroll / 120f);
        if (delta != 0f) {
            selectedScrollAnim.extend(-delta);
        }
        clampSelectedScroll();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        getTextField().setText("");
        selectedScrollAnim.reset(0f);
    }

    @Override
    public float getHeightF() {
        int count = setting.getEntries().size();
        float selectedHeight = count == 0 ? 0f : SELECTED_LIST_GAP + Math.min(MAX_VISIBLE_SELECTED, count) * ROW_HEIGHT;
        return (2f * ROW_HEIGHT) + selectedHeight;
    }

    @Override
    public boolean isBaseVisible() {
        return setting.visible;
    }

    @Override
    public String getGroupName() {
        return setting.group != null ? setting.group.getName() : "";
    }

    public boolean capturesCategoryScroll(float mouseX, float mouseY) {
        return setting.getEntries().size() > MAX_VISIBLE_SELECTED && isMouseOverSelectedList(mouseX, mouseY);
    }

    @Override
    public boolean containsClick(int mouseX, int mouseY) {
        Layout layout = layout(true);
        return isTextFieldClicked(mouseX, mouseY, layout) || isMouseOverSelectedList(mouseX, mouseY);
    }

    private void submitText() {
        String typed = getTextField().getText();
        if (typed == null || typed.trim().isEmpty()) {
            return;
        }

        if (setting.addEntry(typed)) {
            getTextField().setText("");
        }
        moduleComponent.updateSettingPositions();
        clampSelectedScroll();
    }

    private void renderSelectedEntries(Layout layout) {
        List<String> entries = setting.getEntries();
        if (entries.isEmpty()) {
            return;
        }

        float selectedTop = getSelectedTop(layout);
        float selectedHeight = getSelectedVisibleHeight(entries.size());
        float scrollOffset = moduleComponent.categoryComponent.getModuleY() - layout.cy;
        RenderUtils.scissorPushGui(layout.left, selectedTop + scrollOffset, layout.right - layout.left, selectedHeight);

        float offsetPx = selectedScrollAnim.getValue();
        int firstRow = (int) (offsetPx / ROW_HEIGHT);
        int end = Math.min(firstRow + MAX_VISIBLE_SELECTED + 1, entries.size());
        for (int i = firstRow; i < end; i++) {
            String entry = entries.get(i);
            float rowTop = selectedTop - offsetPx + i * ROW_HEIGHT;
            int bg = (i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E;
            renderEntryRow(entry, layout.left, layout.right, rowTop, bg);
        }

        RenderUtils.scissorPop();
    }

    private void renderEntryRow(String entry, float left, float right, float rowTop, int bgColor) {
        RenderUtils.drawRect(left, rowTop, right, rowTop + ROW_HEIGHT - 1f, bgColor);
        drawScaledTextNoShadow(entry, left + 4f, centeredScaledTextY(rowTop, LIST_ROW_VISUAL_HEIGHT, LIST_ROW_TEXT_SCALE) + LIST_ROW_TEXT_Y_OFFSET, 0xFFCCCCCC);
        renderCloseIcon(right, rowTop);
    }

    private boolean handleSelectedEntryClick(int mouseX, int mouseY, Layout layout) {
        List<String> entries = setting.getEntries();
        float offsetPx = selectedScrollAnim.getValue();
        for (int i = 0; i < entries.size(); i++) {
            float rowTop = getSelectedTop(layout) - offsetPx + i * ROW_HEIGHT;
            if (isOverClose(mouseX, mouseY, rowTop, layout.right)) {
                setting.removeEntry(entries.get(i));
                moduleComponent.updateSettingPositions();
                clampSelectedScroll();
                return true;
            }
        }
        return false;
    }

    private void clampSelectedScroll() {
        int count = setting.getEntries().size();
        float maxScrollPx = Math.max(0f, (count - MAX_VISIBLE_SELECTED) * ROW_HEIGHT);
        selectedScrollAnim.clampTarget(0f, maxScrollPx);
        if (selectedScrollAnim.getValue() > maxScrollPx) {
            selectedScrollAnim.reset(maxScrollPx);
        }
    }

    private boolean isMouseOverSelectedList(float mouseX, float mouseY) {
        List<String> entries = setting.getEntries();
        if (entries.isEmpty()) {
            return false;
        }

        Layout layout = layout(true);
        float selectedTop = getSelectedTop(layout);
        float selectedHeight = getSelectedVisibleHeight(entries.size());
        return mouseX >= layout.left && mouseX <= layout.right && mouseY >= selectedTop && mouseY < selectedTop + selectedHeight;
    }

    private float getSelectedTop(Layout layout) {
        return layout.contentTop + SELECTED_LIST_GAP;
    }

    private float getSelectedVisibleHeight(int count) {
        return Math.min(MAX_VISIBLE_SELECTED, count) * ROW_HEIGHT;
    }

    private void renderCloseIcon(float right, float rowTop) {
        ResourceLocation close = RenderUtils.getIcon(CLOSE_ICON_PATH);
        if (close == null) {
            return;
        }
        float closeX = right - CLOSE_SIZE - CLOSE_PAD;
        float closeY = rowTop + (LIST_ROW_VISUAL_HEIGHT - CLOSE_SIZE) / 2f;
        int closeColor = Theme.getGradient(Theme.hiddenBind[0], Theme.hiddenBind[1], 0);
        RenderUtils.drawIcon(close, closeX, closeY, CLOSE_SIZE, closeColor);
    }

    private boolean isOverClose(float mouseX, float mouseY, float rowTop, float right) {
        float closeX = right - CLOSE_SIZE - CLOSE_PAD;
        float closeY = rowTop + (LIST_ROW_VISUAL_HEIGHT - CLOSE_SIZE) / 2f;
        return mouseX >= closeX && mouseX <= closeX + CLOSE_SIZE && mouseY >= closeY && mouseY <= closeY + CLOSE_SIZE;
    }

    private static void drawScaledTextNoShadow(String text, float x, float y, int color) {
        drawScaledText(text, x, y, color, LIST_ROW_TEXT_SCALE);
    }
}
