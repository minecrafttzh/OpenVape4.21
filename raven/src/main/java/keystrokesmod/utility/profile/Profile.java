package keystrokesmod.utility.profile;

public class Profile {
    private final ProfileModule module;
    private final int bind;
    private String profileName;

    public Profile(String profileName, int bind) {
        this.profileName = profileName;
        this.bind = bind;
        this.module = new ProfileModule(this, profileName, bind);
        this.module.ignoreOnSave = true;
    }

    public ProfileModule getModule() {
        return module;
    }

    public int getBind() {
        return bind;
    }

    public String getName() {
        return profileName;
    }

    public void setName(String profileName) {
        this.profileName = profileName;
        this.module.setProfileName(profileName);
    }
}
