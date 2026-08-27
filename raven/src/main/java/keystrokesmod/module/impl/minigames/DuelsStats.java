package keystrokesmod.module.impl.minigames;

import keystrokesmod.Raven;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.ProfileUtils;
import keystrokesmod.utility.NetworkUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class DuelsStats extends Module {
    private SliderSetting mode;
    private ButtonSetting sendIgnOnJoin;
    private ButtonSetting threatLevel;

    public static String nick = "";
    private String ign = "";
    private String en = "";

    private static final String[] THREAT_LEVELS = new String[]{"§4VERY HIGH", "§cHIGH", "§6MODERATE", "§aLOW", "§2VERY LOW"};
    private List<String> q = new ArrayList();

    public DuelsStats() {
        super("Duels Stats", category.minigames);
        this.registerSetting(mode = new SliderSetting("Mode", 0, THREAT_LEVELS));
        this.registerSetting(sendIgnOnJoin = new ButtonSetting("Send ign on join", false));
        this.registerSetting(threatLevel = new ButtonSetting("Threat Level", true));
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            this.ign = mc.thePlayer.getName();
        }
        else {
            this.disable();
        }
    }

    @Override
    public void onDisable() {
        this.en = "";
        this.q.clear();
    }

    @Override
    public void onUpdate() {
        if (this.id() && this.en.isEmpty()) {
            List<EntityPlayer> players = mc.theWorld.playerEntities;
            players.remove(mc.thePlayer);

            for (EntityPlayer player : players) {
                String name = player.getName();
                if (!name.equals(this.ign) && !name.equals(nick) && !this.q.contains(name) && player.getDisplayName().getUnformattedText().contains("§k")) {
                    this.ef(name);
                    break;
                }
            }
        }

    }

    @SubscribeEvent
    public void onMessageReceived(ClientChatReceivedEvent e) {
        if (Utils.nullCheck() && this.id()) {
            String s = Utils.stripColor(e.message.getUnformattedText());
            if (s.contains(" ")) {
                String[] sp = s.split(" ");
                String n;
                if (sp.length == 4 && sp[1].equals("has") && sp[2].equals("joined") && sp[3].equals("(2/2)!")) {
                    n = sp[0];
                    if (!n.equals(this.ign) && !n.equals(nick) && this.en.isEmpty()) {
                        this.q.remove(n);
                        this.ef(n);
                    }
                }
                else if (sp.length == 3 && sp[1].equals("has") && sp[2].equals("quit!")) {
                    n = sp[0];
                    if (this.en.equals(n)) {
                        this.en = "";
                    }

                    this.q.add(n);
                }
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            this.en = "";
            this.q.clear();
        }

    }

    private void ef(String name) {
        this.en = name;
        if (sendIgnOnJoin.isToggled()) {
            Utils.sendMessage("&eOpponent found: " + "&3" + name);
        }

        if (NetworkUtils.API_KEY.isEmpty()) {
            Utils.sendMessage("&cAPI Key is empty!");
        }
        else {
            ProfileUtils.DM dm = ProfileUtils.DM.values()[(int) (mode.getInput() - 1.0D)];
            Raven.getScheduledExecutor().execute(() -> {
                int[] s = ProfileUtils.getHypixelStats(name, dm);
                if (s != null) {
                    if (s[0] == -1) {
                        Utils.sendMessage("&3" + name + " " + "&eis nicked!");
                        return;
                    }

                    double wlr = s[1] != 0 ? Utils.round((double) s[0] / (double) s[1], 2) : (double) s[0];
                    Utils.sendMessage("&7&m-------------------------");
                    if (dm != ProfileUtils.DM.OVERALL) {
                        Utils.sendMessage("&eMode: &3" + dm.name());
                    }

                    Utils.sendMessage("&eOpponent: &3" + name);
                    Utils.sendMessage("&eWins: &3" + s[0]);
                    Utils.sendMessage("&eLosses: &3" + s[1]);
                    Utils.sendMessage("&eWLR: &3" + wlr);
                    Utils.sendMessage("&eWS: &3" + s[2]);
                    if (threatLevel.isToggled()) {
                        Utils.sendMessage("&eThreat: &3" + gtl(s[0], s[1], wlr, s[2]));
                    }

                    Utils.sendMessage("&7&m-------------------------");
                } else {
                    Utils.sendMessage("&cThere was an error.");
                }

            });
        }
    }

    private boolean id() {
        if (Utils.isHypixel()) {
            int l = 0;

            for (String s : Utils.getScoreBoardOld()) {
                if (s.contains("Map:")) {
                    ++l;
                } else if (s.contains("Players:") && s.contains("/2")) {
                    ++l;
                }
            }

            return l == 2;
        } else {
            return false;
        }
    }

    public static String gtl(int w, int l, double wlr, int ws) {
        int t = 0;
        int m = w + l;
        if (m <= 13) {
            t += 2;
        }

        if (ws >= 30) {
            t += 9;
        } else if (ws >= 15) {
            t += 7;
        } else if (ws >= 8) {
            t += 5;
        } else if (ws >= 4) {
            t += 3;
        } else if (ws >= 1) {
            ++t;
        }

        if (wlr >= 20.0D) {
            t += 8;
        } else if (wlr >= 10.0D) {
            t += 5;
        } else if (wlr >= 5.0D) {
            t += 4;
        } else if (wlr >= 3.0D) {
            t += 2;
        } else if (wlr >= 0.8D) {
            ++t;
        }

        if (w >= 20000) {
            t += 4;
        } else if (w >= 10000) {
            t += 3;
        } else if (w >= 5000) {
            t += 2;
        } else if (w >= 1000) {
            ++t;
        }

        if (l == 0) {
            if (w == 0) {
                t += 3;
            } else {
                t += 4;
            }
        } else if (l <= 10 && wlr >= 4.0D) {
            t += 2;
        }

        String thr;
        if (t == 0) {
            thr = THREAT_LEVELS[4];
        } else if (t <= 3) {
            thr = THREAT_LEVELS[3];
        } else if (t <= 6) {
            thr = THREAT_LEVELS[2];
        } else if (t <= 10) {
            thr = THREAT_LEVELS[1];
        } else {
            thr = THREAT_LEVELS[0];
        }

        return thr;
    }
}