package keystrokesmod.utility;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import keystrokesmod.Raven;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.clickgui.components.impl.CategoryComponent;
import keystrokesmod.clickgui.components.impl.ModuleComponent;
import keystrokesmod.clickgui.components.impl.PlayerListComponent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlayerRelationsManager implements IMinecraftInstance {
    public enum RelationType {
        FRIEND("friends"),
        ENEMY("enemies");

        private final String jsonKey;

        RelationType(String jsonKey) {
            this.jsonKey = jsonKey;
        }

        public String getJsonKey() {
            return jsonKey;
        }
    }

    public static final class PlayerEntry {
        private final String key;
        private final String displayName;

        public PlayerEntry(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public String getKey() {
            return key;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final File file;
    private final LinkedHashMap<String, String> friends = new LinkedHashMap<String, String>();
    private final LinkedHashMap<String, String> enemies = new LinkedHashMap<String, String>();

    private boolean active = true;
    private boolean middleClickFriends;

    public PlayerRelationsManager() {
        File directory = new File(mc.mcDataDir, "keystrokes");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        this.file = new File(directory, "players.json");
    }

    public void load() {
        friends.clear();
        enemies.clear();
        active = true;
        middleClickFriends = false;

        if (!file.exists()) {
            syncUtilsViews();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            if (root == null) {
                syncUtilsViews();
                return;
            }

            if (root.has("middleClickFriends")) {
                middleClickFriends = root.get("middleClickFriends").getAsBoolean();
            }
            if (root.has("active")) {
                active = root.get("active").getAsBoolean();
            }

            loadEntries(root.get("friends"), friends);
            loadEntries(root.get("enemies"), enemies);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        syncUtilsViews();
    }

    public boolean addFriend(String name) {
        return addRelation(RelationType.FRIEND, name);
    }

    public boolean addEnemy(String name) {
        return addRelation(RelationType.ENEMY, name);
    }

    public boolean removeFriend(String name) {
        return removeRelation(RelationType.FRIEND, name);
    }

    public boolean removeEnemy(String name) {
        return removeRelation(RelationType.ENEMY, name);
    }

    public void clearFriends() {
        clearRelation(RelationType.FRIEND);
    }

    public void clearEnemies() {
        clearRelation(RelationType.ENEMY);
    }

    public boolean addRelation(RelationType relationType, String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return false;
        }

        LinkedHashMap<String, String> primary = relationType == RelationType.FRIEND ? friends : enemies;
        LinkedHashMap<String, String> opposite = relationType == RelationType.FRIEND ? enemies : friends;
        String displayName = normalizeDisplayName(name, normalized);
        boolean added = !primary.containsKey(normalized);
        boolean removedOpposite = opposite.remove(normalized) != null;
        boolean displayChanged = !displayName.equals(primary.get(normalized));

        if (!added && !removedOpposite && !displayChanged) {
            return false;
        }

        primary.put(normalized, displayName);
        persistAndNotify();
        return added;
    }

    public boolean removeRelation(RelationType relationType, String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return false;
        }

        LinkedHashMap<String, String> map = relationType == RelationType.FRIEND ? friends : enemies;
        if (map.remove(normalized) == null) {
            return false;
        }

        persistAndNotify();
        return true;
    }

    public void clearRelation(RelationType relationType) {
        LinkedHashMap<String, String> map = relationType == RelationType.FRIEND ? friends : enemies;
        if (map.isEmpty()) {
            return;
        }

        map.clear();
        persistAndNotify();
    }

    public boolean isFriend(String name) {
        return active && contains(friends, name);
    }

    public boolean isEnemy(String name) {
        return active && contains(enemies, name);
    }

    public int getCount(RelationType relationType) {
        return getMap(relationType).size();
    }

    public List<PlayerEntry> getEntries(RelationType relationType) {
        LinkedHashMap<String, String> map = getMap(relationType);
        List<PlayerEntry> entries = new ArrayList<PlayerEntry>(map.size());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            entries.add(new PlayerEntry(entry.getKey(), entry.getValue()));
        }
        return entries;
    }

    public boolean isMiddleClickFriends() {
        return middleClickFriends;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }

        this.active = active;
        save();
        syncRelationshipsModuleState();
    }

    public void setMiddleClickFriends(boolean middleClickFriends) {
        if (this.middleClickFriends == middleClickFriends) {
            return;
        }

        this.middleClickFriends = middleClickFriends;
        save();
        syncRelationshipsModuleState();
    }

    public void refreshDisplayName(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return;
        }

        boolean changed = refreshDisplayName(friends, normalized, name);
        changed = refreshDisplayName(enemies, normalized, name) || changed;
        if (changed) {
            persistAndNotify();
        }
    }

    private boolean refreshDisplayName(LinkedHashMap<String, String> map, String normalized, String displayName) {
        if (!map.containsKey(normalized)) {
            return false;
        }

        String next = normalizeDisplayName(displayName, normalized);
        if (next.equals(map.get(normalized))) {
            return false;
        }

        map.put(normalized, next);
        return true;
    }

    private void persistAndNotify() {
        save();
        syncUtilsViews();
        syncRelationshipsModuleState();
        refreshRelationshipsModuleUi();
    }

    private void syncUtilsViews() {
        Utils.friends.clear();
        Utils.friends.addAll(friends.keySet());
        Utils.enemies.clear();
        Utils.enemies.addAll(enemies.keySet());
    }

    private void syncRelationshipsModuleState() {
        if (ModuleManager.relationships != null) {
            ModuleManager.relationships.middleClickFriends.setEnabled(middleClickFriends);
            if (active && !ModuleManager.relationships.isEnabled()) {
                ModuleManager.relationships.enable();
            }
            else if (!active && ModuleManager.relationships.isEnabled()) {
                ModuleManager.relationships.disable();
            }
        }
    }

    private void refreshRelationshipsModuleUi() {
        if (Raven.clickGui == null || ClickGui.categories == null || ModuleManager.relationships == null) {
            return;
        }

        for (CategoryComponent categoryComponent : ClickGui.categories) {
            if (categoryComponent.category != Module.category.client) {
                continue;
            }

            for (ModuleComponent moduleComponent : categoryComponent.modules) {
                if (moduleComponent.mod != ModuleManager.relationships) {
                    continue;
                }

                for (Component component : moduleComponent.settings) {
                    if (component instanceof PlayerListComponent) {
                        ((PlayerListComponent) component).onExternalDataChanged();
                    }
                }
                moduleComponent.updateSettingPositions();
                return;
            }
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        root.addProperty("active", active);
        root.addProperty("middleClickFriends", middleClickFriends);
        root.add("friends", toJsonArray(friends));
        root.add("enemies", toJsonArray(enemies));

        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(root, writer);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadEntries(JsonElement element, LinkedHashMap<String, String> target) {
        if (element == null || !element.isJsonArray()) {
            return;
        }

        JsonArray array = element.getAsJsonArray();
        for (JsonElement entryElement : array) {
            if (entryElement == null || entryElement.isJsonNull()) {
                continue;
            }

            if (entryElement.isJsonPrimitive()) {
                String raw = entryElement.getAsString();
                String normalized = normalize(raw);
                if (!normalized.isEmpty()) {
                    target.put(normalized, normalizeDisplayName(raw, normalized));
                }
                continue;
            }

            if (!entryElement.isJsonObject()) {
                continue;
            }

            JsonObject entryObject = entryElement.getAsJsonObject();
            String key = entryObject.has("key") ? entryObject.get("key").getAsString() : "";
            String displayName = entryObject.has("displayName") ? entryObject.get("displayName").getAsString() : key;
            String normalized = normalize(key);
            if (!normalized.isEmpty()) {
                target.put(normalized, normalizeDisplayName(displayName, normalized));
            }
        }
    }

    private JsonArray toJsonArray(LinkedHashMap<String, String> map) {
        JsonArray array = new JsonArray();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            JsonObject object = new JsonObject();
            object.addProperty("key", entry.getKey());
            object.addProperty("displayName", entry.getValue());
            array.add(object);
        }
        return array;
    }

    private LinkedHashMap<String, String> getMap(RelationType relationType) {
        return relationType == RelationType.FRIEND ? friends : enemies;
    }

    private boolean contains(LinkedHashMap<String, String> map, String name) {
        String normalized = normalize(name);
        return !normalized.isEmpty() && map.containsKey(normalized);
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String displayName, String normalized) {
        String trimmed = displayName == null ? "" : displayName.trim();
        return trimmed.isEmpty() ? normalized : trimmed;
    }
}
