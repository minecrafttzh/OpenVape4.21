package keystrokesmod.utility.profile;

import keystrokesmod.Raven;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.TextSetting;
import keystrokesmod.utility.Utils;

import java.awt.*;
import java.io.IOException;

public class Manager extends Module {
    private final TextSetting createProfileName;
    private ButtonSetting loadProfiles, openFolder, createProfile;

    public Manager() {
        super("Manager", category.profiles);
        this.registerSetting(createProfileName = new TextSetting("Profile name", "", "Type a profile name...", 32, this::createProfile));
        this.registerSetting(createProfile = new ButtonSetting("Create profile", () -> {
            createProfile();
        }));
        this.registerSetting(loadProfiles = new ButtonSetting("Load profiles", () -> {
            if (Utils.nullCheck() && Raven.profileManager != null) {
                Raven.profileManager.loadProfiles();
            }
        }));
        this.registerSetting(openFolder = new ButtonSetting("Open folder", () -> {
            try {
                Desktop.getDesktop().open(Raven.profileManager.directory);
            }
            catch (IOException ex) {
                Raven.profileManager.directory.mkdirs();
                Utils.sendMessage("&cError locating folder, recreated.");
            }
        }));
        ignoreOnSave = true;
        canBeEnabled = false;
    }

    private void createProfile() {
        if (!Utils.nullCheck() || Raven.profileManager == null) {
            return;
        }

        Profile profile = Raven.profileManager.createProfile(createProfileName.getText(), 0);
        if (profile != null) {
            createProfileName.setText("");
            Utils.sendMessage("&7Created profile: &b" + profile.getName());
        }
    }
}
