package keystrokesmod;

import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.command.CommandManager;
import keystrokesmod.event.PostProfileLoadEvent;
import keystrokesmod.event.PostSetSliderEvent;
import keystrokesmod.helper.DebugHelper;
import keystrokesmod.helper.MouseHelper;
import keystrokesmod.helper.PingHelper;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.keystroke.KeyStrokeCommand;
import keystrokesmod.keystroke.KeyStrokeConfigGui;
import keystrokesmod.keystroke.KeyStrokeRenderer;
import keystrokesmod.lag.handler.UnifiedLagHandler;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.script.ScriptDefaults;
import keystrokesmod.script.ScriptManager;
import keystrokesmod.script.model.Entity;
import keystrokesmod.script.model.NetworkPlayer;
import keystrokesmod.utility.BlockHighlightSharedHandler;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.ModuleUtils;
import keystrokesmod.utility.PacketsHandler;
import keystrokesmod.utility.PlayerRelationsManager;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.profile.Profile;
import keystrokesmod.utility.profile.ProfileManager;
import keystrokesmod.module.setting.impl.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Mod(modid = "keystrokes", name = "KeystrokesMod", version = "KMV5", acceptedMinecraftVersions = "[1.8.9]")
public class Raven {
    public static boolean DEBUG = false;

    public static Minecraft mc = Minecraft.getMinecraft();

    private static KeyStrokeRenderer keyStrokeRenderer;

    private static boolean isKeyStrokeConfigGuiToggled;

    private static final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);
    private static final ExecutorService cachedExecutor = Executors.newCachedThreadPool();

    public static ModuleManager moduleManager;
    public static ClickGui clickGui;
    public static ProfileManager profileManager;
    public static ScriptManager scriptManager;
    public static CommandManager commandManager;
    public static PlayerRelationsManager playerRelationsManager;
    public static Profile currentProfile;
    public static PacketsHandler packetsHandler;
    public static UnifiedLagHandler lagHandler;

    private static boolean firstLoad;

    public Raven() {
        moduleManager = new ModuleManager();
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        Runtime.getRuntime().addShutdownHook(new Thread(scheduledExecutor::shutdown));
        Runtime.getRuntime().addShutdownHook(new Thread(cachedExecutor::shutdown));

        ClientCommandHandler.instance.registerCommand(new KeyStrokeCommand());

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new DebugHelper());
        MinecraftForge.EVENT_BUS.register(new MouseHelper());
        MinecraftForge.EVENT_BUS.register(RotationHelper.get());
        MinecraftForge.EVENT_BUS.register(new KeyStrokeRenderer());
        MinecraftForge.EVENT_BUS.register(new PingHelper());
        MinecraftForge.EVENT_BUS.register(packetsHandler = new PacketsHandler());
        MinecraftForge.EVENT_BUS.register(new ModuleUtils());
        MinecraftForge.EVENT_BUS.register(lagHandler = new UnifiedLagHandler());

        ReflectionUtils.setupFields();
        playerRelationsManager = new PlayerRelationsManager();
        playerRelationsManager.load();
        moduleManager.register();
        MinecraftForge.EVENT_BUS.register(new BlockHighlightSharedHandler());
        scriptManager = new ScriptManager();
        keyStrokeRenderer = new KeyStrokeRenderer();
        clickGui = new ClickGui();
        profileManager = new ProfileManager();
        ScriptDefaults.reloadModules();
        scriptManager.loadScripts();
        profileManager.loadProfiles();
        ReflectionUtils.setKeyBindings();

        commandManager = new CommandManager();
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent e) {
        if (e.phase == Phase.END) {
            if (Utils.nullCheck()) {
                if (mc.thePlayer.ticksExisted % 6000 == 0) { // reset cache every 5 minutes
                    Entity.clearCache();
                    NetworkPlayer.clearCache();
                    if (DebugHelper.BACKGROUND) {
                        Utils.sendMessage("&aticks % 6000 == 0 &7reached, clearing script caches. (&dEntity&7, &dNetworkPlayer&7)");
                    }
                }
                if (ReflectionUtils.ERROR) {
                    Utils.sendMessage("&cThere was an error, relaunch the game.");
                    ReflectionUtils.ERROR = false;
                }

                MouseHelper.updateWheelCache();

                for (Module module : getModuleManager().getModules()) {
                    if (mc.currentScreen == null && module.canBeEnabled()) {
                        module.onKeyBind();
                    }
                    else if (mc.currentScreen instanceof ClickGui) {
                        module.guiUpdate();
                        module.syncKeyBindState();
                    }
                    else {
                        module.syncKeyBindState();
                    }

                    if (module.isEnabled()) {
                        module.onUpdate();
                    }
                }
                if (mc.currentScreen == null) {
                    for (Module module : Raven.scriptManager.scripts.values()) {
                        module.onKeyBind();
                    }
                }
                else {
                    for (Module module : Raven.scriptManager.scripts.values()) {
                        module.syncKeyBindState();
                    }
                    if (mc.currentScreen instanceof ClickGui) {
                    if (applyKillAuraRangeConstraints()) {
                        clickGui.onSliderChange();
                    }
                    if (mc.thePlayer.getHealth() <= 0.0f) {
                        mc.displayGuiScreen(null);
                    }
                    }
                }
            }

            if (isKeyStrokeConfigGuiToggled) {
                isKeyStrokeConfigGuiToggled = false;
                mc.displayGuiScreen(new KeyStrokeConfigGui());
            }
        }
        else {
            MouseHelper.clearWheelCache();
            if (mc.currentScreen == null && Utils.nullCheck()) {
                for (Profile profile : Raven.profileManager.profiles) {
                    profile.getModule().onKeyBind();
                }
            }
            else if (Utils.nullCheck()) {
                for (Profile profile : Raven.profileManager.profiles) {
                    profile.getModule().syncKeyBindState();
                }
            }
        }
    }

    @SubscribeEvent
    public void onPostProfileLoad(PostProfileLoadEvent e) {
        applyKillAuraRangeConstraints();
        clickGui.onSliderChange();
    }

    @SubscribeEvent
    public void onPostSetSlider(PostSetSliderEvent e) {
        applyKillAuraRangeConstraints();
        clickGui.onSliderChange();
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            if (!firstLoad) {
                firstLoad = true;
                scriptManager.loadScripts();
            }
            Entity.clearCache();
            NetworkPlayer.clearCache();
            keystrokesmod.utility.FrozenEntitySync.get().clearAll();
            if (DebugHelper.BACKGROUND) {
                Utils.sendMessage("&enew world&7, clearing script caches. (&dEntity&7, &dNetworkPlayer&7)");
            }
        }
    }

    public static ModuleManager getModuleManager() {
        return moduleManager;
    }

    public static ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }

    public static ExecutorService getCachedExecutor() {
        return cachedExecutor;
    }

    public static KeyStrokeRenderer getKeyStrokeRenderer() {
        return keyStrokeRenderer;
    }

    public static void toggleKeyStrokeConfigGui() {
        isKeyStrokeConfigGuiToggled = true;
    }

    public static void handleFrozenKeybinds() {
        if (!Utils.nullCheck()) return;

        MouseHelper.updateWheelCache();

        if (mc.currentScreen == null) {
            for (Module module : getModuleManager().getModules()) {
                if (module.canBeEnabled()) {
                    module.onKeyBind();
                }
            }
            for (Module module : scriptManager.scripts.values()) {
                module.onKeyBind();
            }
        } else if (mc.currentScreen instanceof ClickGui) {
            for (Module module : getModuleManager().getModules()) {
                module.guiUpdate();
                module.syncKeyBindState();
            }
            for (Module module : scriptManager.scripts.values()) {
                module.syncKeyBindState();
            }
        } else {
            for (Module module : getModuleManager().getModules()) {
                module.syncKeyBindState();
            }
            for (Module module : scriptManager.scripts.values()) {
                module.syncKeyBindState();
            }
        }

        if (isKeyStrokeConfigGuiToggled) {
            isKeyStrokeConfigGuiToggled = false;
            mc.displayGuiScreen(new KeyStrokeConfigGui());
        }
    }

    private boolean applyKillAuraRangeConstraints() {
        if (ModuleManager.killAura == null) {
            return false;
        }

        SliderSetting attackRange = ModuleManager.killAura.getAttackRangeSetting();
        SliderSetting swingRange = ModuleManager.killAura.getSwingRangeSetting();
        SliderSetting aimRange = ModuleManager.killAura.getAimRangeSetting();
        if (attackRange == null || swingRange == null || aimRange == null) {
            return false;
        }

        boolean changed = false;
        double attack = attackRange.getInput();
        double swing = swingRange.getInput();
        double aim = aimRange.getInput();

        if (swing < attack) {
            swingRange.setValue(attack);
            swing = swingRange.getInput();
            changed = true;
        }

        if (aim < swing) {
            aimRange.setValue(swing);
            changed = true;
        }

        return changed;
    }
}
