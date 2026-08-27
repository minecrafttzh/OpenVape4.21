package keystrokesmod.command;

import keystrokesmod.command.impl.Binds;
import keystrokesmod.command.impl.Bind;
import keystrokesmod.command.impl.Cname;
import keystrokesmod.command.impl.Debug;
import keystrokesmod.command.impl.Enemy;
import keystrokesmod.command.impl.Friend;
import keystrokesmod.command.impl.HideAll;
import keystrokesmod.command.impl.Name;
import keystrokesmod.command.impl.Ping;
import keystrokesmod.command.impl.Prefix;
import keystrokesmod.command.impl.Profiles;
import keystrokesmod.command.impl.ShowAll;
import keystrokesmod.command.impl.Toggle;
import keystrokesmod.command.impl.Track;
import keystrokesmod.command.impl.Unbind;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.ChatCommands;
import keystrokesmod.utility.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CommandManager {
    private final List<Command> commands = new ArrayList<>();

    public final Track trackCommand;

    public CommandManager() {
        register(new Ping());
        register(new Name());
        register(new Toggle());
        register(new Bind());
        register(new Unbind());
        register(new Binds());
        register(new Cname());
        register(new Debug());
        register(new Friend());
        register(new Enemy());
        register(new Prefix());
        register(trackCommand = new Track());
        register(new Profiles());
        register(new ShowAll());
        register(new HideAll());
    }

    public boolean handleChatMessage(String message) {
        if (!isCommand(message)) {
            return false;
        }

        ParsedCommand parsed = parse(message, false);
        if (parsed == null) {
            return true;
        }

        if (parsed.command == null) {
            Utils.sendMessage(formatOutput("&cUnknown command. Use tab to browse command suggestions."));
            return true;
        }

        parsed.command.execute(parsed.input);
        return true;
    }

    public String[] getAutoComplete(String message) {
        if (!isCommand(message)) {
            return new String[0];
        }

        ParsedCommand parsed = parse(message, true);
        if (parsed == null) {
            return prefixSuggestions("");
        }

        if (parsed.command == null) {
            return prefixSuggestions(parsed.input.getLabel());
        }

        String[] labelSuggestions = getCommandLabelSuggestions(message, parsed);
        if (labelSuggestions.length > 0) {
            return labelSuggestions;
        }

        return buildAutoCompleteSuggestions(parsed, parsed.command.suggest(parsed.input));
    }

    public String[] getPreviewSuggestions(String message) {
        if (!isCommand(message)) {
            return new String[0];
        }

        ParsedCommand parsed = parse(message, true);
        if (parsed == null) {
            return prefixSuggestions("");
        }

        if (parsed.command == null) {
            return prefixSuggestions(parsed.input.getLabel());
        }

        String[] labelSuggestions = getCommandLabelSuggestions(message, parsed);
        if (labelSuggestions.length > 0) {
            return labelSuggestions;
        }

        List<String> suggestions = parsed.command.suggest(parsed.input);
        if (suggestions == null || suggestions.isEmpty()) {
            return new String[0];
        }

        Set<String> preview = new LinkedHashSet<>();
        for (String suggestion : suggestions) {
            if (suggestion != null && !suggestion.isEmpty()) {
                preview.add(suggestion);
            }
        }
        return preview.toArray(new String[0]);
    }

    private String[] getCommandLabelSuggestions(String message, ParsedCommand parsed) {
        if (parsed == null || parsed.input.argumentCount() != 0 || endsWithWhitespace(message)) {
            return new String[0];
        }

        String[] suggestions = prefixSuggestions(parsed.input.getLabel());
        for (String suggestion : suggestions) {
            if (!suggestion.equalsIgnoreCase(message)) {
                return suggestions;
            }
        }

        return new String[0];
    }

    public boolean isEnabled() {
        ChatCommands settings = getSettings();
        return settings != null && settings.isEnabled();
    }

    public boolean isCommand(String message) {
        return message != null && isEnabled() && message.startsWith(getPrefix());
    }

    public String getPrefix() {
        ChatCommands settings = getSettings();
        return settings != null ? settings.getPrefix() : ".";
    }

    public String formatOutput(String message) {
        ChatCommands settings = getSettings();
        if (settings != null && settings.lowercase()) {
            return message.toLowerCase();
        }
        return message;
    }

    private void register(Command command) {
        commands.add(command);
    }

    private ChatCommands getSettings() {
        return ModuleManager.chatCommands;
    }

    private ParsedCommand parse(String message, boolean preserveTrailingArgument) {
        String body = stripPrefix(message);
        if (body == null) {
            return null;
        }

        String normalizedBody = trimLeadingWhitespace(body);
        if (normalizedBody.isEmpty()) {
            return null;
        }

        String[] tokens = tokenize(normalizedBody, preserveTrailingArgument && endsWithWhitespace(body));
        if (tokens.length == 0) {
            return null;
        }

        String label = tokens[0];
        String[] arguments = tokens.length > 1 ? Arrays.copyOfRange(tokens, 1, tokens.length) : new String[0];
        return new ParsedCommand(findCommand(label), new CommandInput(message, label, arguments));
    }

    private String[] prefixSuggestions(String query) {
        Set<String> suggestions = new LinkedHashSet<>();
        String loweredQuery = query == null ? "" : query.toLowerCase();

        for (Command command : commands) {
            if (command.getName().toLowerCase().startsWith(loweredQuery)) {
                suggestions.add(getPrefix() + command.getName());
            }

            for (String alias : command.getAliases()) {
                if (alias.toLowerCase().startsWith(loweredQuery)) {
                    suggestions.add(getPrefix() + alias);
                }
            }
        }

        return suggestions.toArray(new String[0]);
    }

    private Command findCommand(String label) {
        for (Command command : commands) {
            if (command.matches(label)) {
                return command;
            }
        }
        return null;
    }

    private String[] buildAutoCompleteSuggestions(ParsedCommand parsed, List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return new String[0];
        }

        Set<String> normalized = new LinkedHashSet<>();
        int replacementStart = Math.max(0, Math.min(parsed.input.argumentCount(), parsed.command.getSuggestionArgumentStart(parsed.input)));

        for (String suggestion : suggestions) {
            if (suggestion == null || suggestion.isEmpty()) {
                continue;
            }

            StringBuilder completed = new StringBuilder(getPrefix()).append(parsed.input.getLabel());
            for (int index = 0; index < replacementStart; index++) {
                completed.append(' ').append(parsed.input.getArgument(index));
            }

            if (completed.length() > 0) {
                completed.append(' ');
            }
            completed.append(suggestion);
            normalized.add(completed.toString());
        }

        return normalized.toArray(new String[0]);
    }

    private String stripPrefix(String message) {
        if (!isCommand(message)) {
            return null;
        }
        return message.substring(getPrefix().length());
    }

    private static String trimLeadingWhitespace(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return value.substring(index);
    }

    private static boolean endsWithWhitespace(String value) {
        return !value.isEmpty() && Character.isWhitespace(value.charAt(value.length() - 1));
    }

    private static String[] tokenize(String value, boolean preserveTrailingEmptyArgument) {
        List<String> tokens = new ArrayList<>();
        int length = value.length();
        int index = 0;

        while (index < length) {
            while (index < length && Character.isWhitespace(value.charAt(index))) {
                index++;
            }

            if (index >= length) {
                break;
            }

            int start = index;
            while (index < length && !Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            tokens.add(value.substring(start, index));
        }

        if (preserveTrailingEmptyArgument) {
            tokens.add("");
        }

        return tokens.toArray(new String[0]);
    }

    private static final class ParsedCommand {
        private final Command command;
        private final CommandInput input;

        private ParsedCommand(Command command, CommandInput input) {
            this.command = command;
            this.input = input;
        }
    }
}
