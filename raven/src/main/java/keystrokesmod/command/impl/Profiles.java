package keystrokesmod.command.impl;

import keystrokesmod.Raven;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.profile.Profile;

import java.util.ArrayList;
import java.util.List;

public class Profiles extends Command {
    public Profiles() {
        super("profiles", "profile", "p");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            List<Profile> profiles = Raven.profileManager.profiles;
            if (profiles.isEmpty()) {
                replyWithHeader("&7No profiles found");
                return;
            }

            replyWithHeader("&b" + profiles.size() + " &7profile" + (profiles.size() == 1 ? "" : "s") + " loaded.");
            for (Profile profile : profiles) {
                replyWithHeader(" &7" + profile.getName() + (profile == Raven.currentProfile ? " &7(&bcurrent&7)" : ""));
            }
            return;
        }

        String subCommand = input.getArgument(0).toLowerCase();
        switch (subCommand) {
            case "save":
            case "s":
                handleSave(input);
                return;
            case "load":
            case "l":
                handleLoad(input);
                return;
            case "delete":
            case "remove":
            case "r":
                handleDelete(input);
                return;
            case "rename":
                handleRename(input);
                return;
            default:
                syntaxError();
        }
    }

    @Override
    public List<String> suggest(CommandInput input) {
        if (input.argumentCount() == 1) {
            return filterSuggestions(input, "save", "s", "load", "l", "delete", "remove", "r", "rename");
        }

        String subCommand = input.getArgument(0);
        if (isLoadOrDeleteCommand(subCommand)) {
            return suggestProfileNames(input.joinArguments(1));
        }

        if (isRenameCommand(subCommand) && input.argumentCount() == 2) {
            return suggestProfileNames(input.joinArguments(1));
        }

        return new ArrayList<>();
    }

    @Override
    public int getSuggestionArgumentStart(CommandInput input) {
        String subCommand = input.getArgument(0);
        if (isLoadOrDeleteCommand(subCommand) || isRenameCommand(subCommand)) {
            return 1;
        }
        return super.getSuggestionArgumentStart(input);
    }

    private void handleSave(CommandInput input) {
        if (input.argumentCount() < 2) {
            if (Raven.currentProfile != null) {
                Utils.sendMessage("&7Saved profile: &b" + Raven.currentProfile);
                Raven.profileManager.saveProfile(Raven.currentProfile);
            } else {
                syntaxError();
            }
            return;
        }

        String saveName = input.joinArguments(1);
        Profile savedProfile = Raven.profileManager.getProfile(saveName);
        if (savedProfile == null) {
            savedProfile = Raven.profileManager.createProfile(saveName, 0);
            if (savedProfile == null) {
                return;
            }
        } else {
            Raven.profileManager.saveProfile(savedProfile);
        }
        replyWithHeader("&7Saved profile: &b" + saveName);
    }

    private void handleLoad(CommandInput input) {
        if (input.argumentCount() < 2) {
            syntaxError();
            return;
        }

        String loadName = input.joinArguments(1);
        if (Raven.profileManager.getProfile(loadName) == null) {
            replyWithHeader("&b" + loadName + " &7does not exist");
            return;
        }

        Raven.profileManager.loadProfile(loadName);
        replyWithHeader("&7Enabled profile: &b" + loadName);
    }

    private void handleDelete(CommandInput input) {
        if (input.argumentCount() < 2) {
            syntaxError();
            return;
        }

        String deleteName = input.joinArguments(1);
        if (Raven.profileManager.getProfile(deleteName) == null) {
            replyWithHeader("&cProfile &b" + deleteName + " &7does not exist");
            return;
        }

        Raven.profileManager.deleteProfile(deleteName);
        replyWithHeader("&7Removed profile: &b" + deleteName);
        Raven.profileManager.loadProfiles();
    }

    private void handleRename(CommandInput input) {
        if (input.argumentCount() < 3) {
            syntaxError();
            return;
        }

        RenameArguments renameArguments = parseRenameArguments(input.joinArguments(1));
        if (renameArguments == null) {
            replyWithHeader("&7Rename requires an existing profile name followed by the new name.");
            return;
        }

        String oldName = renameArguments.oldName;
        String newName = renameArguments.newName;
        Profile oldProfile = Raven.profileManager.getProfile(oldName);
        if (oldProfile == null) {
            replyWithHeader("&b" + oldName + " &7does not exist");
            return;
        }

        if (Raven.profileManager.getProfile(newName) != null) {
            replyWithHeader("&b" + newName + " &7already exists");
            return;
        }

        if (!Raven.profileManager.renameProfile(oldProfile, newName)) {
            return;
        }

        replyWithHeader("&b" + oldName + " &7renamed to &b" + oldProfile.getName());
    }

    private List<String> suggestProfileNames(String query) {
        String loweredQuery = query == null ? "" : query.toLowerCase();
        List<String> profileNames = new ArrayList<>();
        for (Profile profile : Raven.profileManager.profiles) {
            if (profile.getName().toLowerCase().startsWith(loweredQuery)) {
                profileNames.add(profile.getName());
            }
        }
        return profileNames;
    }

    private boolean isLoadOrDeleteCommand(String subCommand) {
        return "load".equalsIgnoreCase(subCommand)
            || "l".equalsIgnoreCase(subCommand)
            || "delete".equalsIgnoreCase(subCommand)
            || "remove".equalsIgnoreCase(subCommand)
            || "r".equalsIgnoreCase(subCommand);
    }

    private boolean isRenameCommand(String subCommand) {
        return "rename".equalsIgnoreCase(subCommand);
    }

    private RenameArguments parseRenameArguments(String remaining) {
        if (remaining == null) {
            return null;
        }

        String trimmed = remaining.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String bestMatch = null;
        for (Profile profile : Raven.profileManager.profiles) {
            String profileName = profile.getName();
            if (trimmed.length() <= profileName.length()) {
                continue;
            }

            if (!trimmed.regionMatches(true, 0, profileName, 0, profileName.length())) {
                continue;
            }

            if (!Character.isWhitespace(trimmed.charAt(profileName.length()))) {
                continue;
            }

            if (bestMatch == null || profileName.length() > bestMatch.length()) {
                bestMatch = profileName;
            }
        }

        if (bestMatch == null) {
            return null;
        }

        String newName = trimmed.substring(bestMatch.length()).trim();
        if (newName.isEmpty()) {
            return null;
        }

        return new RenameArguments(bestMatch, newName);
    }

    private static final class RenameArguments {
        private final String oldName;
        private final String newName;

        private RenameArguments(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }
    }
}
