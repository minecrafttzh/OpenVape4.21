package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;

public class Toggle extends Command {
    public Toggle() {
        super("toggle", "toggle", "t");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            syntaxError();
            return;
        }

        Module module = findModule(input.joinArguments(0));
        if (module == null) {
            replyWithHeader("&cModule not found.");
            return;
        }

        module.toggle();
        replyWithHeader("&7" + module.getName() + " " + (module.isEnabled() ? "&aenabled" : "&cdisabled") + "&7.");
    }

    @Override
    public List<String> suggest(CommandInput input) {
        List<String> completions = new ArrayList<>();
        String query = input.argumentCount() == 0 ? "" : input.joinArguments(0).toLowerCase();

        for (Module module : ModuleManager.modules) {
            if (module.getName().toLowerCase().startsWith(query)) {
                completions.add(module.getName());
            }
        }
        return completions;
    }

    @Override
    public int getSuggestionArgumentStart(CommandInput input) {
        return 0;
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

    private String normalize(String value) {
        return value == null ? "" : value.replace(" ", "").toLowerCase();
    }
}
