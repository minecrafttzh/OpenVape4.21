package keystrokesmod.utility;

import keystrokesmod.Raven;
import keystrokesmod.helper.PingHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.impl.combat.Velocity;
import keystrokesmod.module.impl.minigames.DuelsStats;
import keystrokesmod.module.impl.movement.BHop;
import keystrokesmod.module.impl.movement.Fly;
import keystrokesmod.module.impl.movement.Speed;
import keystrokesmod.module.impl.other.FakeChat;
import keystrokesmod.module.impl.other.NameHider;
import keystrokesmod.utility.PlayerRelationsManager;
import keystrokesmod.utility.profile.Profile;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandHandler implements IMinecraftInstance {
    private static final int GL_SCISSOR_TEST = 3089;
    private static final int COMMAND_PANEL_X_OFFSET = 195;
    private static final int COMMAND_PANEL_Y_OFFSET = 130;
    private static final int COMMAND_PANEL_START_Y = 345;
    private static final int COMMAND_PANEL_HEIGHT = 230;
    private static final int MIN_SCISSOR_WIDTH = 2;

    private static boolean hasShownWelcome = true;

    private static final List<Integer> backgroundColors = Arrays.asList(
        new Color(170, 107, 148, 50).getRGB(),
        new Color(122, 158, 134, 50).getRGB(),
        new Color(16, 16, 16, 50).getRGB(),
        new Color(64, 114, 148, 50).getRGB()
    );

    private static int currentBackgroundColor = 0;
    private static int lastBackgroundColorIndex = -1;
    public static List<String> responseLines = new ArrayList<>();

    private static final String INVALID_SYNTAX = "&cInvalid syntax.";
    private static final String INVALID_COMMAND = "&cInvalid command.";

    private static boolean isArgsValid(String[] args, int requiredCount) {
        return args != null && args.length == requiredCount;
    }

    private static void handleSetKeyCommand(String[] args) {
        if (!isArgsValid(args, 2)) {
            print(INVALID_SYNTAX, 1);
            return;
        }

        print("Setting...", 1);
        String apiKey = args[1];
        Raven.getScheduledExecutor().execute(() -> {
            if (NetworkUtils.isHypixelKeyValid(apiKey)) {
                NetworkUtils.API_KEY = apiKey;
                print("&a" + "success!", 0);
            }
            else {
                print("&cInvalid key.", 0);
            }
        });
    }

    private static void handleNickCommand(String[] args) {
        if (!isArgsValid(args, 2)) {
            print(INVALID_SYNTAX, 1);
            return;
        }

        if (args[1].equals("reset")) {
            print("&aNick reset.", 1);
            return;
        }

        DuelsStats.nick = args[1];
        print("&aNick has been set to:", 1);
        print("\"" + DuelsStats.nick + "\"", 0);
    }

    private static void handleNameHiderCommand(String[] args) {
        if (args == null || args.length < 2) {
            print(INVALID_SYNTAX, 1);
            return;
        }

        NameHider.setFakeName(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        print("&aName has been set to:", 1);
        print("\"" + NameHider.fakeName + "\"", 0);
    }

    private static void handleFakeChatCommand(String contents) {
        String message = contents.replaceFirst(FakeChat.command, "").substring(1);
        if (message.isEmpty() || message.equals("\\n")) {
            print(FakeChat.c4, 1);
            return;
        }

        FakeChat.msg = message;
        print("&aMessage set!", 1);
    }

    private static void handleDuelsCommand(String[] args) {
        if (!isArgsValid(args, 2)) {
            print(INVALID_SYNTAX, 1);
            return;
        }

        if (NetworkUtils.API_KEY.isEmpty()) {
            print("&cAPI Key is empty!", 1);
            print("Use \"setkey [api_key]\".", 0);
            return;
        }

        String playerName = args[1];
        print("Retrieving data...", 1);
        Raven.getScheduledExecutor().execute(() -> {
            int[] stats = ProfileUtils.getHypixelStats(playerName, ProfileUtils.DM.OVERALL);
            if (stats != null) {
                if (stats[0] == -1) {
                    String displayName = playerName.length() > 16 ? playerName.substring(0, 16) + "..." : playerName;
                    print("&c" + displayName + " does not exist!", 0);
                }
                else {
                    double winLossRatio = stats[1] != 0 ? Utils.round((double) stats[0] / (double) stats[1], 2) : (double) stats[0];
                    print("&e" + playerName + " stats:", 1);
                    print("Wins: " + stats[0], 0);
                    print("Losses: " + stats[1], 0);
                    print("WLR: " + winLossRatio, 0);
                    print("Winstreak: " + stats[2], 0);
                    print("Threat: " + DuelsStats.gtl(stats[0], stats[1], winLossRatio, stats[2]).substring(2), 0);
                }
            }
            else {
                print("&cThere was an error.", 0);
            }
        });
    }

    private static void handleSetSpeedCommand(String[] args) {
        if (args == null || args.length != 3) {
            print(INVALID_SYNTAX, 1);
            return;
        }

        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (Exception e) {
            print("&cInvalid value. [0 - 100]", 1);
            return;
        }

        if (value > 100 || value < 0) {
            print("&cInvalid value. [0 - 100]", 1);
            return;
        }

        switch (args[1]) {
            case "fly":
                Fly.horizontalSpeed.setValueRawWithEvent(value);
                break;
            case "bhop":
                BHop.speedSetting.setValueRawWithEvent(value);
                break;
            case "speed":
                Speed.multiplier.setValueRawWithEvent(value);
                break;
            default:
                print(INVALID_SYNTAX, 1);
                return;
        }
        print("&aSet speed to ", 1);
        print(args[2], 0);
    }

    private static void handleSetVelocityCommand(String[] args) {
        if (args == null || args.length != 3) {
            print(INVALID_SYNTAX, 1);
            return;
        }

        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (Exception e) {
            print("&cInvalid value. [-100 - 300]", 1);
            return;
        }

        if (value > 300 || value < -100) {
            print("&cInvalid value. [-100 - 300]", 1);
            return;
        }

        switch (args[1]) {
            case "horizontal":
            case "h":
                Velocity.horizontal.setValueRawWithEvent(value);
                break;
            case "vertical":
            case "v":
                Velocity.vertical.setValueRawWithEvent(value);
                break;
            default:
                print(INVALID_SYNTAX, 1);
                return;
        }

        print("&aSet " + args[1] + " velocity to ", 1);
        print(args[2], 0);
    }

    private static void handleHideCommand(String[] args) {
        if (args == null || args.length != 2) {
            print(INVALID_SYNTAX, 1);
            return;
        }

        for (Module module : Raven.getModuleManager().getModules()) {
            String moduleName = module.getName().toLowerCase().replace(" ", "");
            if (moduleName.equals(args[1].toLowerCase())) {
                module.setHidden(true);
                print("&a" + module.getName() + " is now hidden in HUD", 1);
            }
        }
    }

    private static void handleShowCommand(String[] args) {
        if (!isArgsValid(args, 2)) {
            print(INVALID_SYNTAX, 1);
            return;
        }

        for (Module module : Raven.getModuleManager().getModules()) {
            String moduleName = module.getName().toLowerCase().replace(" ", "");
            if (moduleName.equals(args[1].toLowerCase())) {
                module.setHidden(false);
                print("&a" + module.getName() + " is now visible in HUD", 1);
            }
        }
    }

    private static void handleFriendCommand(String[] args) {
        if (!isArgsValid(args, 2)) {
            print(INVALID_SYNTAX, 1);
            return;
        }

        if (args[1].equals("clear")) {
            Raven.playerRelationsManager.clearFriends();
            print("&aFriends cleared.", 1);
            return;
        }

        boolean added = Utils.addFriend(args[1]);
        if (added) {
            print("&aAdded friend: " + args[1], 1);
        }
        else {
            print("&aRemoved friend: " + args[1], 1);
            Utils.removeFriend(args[1]);
        }
    }

    private static void handleEnemyCommand(String[] args) {
        if (!isArgsValid(args, 2)) {
            print(INVALID_SYNTAX, 1);
            return;
        }

        if (args[1].equals("clear")) {
            Raven.playerRelationsManager.clearEnemies();
            print("&aEnemies cleared.", 1);
            return;
        }

        boolean added = Utils.addEnemy(args[1]);
        if (!added) {
            print("&aRemoved enemy: " + args[1], 1);
            Utils.removeEnemy(args[1]);
        }
        else {
            print("&aAdded enemy: " + args[1], 1);
        }
    }

    private static void handleProfilesCommand(String[] args, boolean hasArgs) {
        if (!hasArgs) {
            print("&aAvailable profiles:", 1);
            if (Raven.profileManager.profiles.isEmpty()) {
                print("None", 0);
                return;
            }
            for (int i = 0; i < Raven.profileManager.profiles.size(); ++i) {
                print(i + 1 + ". " + Raven.profileManager.profiles.get(i).getName(), 0);
            }
        }
        else if (args != null && args.length > 1) {
            switch (args[1]) {
                case "save":
                case "s": {
                    if (args.length != 3) {
                        print(INVALID_SYNTAX, 1);
                        return;
                    }
                    String profileName = args[2];
                    if (profileName.length() < 2 || profileName.length() > 10) {
                        print("&cInvalid name.", 1);
                        return;
                    }
                    Raven.profileManager.saveProfile(new Profile(profileName, 0));
                    print("&aSaved profile:", 1);
                    print(profileName, 0);
                    Raven.profileManager.loadProfiles();
                    break;
                }
                case "load":
                case "l": {
                    if (args.length != 3) {
                        print(INVALID_SYNTAX, 1);
                        return;
                    }
                    String profileName = args[2];
                    for (Profile profile : Raven.profileManager.profiles) {
                        if (profile.getName().equals(profileName)) {
                            Raven.profileManager.loadProfile(profile.getName());
                            print("&aLoaded profile:", 1);
                            print(profileName, 0);
                            if (Settings.sendMessage.isToggled()) {
                                Utils.sendMessage("&7Enabled profile: &b" + profileName);
                            }
                            return;
                        }
                    }
                    print("&cInvalid profile.", 1);
                    break;
                }
                case "remove":
                case "r": {
                    if (args.length != 3) {
                        print(INVALID_SYNTAX, 1);
                        return;
                    }
                    String profileName = args[2];
                    for (Profile profile : Raven.profileManager.profiles) {
                        if (profile.getName().equals(profileName)) {
                            Raven.profileManager.deleteProfile(profile.getName());
                            print("&aRemoved profile:", 1);
                            print(profileName, 0);
                            Raven.profileManager.loadProfiles();
                            return;
                        }
                    }
                    print("&cInvalid profile.", 1);
                    break;
                }
            }
        }
    }

    public static void runCommand(String contents) {
        if (contents.isEmpty()) {
            return;
        }
        
        String command = contents.toLowerCase();
        boolean hasArgs = contents.contains(" ");
        String[] args = hasArgs ? contents.split(" ") : null;
        String commandName = args != null && args.length > 0 ? args[0] : command;
        
        switch (commandName) {
            case "setkey":
                handleSetKeyCommand(args);
                break;
            case "nick":
                handleNickCommand(args);
                break;
            case "namehider":
                handleNameHiderCommand(args);
                break;
            case "fakechat":
                handleFakeChatCommand(contents);
                break;
            case "duels":
                handleDuelsCommand(args);
                break;
            case "setspeed":
                handleSetSpeedCommand(args);
                break;
            case "setvelocity":
                handleSetVelocityCommand(args);
                break;
            case "ping":
                PingHelper.checkPing(false);
                break;
            case "clear":
                responseLines.clear();
                break;
            case "hide":
                handleHideCommand(args);
                break;
            case "show":
                handleShowCommand(args);
                break;
            case "friend":
            case "f":
                handleFriendCommand(args);
                break;
            case "enemy":
            case "e":
                handleEnemyCommand(args);
                break;
            case "debug":
                Raven.DEBUG = !Raven.DEBUG;
                print("Debug " + (Raven.DEBUG ? "enabled" : "disabled") + ".", 1);
                break;
            case "profiles":
            case "p":
                handleProfilesCommand(args, hasArgs);
                break;
            case "help":
            case "?":
                print("&eAvailable commands:", 1);
                print("1 setkey [key]", 0);
                print("2 friend/enemy [name/clear]", 0);
                print("3 duels [player]", 0);
                print("4 nick [name/reset]", 0);
                print("5 ping", 0);
                print("6 hide/show [module]", 0);
                print("&eProfiles:", 0);
                print("1 profiles", 0);
                print("2 profiles save [profile]", 0);
                print("3 profiles load [profile]", 0);
                print("4 profiles remove [profile]", 0);
                print("&eModule-specific:", 0);
        print("1 namehider [name]", 0);
                print("2 " + FakeChat.command + " [msg]", 0);
                print("3 setspeed [fly/bhop/speed] [value]", 0);
                print("4 setvelocity [h/v] [value]", 0);
                break;
            default:
                String commandPreview = command.length() > 5 ? command.substring(0, 5) + "..." : command;
                print(INVALID_COMMAND + " (" + commandPreview + ")", 1);
                break;
        }
    }

    public static void print(String message, int type) {
        if (type == 1 || type == 2) {
            responseLines.add("");
        }
        responseLines.add(message);
        if (type == 2 || type == 3) {
            responseLines.add("");
        }
    }

    public static void renderCommandOutput(FontRenderer fontRenderer, int height, int width, double scale) {
        int x = width - COMMAND_PANEL_X_OFFSET;
        int y = height - COMMAND_PANEL_Y_OFFSET;
        int startY = height - COMMAND_PANEL_START_Y;
        int panelHeight = COMMAND_PANEL_HEIGHT;

        GL11.glEnable(GL_SCISSOR_TEST);
        double maxWidth = width * scale;
        int scissorWidth = (int) (maxWidth - (maxWidth < MIN_SCISSOR_WIDTH ? 0 : MIN_SCISSOR_WIDTH));
        int scissorHeight = (int) (panelHeight * scale - MIN_SCISSOR_WIDTH);
        int scissorY = (int) (mc.displayHeight - (startY + panelHeight) * scale);

        GL11.glScissor(0, scissorY, scissorWidth, scissorHeight);
        RenderUtils.db(1000, 1000, currentBackgroundColor);
        renderResponseStrings(fontRenderer, responseLines, x, y);
        GL11.glDisable(GL_SCISSOR_TEST);
    }

    private static void renderResponseStrings(FontRenderer fontRenderer, List<String> responseLines, int x, int y) {
        if (hasShownWelcome) {
            hasShownWelcome = false;
            print("Welcome,", 0);
            print("Use \"help\" for help.", 0);
        }

        if (!responseLines.isEmpty()) {
            for (int i = responseLines.size() - 1; i >= 0; --i) {
                String line = responseLines.get(i);
                int color = -1;

                if (line.contains("&a")) {
                    line = line.replace("&a", "");
                    color = Color.green.getRGB();
                }
                else if (line.contains("&c")) {
                    line = line.replace("&c", "");
                    color = Color.red.getRGB();
                }
                else if (line.contains("&e")) {
                    line = line.replace("&e", "");
                    color = Color.yellow.getRGB();
                }

                fontRenderer.drawString(line, x, y, color);
                y -= fontRenderer.FONT_HEIGHT + 5;
            }
        }
    }

    public static void setBackgroundColor() {
        int randomIndex = Utils.getRandom().nextInt(backgroundColors.size());
        if (randomIndex == lastBackgroundColorIndex) {
            randomIndex += randomIndex == 3 ? -3 : 1;
        }
        lastBackgroundColorIndex = randomIndex;
        currentBackgroundColor = backgroundColors.get(randomIndex);
    }

    public static void onDisable() {
        PingHelper.reset(false);
    }
}
