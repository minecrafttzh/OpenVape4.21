package gg.vape.module.combat;

import gg.vape.event.Event;
import gg.vape.event.EventHandler;
import gg.vape.event.EventPriority;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventMouseButton;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.SyntheticAttackRequestEvent;
import gg.vape.input.AttackKeyController;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.rotation.RotationManager;
import gg.vape.unmap.ItemLimitData;
import gg.vape.utils.ItemStackScoreUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.LimitValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityOtherPlayerMP;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;

public class ShieldBreaker extends Mod {
    private static final long MODULE_ID = 7954407336301342843L;

    private final NumberValue swapDelay;
    private final NumberValue swapBackDelay;
    private final BooleanValue doubleClick;
    private final BooleanValue limitToItems;
    private final LimitValue allowedItems;

    private boolean active;
    private boolean waitingToAttack;
    private boolean releasePending;
    private int originalSlot = -1;
    private int axeSlot = -1;
    private int swapTicks;
    private int restoreTicks;

    public ShieldBreaker() {
        super("ShieldBreaker", (int)MODULE_ID, Category.COMBAT,
                "Swaps to an axe when attacking a player with a raised shield");
        this.swapDelay = NumberValue.create(this, "Swap delay", "#", "ticks", 0.0, 0.0, 10.0, 1.0,
                "Delay between swapping to an axe and attacking");
        this.swapBackDelay = NumberValue.create(this, "Swap back delay", "#", "ticks", 1.0, 2.0, 10.0, 1.0,
                "Delay between attacking and swapping back to the original slot");
        this.doubleClick = BooleanValue.create(this, "Double click", false,
                "Attacks again immediately after breaking the shield to knock the target back");
        this.limitToItems = BooleanValue.create(this, "Limit to items", false,
                "ShieldBreaker functions only while holding selected items");
        this.allowedItems = LimitValue.create(this, "shieldbreaker-alloweditems", "Allowed Items",
                LimitValue.ALLOW_LIST_COLOR, new ItemLimitData("swords"));
        this.swapDelay.setMaximumFractionDigits(0);
        this.swapBackDelay.setMaximumFractionDigits(0);
        this.limitToItems.addDependentValues(this.allowedItems);
        this.addValue(this.swapDelay, this.swapBackDelay, this.doubleClick, this.limitToItems, this.allowedItems);
    }

    @EventHandler(priority = EventPriority.HIGH, skipCanceled = true)
    public void onKeyPress(EventKeyPress event) {
        if (event.isKeybinding(Minecraft.gameSettings().F()) && event.isDown()) {
            this.handleAttack(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, skipCanceled = true)
    public void onMouseButton(EventMouseButton event) {
        if (event.isKeybinding(Minecraft.gameSettings().F()) && event.isDown()) {
            this.handleAttack(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, skipCanceled = true)
    public void onSyntheticAttack(SyntheticAttackRequestEvent event) {
        Mod source = event.getSource();
        if (source != this && !(source instanceof HitSwap) && !(source instanceof AutoMace)) {
            this.handleAttack(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = event.getThePlayer();
        if (this.releasePending) {
            AttackKeyController.releaseAttackKey();
            this.releasePending = false;
        }
        if (player.isNull()) {
            this.reset(null, false);
            return;
        }
        if (!this.active) {
            return;
        }
        if (!this.isStateValid(player)) {
            this.restoreSlot(player);
            this.reset(player, false);
            return;
        }
        if (this.waitingToAttack) {
            if (this.swapTicks++ >= this.swapDelay.getValue().intValue()) {
                this.attack();
                this.waitingToAttack = false;
                this.restoreTicks = 0;
            }
            return;
        }
        if (this.restoreTicks++ >= this.swapBackDelay.getValue().intValue()) {
            this.restoreSlot(player);
            this.reset(player, false);
        }
    }

    private void handleAttack(Event event) {
        if (this.active || Minecraft.currentScreen().isNotNull()) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull() || !this.canUseHeldItem(player) || !this.isAttackingRaisedShield()) {
            return;
        }
        InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        int selectedSlot = inventory.v();
        if (this.isAxe(inventory.c(selectedSlot))) {
            if (this.doubleClick.getEffectiveValue().booleanValue()) {
                this.releasePending = AttackKeyController.requestSyntheticAttack(this);
            }
            return;
        }
        int foundAxeSlot = this.findAxeSlot(inventory);
        if (foundAxeSlot < 0) {
            return;
        }
        event.setCancelled(true);
        this.originalSlot = selectedSlot;
        this.axeSlot = foundAxeSlot;
        inventory.g(foundAxeSlot);
        this.active = true;
        this.waitingToAttack = true;
        this.swapTicks = 0;
        this.restoreTicks = 0;
        if (this.swapDelay.getValue().intValue() == 0) {
            this.attack();
            this.waitingToAttack = false;
        }
    }

    private void attack() {
        AttackKeyController.releaseAttackKey();
        this.releasePending = AttackKeyController.requestSyntheticAttack(this);
        if (this.doubleClick.getEffectiveValue().booleanValue() && this.releasePending) {
            AttackKeyController.releaseAttackKey();
            this.releasePending = AttackKeyController.requestSyntheticAttack(this);
        }
    }

    private boolean isStateValid(EntityPlayerSP player) {
        return Minecraft.currentScreen().isNull()
                && player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().v() == this.axeSlot
                && (!this.waitingToAttack || this.isAttackingRaisedShield());
    }

    private boolean canUseHeldItem(EntityPlayerSP player) {
        return !this.limitToItems.getEffectiveValue().booleanValue()
                || this.allowedItems.isValid(player.getHeldItemHand(), false);
    }

    private boolean isAttackingRaisedShield() {
        RayTraceResult rayTrace = RotationManager.INSTANCE.getExtendedReachRayTrace();
        if (rayTrace == null || !rayTrace.isEntityHit()) {
            return false;
        }
        Entity target = rayTrace.getEntity();
        return target != null && target.isNotNull() && target.isInstance(MappedClasses.lG)
                && RotationUtil.n(new EntityOtherPlayerMP(target.getObject()));
    }

    public boolean hasAxeInHotbar() {
        EntityPlayerSP player = Minecraft.thePlayer();
        return player.isNotNull() && this.findAxeSlot(player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6()) >= 0;
    }

    private int findAxeSlot(InventoryPlayer inventory) {
        for (int slot = 0; slot < 9; ++slot) {
            if (this.isAxe(inventory.c(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isAxe(ItemStack stack) {
        return stack != null && stack.isNotNull() && stack.getItem().isNotNull()
                && ItemStackScoreUtil.T(stack.getItem());
    }

    private void restoreSlot(EntityPlayerSP player) {
        if (player != null && player.isNotNull() && this.originalSlot >= 0) {
            player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(this.originalSlot);
        }
    }

    private void reset(EntityPlayerSP player, boolean restore) {
        if (restore) {
            this.restoreSlot(player);
        }
        if (this.releasePending) {
            AttackKeyController.releaseAttackKey();
        }
        this.active = false;
        this.waitingToAttack = false;
        this.releasePending = false;
        this.originalSlot = -1;
        this.axeSlot = -1;
        this.swapTicks = 0;
        this.restoreTicks = 0;
    }

    @Override
    public void onDisable() {
        this.reset(Minecraft.thePlayer(), true);
    }
}
