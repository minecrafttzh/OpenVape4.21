package keystrokesmod.command.impl;

import keystrokesmod.Raven;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.utility.PlayerRelationsManager;
import keystrokesmod.utility.Utils;

import java.util.List;

public class Enemy extends Command {
    public Enemy() {
        super("enemy", "enemy", "e");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            List<PlayerRelationsManager.PlayerEntry> entries = Raven.playerRelationsManager.getEntries(PlayerRelationsManager.RelationType.ENEMY);
            replyWithHeader("&b" + entries.size() + " &7enem" + (entries.size() == 1 ? "y" : "ies") + ".");
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
            int cleared = Raven.playerRelationsManager.getCount(PlayerRelationsManager.RelationType.ENEMY);
            Raven.playerRelationsManager.clearEnemies();
            replyWithHeader("&b" + cleared + " &7enem" + (cleared == 1 ? "y" : "ies") + " cleared.");
            return;
        }

        if (Utils.addEnemy(name)) {
            replyWithHeader("&7Added enemy&7: &b" + name);
        }
        else {
            Utils.removeEnemy(name);
            replyWithHeader("&7Removed enemy&7: &b" + name);
        }
    }
}
