package keystrokesmod.module.setting.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import keystrokesmod.module.setting.Setting;

import java.util.ArrayList;
import java.util.List;

public class BlockListSetting extends Setting {
    private final List<String> blocks = new ArrayList<>();
    private final String[] legacyProfileKeys;
    public GroupSetting group;

    public BlockListSetting(String name) {
        this(name, new String[0]);
    }

    public BlockListSetting(String name, String... legacyProfileKeys) {
        super(name);
        this.legacyProfileKeys = legacyProfileKeys != null ? legacyProfileKeys : new String[0];
    }

    public BlockListSetting(GroupSetting group, String name) {
        this(group, name, new String[0]);
    }

    public BlockListSetting(GroupSetting group, String name, String... legacyProfileKeys) {
        super(name);
        this.group = group;
        this.legacyProfileKeys = legacyProfileKeys != null ? legacyProfileKeys : new String[0];
    }

    public void addBlock(String registryName) {
        if (!blocks.contains(registryName)) {
            blocks.add(registryName);
        }
    }

    public void removeBlock(String registryName) {
        blocks.remove(registryName);
    }

    public List<String> getBlocks() {
        return blocks;
    }

    public boolean contains(String storageId) {
        if (blocks.contains(storageId)) return true;
        String registryId = extractRegistryId(storageId);
        return registryId != null && blocks.contains(registryId + ":*");
    }

    @Override
    public String getProfileKey() {
        return group == null ? getName() : group.getName() + "." + getName();
    }

    private static String extractRegistryId(String storageId) {
        if (storageId == null || storageId.isEmpty()) return null;
        if (storageId.endsWith(":*")) return storageId.substring(0, storageId.length() - 2);
        String[] p = storageId.split(":");
        if (p.length >= 3) return p[0] + ":" + p[1];
        if (p.length == 2) return storageId;
        return null;
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
            for (String legacyProfileKey : legacyProfileKeys) {
                if (data.has(legacyProfileKey)) {
                    key = legacyProfileKey;
                    break;
                }
            }
        }
        if (key == null) return;
        blocks.clear();
        JsonElement el = data.get(key);
        if (el.isJsonArray()) {
            for (JsonElement entry : el.getAsJsonArray()) {
                blocks.add(entry.getAsString());
            }
        }
    }

    public JsonArray toJsonArray() {
        JsonArray arr = new JsonArray();
        for (String block : blocks) {
            arr.add(new JsonPrimitive(block));
        }
        return arr;
    }

    protected String[] getLegacyProfileKeys() {
        return legacyProfileKeys;
    }
}
