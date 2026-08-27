package keystrokesmod.module.setting.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import keystrokesmod.module.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StringListSetting extends Setting {
    private final String placeholder;
    private final int maxLength;
    private final List<String> entries = new ArrayList<String>();
    public GroupSetting group;

    public StringListSetting(String name, String placeholder, int maxLength) {
        this(null, name, placeholder, maxLength);
    }

    public StringListSetting(GroupSetting group, String name, String placeholder, int maxLength) {
        super(name);
        this.group = group;
        this.placeholder = placeholder == null ? "" : placeholder;
        this.maxLength = Math.max(1, maxLength);
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public List<String> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean addEntry(String entry) {
        if (entry == null || entry.trim().isEmpty()) {
            return false;
        }
        String trimmed = entry.trim();
        for (String existing : entries) {
            if (existing.equalsIgnoreCase(trimmed)) {
                return false;
            }
        }
        entries.add(trimmed);
        return true;
    }

    public boolean removeEntry(String entry) {
        return entries.removeIf(e -> e.equalsIgnoreCase(entry));
    }

    public void clearEntries() {
        entries.clear();
    }

    public JsonArray toJsonArray() {
        JsonArray array = new JsonArray();
        for (String entry : entries) {
            array.add(new com.google.gson.JsonPrimitive(entry));
        }
        return array;
    }

    public void fromJsonArray(JsonArray array) {
        entries.clear();
        if (array == null) {
            return;
        }
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null && !value.trim().isEmpty()) {
                    entries.add(value.trim());
                }
            }
        }
    }

    @Override
    public String getProfileKey() {
        return group == null ? getName() : group.getName() + "." + getName();
    }

    @Override
    public void loadProfile(JsonObject data) {
        if (data == null) {
            return;
        }
        String key = getProfileKey();
        if (data.has(key) && data.get(key).isJsonArray()) {
            fromJsonArray(data.getAsJsonArray(key));
        }
    }
}
