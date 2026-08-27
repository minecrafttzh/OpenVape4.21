package keystrokesmod.module.setting.impl;

import java.util.List;

public class PotionListSetting extends BlockListSetting {
    public PotionListSetting(String name) {
        super(name);
    }

    public PotionListSetting(String name, String... legacyProfileKeys) {
        super(name, legacyProfileKeys);
    }

    public PotionListSetting(GroupSetting group, String name) {
        super(group, name);
    }

    public PotionListSetting(GroupSetting group, String name, String... legacyProfileKeys) {
        super(group, name, legacyProfileKeys);
    }

    public void addPotion(String potionKey) {
        addBlock(potionKey);
    }

    public void removePotion(String potionKey) {
        removeBlock(potionKey);
    }

    public boolean containsPotion(String potionKey) {
        return contains(potionKey);
    }

    public List<String> getPotions() {
        return getBlocks();
    }
}
