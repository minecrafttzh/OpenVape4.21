package keystrokesmod.utility;

import keystrokesmod.module.setting.impl.ItemListSetting;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ItemSearchIndex {
    private static final int MAX_SEARCH_RESULTS = 100;
    private static List<ItemEntry> allItemEntries;
    private static Map<String, List<ItemEntry>> variantsByRegistryId;

    public static final class ItemEntry {
        public final Item item;
        public final int meta;
        public final String displayName;
        public final String storageId;
        private final ItemStack previewStack;

        ItemEntry(Item item, int meta, String displayName, String storageId) {
            this(item, meta, displayName, storageId, item != null ? new ItemStack(item, 1, meta) : null);
        }

        ItemEntry(Item item, int meta, String displayName, String storageId, ItemStack previewStack) {
            this.item = item;
            this.meta = meta;
            this.displayName = displayName != null ? displayName : "";
            this.storageId = storageId;
            this.previewStack = previewStack != null ? previewStack.copy() : null;
        }

        public ItemStack toItemStack() {
            return previewStack != null ? previewStack.copy() : null;
        }
    }

    public static final class GroupedItemResult {
        public final String registryId;
        public final List<ItemEntry> variants;
        public final int score;
        private final String groupDisplayName;
        private final String allSelectionStorageId;

        public GroupedItemResult(String registryId, List<ItemEntry> variants, int score, String groupDisplayName, String allSelectionStorageId) {
            this.registryId = registryId;
            this.variants = variants;
            this.score = score;
            this.groupDisplayName = groupDisplayName;
            this.allSelectionStorageId = allSelectionStorageId;
        }

        public boolean isSingleVariant() {
            return variants.size() <= 1;
        }

        public String getGroupLabel() {
            return groupDisplayName + " (" + variants.size() + ")";
        }

        public String getGroupDisplayName() {
            return groupDisplayName;
        }

        public String getAllSelectionStorageId() {
            return allSelectionStorageId;
        }

        public ItemStack getCyclingIcon() {
            int idx = (int) ((System.currentTimeMillis() / 1000) % variants.size());
            return variants.get(idx).toItemStack();
        }
    }

    private static final class ScoredItem {
        final ItemEntry entry;
        final int score;

        ScoredItem(ItemEntry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }

    private enum SyntheticItemCategory {
        FIST("@fist", "Fist", null) {
            @Override
            boolean matches(ItemStack stack) {
                return stack == null;
            }
        },
        SWORD("@category:sword", "Sword", new ItemStack(Items.diamond_sword)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemSword;
            }
        },
        BOW("@category:bow", "Bow", new ItemStack(Items.bow)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemBow;
            }
        },
        TOOL("@category:tool", "Tool", new ItemStack(Items.diamond_pickaxe)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && isToolLike(stack.getItem());
            }
        },
        AXE("@category:axe", "Axe", new ItemStack(Items.diamond_axe)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemAxe;
            }
        },
        PICKAXE("@category:pickaxe", "Pickaxe", new ItemStack(Items.diamond_pickaxe)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemPickaxe;
            }
        },
        SHOVEL("@category:shovel", "Shovel", new ItemStack(Items.diamond_shovel)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemSpade;
            }
        },
        HOE("@category:hoe", "Hoe", new ItemStack(Items.diamond_hoe)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemHoe;
            }
        },
        SHEARS("@category:shears", "Shears", new ItemStack(Items.shears)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemShears;
            }
        };

        private final String storageId;
        private final String displayName;
        private final ItemStack previewStack;

        SyntheticItemCategory(String storageId, String displayName, ItemStack previewStack) {
            this.storageId = storageId;
            this.displayName = displayName;
            this.previewStack = previewStack;
        }

        abstract boolean matches(ItemStack stack);

        ItemEntry toSearchEntry() {
            return new ItemEntry(null, 0, displayName, storageId, previewStack);
        }

        boolean isCoveredBy(List<String> selectedItems) {
            if (selectedItems == null || selectedItems.isEmpty()) {
                return false;
            }
            if (selectedItems.contains(storageId)) {
                return true;
            }
            return this != TOOL && isToolSubtype() && selectedItems.contains(TOOL.storageId);
        }

        private boolean isToolSubtype() {
            return this == AXE || this == PICKAXE || this == SHOVEL || this == HOE || this == SHEARS;
        }

        static SyntheticItemCategory fromStorageId(String storageId) {
            if (storageId == null) {
                return null;
            }
            for (SyntheticItemCategory category : values()) {
                if (category.storageId.equals(storageId)) {
                    return category;
                }
            }
            return null;
        }
    }

    private ItemSearchIndex() {
    }

    public static List<GroupedItemResult> searchGrouped(String query, ItemListSetting setting) {
        ensureItemList();
        if (query == null || (query = query.trim()).isEmpty()) {
            return Collections.emptyList();
        }

        String lowerQuery = query.toLowerCase();
        List<ScoredItem> scored = new ArrayList<ScoredItem>();
        for (ItemEntry entry : allItemEntries) {
            if (isCoveredBySetting(entry, setting)) {
                continue;
            }

            String displayNameLower = entry.displayName.toLowerCase();
            String searchId = getSearchId(entry.storageId).toLowerCase();
            int score = 0;

            if (displayNameLower.equals(lowerQuery)) {
                score = 1000;
            }
            else if (searchId.equals(lowerQuery)) {
                score = 900;
            }
            else if (displayNameLower.startsWith(lowerQuery)) {
                score = 800;
            }
            else if (searchId.startsWith(lowerQuery)) {
                score = 700;
            }
            else {
                String[] tokens = displayNameLower.split("\\s+");
                for (String token : tokens) {
                    if (token.startsWith(lowerQuery)) {
                        score = 600;
                        break;
                    }
                }
                if (score == 0 && displayNameLower.contains(lowerQuery)) {
                    score = 500;
                }
                else if (score == 0 && searchId.contains(lowerQuery)) {
                    score = 400;
                }
            }

            if (score > 0) {
                scored.add(new ScoredItem(entry, score));
            }
        }

        scored.sort(Comparator.<ScoredItem>comparingInt(scoredItem -> -scoredItem.score)
            .thenComparing(scoredItem -> scoredItem.entry.displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(scoredItem -> scoredItem.entry.storageId));

        Map<String, Integer> bestScoreByRegistry = new HashMap<String, Integer>();
        for (ScoredItem scoredItem : scored) {
            String registryId = getRegistryId(scoredItem.entry.storageId);
            if (registryId == null) {
                continue;
            }

            Integer currentBest = bestScoreByRegistry.get(registryId);
            if (currentBest == null || currentBest < scoredItem.score) {
                bestScoreByRegistry.put(registryId, scoredItem.score);
            }
        }

        List<GroupedItemResult> results = new ArrayList<GroupedItemResult>();
        Set<String> seen = new HashSet<String>();
        for (ScoredItem scoredItem : scored) {
            String registryId = getRegistryId(scoredItem.entry.storageId);
            if (registryId == null || !seen.add(registryId)) {
                continue;
            }

            List<ItemEntry> variants = getVariants(registryId);
            if (variants.isEmpty()) {
                continue;
            }

            int groupScore = bestScoreByRegistry.containsKey(registryId) ? bestScoreByRegistry.get(registryId) : scoredItem.score;
            results.add(new GroupedItemResult(
                registryId,
                variants,
                groupScore,
                getGroupDisplayName(registryId, variants),
                getAllSelectionStorageId(registryId)
            ));
            if (results.size() >= MAX_SEARCH_RESULTS) {
                break;
            }
        }

        results.sort(Comparator.<GroupedItemResult>comparingInt(groupedItemResult -> -groupedItemResult.score)
            .thenComparing(groupedItemResult -> groupedItemResult.getGroupDisplayName(), String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    public static List<ItemEntry> getVariants(String registryId) {
        ensureItemList();
        if (variantsByRegistryId == null) {
            return Collections.emptyList();
        }
        List<ItemEntry> variants = variantsByRegistryId.get(registryId);
        return variants != null ? variants : Collections.<ItemEntry>emptyList();
    }

    public static String getStorageId(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        String registryId = getRegistryId(stack.getItem());
        if (registryId == null) {
            return null;
        }

        if (shouldStoreMeta(stack.getItem())) {
            int meta = stack.getMetadata();
            return meta != 0 ? registryId + ":" + meta : registryId;
        }
        return registryId;
    }

    public static String getRegistryId(Item item) {
        if (item == null || Item.itemRegistry.getNameForObject(item) == null) {
            return null;
        }
        return Item.itemRegistry.getNameForObject(item).toString();
    }

    public static boolean hasMultipleVariants(Item item) {
        String registryId = getRegistryId(item);
        return registryId != null && getVariants(registryId).size() > 1;
    }

    public static boolean isWildcard(String storageId) {
        return storageId != null && storageId.endsWith(":*");
    }

    public static boolean isGroupedSelection(String storageId) {
        return SyntheticItemCategory.fromStorageId(storageId) != null || isWildcard(storageId);
    }

    public static String getAllSelectionStorageId(String groupId) {
        if (SyntheticItemCategory.fromStorageId(groupId) != null) {
            return groupId;
        }
        return groupId + ":*";
    }

    public static List<ItemEntry> getSelectionVariants(String storageId) {
        if (storageId == null || storageId.isEmpty()) {
            return Collections.emptyList();
        }

        SyntheticItemCategory syntheticCategory = SyntheticItemCategory.fromStorageId(storageId);
        if (syntheticCategory != null) {
            return getVariants(storageId);
        }

        if (isWildcard(storageId)) {
            return getVariants(getRegistryId(storageId));
        }

        return Collections.emptyList();
    }

    public static boolean matches(List<String> storageIds, ItemStack stack) {
        if (storageIds == null || storageIds.isEmpty()) {
            return false;
        }
        for (String storageId : storageIds) {
            if (matches(storageId, stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(String storageId, ItemStack stack) {
        if (storageId == null || storageId.isEmpty()) {
            return false;
        }

        SyntheticItemCategory syntheticCategory = SyntheticItemCategory.fromStorageId(storageId);
        if (syntheticCategory != null) {
            return syntheticCategory.matches(stack);
        }

        if (stack == null || stack.getItem() == null) {
            return false;
        }

        String stackStorageId = getStorageId(stack);
        if (stackStorageId == null) {
            return false;
        }

        if (storageId.equals(stackStorageId)) {
            return true;
        }

        String registryId = getRegistryId(stackStorageId);
        return registryId != null && storageId.equals(registryId + ":*");
    }

    public static double getMatchQuality(String storageId, ItemStack stack) {
        if (!matches(storageId, stack)) {
            return Double.NEGATIVE_INFINITY;
        }

        SyntheticItemCategory syntheticCategory = SyntheticItemCategory.fromStorageId(storageId);
        if (syntheticCategory == null) {
            return 0.0D;
        }

        switch (syntheticCategory) {
            case SWORD:
                return ItemSortScoring.getMeleeSelectionScore(stack);
            case BOW:
                return ItemSortScoring.getBowSelectionScore(stack);
            case TOOL:
                return ItemSortScoring.getToolSelectionScore(stack);
            case AXE:
                return ItemSortScoring.getAxeScore(stack);
            case PICKAXE:
                return ItemSortScoring.getPickaxeScore(stack);
            case SHOVEL:
                return ItemSortScoring.getShovelScore(stack);
            case HOE:
                return ItemSortScoring.getHoeScore(stack);
            case SHEARS:
                return ItemSortScoring.getShearsScore(stack);
            default:
                return 0.0D;
        }
    }

    public static String getRegistryId(String storageId) {
        if (storageId == null || storageId.isEmpty()) {
            return null;
        }
        if (SyntheticItemCategory.fromStorageId(storageId) != null) {
            return storageId;
        }
        if (storageId.endsWith(":*")) {
            return storageId.substring(0, storageId.length() - 2);
        }

        String[] parts = storageId.split(":");
        if (parts.length >= 3) {
            return parts[0] + ":" + parts[1];
        }
        if (parts.length == 2) {
            return storageId;
        }
        return null;
    }

    public static ItemStack getItemStack(String storageId) {
        ensureItemList();
        if (storageId == null || storageId.isEmpty()) {
            return null;
        }

        SyntheticItemCategory syntheticCategory = SyntheticItemCategory.fromStorageId(storageId);
        if (syntheticCategory != null) {
            return syntheticCategory.previewStack != null ? syntheticCategory.previewStack.copy() : null;
        }

        if (isWildcard(storageId)) {
            List<ItemEntry> variants = getVariants(getRegistryId(storageId));
            return variants.isEmpty() ? null : variants.get(0).toItemStack();
        }

        String registryId = getRegistryId(storageId);
        if (registryId == null) {
            return null;
        }

        List<ItemEntry> variants = getVariants(registryId);
        for (ItemEntry variant : variants) {
            if (storageId.equals(variant.storageId)) {
                return variant.toItemStack();
            }
        }

        Item item = getItemForName(registryId);
        if (item == null) {
            return null;
        }

        return new ItemStack(item, 1, getMetaFromStorageId(storageId));
    }

    public static String getDisplayName(String storageId) {
        SyntheticItemCategory syntheticCategory = SyntheticItemCategory.fromStorageId(storageId);
        if (syntheticCategory != null) {
            List<ItemEntry> variants = getVariants(storageId);
            return variants.size() > 1 ? syntheticCategory.displayName + " (All)" : syntheticCategory.displayName;
        }

        if (isWildcard(storageId)) {
            List<ItemEntry> variants = getVariants(getRegistryId(storageId));
            return !variants.isEmpty() ? getGroupDisplayName(getRegistryId(storageId), variants) + " (All)" : storageId;
        }

        ItemStack stack = getItemStack(storageId);
        return stack != null ? stack.getDisplayName() : storageId;
    }

    private static void ensureItemList() {
        if (allItemEntries != null) {
            return;
        }

        List<ItemEntry> regularEntries = new ArrayList<ItemEntry>();
        Map<String, List<ItemEntry>> variantMap = new HashMap<String, List<ItemEntry>>();

        for (Object obj : Item.itemRegistry) {
            Item item = (Item) obj;
            String registryId = getRegistryId(item);
            if (registryId == null) {
                continue;
            }

            List<ItemStack> subItems = new ArrayList<ItemStack>();
            collectSubItems(item, subItems);
            if (subItems.isEmpty()) {
                subItems.add(new ItemStack(item, 1, 0));
            }

            Map<String, ItemEntry> dedupedEntries = new LinkedHashMap<String, ItemEntry>();
            for (ItemStack stack : subItems) {
                if (stack == null || stack.getItem() == null) {
                    continue;
                }

                String storageId = getStorageId(stack);
                if (storageId == null || dedupedEntries.containsKey(storageId)) {
                    continue;
                }

                String displayName = stack.getDisplayName();
                if (displayName == null || displayName.isEmpty()) {
                    continue;
                }

                int meta = shouldStoreMeta(item) ? stack.getMetadata() : 0;
                dedupedEntries.put(storageId, new ItemEntry(item, meta, displayName, storageId, stack));
            }

            if (dedupedEntries.isEmpty()) {
                continue;
            }

            List<ItemEntry> group = new ArrayList<ItemEntry>(dedupedEntries.values());
            group.sort(Comparator.comparingInt(itemEntry -> itemEntry.meta));
            regularEntries.addAll(group);
            variantMap.put(registryId, group);
        }

        allItemEntries = new ArrayList<ItemEntry>(regularEntries);
        for (SyntheticItemCategory category : SyntheticItemCategory.values()) {
            ItemEntry searchEntry = category.toSearchEntry();
            allItemEntries.add(searchEntry);

            List<ItemEntry> syntheticVariants = new ArrayList<ItemEntry>();
            for (ItemEntry entry : regularEntries) {
                if (category.matches(entry.toItemStack())) {
                    syntheticVariants.add(entry);
                }
            }

            syntheticVariants.sort(Comparator.comparing(itemEntry -> itemEntry.displayName, String.CASE_INSENSITIVE_ORDER));
            if (syntheticVariants.isEmpty()) {
                syntheticVariants.add(searchEntry);
            }
            variantMap.put(category.storageId, syntheticVariants);
        }

        allItemEntries.sort(Comparator.comparing((ItemEntry itemEntry) -> itemEntry.displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(itemEntry -> itemEntry.storageId));
        variantsByRegistryId = variantMap;
    }

    private static boolean isCoveredBySetting(ItemEntry entry, ItemListSetting setting) {
        if (setting == null) {
            return false;
        }

        SyntheticItemCategory syntheticCategory = SyntheticItemCategory.fromStorageId(entry.storageId);
        if (syntheticCategory != null) {
            return syntheticCategory.isCoveredBy(setting.getItems());
        }

        return setting.matches(entry.toItemStack());
    }

    private static String getSearchId(String storageId) {
        SyntheticItemCategory syntheticCategory = SyntheticItemCategory.fromStorageId(storageId);
        if (syntheticCategory != null) {
            return syntheticCategory.displayName;
        }

        String registryId = getRegistryId(storageId);
        if (registryId == null) {
            return storageId;
        }

        int separator = registryId.indexOf(':');
        return separator >= 0 ? registryId.substring(separator + 1) : registryId;
    }

    private static String getGroupDisplayName(String registryId, List<ItemEntry> variants) {
        SyntheticItemCategory syntheticCategory = SyntheticItemCategory.fromStorageId(registryId);
        if (syntheticCategory != null) {
            return syntheticCategory.displayName;
        }
        return !variants.isEmpty() ? variants.get(0).displayName : registryId;
    }

    private static void collectSubItems(Item item, List<ItemStack> subItems) {
        try {
            item.getSubItems(item, CreativeTabs.tabAllSearch, subItems);
        }
        catch (Exception ignored) {
        }

        if (!subItems.isEmpty()) {
            return;
        }

        CreativeTabs creativeTab = item.getCreativeTab();
        if (creativeTab == null) {
            return;
        }

        try {
            item.getSubItems(item, creativeTab, subItems);
        }
        catch (Exception ignored) {
        }
    }

    private static int getMetaFromStorageId(String storageId) {
        if (storageId == null) {
            return 0;
        }

        String[] parts = storageId.split(":");
        if (parts.length >= 3) {
            try {
                return Integer.parseInt(parts[2]);
            }
            catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static Item getItemForName(String registryId) {
        try {
            return (Item) Item.itemRegistry.getObject(new ResourceLocation(registryId));
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private static boolean shouldStoreMeta(Item item) {
        return item != null && item.getHasSubtypes() && !item.isDamageable();
    }

    private static boolean isToolLike(Item item) {
        return item instanceof ItemTool || item instanceof ItemHoe || item instanceof ItemShears;
    }
}
