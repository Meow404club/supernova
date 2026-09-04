package com.mitchej123.supernova.light;

import com.mitchej123.supernova.api.ExtendedChunk;

/**
 * Internal extension of {@link ExtendedChunk} exposing nibble storage. Mixed into {@code net.minecraft.world.chunk.Chunk}.
 */
public interface SupernovaChunk extends ExtendedChunk {
    int LIGHT_CACHE_EPOCH = 2;

    int getLightEpoch();
    void setLightEpoch(int epoch);


    /** Sync Supernova SWMR visible data -> vanilla nibble arrays so chunk packets carry correct values. */
    void syncLightToVanilla();
    void setLightReady(boolean ready);
    void tryMarkLightPopulated();


    // Sky light
    SWMRNibbleArray[] getSkyNibbles();
    void setSkyNibbles(SWMRNibbleArray[] nibbles);

    // RGB sky light -- R aliases getSkyNibbles()
    SWMRNibbleArray[] getSkyNibblesR();
    SWMRNibbleArray[] getSkyNibblesG();
    SWMRNibbleArray[] getSkyNibblesB();

    void setSkyNibblesG(SWMRNibbleArray[] nibbles);
    void setSkyNibblesB(SWMRNibbleArray[] nibbles);

    boolean[] getSkyEmptinessMap();
    void setSkyEmptinessMap(boolean[] emptinessMap);

    // RGB block light -- one nibble array per channel per section
    SWMRNibbleArray[] getBlockNibblesR();
    SWMRNibbleArray[] getBlockNibblesG();
    SWMRNibbleArray[] getBlockNibblesB();

    void setBlockNibblesR(SWMRNibbleArray[] nibbles);
    void setBlockNibblesG(SWMRNibbleArray[] nibbles);
    void setBlockNibblesB(SWMRNibbleArray[] nibbles);

    boolean[] getBlockEmptinessMap();
    void setBlockEmptinessMap(boolean[] emptinessMap);
}
