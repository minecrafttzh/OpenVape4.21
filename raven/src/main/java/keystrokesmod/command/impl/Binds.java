package keystrokesmod.command.impl;

import keystrokesmod.Raven;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.utility.profile.Profile;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Binds extends Command {
    public Binds() {
        super("binds");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            Map<String, List<String>> binds = getBindsModulesMap(0);
            int size = getTotalModules(binds);
            replyWithHeader("&b" + size + " &7module" + (size == 1 ? "" : "s") + " have keybinds.");
            for (Map.Entry<String, List<String>> entry : binds.entrySet()) {
                for (String moduleName : entry.getValue()) {
                    replyWithHeader(" &b" + entry.getKey() + " &7" + moduleName);
                }
            }
            return;
        }

        if (input.argumentCount() != 1) {
            syntaxError();
            return;
        }

        int keycode = Keyboard.getKeyIndex(input.getArgument(0).toUpperCase());
        if (keycode == Keyboard.KEY_NONE) {
            replyWithHeader("&7Invalid key.");
            return;
        }

        Map<String, List<String>> binds = getBindsModulesMap(keycode);
        int size = getTotalModules(binds);
        replyWithHeader("&b" + size + " &7module" + (size == 1 ? "" : "s") + " has keybind &b" + input.getArgument(0).toUpperCase() + "&7.");
        for (Map.Entry<String, List<String>> entry : binds.entrySet()) {
            for (String moduleName : entry.getValue()) {
                replyWithHeader(" &b" + entry.getKey() + " &7" + moduleName);
            }
        }
    }

    private Map<String, List<String>> getBindsModulesMap(int keycode) {
        Map<String, List<String>> binds = new HashMap<>();
        for (Module module : ModuleManager.modules) {
            addModuleIfMatches(binds, module, keycode);
        }
        for (Profile profile : Raven.profileManager.profiles) {
            addModuleIfMatches(binds, profile.getModule(), keycode);
        }
        for (Module scriptModule : Raven.scriptManager.scripts.values()) {
            addModuleIfMatches(binds, scriptModule, keycode);
        }
        return binds;
    }

    private void addModuleIfMatches(Map<String, List<String>> bindsMap, Module module, int keycode) {
        if (module.getKeycode() == 0) {
            return;
        }
        if (keycode != 0 && module.getKeycode() != keycode) {
            return;
        }

        String keyName = module.getKeycode() >= 1000 ? "M" + (module.getKeycode() - 1000) : Keyboard.getKeyName(module.getKeycode());
        List<String> moduleNames = bindsMap.get(keyName);
        if (moduleNames == null) {
            moduleNames = new ArrayList<>();
            bindsMap.put(keyName, moduleNames);
        }
        moduleNames.add(module.getName());
    }

    private int getTotalModules(Map<String, List<String>> binds) {
        int total = 0;
        for (List<String> modules : binds.values()) {
            total += modules.size();
        }
        return total;
    }
}
