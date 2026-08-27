package keystrokesmod.clickgui.components.impl;

import keystrokesmod.module.setting.impl.BlockListSetting;
import keystrokesmod.utility.BlockSearchIndex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlockSearchComponent extends AbstractSearchListComponent {
    public final BlockListSetting setting;

    private List<BlockSearchIndex.GroupedBlockResult> cachedResults = Collections.emptyList();
    private String expandedGroupId;
    private List<BlockSearchIndex.BlockEntry> expandedVariants = Collections.emptyList();
    private List<SelectedRowData> selectedRowsCache;

    public BlockSearchComponent(BlockListSetting setting, ModuleComponent moduleComponent, float o) {
        super(moduleComponent, o, "Search blocks...");
        this.setting = setting;
    }

    @Override
    protected String getLabelText() {
        int count = setting.getBlocks().size();
        return setting.getName() + (count > 0 ? " (" + count + ")" : "");
    }

    @Override
    protected int getSelectedEntryCount() {
        return setting.getBlocks().size();
    }

    @Override
    protected int getDropdownRowCount() {
        return expandedGroupId != null ? 2 + expandedVariants.size() : cachedResults.size();
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
    protected void renderDropdownRows(Layout layout, float offsetPx, int firstRow, int end, int rowUnderMouse) {
        if (expandedGroupId != null) {
            String groupName = !expandedVariants.isEmpty() ? expandedVariants.get(0).displayName : expandedGroupId;
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
                    BlockSearchIndex.BlockEntry entry = expandedVariants.get(i - 2);
                    renderStandardRow(entry.displayName, entry.toItemStack(), layout.left, layout.right, rowTop, bg, false);
                }
            }
            return;
        }

        for (int i = firstRow; i < end; i++) {
            BlockSearchIndex.GroupedBlockResult result = cachedResults.get(i);
            float rowTop = layout.contentTop - offsetPx + i * ROW_HEIGHT;
            int bg = i == rowUnderMouse ? 0xFF2A2A3C : ((i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E);
            if (result.isSingleVariant()) {
                BlockSearchIndex.BlockEntry single = result.variants.get(0);
                renderStandardRow(single.displayName, single.toItemStack(), layout.left, layout.right, rowTop, bg, false);
            }
            else {
                renderStandardRow(result.getGroupLabel(), result.getCyclingIcon(), layout.left, layout.right, rowTop, bg, false);
            }
        }
    }

    @Override
    protected void renderSelectedRows(Layout layout, float offsetPx, int firstRow, int end) {
        List<String> blocks = setting.getBlocks();
        if (selectedRowsCache == null || selectedRowsCache.size() != blocks.size()) {
            selectedRowsCache = new ArrayList<SelectedRowData>();
            for (String storageId : blocks) {
                List<BlockSearchIndex.BlockEntry> variants = null;
                if (BlockSearchIndex.isWildcard(storageId)) {
                    variants = BlockSearchIndex.getVariants(BlockSearchIndex.getRegistryId(storageId));
                }
                List<net.minecraft.item.ItemStack> cyclingStacks = null;
                if (variants != null && !variants.isEmpty()) {
                    cyclingStacks = new ArrayList<net.minecraft.item.ItemStack>();
                    for (BlockSearchIndex.BlockEntry variant : variants) {
                        cyclingStacks.add(variant.toItemStack());
                    }
                }
                selectedRowsCache.add(new SelectedRowData(
                    storageId,
                    BlockSearchIndex.getDisplayName(storageId),
                    BlockSearchIndex.getItemStack(storageId),
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
    protected boolean handleDropdownClick(int mouseX, int mouseY, Layout layout) {
        if (!isSearchFocused()) {
            return false;
        }

        int rowCount = getDropdownRowCount();
        if (rowCount == 0) {
            return false;
        }

        float offsetPx = dropdownScrollAnim.getValue();
        int rowIndex = (int) (((mouseY - layout.contentTop) + offsetPx) / ROW_HEIGHT);
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
                setting.addBlock(expandedGroupId + ":*");
                afterBlockSelectionAdded();
                return true;
            }

            int variantIndex = rowIndex - 2;
            if (variantIndex >= 0 && variantIndex < expandedVariants.size()) {
                setting.addBlock(expandedVariants.get(variantIndex).storageId);
                afterBlockSelectionAdded();
                return true;
            }
            return true;
        }

        BlockSearchIndex.GroupedBlockResult result = cachedResults.get(rowIndex);
        if (result.isSingleVariant()) {
            setting.addBlock(result.variants.get(0).storageId);
            afterBlockSelectionAdded();
            return true;
        }

        expandedGroupId = result.registryId;
        expandedVariants = new ArrayList<BlockSearchIndex.BlockEntry>();
        for (BlockSearchIndex.BlockEntry entry : result.variants) {
            if (!setting.contains(entry.storageId)) {
                expandedVariants.add(entry);
            }
        }
        dropdownScrollAnim.reset(0);
        updateDropdownAnimation();
        moduleComponent.updateSettingPositions();
        return true;
    }

    @Override
    protected boolean handleSelectedEntryClick(int mouseX, int mouseY, Layout layout) {
        float offsetPx = selectedScrollAnim.getValue();
        List<String> blocks = new ArrayList<String>(setting.getBlocks());
        for (int i = 0; i < blocks.size(); i++) {
            float rowTop = getSelectedTop(layout) - offsetPx + i * ROW_HEIGHT;
            if (isOverClose(mouseX, mouseY, rowTop, layout.right)) {
                setting.removeBlock(blocks.get(i));
                selectedRowsCache = null;
                notifySelectionListChanged();
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onSearchTextChanged(String text) {
        expandedGroupId = null;
        expandedVariants = Collections.emptyList();
        cachedResults = BlockSearchIndex.searchGrouped(text, setting);
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
            cachedResults = BlockSearchIndex.searchGrouped(getTextField().getText(), setting);
        }
    }

    @Override
    protected void resetSearchState() {
        cachedResults = Collections.emptyList();
        expandedGroupId = null;
        expandedVariants = Collections.emptyList();
        selectedRowsCache = null;
    }

    private void afterBlockSelectionAdded() {
        getTextField().setText("");
        setTextFieldFocused(false);
        cachedResults = Collections.emptyList();
        expandedGroupId = null;
        expandedVariants = Collections.emptyList();
        dropdownScrollAnim.reset(0);
        selectedRowsCache = null;
        notifySelectionListChanged();
    }

    private void collapseExpandedGroup() {
        expandedGroupId = null;
        expandedVariants = Collections.emptyList();
        dropdownScrollAnim.reset(0);
        updateDropdownAnimation();
        moduleComponent.updateSettingPositions();
    }

    private net.minecraft.item.ItemStack getExpandedAllCyclingIcon() {
        if (expandedVariants.isEmpty()) {
            return null;
        }
        int index = (int) ((System.currentTimeMillis() / 1000L) % expandedVariants.size());
        return expandedVariants.get(index).toItemStack();
    }
}
