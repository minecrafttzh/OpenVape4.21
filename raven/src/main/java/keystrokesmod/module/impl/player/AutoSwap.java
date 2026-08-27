package keystrokesmod.module.impl.player;

import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorPlayerControllerMP;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.BlockListSetting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class AutoSwap extends Module {
    private final ButtonSetting useBlockWhitelist;
    private final BlockListSetting blockWhitelist;

    private ItemStack trackedStack;
    private int lastPlaceSlot = -1;
    private int lastSwapSlot = -1;
    private long lastSwapTime;

    public AutoSwap() {
        super("Auto Swap", category.player);

        this.registerSetting(useBlockWhitelist = new ButtonSetting("Use block whitelist", false));
        this.registerSetting(blockWhitelist = new BlockListSetting("Whitelisted blocks", "Blocks", "Blocks.Block whitelist", "Blocks.Whitelisted blocks"));
        blockWhitelist.visible = false;
        this.closetModule = true;
    }

    @Override
    public void guiUpdate() {
        blockWhitelist.setVisible(useBlockWhitelist.isToggled(), this);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!Utils.nullCheck() || !(e.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
            return;
        }

        C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) e.getPacket();
        if (packet.getPlacedBlockDirection() == 255) {
            return;
        }

        ItemStack stack = packet.getStack();
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) {
            return;
        }

        trackedStack = stack.copy();
        trackedStack.stackSize = 1;
        lastPlaceSlot = mc.thePlayer.inventory.currentItem;
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (!Utils.nullCheck()) {
            resetState();
            return;
        }

        if (!mc.inGameHasFocus || mc.currentScreen != null || !Utils.isBindDown(mc.gameSettings.keyBindUseItem)) {
            return;
        }

        if (trackedStack == null || lastPlaceSlot == -1 || mc.thePlayer.inventory.currentItem != lastPlaceSlot) {
            return;
        }

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null && held.stackSize > 0) {
            return;
        }

        if (!isWhitelistedBlock(trackedStack)) {
            return;
        }

        long now = System.currentTimeMillis();
        for (int slot = 8; slot >= 0; --slot) {
            if (slot == lastSwapSlot && now - lastSwapTime < 300L) {
                continue;
            }

            ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(slot);
            if (!matchesTrackedStack(candidate)) {
                continue;
            }

            swapToSlot(slot);
            lastSwapSlot = slot;
            lastSwapTime = now;
            break;
        }
    }

    private boolean isWhitelistedBlock(ItemStack stack) {
        if (!useBlockWhitelist.isToggled()) {
            return true;
        }

        if (stack == null || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }

        Block block = ((ItemBlock) stack.getItem()).getBlock();
        Object registryName = Block.blockRegistry.getNameForObject(block);
        if (block == null || registryName == null) {
            return false;
        }

        String registryId = registryName.toString();
        int meta = stack.getMetadata();
        String storageId = meta != 0 ? registryId + ":" + meta : registryId;
        return blockWhitelist.contains(storageId) || blockWhitelist.contains(registryId);
    }

    private boolean matchesTrackedStack(ItemStack stack) {
        if (trackedStack == null || stack == null || stack.getItem() != trackedStack.getItem()) {
            return false;
        }

        if (stack.getHasSubtypes() && stack.getMetadata() != trackedStack.getMetadata()) {
            return false;
        }

        return ItemStack.areItemStackTagsEqual(stack, trackedStack);
    }

    private void swapToSlot(int slot) {
        if (slot == -1 || slot == mc.thePlayer.inventory.currentItem) {
            return;
        }

        mc.thePlayer.inventory.currentItem = slot;
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    }

    private void resetState() {
        trackedStack = null;
        lastPlaceSlot = -1;
        lastSwapSlot = -1;
        lastSwapTime = 0L;
    }
}
