package com.mitchej123.supernova.light.engine;

import com.mitchej123.supernova.api.PackedColorLight;
import com.mitchej123.supernova.light.SWMRNibbleArray;
import com.mitchej123.supernova.light.SupernovaChunk;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.Arrays;

public abstract class SupernovaRGBEngine extends SupernovaEngine {

    protected static final int RGB_DIR_SHIFT = 40;
    protected static final int WHITE = PackedColorLight.pack(15, 15, 15);
    protected static final int MIN_ABSORPTION = PackedColorLight.pack(1, 1, 1);

    // Per-channel nibble caches for RGB. nibbleCacheR aliases nibbleCache (same object refs)
    protected final SWMRNibbleArray[] nibbleCacheR;
    protected final SWMRNibbleArray[] nibbleCacheG;
    protected final SWMRNibbleArray[] nibbleCacheB;

    // Packed RGB cache -- stores pre-packed 0x0R0G0B values per section for fast BFS access.
    protected final int[][] packedRGBCache;
    protected final boolean[] packedCacheDirty;
    protected final int[] packedOr;
    protected final int[] packedAnd;

    // Temporary storage for fresh nibbles during light() -- saved to chunk after lighting
    protected SWMRNibbleArray[] currentLightNibblesG;
    protected SWMRNibbleArray[] currentLightNibblesB;

    protected SupernovaRGBEngine(final boolean skylightPropagator, final World world) {
        super(skylightPropagator, world);
        final int totalLightSections = (this.maxLightSection - this.minLightSection + 1) + 2;
        final int cacheSize = 5 * 5 * totalLightSections;
        this.nibbleCacheR = new SWMRNibbleArray[cacheSize];
        this.nibbleCacheG = new SWMRNibbleArray[cacheSize];
        this.nibbleCacheB = new SWMRNibbleArray[cacheSize];
        this.packedRGBCache = new int[cacheSize][];
        this.packedCacheDirty = new boolean[cacheSize];
        this.packedOr = new int[cacheSize];
        this.packedAnd = new int[cacheSize];
    }

    /** Save G/B nibbles to the chunk after lighting. Block -> setBlockNibblesG/B, Sky -> setSkyNibblesG/B. */
    protected abstract void saveChannelNibbles(SupernovaChunk chunk, SWMRNibbleArray[] g, SWMRNibbleArray[] b);

    /** Load R/G/B nibbles from chunk into cache. Block -> getBlockNibblesR/G/B, Sky -> getSkyNibblesR/G/B. */
    protected abstract void loadChannelNibbles(SupernovaChunk chunk, int cx, int cz);

    /** Sync dirty nibble data back to vanilla ExtendedBlockStorage for save persistence. */
    protected abstract void syncToVanilla(ExtendedBlockStorage section, SWMRNibbleArray nibR, SWMRNibbleArray nibG, SWMRNibbleArray nibB, int minByte, int maxByte);

    protected void setChannelNibblesForChunkInCache(final SWMRNibbleArray[] cache, final int chunkX, final int chunkZ, final SWMRNibbleArray[] nibbles) {
        for (int cy = this.minLightSection; cy <= this.maxLightSection; ++cy) {
            cache[chunkX + 5 * chunkZ + (5 * 5) * cy + this.chunkSectionIndexOffset] = nibbles == null ? null : nibbles[cy - this.minLightSection];
        }
    }

    @Override
    protected void setupExtraLightNibbles(final int chunkX, final int chunkZ) {
        for (int cy = this.minLightSection; cy <= this.maxLightSection; ++cy) {
            final int idx = chunkX + 5 * chunkZ + (5 * 5) * cy + this.chunkSectionIndexOffset;
            this.nibbleCacheR[idx] = this.nibbleCache[idx];
        }
        this.currentLightNibblesG = getFilledEmptyLight();
        this.currentLightNibblesB = getFilledEmptyLight();
        setChannelNibblesForChunkInCache(this.nibbleCacheG, chunkX, chunkZ, this.currentLightNibblesG);
        setChannelNibblesForChunkInCache(this.nibbleCacheB, chunkX, chunkZ, this.currentLightNibblesB);
    }

    @Override
    protected void saveExtraLightNibbles(final Chunk chunk) {
        saveChannelNibbles((SupernovaChunk) chunk, this.currentLightNibblesG, this.currentLightNibblesB);
        this.currentLightNibblesG = null;
        this.currentLightNibblesB = null;
    }

    @Override
    protected void loadExtraNibblesToCache(final int cx, final int cz, final Chunk chunk) {
        loadChannelNibbles((SupernovaChunk) chunk, cx, cz);
    }

    @Override
    protected void destroyExtraCaches() {
        Arrays.fill(this.nibbleCacheR, null);
        Arrays.fill(this.nibbleCacheG, null);
        Arrays.fill(this.nibbleCacheB, null);
        for (int i = 0; i < this.packedRGBCache.length; ++i) {
            releasePackedArray(this.packedRGBCache[i]);
            this.packedRGBCache[i] = null;
        }
        Arrays.fill(this.packedCacheDirty, false);
    }

    @Override
    protected int getLightLevel(final int worldX, final int worldY, final int worldZ) {
        final int idx = (worldX >> 4) + 5 * (worldZ >> 4) + (5 * 5) * (worldY >> 4) + this.chunkSectionIndexOffset;
        return getLightLevelFromCache(idx, (worldX & 15) | ((worldZ & 15) << 4) | ((worldY & 15) << 8));
    }

    @Override
    protected int getLightLevel(final int sectionIndex, final int localIndex) {
        return getLightLevelFromCache(sectionIndex, localIndex);
    }

    protected int getLightLevelFromCache(final int idx, final int localIndex) {
        int[] packed = this.packedRGBCache[idx];
        if (packed != null) {
            return packed[localIndex];
        }
        final SWMRNibbleArray nibR = this.nibbleCacheR[idx];
        if (nibR == null) {
            return 0;
        }
        this.packSectionToCache(idx);
        packed = this.packedRGBCache[idx];
        if (packed != null) {
            return packed[localIndex];
        }
        final int r = nibR.getUpdating(localIndex);
        final SWMRNibbleArray nibG = this.nibbleCacheG[idx];
        final int g = nibG == null ? 0 : nibG.getUpdating(localIndex);
        final SWMRNibbleArray nibB = this.nibbleCacheB[idx];
        final int b = nibB == null ? 0 : nibB.getUpdating(localIndex);
        return PackedColorLight.pack(r, g, b);
    }

    @Override
    protected long encodeQueueLevel(final int level) {
        return PackedColorLightQueue.encodeQueuePackedRGB(level);
    }

    @Override
    protected int getDirectionShift() {
        return RGB_DIR_SHIFT;
    }

    @Override
    protected boolean isBelowPropagationThreshold(final int level) {
        return PackedColorLight.maxComponent(level) <= 1;
    }

    @Override
    protected void setLightLevel(final int worldX, final int worldY, final int worldZ, final int packedRGB) {
        final int idx = (worldX >> 4) + 5 * (worldZ >> 4) + (5 * 5) * (worldY >> 4) + this.chunkSectionIndexOffset;
        final int localIndex = (worldX & 15) | ((worldZ & 15) << 4) | ((worldY & 15) << 8);
        if (setLightLevelInCache(idx, localIndex, packedRGB)) {
            this.postLightUpdate(idx, localIndex);
        }
    }

    /**
     * Writes the packed RGB value into the section cache / channel nibbles. The write itself is
     * unconditional -- BFS passes rely on its layer-sync side effects -- but the return value tells
     * callers whether any stored byte actually changed, so render notifications can be suppressed
     * for recomputations that produce identical light (perpetual fluid churn etc.).
     */
    protected boolean setLightLevelInCache(final int idx, final int localIndex, final int packedRGB) {
        boolean changed = false;
        final int[] packed = this.packedRGBCache[idx];
        if (packed != null) {
            changed = packed[localIndex] != packedRGB;
            packed[localIndex] = packedRGB;
            this.packedCacheDirty[idx] = true;
            this.packedOr[idx] |= packedRGB;
            this.packedAnd[idx] &= packedRGB;
            return changed;
        }
        final SWMRNibbleArray nibR = this.nibbleCacheR[idx];
        if (nibR != null) {
            changed |= nibR.getUpdating(localIndex) != PackedColorLight.red(packedRGB);
            nibR.set(localIndex, PackedColorLight.red(packedRGB));
        }
        final SWMRNibbleArray nibG = this.nibbleCacheG[idx];
        if (nibG != null) {
            changed |= nibG.getUpdating(localIndex) != PackedColorLight.green(packedRGB);
            nibG.set(localIndex, PackedColorLight.green(packedRGB));
        }
        final SWMRNibbleArray nibB = this.nibbleCacheB[idx];
        if (nibB != null) {
            changed |= nibB.getUpdating(localIndex) != PackedColorLight.blue(packedRGB);
            nibB.set(localIndex, PackedColorLight.blue(packedRGB));
        }
        return changed;
    }

    protected void packSectionToCache(final int idx) {
        final SWMRNibbleArray nibR = this.nibbleCacheR[idx];
        if (nibR == null) return;
        final SWMRNibbleArray nibG = this.nibbleCacheG[idx];
        final SWMRNibbleArray nibB = this.nibbleCacheB[idx];

        if (nibR.isFullUpdating() && (nibG == null || nibG.isFullUpdating()) && (nibB == null || nibB.isFullUpdating())) {
            final int[] packed = acquirePackedArray();
            Arrays.fill(packed, WHITE);
            this.packedRGBCache[idx] = packed;
            this.packedCacheDirty[idx] = false;
            this.packedOr[idx] = WHITE;
            this.packedAnd[idx] = WHITE;
            return;
        }
        if (nibR.isZeroUpdating() && (nibG == null || nibG.isZeroUpdating()) && (nibB == null || nibB.isZeroUpdating())) {
            final int[] packed = acquirePackedArray();
            Arrays.fill(packed, 0);
            this.packedRGBCache[idx] = packed;
            this.packedCacheDirty[idx] = false;
            this.packedOr[idx] = 0;
            this.packedAnd[idx] = 0;
            return;
        }

        final byte[] rData = nibR.getUpdatingStorage();
        if (rData == null) return;
        final byte[] gData = nibG != null ? nibG.getUpdatingStorage() : null;
        final byte[] bData = nibB != null ? nibB.getUpdatingStorage() : null;

        final int[] packedArr = acquirePackedArray();
        int or = 0, and = WHITE;
        if (gData != null && bData != null) {
            for (int byteIdx = 0; byteIdx < SWMRNibbleArray.ARRAY_SIZE; byteIdx++) {
                final int combined = ((rData[byteIdx] & 0xFF) << 16) | ((gData[byteIdx] & 0xFF) << 8) | (bData[byteIdx] & 0xFF);
                final int lo = combined & 0x0F0F0F;
                final int hi = (combined >>> 4) & 0x0F0F0F;
                packedArr[byteIdx * 2] = lo;
                packedArr[byteIdx * 2 + 1] = hi;
                or |= lo | hi;
                and &= lo & hi;
            }
        } else if (gData != null) {
            for (int byteIdx = 0; byteIdx < SWMRNibbleArray.ARRAY_SIZE; byteIdx++) {
                final int combined = ((rData[byteIdx] & 0xFF) << 16) | ((gData[byteIdx] & 0xFF) << 8);
                final int lo = combined & 0x0F0F0F;
                final int hi = (combined >>> 4) & 0x0F0F0F;
                packedArr[byteIdx * 2] = lo;
                packedArr[byteIdx * 2 + 1] = hi;
                or |= lo | hi;
                and &= lo & hi;
            }
        } else {
            for (int byteIdx = 0; byteIdx < SWMRNibbleArray.ARRAY_SIZE; byteIdx++) {
                final int combined = (rData[byteIdx] & 0xFF) << 16;
                final int lo = combined & 0x0F0F0F;
                final int hi = (combined >>> 4) & 0x0F0F0F;
                packedArr[byteIdx * 2] = lo;
                packedArr[byteIdx * 2 + 1] = hi;
                or |= lo | hi;
                and &= lo & hi;
            }
        }
        this.packedRGBCache[idx] = packedArr;
        this.packedCacheDirty[idx] = false;
        this.packedOr[idx] = or;
        this.packedAnd[idx] = and;
    }

    protected void unpackDirtySections() {
        for (int idx = 0; idx < this.packedRGBCache.length; ++idx) {
            if (!this.packedCacheDirty[idx]) continue;
            final int[] packed = this.packedRGBCache[idx];
            if (packed == null) continue;

            final SWMRNibbleArray nibR = this.nibbleCacheR[idx];
            final SWMRNibbleArray nibG = this.nibbleCacheG[idx];
            final SWMRNibbleArray nibB = this.nibbleCacheB[idx];
            if (nibR == null) continue;

            final int or = this.packedOr[idx];
            final int and = this.packedAnd[idx];

            if (and == WHITE && or == WHITE) {
                nibR.setFull();
                if (nibG != null) nibG.setFull();
                if (nibB != null) nibB.setFull();
            } else if (or == 0) {
                nibR.setZero();
                if (nibG != null) nibG.setZero();
                if (nibB != null) nibB.setZero();
            } else {
                unpackSectionFromCache(packed, nibR, nibG, nibB);
            }
            this.packedCacheDirty[idx] = false;
        }
    }

    protected static void unpackSectionFromCache(final int[] packed, final SWMRNibbleArray nibR, final SWMRNibbleArray nibG, final SWMRNibbleArray nibB) {
        final byte[] rBuf = nibR.prepareForBulkWrite();
        final byte[] gBuf = nibG != null ? nibG.prepareForBulkWrite() : null;
        final byte[] bBuf = nibB != null ? nibB.prepareForBulkWrite() : null;

        for (int byteIdx = 0; byteIdx < SWMRNibbleArray.ARRAY_SIZE; byteIdx++) {
            final int lo = packed[byteIdx * 2];
            final int hi = packed[byteIdx * 2 + 1];
            final int combined = lo | (hi << 4);
            rBuf[byteIdx] = (byte) (combined >>> 16);
            if (gBuf != null) gBuf[byteIdx] = (byte) (combined >>> 8);
            if (bBuf != null) bBuf[byteIdx] = (byte) combined;
        }
    }

    @Override
    protected void updateVisibleExtra() {
        this.unpackDirtySections();

        for (int index = 0, max = this.nibbleCacheG.length; index < max; ++index) {
            final SWMRNibbleArray nibR = this.nibbleCacheR[index];
            final SWMRNibbleArray nibG = this.nibbleCacheG[index];
            final SWMRNibbleArray nibB = this.nibbleCacheB[index];

            int dirtyMin = SWMRNibbleArray.ARRAY_SIZE;
            int dirtyMax = -1;
            if (nibR != null) {
                dirtyMin = Math.min(dirtyMin, nibR.getDirtyByteMin());
                dirtyMax = Math.max(dirtyMax, nibR.getDirtyByteMax());
            }
            if (nibG != null) {
                dirtyMin = Math.min(dirtyMin, nibG.getDirtyByteMin());
                dirtyMax = Math.max(dirtyMax, nibG.getDirtyByteMax());
            }
            if (nibB != null) {
                dirtyMin = Math.min(dirtyMin, nibB.getDirtyByteMin());
                dirtyMax = Math.max(dirtyMax, nibB.getDirtyByteMax());
            }

            if (nibR != null && nibR.isDirty()) nibR.updateVisible();
            if (nibG != null && nibG.isDirty()) nibG.updateVisible();
            if (nibB != null && nibB.isDirty()) nibB.updateVisible();

            if (nibR != null) nibR.resetDirtyRange();
            if (nibG != null) nibG.resetDirtyRange();
            if (nibB != null) nibB.resetDirtyRange();

            if (dirtyMin <= dirtyMax) {
                final ExtendedBlockStorage section = this.sectionCache[index];
                if (section != null) {
                    syncToVanilla(section, nibR, nibG, nibB, dirtyMin, dirtyMax);
                }
            }
        }
    }

    @Override
    protected void onNibbleVisible(final int cacheIndex, final SWMRNibbleArray nibble) {
        // Handled by updateVisibleExtra
    }

    @Override
    protected boolean areBothEdgeSectionsFull(final int currIdx, final int nIdx) {
        return isEdgeSectionFull(currIdx) && isEdgeSectionFull(nIdx);
    }

    private boolean isEdgeSectionFull(final int idx) {
        if (this.packedRGBCache[idx] != null) {
            return this.packedAnd[idx] == WHITE && this.packedOr[idx] == WHITE;
        }
        final SWMRNibbleArray r = this.nibbleCacheR[idx];
        final SWMRNibbleArray g = this.nibbleCacheG[idx];
        final SWMRNibbleArray b = this.nibbleCacheB[idx];
        return r != null && r.isFullUpdating() && g != null && g.isFullUpdating() && b != null && b.isFullUpdating();
    }

    @Override
    protected boolean areBothEdgeSectionsZero(final int currIdx, final int nIdx) {
        return isEdgeSectionZero(currIdx) && isEdgeSectionZero(nIdx);
    }

    private boolean isEdgeSectionZero(final int idx) {
        if (this.packedRGBCache[idx] != null) {
            return this.packedOr[idx] == 0;
        }
        final SWMRNibbleArray r = this.nibbleCacheR[idx];
        final SWMRNibbleArray g = this.nibbleCacheG[idx];
        final SWMRNibbleArray b = this.nibbleCacheB[idx];
        return (r == null || r.isZeroUpdating()) && (g == null || g.isZeroUpdating()) && (b == null || b.isZeroUpdating());
    }
}
