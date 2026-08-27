package myau.anticheat;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class flag {
    private static final String CHEAT_AUTOBLOCK = "AutoBlock";
    private static final String CHEAT_NOSLOW = "Noslow";
    private static final String CHEAT_KILLAURA = "KillAura";
    private static final String CHEAT_SCAFFOLD = "Scaffold";
    private static final int FLAG_WINDOW_SECONDS = 5;
    private static final int ALERT_COOLDOWN_SECONDS = 5;
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static boolean acflag = true;
    public static final List<String> whitelist = new ArrayList<>();
    private static final Map<String, int[]> flagMap = new HashMap<>();
    private static final Map<String, Integer> alertCooldowns = new HashMap<>();

    public static void receiveSignal(String playerName, String cheatName) {
        if (!acflag) return;
        if (playerName == null || playerName.isEmpty() || cheatName == null) return;
        if (mc.theWorld == null || mc.thePlayer == null || Myau.targetManager == null) return;
        if (playerName.equalsIgnoreCase(mc.thePlayer.getName())) return;
        if (isWhitelisted(playerName)) return;
        if (!isKnownCheck(cheatName)) return;

        int currentTimeInSeconds = (int) (mc.theWorld.getTotalWorldTime() / 20);
        String flagKey = getFlagKey(playerName, cheatName);
        int[] flagData = flagMap.getOrDefault(flagKey, new int[]{0, currentTimeInSeconds});
        if (currentTimeInSeconds - flagData[1] > FLAG_WINDOW_SECONDS) {
            flagData[0] = 0;
        }

        flagData[0] += 1;
        flagData[1] = currentTimeInSeconds;

        flagMap.put(flagKey, flagData);
        int maxFlagCount = maxFlagsFor(cheatName);
        int lastAlert = alertCooldowns.getOrDefault(flagKey, -ALERT_COOLDOWN_SECONDS);

        if (flagData[0] >= maxFlagCount && currentTimeInSeconds - lastAlert >= ALERT_COOLDOWN_SECONDS) {
            ChatUtil.sendFormatted(
                    String.format(
                            "%s%s%s%s failed %s%s",
                            Myau.clientName,
                            EnumChatFormatting.RED,
                            playerName,
                            EnumChatFormatting.GRAY,
                            EnumChatFormatting.RED,
                            cheatName
                    )
            );
            mc.thePlayer.playSound("random.orb", 0.3f, 1);
            Myau.targetManager.add(playerName);
            alertCooldowns.put(flagKey, currentTimeInSeconds);
            flagMap.remove(flagKey);
        }
    }

    @EventTarget
    public void onClientTick(TickEvent event) {
        if (event.getType() == EventType.POST && mc.theWorld != null) {
            int currentTimeInSeconds = (int) (mc.theWorld.getTotalWorldTime() / 20);

            Map<String, int[]> nextFlagMap = new HashMap<>();

            for (Map.Entry<String, int[]> entry : flagMap.entrySet()) {
                String playerName = entry.getKey();
                int[] flagData = entry.getValue();

                if (currentTimeInSeconds - flagData[1] <= FLAG_WINDOW_SECONDS) {
                    nextFlagMap.put(playerName, flagData);
                }
            }

            flagMap.clear();
            flagMap.putAll(nextFlagMap);
            alertCooldowns.entrySet().removeIf(entry -> currentTimeInSeconds - entry.getValue() > ALERT_COOLDOWN_SECONDS);
        }
    }

    private static boolean isKnownCheck(String cheatName) {
        return cheatName.equals(CHEAT_AUTOBLOCK)
                || cheatName.equals(CHEAT_NOSLOW)
                || cheatName.equals(CHEAT_KILLAURA)
                || cheatName.equals(CHEAT_SCAFFOLD);
    }

    private static boolean isWhitelisted(String playerName) {
        for (String whitelisted : whitelist) {
            if (whitelisted.equalsIgnoreCase(playerName)) {
                return true;
            }
        }
        return false;
    }

    private static int maxFlagsFor(String cheatName) {
        if (cheatName.equals(CHEAT_AUTOBLOCK)) return 5;
        if (cheatName.equals(CHEAT_NOSLOW)) return 3;
        if (cheatName.equals(CHEAT_KILLAURA)) return 4;
        if (cheatName.equals(CHEAT_SCAFFOLD)) return 4;
        return 2;
    }

    private static String getFlagKey(String playerName, String cheatName) {
        return playerName.toLowerCase(Locale.ROOT) + ":" + cheatName;
    }
}
