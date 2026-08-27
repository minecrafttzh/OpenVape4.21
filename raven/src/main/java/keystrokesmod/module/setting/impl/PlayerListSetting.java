package keystrokesmod.module.setting.impl;

import com.google.gson.JsonObject;
import keystrokesmod.Raven;
import keystrokesmod.module.setting.Setting;
import keystrokesmod.utility.PlayerRelationsManager;

import java.util.Collections;
import java.util.List;

public class PlayerListSetting extends Setting {
    private final PlayerRelationsManager.RelationType relationType;
    private final String placeholder;
    private final int maxLength;
    public GroupSetting group;

    public PlayerListSetting(GroupSetting group, String name, PlayerRelationsManager.RelationType relationType, String placeholder, int maxLength) {
        super(name);
        this.group = group;
        this.relationType = relationType;
        this.placeholder = placeholder == null ? "" : placeholder;
        this.maxLength = Math.max(1, maxLength);
    }

    public PlayerRelationsManager.RelationType getRelationType() {
        return relationType;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public boolean addPlayer(String name) {
        return Raven.playerRelationsManager != null && Raven.playerRelationsManager.addRelation(relationType, name);
    }

    public boolean removePlayer(String name) {
        return Raven.playerRelationsManager != null && Raven.playerRelationsManager.removeRelation(relationType, name);
    }

    public void clearPlayers() {
        if (Raven.playerRelationsManager != null) {
            Raven.playerRelationsManager.clearRelation(relationType);
        }
    }

    public List<PlayerRelationsManager.PlayerEntry> getEntries() {
        if (Raven.playerRelationsManager == null) {
            return Collections.emptyList();
        }
        return Raven.playerRelationsManager.getEntries(relationType);
    }

    @Override
    public String getProfileKey() {
        return group == null ? getName() : group.getName() + "." + getName();
    }

    @Override
    public void loadProfile(JsonObject data) {
    }
}
