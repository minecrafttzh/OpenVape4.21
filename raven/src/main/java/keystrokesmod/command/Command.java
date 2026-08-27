package keystrokesmod.command;

import keystrokesmod.Raven;
import keystrokesmod.utility.IMinecraftInstance;
import keystrokesmod.utility.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Command implements IMinecraftInstance {
    private final String name;
    private final String[] aliases;

    protected Command(String name, String... aliases) {
        this.name = name;
        this.aliases = aliases == null || aliases.length == 0 ? new String[] {name} : aliases;
    }

    public final String getName() {
        return name;
    }

    public final String[] getAliases() {
        return aliases.clone();
    }

    public final boolean matches(String label) {
        if (label == null) {
            return false;
        }

        if (name.equalsIgnoreCase(label)) {
            return true;
        }

        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(label)) {
                return true;
            }
        }

        return false;
    }

    public abstract void execute(CommandInput input);

    public List<String> suggest(CommandInput input) {
        return Collections.emptyList();
    }

    public int getSuggestionArgumentStart(CommandInput input) {
        return Math.max(0, input.argumentCount() - 1);
    }

    protected final void replyWithHeader(String message) {
        replyRaw(message);
    }

    protected final void reply(String message) {
        replyRaw(message);
    }

    protected final void syntaxError() {
        replyRaw("&7Unknown command.");
    }

    protected final String prefixed(String commandName) {
        return getPrefix() + commandName;
    }

    protected final List<String> filterSuggestions(CommandInput input, List<String> options) {
        String query = input.argumentCount() == 0 ? "" : input.getArgument(input.argumentCount() - 1);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (query == null || query.isEmpty() || option.toLowerCase().startsWith(query.toLowerCase())) {
                matches.add(option);
            }
        }
        return matches;
    }

    protected final List<String> filterSuggestions(CommandInput input, String... options) {
        List<String> values = new ArrayList<>();
        Collections.addAll(values, options);
        return filterSuggestions(input, values);
    }

    private void replyRaw(String message) {
        Utils.sendMessage(formatOutput(message));
    }

    private String formatOutput(String message) {
        return Raven.commandManager != null ? Raven.commandManager.formatOutput(message) : message;
    }

    private String getPrefix() {
        return Raven.commandManager != null ? Raven.commandManager.getPrefix() : ".";
    }
}
