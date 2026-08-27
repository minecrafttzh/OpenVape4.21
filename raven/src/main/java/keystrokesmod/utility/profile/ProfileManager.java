package keystrokesmod.utility.profile;

import com.google.gson.*;
import keystrokesmod.Raven;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.clickgui.components.impl.CategoryComponent;
import keystrokesmod.event.PostProfileLoadEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.impl.client.Relationships;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.impl.player.HideWindow;
import keystrokesmod.module.impl.render.HUD;
import keystrokesmod.module.impl.render.PotionHUD;
import keystrokesmod.module.impl.render.TargetHUD;
import keystrokesmod.module.setting.Setting;
import keystrokesmod.module.setting.impl.BlockListSetting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.impl.StringListSetting;
import keystrokesmod.module.setting.impl.TextSetting;
import keystrokesmod.script.Manager;
import keystrokesmod.utility.IMinecraftInstance;
import keystrokesmod.utility.Utils;
import net.minecraftforge.common.MinecraftForge;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.*;

public class ProfileManager implements IMinecraftInstance {
    private static final String DEFAULT_PROFILE_NAME = "default";
    private static final char[] INVALID_PROFILE_NAME_CHARS = new char[]{'\\', '/', ':', '*', '?', '"', '<', '>', '|'};

    private static final class SavedCategoryState {
        final float x, y;
        final boolean opened;

        SavedCategoryState(float x, float y, boolean opened) {
            this.x = x;
            this.y = y;
            this.opened = opened;
        }
    }

    private static final class RequestedModuleState {
        boolean enabled;
        int keybind;
        Boolean hidden;

        RequestedModuleState(boolean enabled, int keybind) {
            this.enabled = enabled;
            this.keybind = keybind;
        }
    }

    public File directory;
    public List<Profile> profiles = new ArrayList<>();

    public ProfileManager() {
        directory = new File(mc.mcDataDir + File.separator + "keystrokes", "profiles");
        if (!directory.exists()) {
            boolean success = directory.mkdirs();
            if (!success) {
                System.out.println("There was an issue creating profiles directory.");
                return;
            }
        }
        if (getProfileFiles().isEmpty()) {
            saveProfile(new Profile(DEFAULT_PROFILE_NAME, 0));
        }
    }

    public void saveProfile(Profile profile) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("keybind", profile.getModule().getKeycode());
        JsonArray jsonArray = new JsonArray();
        for (Module module : Raven.moduleManager.getModules()) {
            if (module.ignoreOnSave && !shouldSaveModuleStateOnly(module)) {
                continue;
            }
            JsonObject moduleInformation = module.ignoreOnSave ? getModuleStateObject(module) : getJsonObject(module);
            jsonArray.add(moduleInformation);
        }
        if (Raven.scriptManager != null && Raven.scriptManager.scripts != null) {
            for (Module module : Raven.scriptManager.scripts.values()) {
                if (module.ignoreOnSave) {
                    continue;
                }
                JsonObject moduleInformation = getJsonObject(module);
                jsonArray.add(moduleInformation);
            }
        }
        jsonObject.add("modules", jsonArray);
        try (FileWriter fileWriter = new FileWriter(new File(directory, profile.getName() + ".json"))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(jsonObject, fileWriter);
        } catch (Exception e) {
            failedMessage("save", profile.getName());
            e.printStackTrace();
        }
    }

    public Profile createProfile(String requestedName, int bind) {
        String profileName = normalizeProfileName(requestedName);
        String validationError = validateProfileName(profileName, null);
        if (validationError != null) {
            Utils.sendMessage("&c" + validationError);
            return null;
        }

        Profile profile = new Profile(profileName, bind);
        saveProfile(profile);
        profiles.add(profile);
        refreshProfileModules();
        return profile;
    }

    private static JsonObject getJsonObject(Module module) {
        JsonObject moduleInformation = new JsonObject();
        moduleInformation.addProperty("name", (module.moduleCategory() == Module.category.scripts && !(module instanceof Manager)) ?  "sc-" + module.getName() :  module.getName());
        if (module.canBeEnabled) {
            moduleInformation.addProperty("enabled", module.isEnabled());
            moduleInformation.addProperty("hidden", module.isHidden());
            moduleInformation.addProperty("keybind", module.getKeycode());
        }
        if (module instanceof HUD) {
            moduleInformation.addProperty("posX", HUD.posX);
            moduleInformation.addProperty("posY", HUD.posY);
            moduleInformation.addProperty("relPosX", HUD.getRelativePosX());
            moduleInformation.addProperty("relPosY", HUD.getRelativePosY());
        }
        else if (module instanceof TargetHUD) {
            moduleInformation.addProperty("posX", ModuleManager.targetHUD.posX);
            moduleInformation.addProperty("posY", ModuleManager.targetHUD.posY);
        }
        else if (module instanceof PotionHUD) {
            PotionHUD potionHUD = (PotionHUD) module;
            moduleInformation.addProperty("posX", potionHUD.getPosX());
            moduleInformation.addProperty("posY", potionHUD.getPosY());
            moduleInformation.addProperty("relPosX", potionHUD.getRelativePosX());
            moduleInformation.addProperty("relPosY", potionHUD.getRelativePosY());
        }
        else if (module instanceof HideWindow) {
            HideWindow hw = (HideWindow) module;
            moduleInformation.addProperty("posX", hw.getPosX());
            moduleInformation.addProperty("posY", hw.getPosY());
            moduleInformation.addProperty("relPosX", hw.getRelativePosX());
            moduleInformation.addProperty("relPosY", hw.getRelativePosY());
        }
        else if (module instanceof Gui) {
            for (CategoryComponent c : ClickGui.categories) {
                moduleInformation.addProperty(c.category.name(), c.x + "," + c.y + "," + c.opened);
            }
        }
        for (Setting setting : module.getSettings()) {
            if (setting instanceof ButtonSetting && !((ButtonSetting) setting).isMethodButton) {
                moduleInformation.addProperty(setting.getProfileKey(), ((ButtonSetting) setting).isToggled());
            }
            else if (setting instanceof SliderSetting) {
                moduleInformation.addProperty(setting.getProfileKey(), ((SliderSetting) setting).getInput());
            }
            else if (setting instanceof KeySetting) {
                moduleInformation.addProperty(setting.getProfileKey(), ((KeySetting) setting).getKey());
            }
            else if (setting instanceof TextSetting) {
                moduleInformation.addProperty(setting.getProfileKey(), ((TextSetting) setting).getText());
            }
            else if (setting instanceof ColorSetting) {
                ColorSetting cs = (ColorSetting) setting;
                moduleInformation.addProperty(setting.getProfileKey(),
                        cs.getRed() + "," + cs.getGreen() + "," + cs.getBlue() + "," + cs.getAlpha());
            }
            else if (setting instanceof BlockListSetting) {
                moduleInformation.add(setting.getProfileKey(), ((BlockListSetting) setting).toJsonArray());
            }
            else if (setting instanceof StringListSetting) {
                moduleInformation.add(setting.getProfileKey(), ((StringListSetting) setting).toJsonArray());
            }
        }
        return moduleInformation;
    }

    private static JsonObject getModuleStateObject(Module module) {
        JsonObject moduleInformation = new JsonObject();
        moduleInformation.addProperty("name", module.getName());
        if (module.canBeEnabled) {
            moduleInformation.addProperty("enabled", module.isEnabled());
            moduleInformation.addProperty("hidden", module.isHidden());
            moduleInformation.addProperty("keybind", module.getKeycode());
        }
        return moduleInformation;
    }

    private static boolean shouldSaveModuleStateOnly(Module module) {
        return module instanceof Relationships;
    }

    public void loadProfile(String name) {
        Profile existingProfile = getProfile(name);
        String profileName = existingProfile != null ? existingProfile.getName() : normalizeProfileName(name);
        boolean foundProfile = false;
        for (File file : getProfileFiles()) {
            if (!file.exists()) {
                failedMessage("load", profileName);
                System.out.println("Failed to load " + profileName);
                return;
            }
            if (!file.getName().equals(profileName + ".json")) {
                continue;
            }
            foundProfile = true;
            try (FileReader fileReader = new FileReader(file)) {
                JsonParser jsonParser = new JsonParser();
                JsonObject profileJson = jsonParser.parse(fileReader).getAsJsonObject();
                if (profileJson == null) {
                    failedMessage("load", profileName);
                    return;
                }
                JsonArray modules = profileJson.getAsJsonArray("modules");
                if (modules == null) {
                    failedMessage("load", profileName);
                    return;
                }
                List<Module> loadableModules = getLoadableModules();
                Map<Module, RequestedModuleState> requestedModuleStates = createDefaultRequestedModuleStates(loadableModules);
                Map<Module, JsonObject> loadedModuleData = new LinkedHashMap<Module, JsonObject>();
                boolean loadedRelationshipsState = false;
                Map<String, SavedCategoryState> savedGuiCategoryState = new HashMap<>();
                for (JsonElement moduleJson : modules) {
                    JsonObject moduleInformation = moduleJson.getAsJsonObject();
                    String moduleName = moduleInformation.get("name").getAsString();

                    if (moduleName == null || moduleName.isEmpty()) {
                        continue;
                    }

                    Module module = Raven.moduleManager.getModule(moduleName);
                    if (module == null && moduleName.startsWith("sc-") && Raven.scriptManager != null) {
                        for (Module module1 : Raven.scriptManager.scripts.values()) {
                            if (module1.getName().equals(moduleName.substring(3))) {
                                module = module1;
                            }
                        }
                    }

                    if (module == null) {
                        continue;
                    }

                    loadedModuleData.put(module, moduleInformation);

                    if (module instanceof Relationships) {
                        loadedRelationshipsState = true;
                    }

                    if (module.canBeEnabled()) {
                        RequestedModuleState requestedState = requestedModuleStates.get(module);
                        if (requestedState == null) {
                            requestedState = new RequestedModuleState(false, 0);
                            requestedModuleStates.put(module, requestedState);
                        }
                        if (moduleInformation.has("enabled")) {
                            requestedState.enabled = moduleInformation.get("enabled").getAsBoolean();
                        }
                        if (moduleInformation.has("hidden")) {
                            requestedState.hidden = moduleInformation.get("hidden").getAsBoolean();
                        }
                        if (moduleInformation.has("keybind")) {
                            requestedState.keybind = moduleInformation.get("keybind").getAsInt();
                        }
                    }
                }
                if (!loadedRelationshipsState && ModuleManager.relationships != null && Raven.playerRelationsManager != null) {
                    RequestedModuleState relationshipsState = requestedModuleStates.get(ModuleManager.relationships);
                    if (relationshipsState != null) {
                        relationshipsState.enabled = Raven.playerRelationsManager.isActive();
                    }
                }
                for (Module module : loadableModules) {
                    RequestedModuleState requestedState = requestedModuleStates.get(module);
                    if (requestedState == null) {
                        continue;
                    }

                    if (!requestedState.enabled && module.isEnabled()) {
                        module.disable();
                    }
                }

                for (Module module : loadableModules) {
                    RequestedModuleState requestedState = requestedModuleStates.get(module);
                    if (requestedState == null) {
                        continue;
                    }

                    module.setBind(requestedState.keybind);
                    if (requestedState.hidden != null) {
                        module.setHidden(requestedState.hidden);
                    }
                }

                for (Map.Entry<Module, JsonObject> entry : loadedModuleData.entrySet()) {
                    Module module = entry.getKey();
                    JsonObject moduleInformation = entry.getValue();

                    if (module.getName().equals("HUD")) {
                        if (moduleInformation.has("relPosX") && moduleInformation.has("relPosY")) {
                            HUD.setRelativePosition(
                                    moduleInformation.get("relPosX").getAsFloat(),
                                    moduleInformation.get("relPosY").getAsFloat()
                            );
                        }
                        else if (moduleInformation.has("posX") || moduleInformation.has("posY")) {
                            float hudX = moduleInformation.has("posX") ? moduleInformation.get("posX").getAsFloat() : HUD.posX;
                            float hudY = moduleInformation.has("posY") ? moduleInformation.get("posY").getAsFloat() : HUD.posY;
                            HUD.setAbsolutePosition(hudX, hudY);
                        }
                    }
                    else if (module.getName().equals("TargetHUD")) {
                        if (moduleInformation.has("posX")) {
                            int posX = moduleInformation.get("posX").getAsInt();
                            ModuleManager.targetHUD.posX = posX;
                        }
                        if (moduleInformation.has("posY")) {
                            int posY = moduleInformation.get("posY").getAsInt();
                            ModuleManager.targetHUD.posY = posY;
                        }
                    }
                    else if (module.getName().equals("Potion HUD")) {
                        PotionHUD potionHUD = (PotionHUD) module;
                        if (moduleInformation.has("relPosX") && moduleInformation.has("relPosY")) {
                            potionHUD.setRelativePosition(
                                    moduleInformation.get("relPosX").getAsFloat(),
                                    moduleInformation.get("relPosY").getAsFloat()
                            );
                        }
                        else if (moduleInformation.has("posX") || moduleInformation.has("posY")) {
                            float posX = moduleInformation.has("posX") ? moduleInformation.get("posX").getAsFloat() : potionHUD.getPosX();
                            float posY = moduleInformation.has("posY") ? moduleInformation.get("posY").getAsFloat() : potionHUD.getPosY();
                            potionHUD.setAbsolutePosition(posX, posY);
                        }
                    }
                    else if (module.getName().equals("Hide Window")) {
                        HideWindow hw = (HideWindow) module;
                        if (moduleInformation.has("relPosX") && moduleInformation.has("relPosY")) {
                            hw.setRelativePosition(
                                    moduleInformation.get("relPosX").getAsFloat(),
                                    moduleInformation.get("relPosY").getAsFloat()
                            );
                        }
                        else if (moduleInformation.has("posX") || moduleInformation.has("posY")) {
                            float posX = moduleInformation.has("posX") ? moduleInformation.get("posX").getAsFloat() : hw.getPosX();
                            float posY = moduleInformation.has("posY") ? moduleInformation.get("posY").getAsFloat() : hw.getPosY();
                            hw.setAbsolutePosition(posX, posY);
                        }
                    }
                    else if (module.getName().equals("Gui")) {
                        for (Map.Entry<String, JsonElement> setting : moduleInformation.entrySet()) {
                            String settingName = setting.getKey();
                            if (!Module.categoriesString.contains(settingName)) {
                                continue;
                            }
                            String element = setting.getValue().getAsString();
                            String[] statesStr = element.split(",");

                            float posX = Float.parseFloat(statesStr[0]);
                            float posY = Float.parseFloat(statesStr[1]);
                            boolean opened = statesStr.length > 2 && Boolean.parseBoolean(statesStr[2]);
                            savedGuiCategoryState.put(settingName, new SavedCategoryState(posX, posY, opened));
                        }
                    }

                    for (Setting setting : module.getSettings()) {
                        setting.loadProfile(moduleInformation);
                    }
                }

                for (Module module : loadableModules) {
                    RequestedModuleState requestedState = requestedModuleStates.get(module);
                    if (requestedState == null) {
                        continue;
                    }

                    if (requestedState.enabled && !module.isEnabled()) {
                        module.enable();
                    }
                }

                Raven.currentProfile = getProfile(profileName);

                boolean loadGuiPositions = Settings.loadGuiPositions.isToggled();
                Raven.clickGui.refreshAfterProfileLoad();
                if (loadGuiPositions) {
                    for (CategoryComponent c : ClickGui.categories) {
                        SavedCategoryState state = savedGuiCategoryState.get(c.category.name());
                        if (state != null) {
                            c.applySavedState(state.x, state.y, state.opened, true);
                        }
                    }
                }
                MinecraftForge.EVENT_BUS.post(new PostProfileLoadEvent(Raven.currentProfile.getName()));
                return;
            }
            catch (Exception e) {
                failedMessage("load", profileName);
                e.printStackTrace();
                return;
            }
        }
        if (!foundProfile) {
            failedMessage("load", profileName);
        }
    }

    private List<Module> getLoadableModules() {
        List<Module> loadableModules = new ArrayList<Module>(Raven.getModuleManager().getModules());
        if (Raven.scriptManager != null && Raven.scriptManager.scripts != null) {
            loadableModules.addAll(Raven.scriptManager.scripts.values());
        }
        return loadableModules;
    }

    private Map<Module, RequestedModuleState> createDefaultRequestedModuleStates(List<Module> modules) {
        Map<Module, RequestedModuleState> requestedModuleStates = new HashMap<Module, RequestedModuleState>();
        for (Module module : modules) {
            if (module.canBeEnabled()) {
                requestedModuleStates.put(module, new RequestedModuleState(false, 0));
            }
        }
        return requestedModuleStates;
    }

    public boolean deleteProfile(String name) {
        Profile removedProfile = getProfile(name);
        String profileName = removedProfile != null ? removedProfile.getName() : normalizeProfileName(name);
        File profileFile = new File(directory, profileName + ".json");

        if (profileFile.exists() && !(profileFile.delete() || !profileFile.exists())) {
            return false;
        }

        boolean wasCurrentProfile = removedProfile != null && Raven.currentProfile == removedProfile;
        if (removedProfile != null) {
            profiles.remove(removedProfile);
        }
        if (wasCurrentProfile) {
            Raven.currentProfile = null;
        }

        if (profiles.isEmpty()) {
            Profile fallbackProfile = createProfile(DEFAULT_PROFILE_NAME, 0);
            if (fallbackProfile == null) {
                return false;
            }
        }
        else {
            refreshProfileModules();
            if (wasCurrentProfile) {
                Profile fallbackProfile = getDefaultOrFirstProfile();
                if (fallbackProfile != null) {
                    loadProfile(fallbackProfile.getName());
                }
            }
        }
        return removedProfile != null;
    }

    public void loadProfiles() {
        String currentProfileName = Raven.currentProfile != null ? Raven.currentProfile.getName() : null;
        boolean currentProfileSaved = Raven.currentProfile == null || Raven.currentProfile.getModule().saved;
        profiles.clear();
        if (!directory.exists() && !directory.mkdirs()) {
            Utils.sendMessage("&cFailed to load profiles.");
            return;
        }

        List<File> profileFiles = getProfileFiles();
        if (profileFiles.isEmpty()) {
            saveProfile(new Profile(DEFAULT_PROFILE_NAME, 0));
            profileFiles = getProfileFiles();
        }

        for (File file : profileFiles) {
            try (FileReader fileReader = new FileReader(file)) {
                JsonParser jsonParser = new JsonParser();
                JsonObject profileJson = jsonParser.parse(fileReader).getAsJsonObject();
                String profileName = file.getName().replace(".json", "");

                if (profileJson == null) {
                    failedMessage("load", profileName);
                    return;
                }

                int keybind = 0;

                if (profileJson.has("keybind")) {
                    keybind = profileJson.get("keybind").getAsInt();
                }

                Profile profile = new Profile(profileName, keybind);
                profiles.add(profile);
            } catch (Exception e) {
                Utils.sendMessage("&cFailed to load profiles.");
                e.printStackTrace();
            }
        }

        if (currentProfileName != null) {
            Raven.currentProfile = getProfile(currentProfileName);
            if (Raven.currentProfile != null) {
                Raven.currentProfile.getModule().saved = currentProfileSaved;
            }
        }
        refreshProfileModules();
        Utils.sendMessage("&b" + profileFiles.size() + " &7profiles loaded.");
    }

    public List<File> getProfileFiles() {
        List<File> profileFiles = new ArrayList<>();
        if (directory.exists()) {
            File[] files = directory.listFiles();
            for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(".json")) {
                    continue;
                }
                profileFiles.add(file);
            }
        }
        return profileFiles;
    }

    public Profile getProfile(String name) {
        for (Profile profile : profiles) {
            if (profile.getName().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        return null;
    }

    public void loadInitialProfile() {
        Raven.currentProfile = null;
    }

    public void failedMessage(String reason, String name) {
        Utils.sendMessage("&cFailed to " + reason + ": &b" + name);
    }

    public boolean renameProfile(Profile profile, String requestedName) {
        if (profile == null) {
            Utils.sendMessage("&cFailed to rename profile.");
            return false;
        }

        String oldName = profile.getName();
        String newName = normalizeProfileName(requestedName);
        String validationError = validateProfileName(newName, oldName);
        if (validationError != null) {
            Utils.sendMessage("&c" + validationError);
            return false;
        }

        if (oldName.equals(newName)) {
            profile.setName(newName);
            return true;
        }

        File oldFile = new File(directory, oldName + ".json");
        File newFile = new File(directory, newName + ".json");

        if (!oldFile.exists()) {
            failedMessage("rename", oldName);
            return false;
        }

        try {
            Files.move(oldFile.toPath(), newFile.toPath());
            profile.setName(newName);
            return true;
        }
        catch (Exception e) {
            failedMessage("rename", oldName);
            e.printStackTrace();
            return false;
        }
    }

    private void refreshProfileModules() {
        if (Raven.clickGui == null || Raven.clickGui.categories == null) {
            return;
        }
        for (CategoryComponent categoryComponent : Raven.clickGui.categories) {
            if (categoryComponent.category == Module.category.profiles) {
                categoryComponent.reloadModules(true);
                break;
            }
        }
    }

    private Profile getDefaultOrFirstProfile() {
        Profile defaultProfile = getProfile(DEFAULT_PROFILE_NAME);
        if (defaultProfile != null) {
            return defaultProfile;
        }
        return profiles.isEmpty() ? null : profiles.get(0);
    }

    private String validateProfileName(String profileName, String currentName) {
        if (profileName.isEmpty()) {
            return "Profile name cannot be empty.";
        }
        if (profileName.endsWith(".") || profileName.endsWith(" ")) {
            return "Profile name cannot end with a space or period.";
        }
        for (char c : profileName.toCharArray()) {
            if (c < 32 || containsInvalidProfileChar(c)) {
                return "Profile name contains invalid characters.";
            }
        }
        for (Profile profile : profiles) {
            if (profile.getName().equalsIgnoreCase(profileName) && (currentName == null || !profile.getName().equalsIgnoreCase(currentName))) {
                return "Profile already exists: " + profileName;
            }
        }
        return null;
    }

    private boolean containsInvalidProfileChar(char c) {
        for (char invalidChar : INVALID_PROFILE_NAME_CHARS) {
            if (invalidChar == c) {
                return true;
            }
        }
        return false;
    }

    private String normalizeProfileName(String name) {
        return name == null ? "" : name.trim();
    }
}
