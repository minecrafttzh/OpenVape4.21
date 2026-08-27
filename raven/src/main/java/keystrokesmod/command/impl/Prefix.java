package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.ChatCommands;

import java.util.Arrays;
import java.util.List;

public class Prefix extends Command {
    private static final List<String> EXAMPLE_PREFIXES = Arrays.asList(".", ",", ";", "/", "-", "=", "[", "]", "\\", "'", "`");

    public Prefix() {
        super("prefix");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            replyWithHeader("&7Current prefix: &b" + ModuleManager.chatCommands.getPrefix());
            return;
        }

        if (input.argumentCount() != 1) {
            syntaxError();
            return;
        }

        String prefix = input.getArgument(0);
        if (!ChatCommands.isValidPrefix(prefix)) {
            replyWithHeader("&7Prefix must be a single non-space character.");
            return;
        }

        ModuleManager.chatCommands.setPrefix(prefix);
        replyWithHeader("&7Chat command prefix set to &b" + ModuleManager.chatCommands.getPrefix() + "&7.");
    }

    @Override
    public List<String> suggest(CommandInput input) {
        return filterSuggestions(input, EXAMPLE_PREFIXES);
    }
}
