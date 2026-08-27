package keystrokesmod.clickgui.components.impl;

import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.utility.ItemSearchIndex;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractItemSearchComponent<T extends ItemListSetting> extends AbstractSearchListComponent {
    protected final T setting;

    private List<ItemSearchIndex.GroupedItemResult> cachedResults = Collections.emptyList();
    private String expandedGroupId;
    private String expandedGroupLabel;
    private String expandedAllSelectionStorageId;
    private List<ItemSearchIndex.ItemEntry> expandedVariants = Collections.emptyList();

    protected AbstractItemSearchComponent(T setting, ModuleComponent moduleComponent, float o) {
        super(moduleComponent, o, "Search items...");
        this.setting = setting;
    }

    @Override
    protected String getLabelText() {
        int count = setting.getItems().size();
        return setting.getName() + (count > 0 ? " (" + count + ")" : "");
    }

    @Override
    public boolean isBaseVisible() {
        return setting.visible;
    }

    @Override
    public String getGroupName() {
        return setting.group != null ? setting.group.getName() : "";
    }

    @Override
    protected int getDropdownRowCount() {
        return expandedGroupId != null ? 2 + expandedVariants.size() : cachedResults.size();
    }

    @Override
    protected void renderDropdownRows(Layout layout, float offsetPx, int firstRow, int end, int rowUnderMouse) {
        if (expandedGroupId != null) {
            String groupName = expandedGroupLabel != null ? expandedGroupLabel : expandedGroupId;
            for (int i = firstRow; i < end; i++) {
                float rowTop = layout.contentTop - offsetPx + i * ROW_HEIGHT;
                int bg = i == rowUnderMouse ? 0xFF2A2A3C : ((i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E);
                if (i == 0) {
                    renderBackRow(layout.left, layout.right, rowTop, bg, groupName);
                }
                else if (i == 1) {
                    renderStandardRow(groupName + " (All)", getExpandedAllCyclingIcon(), layout.left, layout.right, rowTop, bg, false);
                }
                else {
                    ItemSearchIndex.ItemEntry entry = expandedVariants.get(i - 2);
                    renderStandardRow(entry.displayName, entry.toItemStack(), layout.left, layout.right, rowTop, bg, false);
                }
            }
            return;
        }

        for (int i = firstRow; i < end; i++) {
            ItemSearchIndex.GroupedItemResult result = cachedResults.get(i);
            float rowTop = layout.contentTop - offsetPx + i * ROW_HEIGHT;
            int bg = i == rowUnderMouse ? 0xFF2A2A3C : ((i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E);
            if (result.isSingleVariant()) {
                ItemSearchIndex.ItemEntry single = result.variants.get(0);
                renderStandardRow(single.displayName, single.toItemStack(), layout.left, layout.right, rowTop, bg, false);
            }
            else {
                renderStandardRow(result.getGroupLabel(), result.getCyclingIcon(), layout.left, layout.right, rowTop, bg, false);
            }
        }
    }

    @Override
    protected boolean handleDropdownClick(int mouseX, int mouseY, Layout layout) {
        if (!isSearchFocused()) {
            return false;
        }

        int rowCount = getDropdownRowCount();
        if (rowCount == 0) {
            return false;
        }

        float offsetPx = dropdownScrollAnim.getValue();
        float relY = mouseY - layout.contentTop;
        int rowIndex = (int) ((relY + offsetPx) / ROW_HEIGHT);
        if (rowIndex < 0 || rowIndex >= rowCount || mouseX <= layout.left || mouseX >= layout.right) {
            return false;
        }

        float rowTop = layout.contentTop - offsetPx + rowIndex * ROW_HEIGHT;
        if (mouseY < rowTop || mouseY >= rowTop + ROW_HEIGHT) {
            return false;
        }

        if (expandedGroupId != null) {
            if (rowIndex == 0) {
                collapseExpandedGroup();
                return true;
            }
            if (rowIndex == 1) {
                setting.addItem(expandedAllSelectionStorageId != null ? expandedAllSelectionStorageId : expandedGroupId + ":*");
                afterSelectionAdded();
                return true;
            }

            int variantIndex = rowIndex - 2;
            if (variantIndex >= 0 && variantIndex < expandedVariants.size()) {
                setting.addItem(expandedVariants.get(variantIndex).storageId);
                afterSelectionAdded();
                return true;
            }
            return true;
        }

        ItemSearchIndex.GroupedItemResult result = cachedResults.get(rowIndex);
        if (result.isSingleVariant()) {
            setting.addItem(result.variants.get(0).storageId);
            afterSelectionAdded();
            return true;
        }

        expandedGroupId = result.registryId;
        expandedGroupLabel = result.getGroupDisplayName();
        expandedAllSelectionStorageId = result.getAllSelectionStorageId();
        expandedVariants = new ArrayList<ItemSearchIndex.ItemEntry>();
        for (ItemSearchIndex.ItemEntry entry : result.variants) {
            if (!setting.containsItem(entry.storageId)) {
                expandedVariants.add(entry);
            }
        }
        dropdownScrollAnim.reset(0);
        updateDropdownAnimation();
        moduleComponent.updateSettingPositions();
        return true;
    }

    @Override
    protected void onSearchTextChanged(String text) {
        collapseExpandedGroupStateOnly();
        cachedResults = ItemSearchIndex.searchGrouped(text, setting);
    }

    @Override
    protected boolean handleSearchEscape() {
        if (expandedGroupId != null) {
            collapseExpandedGroup();
            return true;
        }
        return false;
    }

    @Override
    protected void onSearchFieldFocused() {
        if (!getTextField().getText().isEmpty() && cachedResults.isEmpty()) {
            cachedResults = ItemSearchIndex.searchGrouped(getTextField().getText(), setting);
        }
    }

    @Override
    protected void resetSearchState() {
        cachedResults = Collections.emptyList();
        collapseExpandedGroupStateOnly();
        onSearchStateReset();
    }

    protected final void removeSelection(String storageId) {
        setting.removeItem(storageId);
        invalidateSelectedRows();
        notifySelectionListChanged();
    }

    protected final void refreshSelectedRows() {
        invalidateSelectedRows();
    }

    protected final T getItemSetting() {
        return setting;
    }

    protected final ItemSearchIndex.GroupedItemResult getCachedResult(int index) {
        return cachedResults.get(index);
    }

    protected final List<ItemSearchIndex.ItemEntry> getExpandedVariants() {
        return expandedVariants;
    }

    private void collapseExpandedGroup() {
        collapseExpandedGroupStateOnly();
        dropdownScrollAnim.reset(0);
        updateDropdownAnimation();
        moduleComponent.updateSettingPositions();
    }

    private void collapseExpandedGroupStateOnly() {
        expandedGroupId = null;
        expandedGroupLabel = null;
        expandedAllSelectionStorageId = null;
        expandedVariants = Collections.emptyList();
    }

    private void afterSelectionAdded() {
        getTextField().setText("");
        setTextFieldFocused(false);
        cachedResults = Collections.emptyList();
        collapseExpandedGroupStateOnly();
        dropdownScrollAnim.reset(0);
        invalidateSelectedRows();
        notifySelectionListChanged();
    }

    private ItemStack getExpandedAllCyclingIcon() {
        if (expandedVariants.isEmpty()) {
            return null;
        }
        int index = (int) ((System.currentTimeMillis() / 1000L) % expandedVariants.size());
        return expandedVariants.get(index).toItemStack();
    }

    protected void onSearchStateReset() {
    }

    protected abstract void invalidateSelectedRows();
}
