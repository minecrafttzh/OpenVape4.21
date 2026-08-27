package keystrokesmod.command.impl;

import keystrokesmod.Raven;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.helper.DebugHelper;

public class Debug extends Command {
    public Debug() {
        super("debug");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            Raven.DEBUG = !Raven.DEBUG;
            replyWithHeader("&7Debug " + (Raven.DEBUG ? "&aenabled" : "&cdisabled") + "&7.");
            return;
        }

        if (input.argumentCount() != 1) {
            syntaxError();
            return;
        }

        String option = input.getArgument(0).toLowerCase();
        if ("mixin".equals(option)) {
            DebugHelper.MIXIN = !DebugHelper.MIXIN;
            replyWithHeader("&dMixin &7debug " + (DebugHelper.MIXIN ? "&aenabled" : "&cdisabled") + "&7.");
            return;
        }

        if ("bg".equals(option) || "background".equals(option)) {
            DebugHelper.BACKGROUND = !DebugHelper.BACKGROUND;
            replyWithHeader("&6Background &7debug " + (DebugHelper.BACKGROUND ? "&aenabled" : "&cdisabled") + "&7.");
            return;
        }

        syntaxError();
    }
}
