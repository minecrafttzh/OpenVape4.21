package keystrokesmod.module.impl.render;

import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FontManager;
import keystrokesmod.utility.font.RavenFontRenderer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Nametags extends Module {
    private static final float AUTO_SCALE_THRESHOLD = 5.0F;
    private static final Comparator<NametagRenderState> FAR_TO_NEAR = (a, b) -> Double.compare(b.distanceSq, a.distanceSq);
    private static final String[] HEALTH_DISPLAY_MODES = {"Hearts", "Health"};
    private static final String[] FONT_OPTIONS = FontManager.getHudFontOptions();
    private static final int ITEM_SPACING = 14;
    private static final int ENCHANT_LINE_HEIGHT = 8;
    private static final int ENCHANT_Y_OFFSET = 24;

    private SliderSetting scale;
    private SliderSetting font;
    private ButtonSetting autoScale;
    private ButtonSetting showRect;
    private ButtonSetting onlyRenderName;
    private SliderSetting bgOpacity;
    private ButtonSetting bgBorder;
    private ButtonSetting showHealth;
    private SliderSetting healthDisplayMode;
    private ButtonSetting showHeartSymbol;
    private ButtonSetting textShadow;
    private ButtonSetting showDistance;
    private ButtonSetting showInvis;
    private ButtonSetting showArmor;
    private ButtonSetting showEnchants;
    private ButtonSetting showDurability;
    private ButtonSetting showYourself;
    private ButtonSetting hideVanilla;
    private ColorSetting friendColor;
    private ColorSetting enemyColor;
    private final List<NametagRenderState> renderStates = new ArrayList<>();
    private int renderStateCount = 0;

    private static class NametagRenderState {
        private EntityPlayer player;
        private String displayName;
        private int stringHalfWidth;
        private int teamColor;
        private int relationshipColor;
        private int playerNameStart;
        private int playerNameEnd;
        private double distanceSq;
        private float baseScale;
        private float yOffset;
        private ItemStack heldItem;
        private ItemStack boots;
        private ItemStack leggings;
        private ItemStack chestplate;
        private ItemStack helmet;
        private int totalItems;

        private void set(EntityPlayer player, String displayName, int stringHalfWidth, int teamColor, int relationshipColor,
                         int playerNameStart, int playerNameEnd,
                         double distanceSq, float baseScale, float yOffset,
                         ItemStack heldItem, ItemStack boots, ItemStack leggings, ItemStack chestplate, ItemStack helmet,
                         int totalItems) {
            this.player = player;
            this.displayName = displayName;
            this.stringHalfWidth = stringHalfWidth;
            this.teamColor = teamColor;
            this.relationshipColor = relationshipColor;
            this.playerNameStart = playerNameStart;
            this.playerNameEnd = playerNameEnd;
            this.distanceSq = distanceSq;
            this.baseScale = baseScale;
            this.yOffset = yOffset;
            this.heldItem = heldItem;
            this.boots = boots;
            this.leggings = leggings;
            this.chestplate = chestplate;
            this.helmet = helmet;
            this.totalItems = totalItems;
        }
    }

    public Nametags() {
        super("Nametags", category.render, 0);
        this.registerSetting(scale = new SliderSetting("Scale", 1.0, 0.1, 2.0, 0.1));
        this.registerSetting(font = new SliderSetting("Font", 0, FONT_OPTIONS));
        this.registerSetting(autoScale = new ButtonSetting("Auto Scale", false));
        this.registerSetting(showRect = new ButtonSetting("Background", true));
        this.registerSetting(onlyRenderName = new ButtonSetting("Only render name", false));
        this.registerSetting(bgOpacity = new SliderSetting("Background Opacity", 0.5, 0.0, 1.0, 0.05));
        this.registerSetting(bgBorder = new ButtonSetting("Background Border", false));
        this.registerSetting(showHealth = new ButtonSetting("Show Health", false));
        this.registerSetting(healthDisplayMode = new SliderSetting("Health display", 0, HEALTH_DISPLAY_MODES));
        this.registerSetting(showHeartSymbol = new ButtonSetting("Show Heart Symbol", true));
        this.registerSetting(textShadow = new ButtonSetting("Text Shadow", false));
        this.registerSetting(showDistance = new ButtonSetting("Show Distance", false));
        this.registerSetting(showInvis = new ButtonSetting("Show Invis", true));
        this.registerSetting(showArmor = new ButtonSetting("Show Armor", false));
        this.registerSetting(showEnchants = new ButtonSetting("Show Enchantments", false));
        this.registerSetting(showDurability = new ButtonSetting("Show Durability", false));
        this.registerSetting(showYourself = new ButtonSetting("Show Yourself", false));
        this.registerSetting(hideVanilla = new ButtonSetting("Hide Vanilla", true));
        this.registerSetting(friendColor = new ColorSetting("Friend color", 85, 255, 255));
        this.registerSetting(enemyColor = new ColorSetting("Enemy color", 255, 85, 85));
    }

    @Override
    public void guiUpdate() {
        boolean healthOn = showHealth.isToggled();
        healthDisplayMode.setVisible(healthOn, this);
        showHeartSymbol.setVisible(healthOn && (int) healthDisplayMode.getInput() == 0, this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!Utils.nullCheck() || mc.theWorld == null) {
            renderStateCount = 0;
            return;
        }

        updateRenderStates();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }

        renderNametags(event.partialTicks);
    }

    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Specials.Pre event) {
        if (!hideVanilla.isToggled()) {
            return;
        }

        if (event.entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.entity;
            if (shouldRenderNametag(player)) {
                event.setCanceled(true);
            }
        }
    }

    private void updateRenderStates() {
        RavenFontRenderer fontRenderer = getNametagFontRenderer();
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            renderStateCount = 0;
            return;
        }

        boolean renderDistance = showDistance.isToggled();
        boolean renderArmor = showArmor.isToggled();
        float baseScale = computeBaseScaleValue();
        renderStateCount = 0;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!shouldRenderNametag(player)) {
                continue;
            }

            double dx = player.posX - viewer.posX;
            double dy = player.posY - viewer.posY;
            double dz = player.posZ - viewer.posZ;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            float distance = (float) Math.sqrt(distanceSq);

            String displayName = buildDisplayName(player, renderDistance, distance);
            int stringHalfWidth = fontRenderer.getStringWidth(displayName) / 2;
            int relationshipColor = resolveRelationshipColor(player);
            int[] playerNameRange = findVisiblePlayerNameRange(displayName, player.getName());

            ItemStack heldItem = null;
            ItemStack boots = null;
            ItemStack leggings = null;
            ItemStack chestplate = null;
            ItemStack helmet = null;
            int totalItems = 0;

            if (renderArmor) {
                heldItem = player.getEquipmentInSlot(0);
                if (heldItem != null) totalItems++;
                boots = player.getEquipmentInSlot(1);
                if (boots != null) totalItems++;
                leggings = player.getEquipmentInSlot(2);
                if (leggings != null) totalItems++;
                chestplate = player.getEquipmentInSlot(3);
                if (chestplate != null) totalItems++;
                helmet = player.getEquipmentInSlot(4);
                if (helmet != null) totalItems++;
            }

            if (renderStateCount >= renderStates.size()) {
                renderStates.add(new NametagRenderState());
            }

            renderStates.get(renderStateCount++).set(
                    player,
                    displayName,
                    stringHalfWidth,
                    Utils.getColorFromEntity(player),
                    relationshipColor,
                    playerNameRange[0],
                    playerNameRange[1],
                    distanceSq,
                    baseScale,
                    (player.isSneaking() ? (player.height - 0.3F) : player.height) + 0.3F,
                    heldItem,
                    boots,
                    leggings,
                    chestplate,
                    helmet,
                    totalItems
            );
        }

        if (renderStateCount > 1) {
            renderStates.subList(0, renderStateCount).sort(FAR_TO_NEAR);
        }
    }

    private void renderNametags(float partialTicks) {
        RenderManager renderManager = mc.getRenderManager();
        FontRenderer itemFontRenderer = mc.fontRendererObj;
        RavenFontRenderer textRenderer = getNametagFontRenderer();
        if (renderManager == null || itemFontRenderer == null || renderStateCount == 0) {
            return;
        }

        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(partialTicks, 0);

        for (int i = 0; i < renderStateCount; i++) {
            NametagRenderState renderState = renderStates.get(i);
            if (renderState.player == null || !RenderUtils.isInViewFrustum(renderState.player)) {
                continue;
            }
            renderCustomName(renderState, partialTicks, renderManager, textRenderer, itemFontRenderer);
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private boolean shouldRenderNametag(EntityPlayer player) {
        if (player == null) return false;
        if (player == mc.thePlayer) {
            return showYourself.isToggled() && mc.gameSettings.thirdPersonView != 0;
        }
        if (player.isDead || player.deathTime > 0) return false;
        if (!showInvis.isToggled() && player.isInvisible()) return false;
        return !AntiBot.isBot(player);
    }

    private String buildDisplayName(EntityPlayer entity, boolean showDist, float distance) {
        String name;

        if (onlyRenderName.isToggled()) {
            String formatted = Utils.getFirstColorCode(entity.getDisplayName().getFormattedText());
            String color = (formatted.length() >= 2 && formatted.charAt(0) == '\u00a7') ? formatted : "";
            name = color + entity.getName();
        }
        else {
            name = entity.getDisplayName().getFormattedText();
        }

        if (showHealth.isToggled()) {
            name = appendHealth(name, entity);
        }

        if (showDist) {
            int dist = (int) distance;
            String distColor = dist <= 8 ? "\u00a7c" : (dist <= 15 ? "\u00a76" : (dist <= 25 ? "\u00a7e" : "\u00a77"));
            name = distColor + dist + "m\u00a7r " + name;
        }

        return name;
    }

    private int resolveRelationshipColor(EntityPlayer entity) {
        if (Utils.isFriended(entity)) {
            return friendColor.getColor();
        }
        if (Utils.isEnemy(entity)) {
            return enemyColor.getColor();
        }
        return -1;
    }

    private float computeBaseScaleValue() {
        return (float) scale.getInput() * 0.02F;
    }

    private float computeScaleValue(float distance, boolean scaleByDistance) {
        float scaleValue = computeBaseScaleValue();
        if (!scaleByDistance) {
            return scaleValue;
        }

        float effectiveDistance = Math.max(1.0F, distance);
        float scaledValue = scaleValue * (effectiveDistance / AUTO_SCALE_THRESHOLD);
        return Math.max(scaleValue, scaledValue);
    }

    private void renderCustomName(NametagRenderState state, float partialTicks, RenderManager renderManager, RavenFontRenderer textRenderer, FontRenderer itemFontRenderer) {
        EntityPlayer entity = state.player;
        if (entity == null || entity.isDead || entity.deathTime > 0) {
            return;
        }

        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - renderManager.viewerPosX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - renderManager.viewerPosY;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - renderManager.viewerPosZ;
        float renderScale = state.baseScale;
        if (autoScale.isToggled()) {
            renderScale = computeScaleValue((float) Math.sqrt(x * x + y * y + z * z), true);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + state.yOffset, (float) z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-renderScale, -renderScale, renderScale);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.translate(0.0F, -10.0F, 0.0F);

        if ((showRect.isToggled() && bgOpacity.getInput() > 0.01) || bgBorder.isToggled() || state.relationshipColor != -1) {
            renderBackground(state.stringHalfWidth, 0.0f, state.teamColor, state.relationshipColor, textRenderer);
            applyNametagTextState();
        }

        drawDisplayName(state, textRenderer);
        applyNametagTextState();

        if (state.totalItems > 0) {
            int iconX = -(state.totalItems * ITEM_SPACING) / 2;
            int iconY = -20;

            if (state.heldItem != null) {
                renderItemStack(state.heldItem, iconX, iconY, itemFontRenderer);
                iconX += ITEM_SPACING;
            }
            if (state.helmet != null) {
                renderItemStack(state.helmet, iconX, iconY, itemFontRenderer);
                iconX += ITEM_SPACING;
            }
            if (state.chestplate != null) {
                renderItemStack(state.chestplate, iconX, iconY, itemFontRenderer);
                iconX += ITEM_SPACING;
            }
            if (state.leggings != null) {
                renderItemStack(state.leggings, iconX, iconY, itemFontRenderer);
                iconX += ITEM_SPACING;
            }
            if (state.boots != null) {
                renderItemStack(state.boots, iconX, iconY, itemFontRenderer);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void applyNametagTextState() {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawDisplayName(NametagRenderState state, RavenFontRenderer textRenderer) {
        if (state.relationshipColor == -1 || state.playerNameStart < 0 || state.playerNameEnd <= state.playerNameStart) {
            textRenderer.drawString(state.displayName, -state.stringHalfWidth, 0.0f, 0xFFFFFFFF, textShadow.isToggled());
            return;
        }

        final int[] visibleIndex = {0};
        textRenderer.drawGlyphString(state.displayName, -state.stringHalfWidth, 0.0f, (character, xOffset, width, formattingColor) -> {
            int glyphIndex = visibleIndex[0]++;
            if (glyphIndex >= state.playerNameStart && glyphIndex < state.playerNameEnd) {
                return state.relationshipColor;
            }
            return formattingColor != null ? formattingColor : 0xFFFFFFFF;
        }, textShadow.isToggled());
    }

    private void renderBackground(int stringWidth, float textY, int teamColor, int relationshipColor, RavenFontRenderer fontRenderer) {
        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        float alpha = (float) bgOpacity.getInput();
        float innerLeft = -stringWidth - 3.0f;
        float innerRight = stringWidth + 3.0f;
        float innerTop = textY + fontRenderer.getTextTopOffset() - 3.0f;
        float innerBottom = textY + fontRenderer.getTextBottomOffset() + 2.0f;
        boolean renderBaseFill = showRect.isToggled() && alpha > 0.01F;

        if (renderBaseFill) {
            worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            worldRenderer.pos(innerLeft, innerTop, 0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
            worldRenderer.pos(innerLeft, innerBottom, 0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
            worldRenderer.pos(innerRight, innerBottom, 0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
            worldRenderer.pos(innerRight, innerTop, 0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
            tessellator.draw();
        }

        int borderColor = relationshipColor != -1 ? relationshipColor : teamColor;
        if (bgBorder.isToggled() || relationshipColor != -1) {
            float red;
            float green;
            float blue;
            if (borderColor != -1) {
                red = ((borderColor >> 16) & 255) / 255.0F;
                green = ((borderColor >> 8) & 255) / 255.0F;
                blue = (borderColor & 255) / 255.0F;
            }
            else {
                red = 0.6F;
                green = 0.6F;
                blue = 0.6F;
            }

            float borderThickness = 1.0F;
            float borderAlpha = relationshipColor != -1 ? alpha : 1.0F;
            float left = innerLeft - borderThickness;
            float right = innerRight + borderThickness;
            float top = innerTop - borderThickness;
            float bottom = innerBottom + borderThickness;
            float borderZ = -0.001F;

            worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            worldRenderer.pos(left, top, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(left, innerTop, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(right, innerTop, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(right, top, borderZ).color(red, green, blue, borderAlpha).endVertex();

            worldRenderer.pos(left, innerBottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(left, bottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(right, bottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(right, innerBottom, borderZ).color(red, green, blue, borderAlpha).endVertex();

            worldRenderer.pos(left, innerTop, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(left, innerBottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(innerLeft, innerBottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(innerLeft, innerTop, borderZ).color(red, green, blue, borderAlpha).endVertex();

            worldRenderer.pos(innerRight, innerTop, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(innerRight, innerBottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(right, innerBottom, borderZ).color(red, green, blue, borderAlpha).endVertex();
            worldRenderer.pos(right, innerTop, borderZ).color(red, green, blue, borderAlpha).endVertex();
            tessellator.draw();
        }

        GlStateManager.enableTexture2D();
    }

    private int[] findVisiblePlayerNameRange(String formattedText, String playerName) {
        String strippedText = stripFormattingCodes(formattedText);
        int nameStart = strippedText.indexOf(playerName);
        if (nameStart < 0) {
            return new int[] {-1, -1};
        }
        return new int[] {nameStart, nameStart + playerName.length()};
    }

    private String stripFormattingCodes(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            builder.append(character);
        }
        return builder.toString();
    }

    private String getSelectedFontName() {
        if (font == null) {
            return FONT_OPTIONS[0];
        }

        int index = (int) Math.max(0, Math.min(font.getOptions().length - 1, font.getInput()));
        return font.getOptions()[index];
    }

    private RavenFontRenderer getNametagFontRenderer() {
        return FontManager.getNametagRenderer(getSelectedFontName());
    }

    private String appendHealth(String name, EntityPlayer entity) {
        float health = Math.max(0.0f, entity.getHealth());
        float maxHealth = entity.getMaxHealth();
        if (maxHealth <= 0.0f) maxHealth = 20.0f;

        boolean heartsMode = (int) healthDisplayMode.getInput() == 0;
        double ratio = health / maxHealth;

        String color = ratio < 0.3 ? "\u00a7c" : (ratio < 0.5 ? "\u00a76" : (ratio < 0.7 ? "\u00a7e" : "\u00a7a"));
        float displayValue = heartsMode ? health / 2.0f : health;
        String valueStr = fastOneDecimal(displayValue);
        String heartSuffix = heartsMode && showHeartSymbol.isToggled() ? " \u2764" : "";
        name = name + " " + color + valueStr + heartSuffix;

        float absorption = entity.getAbsorptionAmount();
        if (absorption > 0) {
            float absDisplay = heartsMode ? absorption / 2.0f : absorption;
            String absStr = fastOneDecimal(absDisplay);
            String absSuffix = heartsMode && showHeartSymbol.isToggled() ? " \u2764" : "";
            name = name + " \u00a76+" + absStr + absSuffix;
        }
        name = name + "\u00a7r";
        return name;
    }

    private String fastOneDecimal(float value) {
        int whole = (int) value;
        if (value == whole) {
            return String.valueOf(whole);
        }
        int tenths = Math.round(value * 10.0F);
        int intPart = tenths / 10;
        int fracPart = Math.abs(tenths % 10);
        return intPart + "." + fracPart;
    }

    private void renderItemStack(ItemStack stack, int xPos, int yPos, FontRenderer fontRenderer) {
        if (stack == null) {
            return;
        }

        RenderUtils.renderItemAndEffectIntoGui3D(stack, xPos, yPos);

        if (showEnchants.isToggled()) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5, 0.5, 0.5);
            GlStateManager.translate(0, -10, 0);
            renderEnchantText(stack, xPos, yPos, fontRenderer);
            GlStateManager.popMatrix();
        }

        GlStateManager.disableDepth();

        if (stack.stackSize > 1) {
            String countStr = String.valueOf(stack.stackSize);
            fontRenderer.drawStringWithShadow(countStr, xPos + 17 - fontRenderer.getStringWidth(countStr), yPos + 9, 0xFFFFFF);
        }

        if (showDurability.isToggled() && stack.isItemStackDamageable() && stack.getItemDamage() > 0) {
            int maxDamage = stack.getMaxDamage();
            int currentDamage = stack.getItemDamage();
            float durabilityRatio = 1.0F - (float) currentDamage / (float) maxDamage;
            RenderUtils.drawDurabilityBar(xPos, yPos, durabilityRatio);
        }

        GlStateManager.enableDepth();
    }

    private static final int[] ARMOR_ENCHANT_IDS = {0, 7, 34};
    private static final String[] ARMOR_ENCHANT_ABBR = {"P", "T", "U"};
    private static final int[] SWORD_ENCHANT_IDS = {16, 20, 19};
    private static final String[] SWORD_ENCHANT_ABBR = {"S", "F", "K"};
    private static final int[] BOW_ENCHANT_IDS = {48, 49, 50};
    private static final String[] BOW_ENCHANT_ABBR = {"Pw", "Pu", "Fl"};
    private static final int[] TOOL_ENCHANT_IDS = {32, 35, 34};
    private static final String[] TOOL_ENCHANT_ABBR = {"E", "Fo", "U"};
    private static final int[] MISC_ENCHANT_IDS = {19};
    private static final String[] MISC_ENCHANT_ABBR = {"K"};

    private void renderEnchantText(ItemStack stack, int xPos, int yPos, FontRenderer fontRenderer) {
        int[] ids;
        String[] abbreviations;
        Item item = stack.getItem();

        if (item instanceof ItemArmor) {
            ids = ARMOR_ENCHANT_IDS;
            abbreviations = ARMOR_ENCHANT_ABBR;
        }
        else if (item instanceof ItemSword) {
            ids = SWORD_ENCHANT_IDS;
            abbreviations = SWORD_ENCHANT_ABBR;
        }
        else if (item instanceof ItemBow) {
            ids = BOW_ENCHANT_IDS;
            abbreviations = BOW_ENCHANT_ABBR;
        }
        else if (item instanceof ItemTool) {
            ids = TOOL_ENCHANT_IDS;
            abbreviations = TOOL_ENCHANT_ABBR;
        }
        else {
            ids = MISC_ENCHANT_IDS;
            abbreviations = MISC_ENCHANT_ABBR;
        }

        int drawX = xPos * 2;
        int drawY = yPos - ENCHANT_Y_OFFSET;

        for (int i = 0; i < ids.length; i++) {
            int level = EnchantmentHelper.getEnchantmentLevel(ids[i], stack);
            if (level <= 0) {
                continue;
            }

            drawEnchantLine(fontRenderer, abbreviations[i], level, drawX, drawY);
            drawY += ENCHANT_LINE_HEIGHT;
        }
    }

    private void drawEnchantLine(FontRenderer fontRenderer, String abbreviation, int level, int x, int y) {
        fontRenderer.drawStringWithShadow(abbreviation, x, y, 0xFFFFFF);
        int advance = fontRenderer.getStringWidth(abbreviation);
        fontRenderer.drawStringWithShadow(String.valueOf(level), x + advance, y, colorForEnchantLevel(level));
    }

    private int colorForEnchantLevel(int level) {
        if (level <= 5) {
            if (level == 1) return 0xFFFFFF;
            if (level == 2) return 0x55FFFF;
            if (level == 3) return 0x00AAAA;
            if (level == 4) return 0xAA00AA;
            if (level == 5) return 0xFFAA00;
        }
        return 0xFF55FF;
    }
}
