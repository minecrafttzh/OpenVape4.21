package keystrokesmod.command.impl;

import keystrokesmod.Raven;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.utility.PlayerRelationsManager;
import keystrokesmod.utility.Utils;

import java.util.List;

public class Friend extends Command {
    public Friend() {
        super("friend", "friend", "f");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            List<PlayerRelationsManager.PlayerEntry> entries = Raven.playerRelationsManager.getEntries(PlayerRelationsManager.RelationType.FRIEND);
            replyWithHeader("&b" + entries.size() + " &7friend" + (entries.size() == 1 ? "" : "s") + ".");
            for (PlayerRelationsManager.PlayerEntry entry : entries) {
                replyWithHeader(" &b" + entry.getDisplayName());
            }
            return;
        }

        if (input.argumentCount() != 1) {
            syntaxError();
            return;
        }

        String name = input.getArgument(0);
        if ("clear".equalsIgnoreCase(name)) {
            int cleared = Raven.playerRelationsManager.getCount(PlayerRelationsManager.RelationType.FRIEND);
            Raven.playerRelationsManager.clearFriends();
            replyWithHeader("&b" + cleared + " &7friend" + (cleared == 1 ? "" : "s") + " cleared.");
            return;
        }

        if (Utils.addFriend(name)) {
            replyWithHeader("&7Added &afriend&7: &b" + name);
        }
        else {
            Utils.removeFriend(name);
            replyWithHeader("&7Removed &afriend&7: &b" + name);
        }
    }
}
