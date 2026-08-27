package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.animation.ScrollOffsetAnimation;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Timer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.List;

public abstract class AbstractSearchListComponent extends AbstractTextInputComponent {
    private static final String CLOSE_ICON_PATH = "/assets/keystrokesmod/textures/gui/close.png";
    private static final String ARROW_ICON_PATH = "/assets/keystrokesmod/textures/gui/arrow_left.png";

    protected static final float ANIMATION_DURATION = 250f;
    protected static final int MAX_VISIBLE_RESULTS = 7;
    protected static final int MAX_VISIBLE_SELECTED = 7;
    protected static final int CLOSE_SIZE = 6;
    protected static final float CLOSE_PAD = 3f;
    protected static final float SELECTED_LIST_GAP = 4f;
    protected static final float LIST_ROW_TEXT_SCALE = 0.56f;
    protected static final float LIST_ROW_TEXT_Y_OFFSET = LIST_ROW_TEXT_SCALE;
    protected static final float LIST_ROW_VISUAL_HEIGHT = ROW_HEIGHT - 1f;

    protected final ScrollOffsetAnimation dropdownScrollAnim = new ScrollOffsetAnimation(200);
    protected final ScrollOffsetAnimation selectedScrollAnim = new ScrollOffsetAnimation(200);

    protected float lastMouseX;
    protected float lastMouseY;

    private Timer dropdownAnimTimer;
    private float dropdownAnimStartH;
    private float dropdownAnimTargetH;
    private float dropdownAnimH;

    protected static class SelectedRowData {
        final String storageId;
        final String displayName;
        final ItemStack stack;
        final List<ItemStack> cyclingStacks;

        protected SelectedRowData(String storageId, String displayName, ItemStack stack, List<ItemStack> cyclingStacks) {
            this.storageId = storageId;
            this.displayName = displayName;
            this.stack = stack;
            this.cyclingStacks = cyclingStacks;
        }
    }

    protected AbstractSearchListComponent(ModuleComponent moduleComponent, float o, String placeholder) {
        super(moduleComponent, o, placeholder, DEFAULT_TEXT_MAX_LENGTH);
    }

    @Override
    public void render() {
        Layout layout = layout(false);
        renderLabel(layout, getLabelText());
        renderTextField(layout);
        renderDropdown(layout);
        renderSelectedEntries(layout);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        super.drawScreen(mouseX, mouseY);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        updateDropdownAnimation();
        onAfterBaseDrawScreen();
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (!moduleComponent.isOpened || !moduleComponent.isVisible(this)) {
            return false;
        }

        Layout layout = layout(true);
        if (button == 0 && handleDropdownClick(mouseX, mouseY, layout)) {
            onDropdownClickHandled(mouseX, mouseY);
            return true;
        }
        if (button == 0 && handleSelectedEntryClick(mouseX, mouseY, layout)) {
            onSelectedEntryClickHandled(mouseX, mouseY);
            return true;
        }
        if (handleTextFieldFocusClick(mouseX, mouseY, layout)) {
            onSearchFocusHandled(mouseX, mouseY);
            return true;
        }

        if (isSearchFocused()) {
            unfocusSearch();
        }
        onOutsideClick(mouseX, mouseY, button);
        return false;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!moduleComponent.isOpened) {
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE && isSearchFocused()) {
            if (!handleSearchEscape()) {
                unfocusSearch();
            }
            return;
        }

        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && isSearchFocused()) {
            unfocusSearch();
            return;
        }

        if (getTextField().textboxKeyTyped(typedChar, keyCode)) {
            onSearchTextChanged(getTextField().getText());
            dropdownScrollAnim.reset(0);
            updateDropdownAnimation();
            moduleComponent.updateSettingPositions();
        }
    }

    @Override
    public void onScroll(int scroll) {
        if (!moduleComponent.isOpened || !moduleComponent.isVisible(this)) {
            return;
        }

        float scrollSpeed = (float) Gui.scrollSpeed.getInput();
        float delta = scrollSpeed * (scroll / 120f);
        if (isMouseOverDropdown()) {
            if (delta != 0f) {
                dropdownScrollAnim.extend(-delta);
            }
            clampDropdownScroll();
            return;
        }
        if (isMouseOverSelectedList() && getSelectedEntryCount() > MAX_VISIBLE_SELECTED) {
            if (delta != 0f) {
                selectedScrollAnim.extend(-delta);
            }
            clampSelectedScroll();
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        getTextField().setText("");
        resetSearchState();
        dropdownScrollAnim.reset(0);
        selectedScrollAnim.reset(0);
        dropdownAnimTimer = null;
        dropdownAnimH = 0f;
        dropdownAnimStartH = 0f;
        dropdownAnimTargetH = 0f;
    }

    @Override
    public float getHeightF() {
        return getCurrentHeight();
    }

    public boolean isSearchFocused() {
        return isTextFieldFocused();
    }

    public void unfocusSearch() {
        setTextFieldFocused(false);
        updateDropdownAnimation();
    }

    @Override
    public boolean isTextInputFocused() {
        return isSearchFocused() || hasAdditionalTextInputFocus();
    }

    @Override
    public void unfocusTextInput() {
        clearAdditionalTextInputFocus();
        unfocusSearch();
    }

    @Override
    public boolean containsClick(int mouseX, int mouseY) {
        Layout layout = layout(true);
        return isTextFieldClicked(mouseX, mouseY, layout)
            || isMouseOverDropdown(mouseX, mouseY)
            || isMouseOverSelectedList(mouseX, mouseY);
    }

    public boolean capturesCategoryScroll(float mouseX, float mouseY) {
        if (!moduleComponent.isOpened || !moduleComponent.isVisible(this)) {
            return false;
        }

        if (isMouseOverDropdown(mouseX, mouseY) && getDropdownRowCount() > MAX_VISIBLE_RESULTS) {
            return true;
        }

        return isMouseOverSelectedList(mouseX, mouseY) && getSelectedEntryCount() > MAX_VISIBLE_SELECTED;
    }

    public float getCurrentHeight() {
        int selected = getSelectedEntryCount();
        float selectedHeight = selected == 0 ? 0f : SELECTED_LIST_GAP + Math.min(MAX_VISIBLE_SELECTED, selected) * ROW_HEIGHT;
        return (2f * ROW_HEIGHT) + getAnimatedDropdownHeight() + selectedHeight;
    }

    protected final float getAnimatedDropdownHeight() {
        if (dropdownAnimTimer != null) {
            if (System.currentTimeMillis() - dropdownAnimTimer.last >= ANIMATION_DURATION + 30f) {
                dropdownAnimTimer = null;
                dropdownAnimH = dropdownAnimTargetH;
                dropdownAnimStartH = dropdownAnimTargetH;
            }
            else {
                dropdownAnimH = dropdownAnimTimer.getValueFloat(dropdownAnimStartH, dropdownAnimTargetH, 1);
                if (dropdownAnimH == dropdownAnimTargetH) {
                    dropdownAnimTimer = null;
                    dropdownAnimStartH = dropdownAnimTargetH;
                }
            }
        }
        return dropdownAnimH;
    }

    protected final float getSelectedTop(Layout layout) {
        return layout.contentTop + getAnimatedDropdownHeight() + SELECTED_LIST_GAP;
    }

    protected final float getSelectedVisibleHeight() {
        return Math.min(MAX_VISIBLE_SELECTED, getSelectedEntryCount()) * ROW_HEIGHT;
    }

    protected final void clampDropdownScroll() {
        float maxScrollPx = Math.max(0f, (getDropdownRowCount() - MAX_VISIBLE_RESULTS) * ROW_HEIGHT);
        dropdownScrollAnim.clampTarget(0f, maxScrollPx);
    }

    protected final void clampSelectedScroll() {
        float maxScrollPx = Math.max(0f, (getSelectedEntryCount() - MAX_VISIBLE_SELECTED) * ROW_HEIGHT);
        selectedScrollAnim.clampTarget(0f, maxScrollPx);
    }

    protected final void updateDropdownAnimation() {
        float newTarget = computeDropdownTarget();
        if (newTarget != dropdownAnimTargetH) {
            dropdownAnimStartH = dropdownAnimH;
            dropdownAnimTargetH = newTarget;
            dropdownAnimTimer = new Timer(ANIMATION_DURATION);
            dropdownAnimTimer.start();
        }
    }

    protected final void notifySelectionListChanged() {
        markUnsaved();
        updateDropdownAnimation();
        moduleComponent.updateSettingPositions();
    }

    protected final void markUnsaved() {
        if (Raven.currentProfile != null) {
            Raven.currentProfile.getModule().saved = false;
        }
    }

    protected final ItemStack getPreviewStack(SelectedRowData row) {
        if (row == null) {
            return null;
        }
        if (row.cyclingStacks != null && !row.cyclingStacks.isEmpty()) {
            int index = (int) ((System.currentTimeMillis() / 1000L) % row.cyclingStacks.size());
            return row.cyclingStacks.get(index);
        }
        return row.stack;
    }

    protected final void renderBackRow(float left, float right, float rowTop, int bgColor, String groupName) {
        RenderUtils.drawRect(left, rowTop, right, rowTop + ROW_HEIGHT - 1f, bgColor);
        ResourceLocation arrow = RenderUtils.getIcon(ARROW_ICON_PATH);
        if (arrow != null) {
            float iconX = left + 2f;
            float iconY = rowTop + (LIST_ROW_VISUAL_HEIGHT - CLOSE_SIZE) / 2f;
            RenderUtils.drawIcon(arrow, iconX, iconY, CLOSE_SIZE, 0xFFFFFFFF);
        }
        drawListRowText(groupName != null ? groupName : "Back", left + 13f, rowTop, 0xFFCCCCCC);
    }

    protected final void renderStandardRow(String label, ItemStack stack, float left, float right, float rowTop, int bgColor, boolean showClose) {
        RenderUtils.drawRect(left, rowTop, right, rowTop + ROW_HEIGHT - 1f, bgColor);
        renderItemInRow(stack, left + 2f, rowTop);
        drawListRowText(label != null ? label : "", left + 13f, rowTop, 0xFFCCCCCC);
        if (showClose) {
            renderCloseIcon(right, rowTop);
        }
    }

    protected final void drawListRowText(String text, float x, float rowTop, int color) {
        drawScaledText(text, x, centeredScaledTextY(rowTop, LIST_ROW_VISUAL_HEIGHT, LIST_ROW_TEXT_SCALE) + LIST_ROW_TEXT_Y_OFFSET, color, LIST_ROW_TEXT_SCALE);
    }

    protected final void renderCloseIcon(float right, float rowTop) {
        ResourceLocation close = RenderUtils.getIcon(CLOSE_ICON_PATH);
        if (close == null) {
            return;
        }
        float closeX = right - CLOSE_SIZE - CLOSE_PAD;
        float closeY = rowTop + (LIST_ROW_VISUAL_HEIGHT - CLOSE_SIZE) / 2f;
        int closeColor = Theme.getGradient(Theme.hiddenBind[0], Theme.hiddenBind[1], 0);
        RenderUtils.drawIcon(close, closeX, closeY, CLOSE_SIZE, closeColor);
    }

    protected final boolean isOverClose(float mouseX, float mouseY, float rowTop, float right) {
        float closeX = right - CLOSE_SIZE - CLOSE_PAD;
        float closeY = rowTop + (ROW_HEIGHT - CLOSE_SIZE) / 2f;
        return mouseX >= closeX && mouseX <= closeX + CLOSE_SIZE && mouseY >= closeY && mouseY <= closeY + CLOSE_SIZE;
    }

    protected final void renderItemInRow(ItemStack stack, float x, float rowTop) {
        if (stack == null) {
            return;
        }

        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        double scale = 0.55;
        float itemHeight = (float) (16 * scale);
        float pad = (LIST_ROW_VISUAL_HEIGHT - itemHeight) / 2f;
        float itemY = rowTop + pad;
        float px = (float) (x / scale);
        float py = (float) (itemY / scale);

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(px, py, 0f);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.disableBlend();
        renderItem.renderItemAndEffectIntoGUI(stack, 0, 0);
        GlStateManager.enableBlend();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    protected final boolean isMouseOverDropdown(float mouseX, float mouseY) {
        if (getDropdownRowCount() == 0) {
            return false;
        }
        float dropdownHeight = getAnimatedDropdownHeight();
        if (dropdownHeight <= 0f) {
            return false;
        }
        Layout layout = layout(true);
        return mouseX >= layout.left && mouseX <= layout.right && mouseY >= layout.contentTop && mouseY < layout.contentTop + dropdownHeight;
    }

    protected final boolean isMouseOverSelectedList(float mouseX, float mouseY) {
        if (getSelectedEntryCount() == 0) {
            return false;
        }
        Layout layout = layout(true);
        float selectedTop = getSelectedTop(layout);
        float selectedHeight = getSelectedVisibleHeight();
        return mouseX >= layout.left && mouseX <= layout.right && mouseY >= selectedTop && mouseY < selectedTop + selectedHeight;
    }

    protected final boolean isMouseOverDropdown() {
        return isMouseOverDropdown(lastMouseX, lastMouseY);
    }

    protected final boolean isMouseOverSelectedList() {
        return isMouseOverSelectedList(lastMouseX, lastMouseY);
    }

    private float computeDropdownTarget() {
        int rows = getDropdownRowCount();
        if (isSearchFocused() && rows > 0) {
            return Math.min(MAX_VISIBLE_RESULTS, rows) * ROW_HEIGHT;
        }
        return 0f;
    }

    private void renderDropdown(Layout layout) {
        int rowCount = getDropdownRowCount();
        float dropdownHeight = getAnimatedDropdownHeight();
        float scrollOffset = moduleComponent.categoryComponent.moduleY - layout.cy;
        if (dropdownHeight <= 0f || rowCount == 0) {
            return;
        }

        float dropdownTopScreen = layout.contentTop + scrollOffset;
        RenderUtils.scissorPushGui(layout.left, dropdownTopScreen, layout.right - layout.left, dropdownHeight);
        float offsetPx = dropdownScrollAnim.getValue();
        int firstRow = (int) (offsetPx / ROW_HEIGHT);
        int end = Math.min(firstRow + MAX_VISIBLE_RESULTS + 1, rowCount);
        int rowUnderMouse = -1;

        if (lastMouseX >= layout.left && lastMouseX <= layout.right && lastMouseY >= dropdownTopScreen && lastMouseY < dropdownTopScreen + dropdownHeight) {
            float relY = lastMouseY - dropdownTopScreen;
            rowUnderMouse = (int) ((relY + offsetPx) / ROW_HEIGHT);
            if (rowUnderMouse < 0 || rowUnderMouse >= rowCount) {
                rowUnderMouse = -1;
            }
        }

        renderDropdownRows(layout, offsetPx, firstRow, end, rowUnderMouse);
        RenderUtils.scissorPop();
    }

    private void renderSelectedEntries(Layout layout) {
        int selectedCount = getSelectedEntryCount();
        if (selectedCount == 0) {
            return;
        }

        float selectedTop = getSelectedTop(layout);
        float selectedHeight = getSelectedVisibleHeight();
        float scrollOffset = moduleComponent.categoryComponent.moduleY - layout.cy;
        RenderUtils.scissorPushGui(layout.left, selectedTop + scrollOffset, layout.right - layout.left, selectedHeight);
        float offsetPx = selectedScrollAnim.getValue();
        int firstRow = (int) (offsetPx / ROW_HEIGHT);
        int end = Math.min(firstRow + MAX_VISIBLE_SELECTED + 1, selectedCount);
        renderSelectedRows(layout, offsetPx, firstRow, end);
        RenderUtils.scissorPop();
    }

    private boolean handleTextFieldFocusClick(int mouseX, int mouseY, Layout layout) {
        if (isTextFieldClicked(mouseX, mouseY, layout)) {
            setTextFieldFocused(true);
            onSearchFieldFocused();
            updateDropdownAnimation();
            return true;
        }
        return false;
    }


    protected boolean hasAdditionalTextInputFocus() {
        return false;
    }

    protected void clearAdditionalTextInputFocus() {
    }

    protected void onAfterBaseDrawScreen() {
    }

    protected void onDropdownClickHandled(int mouseX, int mouseY) {
    }

    protected void onSelectedEntryClickHandled(int mouseX, int mouseY) {
    }

    protected void onSearchFocusHandled(int mouseX, int mouseY) {
    }

    protected void onOutsideClick(int mouseX, int mouseY, int button) {
    }

    protected abstract String getLabelText();

    protected abstract int getSelectedEntryCount();

    protected abstract int getDropdownRowCount();

    protected abstract void renderDropdownRows(Layout layout, float offsetPx, int firstRow, int end, int rowUnderMouse);

    protected abstract void renderSelectedRows(Layout layout, float offsetPx, int firstRow, int end);

    protected abstract boolean handleDropdownClick(int mouseX, int mouseY, Layout layout);

    protected abstract boolean handleSelectedEntryClick(int mouseX, int mouseY, Layout layout);

    protected abstract void onSearchTextChanged(String text);

    protected abstract boolean handleSearchEscape();

    protected abstract void onSearchFieldFocused();

    protected abstract void resetSearchState();
}
