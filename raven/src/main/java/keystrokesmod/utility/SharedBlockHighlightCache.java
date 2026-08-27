package keystrokesmod.utility;

import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.setting.impl.BlockListSetting;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SharedBlockHighlightCache {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final SharedBlockHighlightCache INSTANCE = new SharedBlockHighlightCache();

    private final Map<Long, Set<BlockPos>> blockListByChunk = new ConcurrentHashMap<>();
    private final Map<Long, Set<BlockPos>> bedFootByChunk = new ConcurrentHashMap<>();
    private final Set<UpdateListener> updateListeners = ConcurrentHashMap.newKeySet();
    private final Deque<long[]> scanQueue = new ArrayDeque<>();

    private BlockListHighlightMatcher blockListMatcher;
    private boolean bedAttached;

    private static final BedFootHighlightMatcher BED_MATCHER = new BedFootHighlightMatcher();

    private SharedBlockHighlightCache() {
    }

    public interface UpdateListener {
        void onBlockChanged(BlockPos pos, IBlockState newState);

        void onChunkQueued(int chunkX, int chunkZ);

        void onChunkRemoved(int chunkX, int chunkZ);

        void onCacheCleared();
    }

    public static SharedBlockHighlightCache get() {
        return INSTANCE;
    }

    public void attachBlockList(BlockListSetting setting) {
        this.blockListMatcher = new BlockListHighlightMatcher(setting);
    }

    public void detachBlockList() {
        this.blockListMatcher = null;
        blockListByChunk.clear();
    }

    public void attachBed() {
        this.bedAttached = true;
    }

    public void detachBed() {
        this.bedAttached = false;
        bedFootByChunk.clear();
    }

    private boolean isBlockListActive() {
        return blockListMatcher != null && blockListMatcher.isActive();
    }

    private boolean isBedActive() {
        return bedAttached;
    }

    public boolean anyConsumerActive() {
        return isBlockListActive() || isBedActive();
    }

    public void clear() {
        blockListByChunk.clear();
        bedFootByChunk.clear();
        scanQueue.clear();
        for (UpdateListener listener : updateListeners) {
            listener.onCacheCleared();
        }
    }

    public void addUpdateListener(UpdateListener listener) {
        if (listener != null) {
            updateListeners.add(listener);
        }
    }

    public void removeUpdateListener(UpdateListener listener) {
        if (listener != null) {
            updateListeners.remove(listener);
        }
    }

    public void enqueueChunk(int chunkX, int chunkZ) {
        if (!anyConsumerActive()) {
            return;
        }
        scanQueue.addLast(new long[]{chunkX, chunkZ});
        for (UpdateListener listener : updateListeners) {
            listener.onChunkQueued(chunkX, chunkZ);
        }
    }

    public void removeChunk(int chunkX, int chunkZ) {
        long k = key(chunkX, chunkZ);
        blockListByChunk.remove(k);
        bedFootByChunk.remove(k);
        for (UpdateListener listener : updateListeners) {
            listener.onChunkRemoved(chunkX, chunkZ);
        }
    }

    public void enqueueLoadedChunks() {
        if (!anyConsumerActive()) {
            return;
        }
        scanQueue.clear();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        int rd = mc.gameSettings.renderDistanceChunks;
        int pcx = (int) mc.thePlayer.posX >> 4;
        int pcz = (int) mc.thePlayer.posZ >> 4;
        for (int cx = pcx - rd; cx <= pcx + rd; cx++) {
            for (int cz = pcz - rd; cz <= pcz + rd; cz++) {
                Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
                if (chunk != null && !(chunk instanceof EmptyChunk)) {
                    enqueueChunk(cx, cz);
                }
            }
        }
    }

    public void tickScan(int maxSections) {
        if (mc.theWorld == null || !anyConsumerActive()) {
            return;
        }
        if (blockListMatcher != null) {
            blockListMatcher.beginScanPass();
        }
        int remaining = maxSections;
        while (remaining > 0 && !scanQueue.isEmpty()) {
            long[] cpos = scanQueue.pollFirst();
            int cx = (int) cpos[0], cz = (int) cpos[1];
            Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
            if (chunk == null || chunk instanceof EmptyChunk) {
                continue;
            }
            remaining -= scanChunk(chunk);
        }
    }

    public void onBlockChange(BlockPos pos, IBlockState newState) {
        if (blockListMatcher != null) {
            blockListMatcher.beginScanPass();
        }
        long ck = key(pos.getX() >> 4, pos.getZ() >> 4);
        BlockPos immutablePos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());

        if (isBlockListActive()) {
            if (blockListMatcher.matchesBlock(newState) && blockListMatcher.shouldIndexAt(pos, newState)) {
                blockListByChunk.computeIfAbsent(ck, k -> ConcurrentHashMap.newKeySet()).add(immutablePos);
            } else {
                Set<BlockPos> set = blockListByChunk.get(ck);
                if (set != null) {
                    set.remove(pos);
                }
            }
        }

        if (isBedActive()) {
            if (BED_MATCHER.matchesBlock(newState) && BED_MATCHER.shouldIndexAt(pos, newState)) {
                bedFootByChunk.computeIfAbsent(ck, k -> ConcurrentHashMap.newKeySet()).add(immutablePos);
            } else {
                Set<BlockPos> set = bedFootByChunk.get(ck);
                if (set != null) {
                    set.remove(pos);
                }
            }
        }

        for (UpdateListener listener : updateListeners) {
            listener.onBlockChanged(immutablePos, newState);
        }
    }

    public void onBlockListSettingsChanged() {
        if (blockListMatcher != null) {
            blockListMatcher.beginScanPass();
        }
        rescanBlockListLayer();
    }

    private void rescanBlockListLayer() {
        blockListByChunk.clear();
        scanQueue.clear();
        if (!anyConsumerActive()) {
            return;
        }
        enqueueLoadedChunks();
    }

    public Iterable<Map.Entry<Long, Set<BlockPos>>> entriesBlockList() {
        return blockListByChunk.entrySet();
    }

    public Iterable<Map.Entry<Long, Set<BlockPos>>> entriesBedFeet() {
        return bedFootByChunk.entrySet();
    }

    public int totalBlockList() {
        int n = 0;
        for (Set<BlockPos> s : blockListByChunk.values()) {
            n += s.size();
        }
        return n;
    }

    public int totalBedFeet() {
        int n = 0;
        for (Set<BlockPos> s : bedFootByChunk.values()) {
            n += s.size();
        }
        return n;
    }

    public boolean containsBlockList(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        long ck = key(pos.getX() >> 4, pos.getZ() >> 4);
        Set<BlockPos> set = blockListByChunk.get(ck);
        return set != null && set.contains(pos);
    }

    public void handleReceivePacket(ReceivePacketEvent e) {
        if (!anyConsumerActive()) {
            return;
        }
        if (e.getPacket() instanceof S23PacketBlockChange) {
            S23PacketBlockChange pkt = (S23PacketBlockChange) e.getPacket();
            onBlockChange(pkt.getBlockPosition(), pkt.getBlockState());
        } else if (e.getPacket() instanceof S22PacketMultiBlockChange) {
            S22PacketMultiBlockChange pkt = (S22PacketMultiBlockChange) e.getPacket();
            for (S22PacketMultiBlockChange.BlockUpdateData data : pkt.getChangedBlocks()) {
                onBlockChange(data.getPos(), data.getBlockState());
            }
        } else if (e.getPacket() instanceof S21PacketChunkData) {
            S21PacketChunkData pkt = (S21PacketChunkData) e.getPacket();
            if (pkt.getExtractedSize() == 0) {
                removeChunk(pkt.getChunkX(), pkt.getChunkZ());
            } else {
                enqueueChunk(pkt.getChunkX(), pkt.getChunkZ());
            }
        } else if (e.getPacket() instanceof S26PacketMapChunkBulk) {
            S26PacketMapChunkBulk pkt = (S26PacketMapChunkBulk) e.getPacket();
            for (int i = 0; i < pkt.getChunkCount(); i++) {
                enqueueChunk(pkt.getChunkX(i), pkt.getChunkZ(i));
            }
        }
    }

    private int scanChunk(Chunk chunk) {
        int scanned = 0;
        long ck = key(chunk.xPosition, chunk.zPosition);
        Set<BlockPos> blockFound = ConcurrentHashMap.newKeySet();
        Set<BlockPos> bedFound = ConcurrentHashMap.newKeySet();

        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        int baseX = chunk.xPosition << 4;
        int baseZ = chunk.zPosition << 4;

        for (int si = 0; si < sections.length; si++) {
            ExtendedBlockStorage section = sections[si];
            if (section == null) {
                continue;
            }
            scanned++;
            int baseY = si << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                        IBlockState state = section.get(x, y, z);
                        if (state == null) {
                            continue;
                        }
                        if (isBlockListActive() && blockListMatcher.matchesBlock(state) && blockListMatcher.shouldIndexAt(pos, state)) {
                            blockFound.add(pos);
                        }
                        if (isBedActive() && BED_MATCHER.matchesBlock(state) && BED_MATCHER.shouldIndexAt(pos, state)) {
                            bedFound.add(pos);
                        }
                    }
                }
            }
        }

        if (isBlockListActive()) {
            if (!blockFound.isEmpty()) {
                blockListByChunk.put(ck, blockFound);
            } else {
                blockListByChunk.remove(ck);
            }
        }
        if (isBedActive()) {
            if (!bedFound.isEmpty()) {
                bedFootByChunk.put(ck, bedFound);
            } else {
                bedFootByChunk.remove(ck);
            }
        }

        return Math.max(scanned, 1);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
