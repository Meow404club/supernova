package com.mitchej123.supernova.compat.angelica;

import com.gtnewhorizons.angelica.api.SectionLightData;
import com.mitchej123.supernova.light.SWMRNibbleArray;

/**
 * Wraps SWMR nibble array references (R, G, B for block and sky) as a {@link SectionLightData}. Snapshots visible byte[] refs and section flags at construction
 * for zero-volatile hot-path reads.
 */
public class SupernovaSectionLightData implements SectionLightData {

    /** Constant fused value for all-zero block + all-white sky sections. */
    private static final long FUSED_ZERO_BLOCK_WHITE_SKY = 0xFFFL; // block=0, sky=0xFFF

    /**
     * Precomputed fused (block | sky) values for all 4096 positions in the section. Entry layout: {@code ((block & 0xFFFF) << 16) | (sky & 0xFFFF)}. Null when
     * allBlockZero && allSkyWhite (constant return).
     */
    private final long[] fusedCache;

    public SupernovaSectionLightData(SWMRNibbleArray r, SWMRNibbleArray g, SWMRNibbleArray b, SWMRNibbleArray skyR, SWMRNibbleArray skyG, SWMRNibbleArray skyB) {
        final byte[] rData = visibleData(r);
        final byte[] gData = visibleData(g);
        final byte[] bData = visibleData(b);
        final byte[] skyRData = visibleData(skyR);
        final byte[] skyGData = visibleData(skyG);
        final byte[] skyBData = visibleData(skyB);

        final boolean allBlockZero = isNullOrZero(r) && isNullOrZero(g) && isNullOrZero(b);
        final boolean allSkyWhite = isFull(skyR) && isFull(skyG) && isFull(skyB);

        if (allBlockZero && allSkyWhite) {
            this.fusedCache = null;
            return;
        }

        final long[] cache = new long[4096];
        for (int idx = 0; idx < 4096; idx++) {
            final int block = allBlockZero ? 0 : (extractNibble(rData, idx) << 8) | (extractNibble(gData, idx) << 4) | extractNibble(bData, idx);
            final int sr = extractSkyNibble(skyRData, idx);
            final int sg = skyGData == null ? sr : extractSkyNibble(skyGData, idx);
            final int sb = skyBData == null ? sr : extractSkyNibble(skyBData, idx);
            final int sky = allSkyWhite ? 0xFFF : (sr << 8) | (sg << 4) | sb;
            cache[idx] = ((long) (block & 0xFFFF) << 16) | (sky & 0xFFFF);
        }
        this.fusedCache = cache;
    }

    private static byte[] visibleData(SWMRNibbleArray nib) {
        return nib == null ? null : nib.getVisibleData();
    }

    private static boolean isNullOrZero(SWMRNibbleArray nib) {
        return nib == null || nib.isNullNibbleVisible() || nib.isZeroVisible();
    }

    private static boolean isFull(SWMRNibbleArray nib) {
        return nib != null && !nib.isNullNibbleVisible() && nib.isFullVisible();
    }

    @Override
    public int getRGB(int localX, int localY, int localZ) {
        if (fusedCache == null) return 0;
        final int idx = (localX & 15) | ((localZ & 15) << 4) | ((localY & 15) << 8);
        return (int) (fusedCache[idx] >>> 16) & 0xFFF;
    }

    @Override
    public int getSkyRGB(int localX, int localY, int localZ) {
        if (fusedCache == null) return 0xFFF;
        final int idx = (localX & 15) | ((localZ & 15) << 4) | ((localY & 15) << 8);
        return (int) fusedCache[idx] & 0xFFF;
    }

    @Override
    public long getRGBAndSkyRGB(int localX, int localY, int localZ) {
        if (fusedCache == null) return FUSED_ZERO_BLOCK_WHITE_SKY;
        final int idx = (localX & 15) | ((localZ & 15) << 4) | ((localY & 15) << 8);
        return fusedCache[idx];
    }

    private static int extractNibble(byte[] data, int idx) {
        if (data == null) return 0;
        return (data[idx >>> 1] >>> ((idx & 1) << 2)) & 0xF;
    }

    private static int extractSkyNibble(byte[] data, int idx) {
        if (data == null) return 0;
        return (data[idx >>> 1] >>> ((idx & 1) << 2)) & 0xF;
    }
}
