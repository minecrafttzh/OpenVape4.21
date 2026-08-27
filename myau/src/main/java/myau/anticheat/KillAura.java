package myau.anticheat;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class KillAura {

    private final Map<String, Long> lastAttackTime = new HashMap<>();
    private final Map<String, Integer> consecutiveHeadshots = new HashMap<>();
    private static final Minecraft mc = Minecraft.getMinecraft();

    @EventTarget
    public void onClientTick(TickEvent event) {
        if (event.getType() == EventType.POST && mc.thePlayer != null && mc.theWorld != null) {
            World world = mc.theWorld;
            long currentTick = world.getTotalWorldTime();

            for (EntityPlayer player : world.playerEntities) {
                if (player == mc.thePlayer) continue;

                if (hasNearbyPlayerTarget(player, world)) {
                    // Check swing progress (attack patterns)
                    boolean isAttacking = (player.swingProgress > 0 && player.prevSwingProgress == 0);

                    if (isAttacking) {
                        long lastAttack = lastAttackTime.getOrDefault(player.getName(), currentTick);
                        long timeSinceLastAttack = currentTick - lastAttack;

                        // Flag if attacks are too consistent (perfect timing)
                        if (timeSinceLastAttack > 0 && timeSinceLastAttack < 3) {
                            flag.receiveSignal(player.getName(), "KillAura");
                        }

                        lastAttackTime.put(player.getName(), currentTick);

                        // Check 2: Multiple consecutive attacks without moving
                        int headshots = consecutiveHeadshots.getOrDefault(player.getName(), 0);
                        headshots++;
                        consecutiveHeadshots.put(player.getName(), headshots);

                        if (headshots > 8) {
                            flag.receiveSignal(player.getName(), "KillAura");
                            consecutiveHeadshots.put(player.getName(), 0);
                        }
                    } else {
                        consecutiveHeadshots.put(player.getName(), 0);
                    }
                }
            }
        }
    }

    private boolean hasNearbyPlayerTarget(EntityPlayer player, World world) {
        Vec3 playerPos = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);

        for (EntityPlayer target : world.playerEntities) {
            if (target == player || target.isDead) continue;

            Vec3 targetPos = new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
            double distance = playerPos.distanceTo(targetPos);
            if (distance < 6.0 && distance > 0.1) {
                return true;
            }
        }

        return false;
    }
}
