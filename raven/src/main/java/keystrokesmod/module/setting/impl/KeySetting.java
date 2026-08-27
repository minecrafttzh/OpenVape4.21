package keystrokesmod.module.setting.impl;

import com.google.gson.JsonObject;
import keystrokesmod.helper.MouseHelper;
import keystrokesmod.module.setting.Setting;
import keystrokesmod.utility.Utils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class KeySetting extends Setting {
    private int key;
    public GroupSetting group;

    public KeySetting(String name, int key) {
        super(name);
        this.key = key;
    }

    public KeySetting(GroupSetting group, String name, int key) {
        super(name);
        this.group = group;
        this.key = key;
    }

    public int getKey() {
        return this.key;
    }

    public String getName() {
        return super.getName();
    }

    @Override
    public String getProfileKey() {
        return group == null ? getName() : group.getName() + "." + getName();
    }

    public void setKey(int key) {
        this.key = key;
    }

    public boolean isPressed() {
        if (this.getKey() == 0) {
            return false;
        }
        if (this.getKey() >= 1000) {
            return (this.getKey() == 1069 || this.getKey() == 1070) ? MouseHelper.isScrollDown(this.getKey()) : Mouse.isButtonDown(this.getKey() - 1000);
        }
        else {
            return Keyboard.isKeyDown(this.getKey());
        }
    }

    @Override
    public void loadProfile(JsonObject data) {
        String profileKey = getProfileKey();
        String legacyKey = getName();
        String key = data.has(profileKey) ? profileKey : legacyKey;
        if (data.has(key) && data.get(key).isJsonPrimitive()) {
            int keyValue = this.key;
            try {
                keyValue = data.getAsJsonPrimitive(key).getAsInt();
            }
            catch (Exception ignored) {}
            this.key = keyValue;
        }
    }
}
