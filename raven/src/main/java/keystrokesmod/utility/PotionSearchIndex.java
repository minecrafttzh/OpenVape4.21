package keystrokesmod.utility;

import keystrokesmod.module.setting.impl.PotionListSetting;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PotionSearchIndex {
    private static final int MAX_SEARCH_RESULTS = 100;
    private static final int MAX_POTION_META = 16384;

    private static List<PotionEntry> allPotionEntries;
    private static Map<String, PotionEntry> entriesByKey;
    private static final Map<Integer, ItemStack> itemStacksByPotionId = new HashMap<Integer, ItemStack>();

    public static final class PotionEntry {
        public final int potionId;
        public final String key;
        public final String displayName;

        private PotionEntry(int potionId, String key, String displayName) {
            this.potionId = potionId;
            this.key = key;
            this.displayName = displayName != null ? displayName : "";
        }
    }

    private static final class ScoredPotionEntry {
        private final PotionEntry entry;
        private final int score;

        private ScoredPotionEntry(PotionEntry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }

    private PotionSearchIndex() {
    }

    public static List<PotionEntry> search(String query, PotionListSetting setting) {
        ensurePotionList();

        String trimmedQuery = query == null ? "" : query.trim();
        String lowerQuery = trimmedQuery.toLowerCase(Locale.ROOT);
        List<ScoredPotionEntry> scoredEntries = new ArrayList<ScoredPotionEntry>();

        for (PotionEntry entry : allPotionEntries) {
            if (setting.containsPotion(entry.key)) {
                continue;
            }

            int score = getScore(entry, lowerQuery);
            if (score > 0) {
                scoredEntries.add(new ScoredPotionEntry(entry, score));
            }
        }

        scoredEntries.sort(Comparator
                .comparingInt((ScoredPotionEntry scoredEntry) -> -scoredEntry.score)
                .thenComparing(scoredEntry -> scoredEntry.entry.displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(scoredEntry -> scoredEntry.entry.key));

        List<PotionEntry> results = new ArrayList<PotionEntry>(Math.min(MAX_SEARCH_RESULTS, scoredEntries.size()));
        for (int i = 0; i < Math.min(MAX_SEARCH_RESULTS, scoredEntries.size()); i++) {
            results.add(scoredEntries.get(i).entry);
        }
        return results;
    }

    public static String getDisplayName(String potionKey) {
        ensurePotionList();
        PotionEntry entry = entriesByKey.get(potionKey);
        return entry != null ? entry.displayName : potionKey;
    }

    public static ItemStack getItemStack(String potionKey) {
        ensurePotionList();
        PotionEntry entry = entriesByKey.get(potionKey);
        if (entry == null) {
            return null;
        }

        if (itemStacksByPotionId.containsKey(entry.potionId)) {
            ItemStack cachedStack = itemStacksByPotionId.get(entry.potionId);
            return cachedStack != null ? cachedStack.copy() : null;
        }

        ItemStack resolvedStack = resolvePotionItemStack(entry.potionId);
        itemStacksByPotionId.put(entry.potionId, resolvedStack);
        return resolvedStack != null ? resolvedStack.copy() : null;
    }

    private static int getScore(PotionEntry entry, String lowerQuery) {
        if (lowerQuery.isEmpty()) {
            return 1;
        }

        String displayName = entry.displayName.toLowerCase(Locale.ROOT);
        String key = entry.key.toLowerCase(Locale.ROOT);
        String simpleKey = getSimpleKey(entry.key).toLowerCase(Locale.ROOT);

        if (displayName.equals(lowerQuery)) {
            return 1000;
        }
        if (simpleKey.equals(lowerQuery)) {
            return 900;
        }
        if (displayName.startsWith(lowerQuery)) {
            return 800;
        }
        if (simpleKey.startsWith(lowerQuery)) {
            return 700;
        }

        for (String token : displayName.split("\\s+")) {
            if (token.startsWith(lowerQuery)) {
                return 600;
            }
        }

        if (displayName.contains(lowerQuery)) {
            return 500;
        }
        if (simpleKey.contains(lowerQuery) || key.contains(lowerQuery)) {
            return 400;
        }
        return 0;
    }

    private static String getSimpleKey(String potionKey) {
        if (potionKey == null || potionKey.isEmpty()) {
            return "";
        }

        int lastDotIndex = potionKey.lastIndexOf('.');
        return lastDotIndex >= 0 && lastDotIndex < potionKey.length() - 1 ? potionKey.substring(lastDotIndex + 1) : potionKey;
    }

    private static void ensurePotionList() {
        if (allPotionEntries != null && entriesByKey != null) {
            return;
        }

        Map<String, PotionEntry> keyedEntries = new LinkedHashMap<String, PotionEntry>();
        for (int potionId = 0; potionId < Potion.potionTypes.length; potionId++) {
            Potion potion = Potion.potionTypes[potionId];
            if (potion == null || potion.getName() == null || potion.getName().isEmpty()) {
                continue;
            }

            String potionKey = potion.getName();
            if (keyedEntries.containsKey(potionKey)) {
                continue;
            }

            String displayName = StatCollector.translateToLocal(potionKey);
            if (displayName == null || displayName.isEmpty() || displayName.equals(potionKey)) {
                displayName = getSimpleKey(potionKey);
            }

            keyedEntries.put(potionKey, new PotionEntry(potionId, potionKey, displayName));
        }

        ArrayList<PotionEntry> sortedEntries = new ArrayList<PotionEntry>(keyedEntries.values());
        sortedEntries.sort(Comparator
                .comparing((PotionEntry entry) -> entry.displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.key));

        allPotionEntries = Collections.unmodifiableList(sortedEntries);
        entriesByKey = keyedEntries;
    }

    private static ItemStack resolvePotionItemStack(int potionId) {
        if (!(Items.potionitem instanceof ItemPotion)) {
            return null;
        }

        ItemPotion potionItem = (ItemPotion) Items.potionitem;
        ItemStack bestStack = null;
        int bestScore = Integer.MIN_VALUE;

        for (int metadata = 0; metadata <= MAX_POTION_META; metadata++) {
            List<PotionEffect> effects = potionItem.getEffects(metadata);
            if (effects == null || effects.isEmpty()) {
                continue;
            }

            int score = 0;
            boolean matchesPotion = false;
            for (PotionEffect listedEffect : effects) {
                if (listedEffect.getPotionID() == potionId) {
                    matchesPotion = true;
                    score += 100;
                    if (listedEffect.getAmplifier() == 0) {
                        score += 10;
                    }
                }
                else {
                    score -= 20;
                }
            }

            if (!matchesPotion) {
                continue;
            }

            if (effects.size() == 1) {
                score += 50;
            }
            if (!ItemPotion.isSplash(metadata)) {
                score += 25;
            }

            if (score > bestScore) {
                bestScore = score;
                bestStack = new ItemStack(Items.potionitem, 1, metadata);
            }
        }

        return bestStack != null ? bestStack : new ItemStack(Items.potionitem);
    }
}
