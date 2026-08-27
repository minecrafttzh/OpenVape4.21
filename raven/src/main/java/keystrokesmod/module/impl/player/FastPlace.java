package keystrokesmod.module.impl.player;

import keystrokesmod.event.RightClickDelayTickEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.BlockListSetting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import keystrokesmod.utility.Utils;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class FastPlace extends Module {
    public SliderSetting tickDelay;
    public SliderSetting activationTime;
    public ButtonSetting blocksOnly, pitchCheck;
    public ButtonSetting ignoredHeldItemsToggle;
    public ItemListSetting ignoredHeldItems;
    public ButtonSetting blockBlacklistToggle;
    public BlockListSetting blockBlacklist;
    private long rightClickStartTime;

    public FastPlace() {
        super("Fast Place", Module.category.player, 0);
        this.registerSetting(tickDelay = new SliderSetting("Tick delay", 1.0, 0.0, 3.0, 1.0));
        this.registerSetting(activationTime = new SliderSetting("Activation time", "ms", 0.0, 0.0, 100.0, 5.0));
        this.registerSetting(blocksOnly = new ButtonSetting("Blocks only", true));
        this.registerSetting(pitchCheck = new ButtonSetting("Pitch check", false));
        this.registerSetting(ignoredHeldItemsToggle = new ButtonSetting("Held item blacklist", false, "Ignore held items", "Restrict held items", "Allow while holding"));
        this.registerSetting(ignoredHeldItems = new ItemListSetting("Held items", "Items"));
        this.registerSetting(blockBlacklistToggle = new ButtonSetting("Block blacklist", false, "Blocks.Block blacklist"));
        this.registerSetting(blockBlacklist = new BlockListSetting("Blacklisted blocks", "Block blacklist", "Blocks.Block blacklist", "Blocks.Blacklisted blocks"));
        this.closetModule = true;
    }

    @Override
    public void onDisable() {
        rightClickStartTime = 0L;
    }

    @Override
    public void guiUpdate() {
        ignoredHeldItems.setVisible(ignoredHeldItemsToggle.isToggled(), this);
        blockBlacklist.setVisible(blockBlacklistToggle.isToggled(), this);
    }

    @SubscribeEvent
    public void onRightClickDelayTick(RightClickDelayTickEvent e) {
        if (!Utils.nullCheck() || !mc.inGameHasFocus) {
            rightClickStartTime = 0L;
            return;
        }

        if (!isRightClickActive()) {
            rightClickStartTime = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (rightClickStartTime == 0L) {
            rightClickStartTime = now;
        }

        if (!canFastPlace(now, true)) {
            return;
        }

        int delay = (int) tickDelay.getInput();
        if (delay == 0) {
            ((IAccessorMinecraft) mc).setRightClickDelayTimer(0);
        }
        else {
            if (delay == 4) {
                return;
            }
            if (((IAccessorMinecraft) mc).getRightClickDelayTimer() > delay) {
                ((IAccessorMinecraft) mc).setRightClickDelayTimer(delay);
            }
        }
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!Utils.nullCheck() || !(e.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }

        C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) e.getPacket();
        if (packet.getPlacedBlockDirection() != 255) {
            return;
        }

        ItemStack packetStack = packet.getStack();
        if (packetStack == null || !(packetStack.getItem() instanceof ItemBlock)) {
            return;
        }

        if (!canFastPlace(System.currentTimeMillis(), true)) {
            return;
        }

        if (Math.random() < 0.7) {
            e.setCanceled(true);
        }
    }

    private boolean isBlockedHoverBlock() {
        if (!blockBlacklistToggle.isToggled() || mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }

        BlockPos hoveredPos = mc.objectMouseOver.getBlockPos();
        if (hoveredPos == null) {
            return false;
        }

        IBlockState state = mc.theWorld.getBlockState(hoveredPos);
        Block hoveredBlock = state.getBlock();
        if (hoveredBlock == null || Block.blockRegistry.getNameForObject(hoveredBlock) == null) {
            return false;
        }

        String registryId = Block.blockRegistry.getNameForObject(hoveredBlock).toString();
        int meta = hoveredBlock.getMetaFromState(state);
        String storageId = meta != 0 ? registryId + ":" + meta : registryId;
        return blockBlacklist.contains(storageId) || blockBlacklist.contains(registryId);
    }

    private boolean isRightClickActive() {
        return Utils.isBindDown(mc.gameSettings.keyBindUseItem) || mc.thePlayer.isUsingItem();
    }

    private boolean canFastPlace(long now, boolean requireActivationDelay) {
        if (blocksOnly.isToggled()) {
            ItemStack item = mc.thePlayer.getHeldItem();
            if (item == null || !(item.getItem() instanceof ItemBlock)) {
                return false;
            }
        }
        if (pitchCheck.isToggled() && mc.thePlayer.rotationPitch < 70.0f) {
            return false;
        }
        if (ignoredHeldItemsToggle.isToggled() && ignoredHeldItems.matches(mc.thePlayer.getHeldItem())) {
            return false;
        }
        if (isBlockedHoverBlock()) {
            return false;
        }
        return !requireActivationDelay || now - rightClickStartTime >= (long) activationTime.getInput();
    }
}
