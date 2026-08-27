package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Bind extends Command {
    public Bind() {
        super("bind", "b");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() < 2) {
            syntaxError();
            return;
        }

        String action = input.getArgument(0);
        String moduleQuery;
        String keyToken;

        if ("add".equalsIgnoreCase(action) || "remove".equalsIgnoreCase(action)) {
            if ("remove".equalsIgnoreCase(action)) {
                String removeQuery = input.joinArguments(1);
                Module module = findModule(removeQuery);
                if (module == null && input.argumentCount() > 2) {
                    removeQuery = joinArguments(input.getArguments(), 1, input.argumentCount() - 1);
                    module = findModule(removeQuery);
                }
                if (module == null) {
                    replyWithHeader("&cModule not found.");
                    return;
                }

                module.setBind(0);
                replyWithHeader("&7Unbound &b" + module.getName() + "&7.");
                return;
            }

            if (input.argumentCount() < 3) {
                syntaxError();
                return;
            }

            keyToken = input.getArgument(input.argumentCount() - 1);
            moduleQuery = joinArguments(input.getArguments(), 1, input.argumentCount() - 1);
        } else {
            keyToken = input.getArgument(input.argumentCount() - 1);
            moduleQuery = joinArguments(input.getArguments(), 0, input.argumentCount() - 1);
        }

        Module module = findModule(moduleQuery);
        if (module == null) {
            replyWithHeader("&cModule not found.");
            return;
        }

        int keycode = parseKey(keyToken);
        if (keycode == Keyboard.KEY_NONE) {
            replyWithHeader("&7Invalid key.");
            return;
        }

        module.setBind(keycode);
        replyWithHeader("&7Bound &b" + module.getName() + " &7to &b" + keyName(keycode) + "&7.");
    }

    @Override
    public List<String> suggest(CommandInput input) {
        if (input.argumentCount() <= 1) {
            List<String> suggestions = new ArrayList<>(Arrays.asList("add", "remove"));
            suggestions.addAll(moduleSuggestions(input.argumentCount() == 0 ? "" : input.joinArguments(0)));
            return filterSuggestions(input, suggestions);
        }

        String first = input.getArgument(0);
        if ("add".equalsIgnoreCase(first)) {
            if (input.argumentCount() == 2) {
                return moduleSuggestions(input.getArgument(1));
            }
            if (input.argumentCount() >= 3) {
                return filterSuggestions(input, "R", "F", "G", "V", "NONE");
            }
        } else if ("remove".equalsIgnoreCase(first)) {
            return moduleSuggestions(input.joinArguments(1));
        } else if (input.argumentCount() >= 2) {
            return filterSuggestions(input, "R", "F", "G", "V", "NONE");
        }

        return new ArrayList<>();
    }

    @Override
    public int getSuggestionArgumentStart(CommandInput input) {
        return Math.max(0, input.argumentCount() - 1);
    }

    private Module findModule(String query) {
        String normalizedQuery = normalize(query);
        for (Module module : ModuleManager.modules) {
            if (normalize(module.getName()).equals(normalizedQuery)) {
                return module;
            }
        }
        return null;
    }

    private List<String> moduleSuggestions(String query) {
        List<String> completions = new ArrayList<>();
        String normalized = normalize(query);
        for (Module module : ModuleManager.modules) {
            String moduleName = module.getName();
            if (normalize(moduleName).startsWith(normalized)) {
                completions.add(moduleName);
            }
        }
        return completions;
    }

    private int parseKey(String keyName) {
        if (keyName == null) {
            return Keyboard.KEY_NONE;
        }
        if ("NONE".equalsIgnoreCase(keyName) || "UNBOUND".equalsIgnoreCase(keyName)) {
            return 0;
        }
        return Keyboard.getKeyIndex(keyName.toUpperCase());
    }

    private String keyName(int keycode) {
        return keycode == 0 ? "NONE" : Keyboard.getKeyName(keycode);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace(" ", "").toLowerCase();
    }

    private String joinArguments(String[] values, int startInclusive, int endExclusive) {
        if (values == null || startInclusive >= endExclusive) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = startInclusive; i < endExclusive; i++) {
            if (i > startInclusive) {
                builder.append(' ');
            }
            builder.append(values[i]);
        }
        return builder.toString();
    }
}
