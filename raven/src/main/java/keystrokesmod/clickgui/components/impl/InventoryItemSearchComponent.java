package keystrokesmod.clickgui.components.impl;

import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.InventoryItemListSetting;
import keystrokesmod.utility.ItemSearchIndex;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.font.RavenFontRenderer;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public class InventoryItemSearchComponent extends AbstractItemSearchComponent<InventoryItemListSetting> {
    private static final float SLOT_PILL_MIN_WIDTH = 9f;
    private static final float SLOT_PILL_HORIZONTAL_PAD = 3f;
    private static final float SLOT_PILL_TEXT_Y_OFFSET = 0.5f;
    private static final float SLOT_BOX_GAP = 3f;
    private static final float DRAG_SCROLL_EDGE = 10f;
    private static final float DRAG_SCROLL_SPEED = 3f;

    private static final class InventorySelectedRowData extends SelectedRowData {
        final Integer assignedSlot;

        private InventorySelectedRowData(String storageId, String displayName, net.minecraft.item.ItemStack stack, List<net.minecraft.item.ItemStack> cyclingStacks, Integer assignedSlot) {
            super(storageId, displayName, stack, cyclingStacks);
            this.assignedSlot = assignedSlot;
        }
    }

    private List<InventorySelectedRowData> selectedRowsCache;
    private String listeningStorageId;
    private String draggingStorageId;
    private float dragGrabOffsetY;

    public InventoryItemSearchComponent(InventoryItemListSetting setting, ModuleComponent moduleComponent, float o) {
        super(setting, moduleComponent, o);
    }

    @Override
    protected int getSelectedEntryCount() {
        return setting.getItems().size();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        super.drawScreen(mouseX, mouseY);
        updateDragState();
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) {
            draggingStorageId = null;
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!moduleComponent.isOpened) {
            return;
        }

        if (listeningStorageId != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                listeningStorageId = null;
                return;
            }

            int slot = getHotbarSlotForKey(keyCode);
            if (slot != -1) {
                setting.setAssignedSlot(listeningStorageId, slot);
                listeningStorageId = null;
                invalidateSelectedRows();
                markUnsaved();
            }
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected boolean hasAdditionalTextInputFocus() {
        return listeningStorageId != null;
    }

    @Override
    protected void clearAdditionalTextInputFocus() {
        listeningStorageId = null;
    }

    @Override
    protected void onDropdownClickHandled(int mouseX, int mouseY) {
        listeningStorageId = null;
    }

    @Override
    protected void onSearchFocusHandled(int mouseX, int mouseY) {
        listeningStorageId = null;
    }

    @Override
    protected void onOutsideClick(int mouseX, int mouseY, int button) {
        listeningStorageId = null;
    }

    @Override
    protected void renderSelectedRows(Layout layout, float offsetPx, int firstRow, int end) {
        List<String> items = setting.getItems();
        if (selectedRowsCache == null || selectedRowsCache.size() != items.size()) {
            selectedRowsCache = new ArrayList<InventorySelectedRowData>();
            for (String storageId : items) {
                List<ItemSearchIndex.ItemEntry> variants = ItemSearchIndex.isGroupedSelection(storageId)
                    ? ItemSearchIndex.getSelectionVariants(storageId)
                    : null;
                List<net.minecraft.item.ItemStack> cyclingStacks = null;
                if (variants != null && !variants.isEmpty()) {
                    cyclingStacks = new ArrayList<net.minecraft.item.ItemStack>();
                    for (ItemSearchIndex.ItemEntry variant : variants) {
                        cyclingStacks.add(variant.toItemStack());
                    }
                }
                selectedRowsCache.add(new InventorySelectedRowData(
                    storageId,
                    ItemSearchIndex.getDisplayName(storageId),
                    ItemSearchIndex.getItemStack(storageId),
                    cyclingStacks,
                    setting.getAssignedSlot(storageId)
                ));
            }
        }

        for (int i = firstRow; i < end; i++) {
            InventorySelectedRowData row = selectedRowsCache.get(i);
            float rowTop = getSelectedTop(layout) - offsetPx + i * ROW_HEIGHT;
            int bg = row.storageId.equals(draggingStorageId) ? 0xFF2A2A3C : ((i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E);
            renderSelectedRow(row, layout.left, layout.right, rowTop, bg);
        }
    }

    @Override
    protected boolean handleSelectedEntryClick(int mouseX, int mouseY, Layout layout) {
        int rowIndex = getSelectedRowIndex(mouseX, mouseY, layout);
        if (rowIndex < 0) {
            draggingStorageId = null;
            return false;
        }

        String storageId = setting.getItems().get(rowIndex);
        float rowTop = getSelectedTop(layout) - selectedScrollAnim.getValue() + rowIndex * ROW_HEIGHT;
        if (isOverClose(mouseX, mouseY, rowTop, layout.right)) {
            setting.removeItem(storageId);
            invalidateSelectedRows();
            listeningStorageId = null;
            draggingStorageId = null;
            markUnsaved();
            clampSelectedScroll();
            updateDropdownAnimation();
            moduleComponent.updateSettingPositions();
            return true;
        }

        if (isOverSlotPill(mouseX, mouseY, rowTop, layout.right, storageId)) {
            listeningStorageId = storageId;
            draggingStorageId = null;
            return true;
        }

        draggingStorageId = storageId;
        dragGrabOffsetY = mouseY - rowTop;
        if (!isPointInSlotPill(mouseX, mouseY, layout)) {
            listeningStorageId = null;
        }
        return true;
    }

    @Override
    protected void invalidateSelectedRows() {
        selectedRowsCache = null;
    }

    @Override
    protected void onSearchStateReset() {
        listeningStorageId = null;
        draggingStorageId = null;
    }

    private void renderSelectedRow(InventorySelectedRowData row, float left, float right, float rowTop, int bgColor) {
        RenderUtils.drawRect(left, rowTop, right, rowTop + ROW_HEIGHT - 1f, bgColor);
        renderItemInRow(getPreviewStack(row), left + 2f, rowTop);
        drawListRowText(row.displayName != null ? row.displayName : "", left + 13f, rowTop, 0xFFCCCCCC);

        float closeX = right - CLOSE_SIZE - CLOSE_PAD;
        float slotRight = closeX - SLOT_BOX_GAP;
        float slotLeft = slotRight - getSlotPillWidth(row.storageId);
        renderSlotPill(row, slotLeft, slotRight, rowTop);
        renderCloseIcon(right, rowTop);
    }

    private void renderSlotPill(InventorySelectedRowData row, float left, float right, float rowTop) {
        boolean listening = row.storageId.equals(listeningStorageId);
        int fill = listening ? 0xFF35557A : 0xFF244966;
        float top = rowTop + 2f;
        float bottom = rowTop + ROW_HEIGHT - 3f;
        RenderUtils.drawRect(left, top, right, bottom, 0xFF11141C);
        RenderUtils.drawRect(left + 1f, top + 1f, right - 1f, bottom - 1f, fill);

        String label = getSlotPillLabel(row.storageId);
        RavenFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();
        float textWidth = renderer.getStringWidth(label) * TEXT_SCALE;
        float textX = left + ((right - left) - textWidth) / 2f;
        float textY = centeredScaledTextY(top, bottom - top) + SLOT_PILL_TEXT_Y_OFFSET;
        drawScaledText(label, textX, textY, 0xFFE8EEF5);
    }

    private void updateDragState() {
        if (draggingStorageId == null || setting.getItems().isEmpty()) {
            return;
        }

        Layout layout = layout(true);
        float selectedTop = getSelectedTop(layout);
        float selectedHeight = getSelectedVisibleHeight();
        if (selectedHeight <= 0f) {
            draggingStorageId = null;
            return;
        }

        if (setting.getItems().size() > MAX_VISIBLE_SELECTED && lastMouseX >= layout.left && lastMouseX <= layout.right) {
            if (lastMouseY < selectedTop + DRAG_SCROLL_EDGE) {
                selectedScrollAnim.extend(DRAG_SCROLL_SPEED);
                clampSelectedScroll();
            }
            else if (lastMouseY > selectedTop + selectedHeight - DRAG_SCROLL_EDGE) {
                selectedScrollAnim.extend(-DRAG_SCROLL_SPEED);
                clampSelectedScroll();
            }
        }

        List<String> orderedItems = setting.getItems();
        int currentIndex = orderedItems.indexOf(draggingStorageId);
        if (currentIndex < 0) {
            draggingStorageId = null;
            return;
        }

        float draggedRowTop = lastMouseY - dragGrabOffsetY;
        float draggedRowCenter = draggedRowTop + ROW_HEIGHT / 2f;
        int desiredIndex = (int) Math.floor((draggedRowCenter - selectedTop + selectedScrollAnim.getValue()) / ROW_HEIGHT);
        desiredIndex = Math.max(0, Math.min(desiredIndex, orderedItems.size() - 1));
        if (desiredIndex != currentIndex) {
            setting.moveItem(draggingStorageId, desiredIndex);
            invalidateSelectedRows();
            markUnsaved();
        }
    }

    private int getSelectedRowIndex(int mouseX, int mouseY, Layout layout) {
        if (!isMouseOverSelectedList(mouseX, mouseY)) {
            return -1;
        }

        float selectedTop = getSelectedTop(layout);
        float offsetPx = selectedScrollAnim.getValue();
        int rowIndex = (int) ((mouseY - selectedTop + offsetPx) / ROW_HEIGHT);
        if (rowIndex < 0 || rowIndex >= setting.getItems().size()) {
            return -1;
        }

        float rowTop = selectedTop - offsetPx + rowIndex * ROW_HEIGHT;
        return mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT ? rowIndex : -1;
    }

    private boolean isPointInSlotPill(int mouseX, int mouseY, Layout layout) {
        int rowIndex = getSelectedRowIndex(mouseX, mouseY, layout);
        if (rowIndex < 0) {
            return false;
        }
        String storageId = setting.getItems().get(rowIndex);
        float rowTop = getSelectedTop(layout) - selectedScrollAnim.getValue() + rowIndex * ROW_HEIGHT;
        return isOverSlotPill(mouseX, mouseY, rowTop, layout.right, storageId);
    }

    private boolean isOverSlotPill(int mouseX, int mouseY, float rowTop, float right, String storageId) {
        float slotRight = right - CLOSE_SIZE - CLOSE_PAD - SLOT_BOX_GAP;
        float slotLeft = slotRight - getSlotPillWidth(storageId);
        return mouseX >= slotLeft && mouseX <= slotRight && mouseY >= rowTop + 1f && mouseY <= rowTop + ROW_HEIGHT - 1f;
    }

    private float getSlotPillWidth(String storageId) {
        String label = getSlotPillLabel(storageId);
        RavenFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();
        float textWidth = renderer.getStringWidth(label) * TEXT_SCALE;
        return Math.max(SLOT_PILL_MIN_WIDTH, textWidth + SLOT_PILL_HORIZONTAL_PAD * 2f);
    }

    private String getSlotPillLabel(String storageId) {
        if (storageId != null && storageId.equals(listeningStorageId)) {
            return "...";
        }

        Integer assignedSlot = setting.getAssignedSlot(storageId);
        return Integer.toString(assignedSlot != null ? assignedSlot : 1);
    }

    private int getHotbarSlotForKey(int keyCode) {
        Minecraft minecraft = Minecraft.getMinecraft();
        for (int i = 0; i < minecraft.gameSettings.keyBindsHotbar.length; i++) {
            if (keyCode == minecraft.gameSettings.keyBindsHotbar[i].getKeyCode()) {
                return i + 1;
            }
        }
        return -1;
    }
}
