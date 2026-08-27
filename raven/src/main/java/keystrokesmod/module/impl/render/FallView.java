package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.block.material.Material;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class FallView extends Module {
    private ButtonSetting disableWhileFlying;
    private ButtonSetting onlyWhileSneaking;
    private ButtonSetting overrideHealthFormat;
    private ButtonSetting showDamage;
    private ButtonSetting showDistance;
    private SliderSetting damageThreshold;

    private double fallStartY = -1;
    private double groundY = -1;
    private float cachedFallDistance = 0;
    private int cachedEnchantmentModifier = -1;
    private ItemStack[] cachedArmorInventory = new ItemStack[4];
    private boolean armorCacheValid = false;

    private final Map<DamageCacheKey, Integer> damageCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    private String damageText;
    private boolean showDamageText;
    private String distanceText;
    private int distanceTextColor = Color.WHITE.getRGB();
    private boolean showDistanceText;

    public FallView() {
        super("Fall View", category.render);
        this.registerSetting(new DescriptionSetting("Shows fall distance damage."));
        this.registerSetting(damageThreshold = new SliderSetting("Damage threshold", "%", 0.0, 0.0, 100.0, 5.0));
        this.registerSetting(disableWhileFlying = new ButtonSetting("Disable while flying", true));
        this.registerSetting(onlyWhileSneaking = new ButtonSetting("Only while sneaking", false));
        this.registerSetting(overrideHealthFormat = new ButtonSetting("Override health format", true));
        this.registerSetting(showDamage = new ButtonSetting("Show damage", true));
        this.registerSetting(showDistance = new ButtonSetting("Show distance", false));
    }

    @Override
    public void onDisable() {
        fallStartY = -1;
        groundY = -1;
        cachedFallDistance = 0;
        cachedEnchantmentModifier = -1;
        cachedArmorInventory = new ItemStack[4];
        armorCacheValid = false;
        damageCache.clear();
        clearOverlayState();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) {
            return;
        }

        clearOverlayState();
        if (mc.currentScreen != null) {
            return;
        }
        if (!Utils.nullCheck() || mc.thePlayer.capabilities.isCreativeMode) {
            return;
        }
        if (disableWhileFlying.isToggled() && mc.thePlayer.capabilities.allowFlying) {
            return;
        }
        if (onlyWhileSneaking.isToggled() && !mc.thePlayer.isSneaking()) {
            return;
        }

        boolean onGround = mc.thePlayer.onGround;
        if (onGround) {
            fallStartY = -1;
            groundY = -1;
            cachedFallDistance = 0;
        }
        else if (fallStartY == -1) {
            fallStartY = mc.thePlayer.posY;
            groundY = findGroundY(mc.thePlayer.posX, mc.thePlayer.posZ);
        }
        else {
            double newGroundY = findGroundY(mc.thePlayer.posX, mc.thePlayer.posZ);
            if (newGroundY != groundY) {
                groundY = newGroundY;
                cachedFallDistance = 0;
            }
        }

        float fallDistance = calculateFallDistance();
        if (fallDistance <= 2.5f) {
            return;
        }

        PotionEffect jumpEffect = mc.thePlayer.getActivePotionEffect(Potion.jump);
        float jumpAmplifier = jumpEffect != null ? (float) (jumpEffect.getAmplifier() + 1) : 0.0f;
        int jumpBoostLevel = jumpEffect != null ? jumpEffect.getAmplifier() + 1 : 0;

        PotionEffect resistanceEffect = mc.thePlayer.getActivePotionEffect(Potion.resistance);
        boolean hasResistance = resistanceEffect != null;
        int resistanceLevel = hasResistance ? resistanceEffect.getAmplifier() + 1 : 0;

        ItemStack[] armorInventory = new ItemStack[4];
        boolean armorChanged = false;
        int armorHash = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack currentArmor = mc.thePlayer.inventory.armorItemInSlot(i);
            armorInventory[i] = currentArmor;
            if (cachedArmorInventory[i] != currentArmor) {
                armorChanged = true;
            }
            if (currentArmor != null) {
                armorHash = armorHash * 31 + (currentArmor.getItem() != null ? currentArmor.getItem().hashCode() : 0);
                armorHash = armorHash * 31 + currentArmor.getItemDamage();
                armorHash = armorHash * 31 + EnchantmentHelper.getEnchantmentLevel(
                        net.minecraft.enchantment.Enchantment.featherFalling.effectId,
                        currentArmor
                );
            }
        }

        int enchantmentModifier = cachedEnchantmentModifier;
        if (armorChanged || !armorCacheValid) {
            long totalModifier = 0;
            for (int i = 0; i < 100; i++) {
                int mod = EnchantmentHelper.getEnchantmentModifierDamage(armorInventory, DamageSource.fall);
                if (mod > 20) {
                    mod = 20;
                }
                totalModifier += mod;
            }
            enchantmentModifier = (int) Math.round(totalModifier / 100.0);
            cachedEnchantmentModifier = enchantmentModifier;
            System.arraycopy(armorInventory, 0, cachedArmorInventory, 0, 4);
            armorCacheValid = true;
            if (damageCache.size() > MAX_CACHE_SIZE / 2) {
                damageCache.clear();
            }
        }

        DamageCacheKey cacheKey = new DamageCacheKey(armorHash, fallDistance, jumpBoostLevel, resistanceLevel);
        Integer cachedFinalDamage = damageCache.get(cacheKey);
        int finalDamage;
        if (cachedFinalDamage != null) {
            finalDamage = cachedFinalDamage;
        }
        else {
            float damagePoints = fallDistance - 3.0f - jumpAmplifier;
            double damage = Math.max(0, MathHelper.ceiling_double_int(damagePoints));

            if (hasResistance && damage > 0) {
                int resistanceReduction = resistanceLevel * 5;
                int damageMultiplier = 25 - resistanceReduction;
                damage = damageMultiplier * damage / 25.0;
            }

            if (damage > 0 && enchantmentModifier > 0) {
                damage = (25 - enchantmentModifier) * damage / 25.0;
            }

            finalDamage = MathHelper.ceiling_double_int(damage);
            if (damageCache.size() >= MAX_CACHE_SIZE) {
                damageCache.clear();
            }
            damageCache.put(cacheKey, finalDamage);
        }

        double currentHealth = mc.thePlayer.getHealth();
        double damagePercent = (double) finalDamage / currentHealth * 100.0;
        if (showDamage.isToggled() && finalDamage > 0 && damagePercent > damageThreshold.getInput()) {
            float hearts = finalDamage;
            if (Settings.showHealthAsHearts.isToggled() || overrideHealthFormat.isToggled()) {
                hearts = finalDamage / 2.0f;
                hearts = (float) Utils.round(hearts, 1);
            }

            double percent = (double) finalDamage / currentHealth;
            String healthStr = finalDamage >= currentHealth
                    ? "\u00a74"
                    : (percent >= 0.7 ? "\u00a7c" : (percent >= 0.5 ? "\u00a76" : (percent >= 0.3 ? "\u00a7e" : "\u00a7a")));
            damageText = healthStr + Utils.asWholeNum(hearts);
            if (Settings.showHeartSymbol.isToggled()) {
                damageText += "\u00a7c\u2764\u00a7r";
            }
            showDamageText = true;
        }

        if (showDistance.isToggled()) {
            distanceText = Utils.asWholeNum(Utils.round(fallDistance, 2)) + "m";
            distanceTextColor = getDistanceColor(fallDistance).getRGB();
            showDistanceText = true;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || mc.currentScreen != null || !Utils.nullCheck()) {
            return;
        }
        if (!showDamageText && !showDistanceText) {
            return;
        }

        ScaledResolution scaledResolution = new ScaledResolution(mc);
        if (showDamageText && damageText != null) {
            mc.fontRendererObj.drawStringWithShadow(
                    damageText,
                    scaledResolution.getScaledWidth() / 2 - mc.fontRendererObj.getStringWidth(damageText) / 2,
                    scaledResolution.getScaledHeight() / 2 - 15,
                    Color.WHITE.getRGB()
            );
        }

        if (showDistanceText && distanceText != null) {
            mc.fontRendererObj.drawStringWithShadow(
                    distanceText,
                    scaledResolution.getScaledWidth() / 2 - mc.fontRendererObj.getStringWidth(distanceText) / 2,
                    scaledResolution.getScaledHeight() / 2 + 6,
                    distanceTextColor
            );
        }
    }

    private float calculateFallDistance() {
        if (fallStartY == -1 || groundY == -1) {
            double currentY = mc.thePlayer.posY;
            double ground = findGroundY(mc.thePlayer.posX, mc.thePlayer.posZ);
            if (ground == -1) {
                return 0;
            }
            return (float) Math.max(0, currentY - ground);
        }

        if (cachedFallDistance == 0) {
            cachedFallDistance = (float) Math.max(0, fallStartY - groundY);
        }
        return cachedFallDistance;
    }

    private double findGroundY(double x, double z) {
        int startY = (int) Math.floor(mc.thePlayer.posY);
        for (int y = startY; y > -1; y--) {
            BlockPos pos = new BlockPos(Math.floor(x), y, Math.floor(z));
            if (mc.theWorld.getBlockState(pos).getBlock().getMaterial() == Material.water) {
                return -1;
            }
            if (!Utils.isPlaceable(pos)) {
                return y + 1;
            }
        }
        return -1;
    }

    private Color getDistanceColor(float distance) {
        float minDistance = 2.5f;
        float maxDistance = 20.0f;
        float normalized = MathHelper.clamp_float((distance - minDistance) / (maxDistance - minDistance), 0.0f, 1.0f);

        int red = 255;
        int green = (int) (255 * (1.0f - normalized));
        int blue = 0;
        return new Color(red, green, blue);
    }

    private void clearOverlayState() {
        damageText = null;
        showDamageText = false;
        distanceText = null;
        distanceTextColor = Color.WHITE.getRGB();
        showDistanceText = false;
    }

    private static class DamageCacheKey {
        final int armorHash;
        final int fallDistanceInt;
        final int jumpBoostLevel;
        final int resistanceLevel;

        DamageCacheKey(int armorHash, float fallDistance, int jumpBoost, int resistance) {
            this.armorHash = armorHash;
            this.fallDistanceInt = Math.round(fallDistance * 100);
            this.jumpBoostLevel = jumpBoost;
            this.resistanceLevel = resistance;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DamageCacheKey that = (DamageCacheKey) o;
            return armorHash == that.armorHash
                    && fallDistanceInt == that.fallDistanceInt
                    && jumpBoostLevel == that.jumpBoostLevel
                    && resistanceLevel == that.resistanceLevel;
        }

        @Override
        public int hashCode() {
            return armorHash * 31 * 31 * 31 + fallDistanceInt * 31 * 31 + jumpBoostLevel * 31 + resistanceLevel;
        }
    }
}
