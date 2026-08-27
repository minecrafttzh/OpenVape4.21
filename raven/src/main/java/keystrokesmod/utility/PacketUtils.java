package keystrokesmod.utility;

import keystrokesmod.Raven;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static net.minecraft.util.EnumFacing.DOWN;

public class PacketUtils implements IMinecraftInstance {
    private static final Set<Packet<?>> skipSendEvent = Collections.newSetFromMap(
            Collections.synchronizedMap(new IdentityHashMap<Packet<?>, Boolean>())
    );
    private static final Set<Packet<?>> skipReceiveEvent = Collections.newSetFromMap(
            Collections.synchronizedMap(new IdentityHashMap<Packet<?>, Boolean>())
    );

    public static boolean consumeSendEventSkip(Packet<?> packet) {
        return skipSendEvent.remove(packet);
    }

    public static boolean consumeReceiveEventSkip(Packet<?> packet) {
        return skipReceiveEvent.remove(packet);
    }

    public static void sendPacketNoEvent(Packet packet) {
        if (packet == null || packet.getClass().getSimpleName().startsWith("S")) {
            return;
        }
        skipSendEvent.add(packet);
        Raven.mc.thePlayer.sendQueue.addToSendQueue(packet);
    }

    public static void receivePacketNoEvent(Packet packet) {
        try {
            packet.processPacket(Raven.mc.getNetHandler());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendReleasePacket() {
        mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, DOWN));
    }
}
