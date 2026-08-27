package keystrokesmod.clickgui.components.impl;

import keystrokesmod.module.setting.impl.PotionListSetting;
import keystrokesmod.utility.PotionSearchIndex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PotionSearchComponent extends AbstractSearchListComponent {
    private final PotionListSetting setting;
    private List<PotionSearchIndex.PotionEntry> cachedResults = Collections.emptyList();

    public PotionSearchComponent(PotionListSetting setting, ModuleComponent moduleComponent, float o) {
        super(moduleComponent, o, "Search potion effects...");
        this.setting = setting;
    }

    @Override
    protected String getLabelText() {
        int count = setting.getPotions().size();
        return setting.getName() + (count > 0 ? " (" + count + ")" : "");
    }

    @Override
    protected int getSelectedEntryCount() {
        return setting.getPotions().size();
    }

    @Override
    protected int getDropdownRowCount() {
        return cachedResults.size();
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
        for (int i = firstRow; i < end; i++) {
            PotionSearchIndex.PotionEntry entry = cachedResults.get(i);
            float rowTop = layout.contentTop - offsetPx + i * ROW_HEIGHT;
            int bg = i == rowUnderMouse ? 0xFF2A2A3C : ((i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E);
            renderStandardRow(entry.displayName, PotionSearchIndex.getItemStack(entry.key), layout.left, layout.right, rowTop, bg, false);
        }
    }

    @Override
    protected void renderSelectedRows(Layout layout, float offsetPx, int firstRow, int end) {
        List<String> selectedPotions = setting.getPotions();
        for (int i = firstRow; i < end; i++) {
            String potionKey = selectedPotions.get(i);
            float rowTop = getSelectedTop(layout) - offsetPx + i * ROW_HEIGHT;
            int bg = (i % 2 == 0) ? 0xFF1A1A2A : 0xFF1E1E2E;
            renderStandardRow(PotionSearchIndex.getDisplayName(potionKey), PotionSearchIndex.getItemStack(potionKey), layout.left, layout.right, rowTop, bg, true);
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

        setting.addPotion(cachedResults.get(rowIndex).key);
        afterPotionSelectionAdded();
        return true;
    }

    @Override
    protected boolean handleSelectedEntryClick(int mouseX, int mouseY, Layout layout) {
        float offsetPx = selectedScrollAnim.getValue();
        List<String> selectedPotions = new ArrayList<String>(setting.getPotions());
        for (int i = 0; i < selectedPotions.size(); i++) {
            float rowTop = getSelectedTop(layout) - offsetPx + i * ROW_HEIGHT;
            if (isOverClose(mouseX, mouseY, rowTop, layout.right)) {
                setting.removePotion(selectedPotions.get(i));
                notifySelectionListChanged();
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onSearchTextChanged(String text) {
        cachedResults = PotionSearchIndex.search(text, setting);
    }

    @Override
    protected boolean handleSearchEscape() {
        return false;
    }

    @Override
    protected void onSearchFieldFocused() {
        cachedResults = PotionSearchIndex.search(getTextField().getText(), setting);
    }

    @Override
    protected void resetSearchState() {
        cachedResults = Collections.emptyList();
    }

    private void afterPotionSelectionAdded() {
        getTextField().setText("");
        setTextFieldFocused(false);
        cachedResults = Collections.emptyList();
        dropdownScrollAnim.reset(0);
        notifySelectionListChanged();
    }
}
