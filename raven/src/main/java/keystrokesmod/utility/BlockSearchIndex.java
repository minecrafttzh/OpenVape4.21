package keystrokesmod.utility;

import keystrokesmod.module.setting.impl.BlockListSetting;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlockSearchIndex {
    private static final int MAX_SEARCH_RESULTS = 100;
    private static List<BlockEntry> allBlockEntries = null;
    private static Map<String, List<BlockEntry>> variantsByRegistryId = null;

    private static final Map<Block, Item> BLOCK_TO_ITEM_FALLBACK = new HashMap<Block, Item>();

    static {
        BLOCK_TO_ITEM_FALLBACK.put(Blocks.bed, Items.bed);
    }

    public static final class BlockEntry {
        public final Block block;
        public final int meta;
        public final String displayName;
        public final String storageId;
        private final Item displayItem;

        BlockEntry(Block block, int meta, String displayName, String storageId) {
            this(block, meta, displayName, storageId, null);
        }

        BlockEntry(Block block, int meta, String displayName, String storageId, Item displayItem) {
            this.block = block;
            this.meta = meta;
            this.displayName = displayName != null ? displayName : "";
            this.storageId = storageId;
            this.displayItem = displayItem;
        }

        public ItemStack toItemStack() {
            if (displayItem != null) return new ItemStack(displayItem, 1, meta);
            return new ItemStack(block, 1, meta);
        }
    }

    public static final class GroupedBlockResult {
        public final String registryId;
        public final List<BlockEntry> variants;
        public final int score;

        public GroupedBlockResult(String registryId, List<BlockEntry> variants, int score) {
            this.registryId = registryId;
            this.variants = variants;
            this.score = score;
        }

        public boolean isSingleVariant() {
            return variants.size() <= 1;
        }

        public String getGroupLabel() {
            return variants.get(0).displayName + " (" + variants.size() + ")";
        }

        public ItemStack getCyclingIcon() {
            int idx = (int) ((System.currentTimeMillis() / 1000) % variants.size());
            return variants.get(idx).toItemStack();
        }
    }

    private static final class ScoredBlock {
        final BlockEntry entry;
        final int score;

        ScoredBlock(BlockEntry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }

    public static List<BlockEntry> search(String query, BlockListSetting setting) {
        ensureBlockList();
        if (query == null || (query = query.trim()).isEmpty()) return Collections.emptyList();
        String lq = query.toLowerCase();
        List<ScoredBlock> scored = new ArrayList<>();
        for (BlockEntry entry : allBlockEntries) {
            String locLower = entry.displayName.toLowerCase();
            String reg = entry.storageId.toLowerCase();
            String regPath = reg.indexOf(':') >= 0 ? reg.substring(reg.indexOf(':') + 1) : reg;
            int score = 0;
            if (locLower.equals(lq)) score = 1000;
            else if (regPath.equals(lq)) score = 900;
            else if (locLower.startsWith(lq)) score = 800;
            else if (regPath.startsWith(lq)) score = 700;
            else {
                String[] tokens = locLower.split("\\s+");
                for (String t : tokens) {
                    if (t.startsWith(lq)) { score = 600; break; }
                }
                if (score == 0 && locLower.contains(lq)) score = 500;
                else if (score == 0 && regPath.contains(lq)) score = 400;
            }
            if (score > 0 && !setting.contains(entry.storageId)) scored.add(new ScoredBlock(entry, score));
        }
        scored.sort(Comparator.<ScoredBlock>comparingInt(s -> -s.score).thenComparing(s -> s.entry.displayName, String.CASE_INSENSITIVE_ORDER).thenComparing(s -> s.entry.storageId));
        List<BlockEntry> results = new ArrayList<>(Math.min(MAX_SEARCH_RESULTS, scored.size()));
        for (int i = 0; i < Math.min(MAX_SEARCH_RESULTS, scored.size()); i++)
            results.add(scored.get(i).entry);
        return results;
    }

    public static List<GroupedBlockResult> searchGrouped(String query, BlockListSetting setting) {
        ensureBlockList();
        if (query == null || (query = query.trim()).isEmpty()) return Collections.emptyList();
        String lq = query.toLowerCase();
        List<ScoredBlock> scored = new ArrayList<>();
        for (BlockEntry entry : allBlockEntries) {
            if (setting.contains(entry.storageId)) continue;
            String wildcard = getRegistryId(entry.storageId);
            if (wildcard != null && setting.contains(wildcard + ":*")) continue;
            String locLower = entry.displayName.toLowerCase();
            String reg = entry.storageId.toLowerCase();
            String regPath = reg.indexOf(':') >= 0 ? reg.substring(reg.indexOf(':') + 1) : reg;
            int score = 0;
            if (locLower.equals(lq)) score = 1000;
            else if (regPath.equals(lq)) score = 900;
            else if (locLower.startsWith(lq)) score = 800;
            else if (regPath.startsWith(lq)) score = 700;
            else {
                String[] tokens = locLower.split("\\s+");
                for (String t : tokens) {
                    if (t.startsWith(lq)) { score = 600; break; }
                }
                if (score == 0 && locLower.contains(lq)) score = 500;
                else if (score == 0 && regPath.contains(lq)) score = 400;
            }
            if (score > 0) scored.add(new ScoredBlock(entry, score));
        }
        scored.sort(Comparator.<ScoredBlock>comparingInt(s -> -s.score).thenComparing(s -> s.entry.displayName, String.CASE_INSENSITIVE_ORDER).thenComparing(s -> s.entry.storageId));
        Map<String, Integer> bestScoreByRegistry = new HashMap<>();
        for (ScoredBlock sb : scored) {
            String rid = getRegistryId(sb.entry.storageId);
            if (rid != null && (!bestScoreByRegistry.containsKey(rid) || bestScoreByRegistry.get(rid) < sb.score))
                bestScoreByRegistry.put(rid, sb.score);
        }
        List<GroupedBlockResult> results = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ScoredBlock sb : scored) {
            String rid = getRegistryId(sb.entry.storageId);
            if (rid == null || seen.contains(rid)) continue;
            seen.add(rid);
            List<BlockEntry> variants = getVariants(rid);
            if (variants.isEmpty()) continue;
            int groupScore = bestScoreByRegistry.containsKey(rid) ? bestScoreByRegistry.get(rid) : sb.score;
            results.add(new GroupedBlockResult(rid, variants, groupScore));
            if (results.size() >= MAX_SEARCH_RESULTS) break;
        }
        results.sort(Comparator.<GroupedBlockResult>comparingInt(g -> -g.score).thenComparing(g -> g.variants.get(0).displayName, String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    public static List<BlockEntry> getVariants(String registryId) {
        ensureBlockList();
        if (variantsByRegistryId == null) return Collections.emptyList();
        List<BlockEntry> list = variantsByRegistryId.get(registryId);
        return list != null ? list : Collections.<BlockEntry>emptyList();
    }

    public static boolean isWildcard(String storageId) {
        return storageId != null && storageId.endsWith(":*");
    }

    public static String getRegistryId(String storageId) {
        if (storageId == null || storageId.isEmpty()) return null;
        if (storageId.endsWith(":*")) return storageId.substring(0, storageId.length() - 2);
        String[] p = storageId.split(":");
        if (p.length >= 3) return p[0] + ":" + p[1];
        if (p.length == 2) return storageId;
        return null;
    }

    public static ItemStack getItemStack(String storageId) {
        String id = isWildcard(storageId) ? getRegistryId(storageId) : storageId;
        Block block = getBlockForName(id);
        if (block == null) return null;
        int meta = isWildcard(storageId) ? 0 : getMetaFromStorageId(storageId);
        Item fallback = BLOCK_TO_ITEM_FALLBACK.get(block);
        if (fallback != null) return new ItemStack(fallback, 1, meta);
        return new ItemStack(block, 1, meta);
    }

    public static String getDisplayName(String storageId) {
        if (isWildcard(storageId)) {
            ItemStack stack = getItemStack(getRegistryId(storageId));
            return stack != null ? stack.getDisplayName() + " (All)" : storageId;
        }
        ItemStack stack = getItemStack(storageId);
        return stack != null ? stack.getDisplayName() : storageId;
    }

    private static void ensureBlockList() {
        if (allBlockEntries != null) return;
        allBlockEntries = new ArrayList<>();
        Map<String, List<BlockEntry>> variantMap = new HashMap<>();
        for (Object obj : Block.blockRegistry) {
            Block block = (Block) obj;
            String registryId = Block.blockRegistry.getNameForObject(block) != null ? Block.blockRegistry.getNameForObject(block).toString() : null;
            if (registryId == null) continue;

            Item item = Item.getItemFromBlock(block);
            if (item == null) {
                Item fallback = BLOCK_TO_ITEM_FALLBACK.get(block);
                if (fallback != null) {
                    ItemStack displayStack = new ItemStack(fallback, 1, 0);
                    String displayName = displayStack.getDisplayName();
                    if (displayName != null && !displayName.isEmpty()) {
                        BlockEntry entry = new BlockEntry(block, 0, displayName, registryId, fallback);
                        allBlockEntries.add(entry);
                        variantMap.put(registryId, Collections.singletonList(entry));
                    }
                }
                continue;
            }

            List<ItemStack> sub = new ArrayList<>();
            block.getSubBlocks(item, CreativeTabs.tabBlock, sub);
            if (sub.isEmpty()) sub.add(new ItemStack(block, 1, 0));
            List<BlockEntry> group = new ArrayList<>();
            for (ItemStack stack : sub) {
                String displayName = stack.getDisplayName();
                if (displayName == null || displayName.isEmpty()) continue;
                int meta = stack.getMetadata();
                String storageId = meta != 0 ? registryId + ":" + meta : registryId;
                BlockEntry entry = new BlockEntry(block, meta, displayName, storageId);
                allBlockEntries.add(entry);
                group.add(entry);
            }
            if (!group.isEmpty()) {
                group.sort(Comparator.comparing(e -> e.meta));
                variantMap.put(registryId, group);
            }
        }
        allBlockEntries.sort(Comparator.comparing(e -> e.displayName, String.CASE_INSENSITIVE_ORDER));
        variantsByRegistryId = variantMap;
    }

    private static String parseRegistryId(String name) {
        if (name == null || name.isEmpty()) return null;
        String[] p = name.split(":");
        if (p.length >= 3) return p[0] + ":" + p[1];
        if (p.length == 2) return name;
        return null;
    }

    private static int getMetaFromStorageId(String name) {
        if (name == null) return 0;
        String[] p = name.split(":");
        if (p.length >= 3) try { return Integer.parseInt(p[2]); } catch (NumberFormatException e) { return 0; }
        return 0;
    }

    private static Block getBlockForName(String name) {
        String registryId = parseRegistryId(name);
        if (registryId == null) return null;
        try {
            return (Block) Block.blockRegistry.getObject(new ResourceLocation(registryId));
        } catch (Exception e) {
            return null;
        }
    }
}
