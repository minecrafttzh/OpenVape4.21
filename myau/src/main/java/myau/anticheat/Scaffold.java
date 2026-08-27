package myau.anticheat;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Scaffold {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /*
     * Lightweight scaffold consistency check
     *
     * Goal:
     * - reduce false positives
     * - avoid flagging normal bridging
     * - use buffering instead of instant punish
     * - track suspicious consistency patterns
     *
     * This is NOT Grim-level simulation.
     * It is a cleaner heuristic-based check.
     */

    private final Map<UUID, Integer> suspiciousPlacements = new HashMap<>();
    private final Map<UUID, Integer> violationBuffer = new HashMap<>();
    private final Map<UUID, Float> lastYaw = new HashMap<>();
    private final Map<UUID, Float> lastPitch = new HashMap<>();
    private final Map<UUID, Long> lastFlag = new HashMap<>();

    @EventTarget
    public void onClientTick(TickEvent event) {

        if (event.getType() != EventType.POST) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        World world = mc.theWorld;

        for (EntityPlayer player : world.playerEntities) {

            if (player == mc.thePlayer) continue;
            if (player.isDead) continue;

            UUID uuid = player.getUniqueID();

            /*
             * Basic movement info
             */
            double horizontalSpeed = Math.sqrt(
                    player.motionX * player.motionX +
                            player.motionZ * player.motionZ
            );

            boolean movingFast = horizontalSpeed > 0.15;
            boolean airborne = !player.onGround;
            boolean falling = player.motionY < -0.08;

            /*
             * Check block directly under player
             */
            BlockPos below = new BlockPos(
                    MathHelper.floor_double(player.posX),
                    MathHelper.floor_double(player.posY - 1.0),
                    MathHelper.floor_double(player.posZ)
            );

            Block blockBelow = world.getBlockState(below).getBlock();

            boolean hasSupportBlock = !(blockBelow instanceof BlockAir);

            /*
             * Suspicious pattern:
             * player is airborne/falling,
             * moving quickly,
             * while consistently having support blocks
             */
            if (airborne && falling && movingFast && hasSupportBlock) {

                int placements = suspiciousPlacements.getOrDefault(uuid, 0) + 1;
                suspiciousPlacements.put(uuid, placements);

            } else {

                /*
                 * Slowly decay instead of instantly reset
                 */
                int placements = suspiciousPlacements.getOrDefault(uuid, 0);

                if (placements > 0) {
                    suspiciousPlacements.put(uuid, placements - 1);
                }
            }

            /*
             * Rotation consistency
             *
             * Scaffold cheats often rotate unnaturally
             * while rapidly bridging.
             */
            float currentYaw = player.rotationYaw;
            float currentPitch = player.rotationPitch;

            float yawDiff = Math.abs(currentYaw - lastYaw.getOrDefault(uuid, currentYaw));
            float pitchDiff = Math.abs(currentPitch - lastPitch.getOrDefault(uuid, currentPitch));

            lastYaw.put(uuid, currentYaw);
            lastPitch.put(uuid, currentPitch);

            boolean suspiciousRotation =
                    yawDiff > 110.0f ||
                            pitchDiff > 35.0f;

            /*
             * Main detection logic
             */
            int placements = suspiciousPlacements.getOrDefault(uuid, 0);

            if (placements > 8 && suspiciousRotation) {

                int vl = violationBuffer.getOrDefault(uuid, 0) + 1;
                violationBuffer.put(uuid, vl);

                /*
                 * Require multiple consistent violations
                 */
                if (vl > 3) {

                    long now = System.currentTimeMillis();
                    long last = lastFlag.getOrDefault(uuid, 0L);

                    /*
                     * Prevent spam flagging
                     */
                    if (now - last > 3000L) {

                        flag.receiveSignal(
                                player.getName(),
                                "Scaffold [VL=" + vl + "]"
                        );

                        lastFlag.put(uuid, now);
                    }
                }

            } else {

                /*
                 * Violation decay
                 */
                int vl = violationBuffer.getOrDefault(uuid, 0);

                if (vl > 0) {
                    violationBuffer.put(uuid, vl - 1);
                }
            }
        }
    }
}