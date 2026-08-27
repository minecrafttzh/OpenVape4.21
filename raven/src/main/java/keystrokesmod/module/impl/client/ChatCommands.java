package keystrokesmod.module.impl.client;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.TextSetting;
import com.google.gson.JsonObject;
import org.lwjgl.input.Keyboard;

import java.util.Locale;

public class ChatCommands extends Module {
    public static final String DEFAULT_PREFIX = ".";

    public final ButtonSetting lowercase;
    public final TextSetting prefix;

    public ChatCommands() {
        super("Chat Commands", category.client);
        this.registerSetting(prefix = new TextSetting("Prefix", DEFAULT_PREFIX, "Type one character...", 1) {
            @Override
            public void loadProfile(JsonObject data) {
                if (data == null) {
                    return;
                }

                String profileKey = getProfileKey();
                if (data.has(profileKey) && data.get(profileKey).isJsonPrimitive()) {
                    String value = data.getAsJsonPrimitive(profileKey).getAsString();
                    if (isValidPrefix(value)) {
                        setText(value);
                    }
                    return;
                }

                if (data.has("Prefix key") && data.get("Prefix key").isJsonPrimitive()) {
                    try {
                        String legacyPrefix = characterForKey(data.getAsJsonPrimitive("Prefix key").getAsInt());
                        if (isValidPrefix(legacyPrefix)) {
                            setText(legacyPrefix);
                        }
                    }
                    catch (Exception ignored) {}
                }
            }
        });
        this.registerSetting(lowercase = new ButtonSetting("Lowercase", false));
    }

    public boolean lowercase() {
        return lowercase.isToggled();
    }

    public String getPrefix() {
        return isValidPrefix(prefix.getText()) ? prefix.getText() : DEFAULT_PREFIX;
    }

    public void setPrefix(String prefix) {
        if (isValidPrefix(prefix)) {
            this.prefix.setText(prefix);
        }
    }

    public static boolean isValidPrefix(String prefix) {
        return prefix != null && prefix.length() == 1 && !Character.isWhitespace(prefix.charAt(0));
    }

    private static String characterForKey(int keyCode) {
        if (keyCode <= Keyboard.KEY_NONE || keyCode >= 256) {
            return null;
        }

        String keyName = Keyboard.getKeyName(keyCode);
        if (keyName == null || keyName.length() != 1 || Character.isWhitespace(keyName.charAt(0))) {
            return null;
        }

        return keyName.toLowerCase(Locale.ROOT);
    }
}
