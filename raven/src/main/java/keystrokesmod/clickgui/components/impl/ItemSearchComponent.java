package keystrokesmod.clickgui.components.impl;

import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.utility.ItemSearchIndex;

import java.util.ArrayList;
import java.util.List;

public class ItemSearchComponent extends AbstractItemSearchComponent<ItemListSetting> {
    private List<SelectedRowData> selectedRowsCache;

    public ItemSearchComponent(ItemListSetting setting, ModuleComponent moduleComponent, float o) {
        super(setting, moduleComponent, o);
    }

    @Override
    protected int getSelectedEntryCount() {
        return setting.getItems().size();
    }

    @Override
    protected void renderSelectedRows(Layout layout, float offsetPx, int firstRow, int end) {
        List<String> items = setting.getItems();
        if (selectedRowsCache == null || selectedRowsCache.size() != items.size()) {
            selectedRowsCache = new ArrayList<SelectedRowData>();
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
                selectedRowsCache.add(new SelectedRowData(
                    storageId,
                    ItemSearchIndex.getDisplayName(storageId),
                    ItemSearchIndex.getItemStack(storageId),
                    cyclingStacks
                ));
            }
        }

        for (int i = firstRow; i < end; i++) {
            SelectedRowData row = selectedRowsCache.get(i);
            float rowTop = getSelectedTop(layout) - offsetPx + i * ROW_HEIGHT;
            int bg = (i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E;
            renderStandardRow(row.displayName, getPreviewStack(row), layout.left, layout.right, rowTop, bg, true);
        }
    }

    @Override
    protected boolean handleSelectedEntryClick(int mouseX, int mouseY, Layout layout) {
        float offsetPx = selectedScrollAnim.getValue();
        List<String> items = new ArrayList<String>(setting.getItems());
        for (int i = 0; i < items.size(); i++) {
            float rowTop = getSelectedTop(layout) - offsetPx + i * ROW_HEIGHT;
            if (isOverClose(mouseX, mouseY, rowTop, layout.right)) {
                removeSelection(items.get(i));
                return true;
            }
        }
        return false;
    }

    @Override
    protected void invalidateSelectedRows() {
        selectedRowsCache = null;
    }
}
