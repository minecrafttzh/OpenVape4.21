package keystrokesmod.utility;

import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class BlockHighlightSharedHandler {

    private static final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        SharedBlockHighlightCache.get().handleReceivePacket(e);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
        if (!cache.anyConsumerActive()) {
            return;
        }
        int budget = 0;
        if (ModuleManager.blockESP != null) {
            budget = Math.max(budget, ModuleManager.blockESP.getScanSpeedBudget());
        }
        if (ModuleManager.bedESP != null) {
            budget = Math.max(budget, ModuleManager.bedESP.getScanSpeedBudget());
        }
        if (budget > 0) {
            cache.tickScan(budget);
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent e) {
        if (e.entity != mc.thePlayer) {
            return;
        }
        SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
        if (!cache.anyConsumerActive()) {
            return;
        }
        cache.clear();
        cache.enqueueLoadedChunks();
    }
}
