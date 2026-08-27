package keystrokesmod.utility;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemStack;

import java.util.Map;

public final class ItemSortScoring {
    private static final double FIRE_ASPECT_BONUS = 4.0D;
    private static final double FLAME_BONUS = 4.0D;
    private static final double BOW_KNOCKBACK_BONUS = 0.15D;
    private static final double MELEE_KNOCKBACK_BONUS = 0.1D;
    private static final double FULLY_CHARGED_ARROW_SPEED = 3.0D;
    private static final double BASE_ARROW_DAMAGE = 2.0D;
    private ItemSortScoring() {
    }

    public static double getMeleeDamage(ItemStack stack) {
        if (stack == null) {
            return 0.0D;
        }

        double damage = getAttackDamageAttribute(stack);
        damage += EnchantmentHelper.getModifierForCreature(stack, EnumCreatureAttribute.UNDEFINED);
        damage += EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack) * FIRE_ASPECT_BONUS;
        return damage;
    }

    public static double getMeleeSelectionScore(ItemStack stack) {
        if (stack == null) {
            return 0.0D;
        }

        double score = getMeleeDamage(stack);
        score += EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack) * MELEE_KNOCKBACK_BONUS;
        score += getDurabilityTieBreaker(stack);
        return score;
    }

    public static double getBowDamage(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBow)) {
            return 0.0D;
        }

        int powerLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
        int flameLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack);

        double projectileDamage = BASE_ARROW_DAMAGE + powerLevel * 0.5D + 0.5D;
        double impactDamage = FULLY_CHARGED_ARROW_SPEED * projectileDamage;
        double damage = impactDamage + impactDamage * 0.25D + 0.5D;
        damage += flameLevel * FLAME_BONUS;
        return damage;
    }

    public static double getBowSelectionScore(ItemStack stack) {
        if (stack == null) {
            return 0.0D;
        }

        double score = getBowDamage(stack);
        score += EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, stack) * BOW_KNOCKBACK_BONUS;
        score += getDurabilityTieBreaker(stack);
        return score;
    }

    public static double getToolSelectionScore(ItemStack stack) {
        return Math.max(
            Math.max(getAxeScore(stack), getPickaxeScore(stack)),
            Math.max(Math.max(getShovelScore(stack), getHoeScore(stack)), getShearsScore(stack))
        );
    }

    public static double getAxeScore(ItemStack stack) {
        return getBlockBreakingScore(stack, Blocks.log);
    }

    public static double getPickaxeScore(ItemStack stack) {
        return getBlockBreakingScore(stack, Blocks.stone);
    }

    public static double getShovelScore(ItemStack stack) {
        return getBlockBreakingScore(stack, Blocks.dirt);
    }

    public static double getHoeScore(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemHoe)) {
            return 0.0D;
        }

        double score = stack.getMaxDamage();
        score += getDurabilityTieBreaker(stack);
        return score;
    }

    public static double getShearsScore(ItemStack stack) {
        return getBlockBreakingScore(stack, Blocks.web);
    }

    public static double getBlockBreakingScore(ItemStack stack, Block block) {
        if (stack == null || block == null) {
            return 0.0D;
        }

        float speed = stack.getStrVsBlock(block);
        if (speed > 1.0F) {
            int efficiencyLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
            if (efficiencyLevel > 0) {
                speed += efficiencyLevel * efficiencyLevel + 1;
            }
        }

        if (speed <= 1.0F) {
            return 0.0D;
        }

        if (!block.getMaterial().isToolNotRequired() && !stack.canHarvestBlock(block)) {
            speed *= 0.3F;
        }

        return speed + getDurabilityTieBreaker(stack);
    }

    private static double getAttackDamageAttribute(ItemStack stack) {
        if (stack == null) {
            return 0.0D;
        }

        for (Map.Entry<String, AttributeModifier> entry : stack.getAttributeModifiers().entries()) {
            if (SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName().equals(entry.getKey())) {
                return entry.getValue().getAmount();
            }
        }

        return 0.0D;
    }

    private static double getDurabilityTieBreaker(ItemStack stack) {
        if (stack == null || !stack.isItemStackDamageable()) {
            return 0.0D;
        }

        int maxDurability = stack.getMaxDamage();
        if (maxDurability <= 0) {
            return 0.0D;
        }

        int remainingDurability = maxDurability - stack.getItemDamage();
        return remainingDurability / (double) maxDurability / 1000.0D;
    }
}
