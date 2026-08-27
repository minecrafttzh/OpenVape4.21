package keystrokesmod.module.setting.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryItemListSetting extends ItemListSetting {
    private static final int DEFAULT_ASSIGNED_SLOT = 1;
    private final Map<String, Integer> assignedSlots = new HashMap<String, Integer>();

    public InventoryItemListSetting(String name) {
        super(name);
    }

    public InventoryItemListSetting(String name, String... legacyProfileKeys) {
        super(name, legacyProfileKeys);
    }

    public InventoryItemListSetting(GroupSetting group, String name) {
        super(group, name);
    }

    public InventoryItemListSetting(GroupSetting group, String name, String... legacyProfileKeys) {
        super(group, name, legacyProfileKeys);
    }

    @Override
    public void addItem(String storageId) {
        if (storageId == null || storageId.isEmpty() || containsItem(storageId)) {
            return;
        }
        super.addItem(storageId);
        assignedSlots.put(storageId, DEFAULT_ASSIGNED_SLOT);
    }

    @Override
    public void removeItem(String storageId) {
        super.removeItem(storageId);
        assignedSlots.remove(storageId);
    }

    public Integer getAssignedSlot(String storageId) {
        if (storageId == null || !getItems().contains(storageId)) {
            return null;
        }
        Integer slot = assignedSlots.get(storageId);
        return slot != null ? slot : DEFAULT_ASSIGNED_SLOT;
    }

    public void setAssignedSlot(String storageId, Integer slot) {
        if (storageId == null || !getItems().contains(storageId)) {
            return;
        }
        assignedSlots.put(storageId, slot == null || slot < 1 || slot > 9 ? DEFAULT_ASSIGNED_SLOT : slot);
    }

    public void moveItem(String storageId, int toIndex) {
        List<String> items = getItems();
        int fromIndex = items.indexOf(storageId);
        if (fromIndex < 0) {
            return;
        }

        int clampedIndex = Math.max(0, Math.min(toIndex, items.size() - 1));
        if (fromIndex == clampedIndex) {
            return;
        }

        items.remove(fromIndex);
        if (clampedIndex > items.size()) {
            clampedIndex = items.size();
        }
        items.add(clampedIndex, storageId);
    }

    @Override
    public void loadProfile(JsonObject data) {
        String key = null;
        if (data.has(getProfileKey())) {
            key = getProfileKey();
        }
        else if (data.has(getName())) {
            key = getName();
        }
        else {
            for (String legacyProfileKey : getLegacyProfileKeys()) {
                if (data.has(legacyProfileKey)) {
                    key = legacyProfileKey;
                    break;
                }
            }
        }

        if (key == null) {
            return;
        }

        getItems().clear();
        assignedSlots.clear();
        JsonElement element = data.get(key);
        if (!element.isJsonArray()) {
            return;
        }

        JsonArray array = element.getAsJsonArray();
        for (JsonElement entry : array) {
            if (entry == null || entry.isJsonNull()) {
                continue;
            }

            if (entry.isJsonPrimitive()) {
                String storageId = entry.getAsString();
                if (storageId == null || storageId.isEmpty() || containsItem(storageId)) {
                    continue;
                }
                super.addItem(storageId);
                assignedSlots.put(storageId, DEFAULT_ASSIGNED_SLOT);
                continue;
            }

            if (!entry.isJsonObject()) {
                continue;
            }

            JsonObject object = entry.getAsJsonObject();
            if (!object.has("id")) {
                continue;
            }

            String storageId = object.get("id").getAsString();
            if (storageId == null || storageId.isEmpty() || containsItem(storageId)) {
                continue;
            }

            super.addItem(storageId);
            int slot = DEFAULT_ASSIGNED_SLOT;
            if (object.has("slot") && object.get("slot").isJsonPrimitive()) {
                int configuredSlot = object.get("slot").getAsInt();
                if (configuredSlot >= 1 && configuredSlot <= 9) {
                    slot = configuredSlot;
                }
            }
            assignedSlots.put(storageId, slot);
        }
    }

    @Override
    public JsonArray toJsonArray() {
        JsonArray array = new JsonArray();
        for (String storageId : getItems()) {
            JsonObject object = new JsonObject();
            object.addProperty("id", storageId);
            object.add("slot", new JsonPrimitive(getAssignedSlot(storageId)));
            array.add(object);
        }
        return array;
    }
}
