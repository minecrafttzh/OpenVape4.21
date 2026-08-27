package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.util.MoveUtil;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.network.play.server.S24PacketBlockAction;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

// 为了防止某个叫秋窈的狗来狗叫，我特定说明这个ChestAura是来自某个魔水的，而不是你那坨狗屎Eternity里面还是我搞上去的ChestAura
// 你那个端里的ChestAura还是我弄上去的，你有什么资格来狗叫我
public class ChestAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat df = new DecimalFormat("0.0");

    public final FloatProperty range = new FloatProperty("Range", 4.0f, 1.0f, 6.0f);
    public final BooleanProperty throughWalls = new BooleanProperty("Through Walls", true);
    public final ModeProperty moveFix = new ModeProperty("Move Fix", 1, new String[]{"None", "Silent", "Strict"});

    private final List<BlockPos> openedChests = new ArrayList<>();
    private TileEntityChest targetChest;
    private float[] rotations;
    private boolean isRotating;
    private boolean scaffoldWasEnabled = false;

    public ChestAura() {
        super("ChestAura", false);
    }

    @Override
    public void onEnabled() {
        Scaffold scaffold = (Scaffold) Myau.moduleManager.getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            scaffoldWasEnabled = true;
            scaffold.setEnabled(false);
        }
        openedChests.clear();
    }

    @Override
    public void onDisabled() {
        if (scaffoldWasEnabled) {
            Scaffold scaffold = (Scaffold) Myau.moduleManager.getModule(Scaffold.class);
            if (scaffold != null) {
                scaffold.setEnabled(true);
            }
            scaffoldWasEnabled = false;
        }
        targetChest = null;
        isRotating = false;
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        openedChests.clear();
        scaffoldWasEnabled = false;
    }

    private void addOpenedChest(BlockPos pos) {
        if (!openedChests.contains(pos)) {
            openedChests.add(pos);
        }
        net.minecraft.block.Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block instanceof BlockChest) {
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                BlockPos neighbor = pos.offset(facing);
                if (mc.theWorld.getBlockState(neighbor).getBlock() == block) {
                    if (!openedChests.contains(neighbor)) {
                        openedChests.add(neighbor);
                    }
                }
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{df.format(range.getValue())};
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled()) return;
        if (event.getPacket() instanceof S24PacketBlockAction) {
            S24PacketBlockAction packet = (S24PacketBlockAction) event.getPacket();
            if (packet.getData2() == 1) {
                addOpenedChest(packet.getBlockPosition());
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled()) return;
        if (event.getType() != EventType.PRE) return;

        KillAura killAura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            targetChest = null;
            isRotating = false;
            return;
        }

        if (mc.currentScreen instanceof GuiContainer || mc.currentScreen instanceof GuiInventory) {
            targetChest = null;
            isRotating = false;
            return;
        }

        for (TileEntity tileEntity : mc.theWorld.loadedTileEntityList) {
            if (tileEntity instanceof TileEntityChest) {
                TileEntityChest chest = (TileEntityChest) tileEntity;
                if (chest.numPlayersUsing > 0) {
                    addOpenedChest(chest.getPos());
                }
            }
        }

        targetChest = getClosestChest();
        isRotating = false;

        if (targetChest != null) {
            double x = targetChest.getPos().getX() + 0.5 - mc.thePlayer.posX;
            double y = targetChest.getPos().getY() + 0.5 - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
            double z = targetChest.getPos().getZ() + 0.5 - mc.thePlayer.posZ;
            double dist = Math.sqrt(x * x + z * z);

            float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f;
            float pitch = (float) -(Math.atan2(y, dist) * 180.0 / Math.PI);

            rotations = new float[]{yaw, pitch};

            event.setRotation(rotations[0], rotations[1], 1);
            mc.thePlayer.rotationYawHead = rotations[0];
            mc.thePlayer.renderYawOffset = rotations[0];
            isRotating = true;

            if (this.moveFix.getValue() != 0) {
                event.setPervRotation(rotations[0], 1);
            }

            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                    mc.thePlayer.inventory.getCurrentItem(),
                    targetChest.getPos(), EnumFacing.UP,
                    new Vec3(targetChest.getPos().getX(), targetChest.getPos().getY(), targetChest.getPos().getZ()))) {
                mc.thePlayer.swingItem();
                addOpenedChest(targetChest.getPos());
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!isEnabled()) return;

        KillAura killAura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) return;

        if (isRotating && targetChest != null) {
            if (this.moveFix.getValue() == 1 && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(rotations[0]);
            }
        }
    }

    private TileEntityChest getClosestChest() {
        List<TileEntityChest> chests = mc.theWorld.loadedTileEntityList.stream()
                .filter(e -> e instanceof TileEntityChest)
                .map(e -> (TileEntityChest) e)
                .filter(e -> !openedChests.contains(e.getPos()))
                .filter(e -> mc.thePlayer.getDistanceSq(e.getPos()) <= range.getValue() * range.getValue())
                .filter(e -> throughWalls.getValue() || mc.thePlayer.canEntityBeSeen(
                        new net.minecraft.entity.item.EntityItem(mc.theWorld, e.getPos().getX(), e.getPos().getY(), e.getPos().getZ())))
                .sorted(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceSq(e.getPos())))
                .collect(Collectors.toList());

        return chests.isEmpty() ? null : chests.get(0);
    }
}