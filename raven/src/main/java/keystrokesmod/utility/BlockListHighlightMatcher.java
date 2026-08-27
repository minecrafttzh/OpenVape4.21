package keystrokesmod.utility;

import keystrokesmod.module.setting.impl.BlockListSetting;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockListHighlightMatcher implements BlockHighlightMatcher {

    private final BlockListSetting setting;
    private Set<String> matcherIds;
    private Map<String, Object> matcherWildcards;
    private int lastListHash;

    public BlockListHighlightMatcher(BlockListSetting setting) {
        this.setting = setting;
        rebuildMatcher();
    }

    @Override
    public void beginScanPass() {
        int h = setting.getBlocks().hashCode();
        if (h != lastListHash) {
            rebuildMatcher();
        }
    }

    @Override
    public boolean isActive() {
        return !setting.getBlocks().isEmpty();
    }

    @Override
    public boolean matchesBlock(IBlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        if (block == null) {
            return false;
        }
        Object nameObj = Block.blockRegistry.getNameForObject(block);
        if (nameObj == null) {
            return false;
        }
        String registryId = nameObj.toString();
        if (matcherWildcards.containsKey(registryId)) {
            return true;
        }
        int meta = block.getMetaFromState(state);
        if (meta != 0) {
            if (matcherIds.contains(registryId + ":" + meta)) {
                return true;
            }
        }
        return matcherIds.contains(registryId);
    }

    @Override
    public boolean shouldIndexAt(BlockPos pos, IBlockState state) {
        return matchesBlock(state);
    }

    private void rebuildMatcher() {
        List<String> blocks = setting.getBlocks();
        lastListHash = blocks.hashCode();
        matcherIds = new HashSet<>();
        matcherWildcards = new HashMap<>();
        for (String id : blocks) {
            if (id.endsWith(":*")) {
                String base = id.substring(0, id.length() - 2);
                matcherWildcards.put(base, null);
            } else {
                matcherIds.add(id);
            }
        }
    }
}
