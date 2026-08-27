package keystrokesmod.module.impl.minigames;

import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.utility.ItemSearchIndex;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.*;
import java.util.List;

public class MurderMystery extends Module {
    private final ItemListSetting murderWeapons;
    private final ButtonSetting alert;
    private final ButtonSetting highlightMurderer;
    private final ButtonSetting highlightBow;
    private final ButtonSetting highlightInnocent;
    private final ButtonSetting highlightDead;
    private final ButtonSetting goldEsp;

    private final List<EntityPlayer> murderers = new ArrayList<EntityPlayer>();
    private final List<EntityPlayer> hasBow = new ArrayList<EntityPlayer>();

    private boolean override;

    public MurderMystery() {
        super("Murder Mystery", category.minigames);
        this.registerSetting(murderWeapons = new ItemListSetting("Murder weapons"));
        addDefaultMurderWeapon(Items.iron_sword);
        addDefaultMurderWeapon(Items.stone_sword);
        addDefaultMurderWeapon(Items.iron_shovel);
        addDefaultMurderWeapon(Items.stick);
        addDefaultMurderWeapon(Items.wooden_axe);
        addDefaultMurderWeapon(Items.wooden_sword);
        addDefaultMurderWeapon(Items.stone_shovel);
        addDefaultMurderWeapon(Items.blaze_rod);
        addDefaultMurderWeapon(Items.diamond_shovel);
        addDefaultMurderWeapon(Items.quartz);
        addDefaultMurderWeapon(Items.pumpkin_pie);
        addDefaultMurderWeapon(Items.golden_pickaxe);
        addDefaultMurderWeapon(Items.apple);
        addDefaultMurderWeapon(Items.name_tag);
        addDefaultMurderWeapon(Items.carrot_on_a_stick);
        addDefaultMurderWeapon(Items.bone);
        addDefaultMurderWeapon(Items.carrot);
        addDefaultMurderWeapon(Items.golden_carrot);
        addDefaultMurderWeapon(Items.cookie);
        addDefaultMurderWeapon(Items.diamond_axe);
        addDefaultMurderWeapon(Items.prismarine_shard);
        addDefaultMurderWeapon(Items.cooked_beef);
        addDefaultMurderWeapon(Items.netherbrick);
        addDefaultMurderWeapon(Items.cooked_chicken);
        addDefaultMurderWeapon(Items.golden_sword);
        addDefaultMurderWeapon(Items.diamond_sword);
        addDefaultMurderWeapon(Items.diamond_hoe);
        addDefaultMurderWeapon(Items.shears);
        addDefaultMurderWeapon(Items.fish);
        addDefaultMurderWeapon(Items.dye);
        addDefaultMurderWeapon(Items.boat);
        addDefaultMurderWeapon(Items.speckled_melon);
        addDefaultMurderWeapon(Items.book);
        addDefaultMurderWeapon(Item.getItemFromBlock(Blocks.double_plant));
        addDefaultMurderWeapon(Item.getItemFromBlock(Blocks.sponge));
        addDefaultMurderWeapon(Item.getItemFromBlock(Blocks.deadbush));
        this.registerSetting(alert = new ButtonSetting("Alert murderer", true));
        this.registerSetting(highlightMurderer = new ButtonSetting("Highlight murderer", true));
        this.registerSetting(highlightBow = new ButtonSetting("Highlight bow", true));
        this.registerSetting(highlightInnocent = new ButtonSetting("Highlight innocent", true));
        this.registerSetting(highlightDead = new ButtonSetting("Highlight dead", true));
        this.registerSetting(goldEsp = new ButtonSetting("Gold ESP", true));
    }

    public void onDisable() {
        this.clear();
    }

    @SubscribeEvent
    public void onRenderWordLast(RenderWorldLastEvent e) {
        if (Utils.nullCheck()) {
            if (!this.isMurderMystery()) {
                this.clear();
            }
            else {
                override = false;
                for (EntityPlayer en : mc.theWorld.playerEntities) {
                    if (en != mc.thePlayer && !en.isInvisible()) {
                        if (AntiBot.isBot(en) && !highlightDead.isToggled()) {
                            continue;
                        }
                        if (en.getHeldItem() != null) {
                            ItemStack heldStack = en.getHeldItem();
                            Item heldItem = heldStack.getItem();
                            if (murderWeapons.matches(heldStack)) {
                                if (!murderers.contains(en)) {
                                    murderers.add(en);
                                    if (alert.isToggled()) {
                                        mc.thePlayer.playSound("note.pling", 1.0F, 1.0F);
                                        Utils.sendMessage("&eAlert: &b" + en.getName() + " &7is the &cmurderer&7! (&d" + (int) mc.thePlayer.getDistanceToEntity(en) + "m&7)");
                                    }
                                }
                            }
                            else if (heldItem instanceof ItemBow && highlightBow.isToggled() && !hasBow.contains(en)) {
                                hasBow.add(en);
                            }
                        }
                        override = true;
                        int rgb = Color.green.getRGB();
                        if (murderers.contains(en) && highlightMurderer.isToggled()) {
                            rgb = Color.red.getRGB();
                        }
                        else if (hasBow.contains(en) && highlightBow.isToggled()) {
                            rgb = Color.orange.getRGB();
                        }
                        else if (!highlightInnocent.isToggled()) {
                            continue;
                        }
                        if (!highlightDead.isToggled() && getBoundingBoxVolume(en) <= 0.009) {
                            continue;
                        }
                        RenderUtils.renderEntity(en, 2, 0.0D, 0.0D, rgb, false);
                    }
                }
                if (!goldEsp.isToggled()) {
                    return;
                }
                float renderPartialTicks = ((IAccessorMinecraft) mc).getTimer().renderPartialTicks;
                int n4 = -331703;
                for (Entity entity : mc.theWorld.loadedEntityList) {
                    if (entity instanceof EntityItem) {
                        if (entity.ticksExisted < 3) {
                            continue;
                        }
                        EntityItem entityItem = (EntityItem) entity;
                        if (entityItem.getEntityItem().stackSize == 0) {
                            continue;
                        }
                        Item getItem = entityItem.getEntityItem().getItem();
                        if (getItem == null || getItem != Items.gold_ingot) {
                            continue;
                        }
                        double n5 = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * renderPartialTicks;
                        double n6 = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * renderPartialTicks;
                        double n7 = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * renderPartialTicks;
                        double n8 = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * renderPartialTicks - n5;
                        double n9 = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * renderPartialTicks - n6;
                        double n10 = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * renderPartialTicks - n7;
                        GlStateManager.pushMatrix();
                        drawBox(n4, n5, n6, n7, MathHelper.sqrt_double(n8 * n8 + n9 * n9 + n10 * n10));
                        GlStateManager.popMatrix();
                    }
                }
            }
        }
    }

    public void drawBox(int n, double n4, double n5, double n6, double n7) {
        n4 -= mc.getRenderManager().viewerPosX;
        n5 -= mc.getRenderManager().viewerPosY;
        n6 -= mc.getRenderManager().viewerPosZ;
        GL11.glPushMatrix();
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glLineWidth(2.0f);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        float n8 = (n >> 16 & 0xFF) / 255.0f;
        float n9 = (n >> 8 & 0xFF) / 255.0f;
        float n10 = (n & 0xFF) / 255.0f;
        float min = Math.min(Math.max(0.2f, (float) (0.009999999776482582 * n7)), 0.4f);
        RenderUtils.drawBoundingBox(new AxisAlignedBB(n4 - min, n5, n6 - min, n4 + min, n5 + min * 2.0f, n6 + min), n8, n9, n10, 0.35f);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
        GL11.glPopMatrix();
    }

    private boolean isMurderMystery() {
        if (Utils.isHypixel()) {
            if (mc.thePlayer.getWorldScoreboard() == null || mc.thePlayer.getWorldScoreboard().getObjectiveInDisplaySlot(1) == null) {
                return false;
            }

            String d = mc.thePlayer.getWorldScoreboard().getObjectiveInDisplaySlot(1).getDisplayName();
            if (!d.contains("MURDER") && !d.contains("MYSTERY")) {
                return false;
            }

            Iterator var2 = Utils.getScoreBoardOld().iterator();

            while (var2.hasNext()) {
                String l = (String) var2.next();
                String s = Utils.stripColor(l);
                if (s.contains("Role:") || s.contains("Innocents Left:")) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isEmpty() {
        return murderers.isEmpty() && hasBow.isEmpty() && !override;
    }

    private void clear() {
        override = false;
        murderers.clear();
        hasBow.clear();
    }

    private double getBoundingBoxVolume(Entity entity) {
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox();

        if (boundingBox == null) {
            return 0;
        }

        double length = boundingBox.maxX - boundingBox.minX;
        double width = boundingBox.maxZ - boundingBox.minZ;
        double height = boundingBox.maxY - boundingBox.minY;

        return length * width * height;
    }

    private void addDefaultMurderWeapon(Item item) {
        if (item == null) {
            return;
        }

        String registryId = ItemSearchIndex.getRegistryId(item);
        if (registryId == null) {
            return;
        }

        murderWeapons.addItem(ItemSearchIndex.hasMultipleVariants(item) ? registryId + ":*" : registryId);
    }
}
