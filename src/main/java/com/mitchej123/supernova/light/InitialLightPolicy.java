package com.mitchej123.supernova.light;

/**
 * Gates the first full sky+block BFS until after {@code Chunk.populate}.
 */
public final class InitialLightPolicy {

    private InitialLightPolicy() {}

    public static boolean shouldQueueFullLight(final boolean remote, final boolean terrainPopulated,
            final boolean lightReady) {
        return !remote && terrainPopulated && !lightReady;
    }

    public static boolean shouldTrustSavedLight(final boolean remote, final boolean terrainPopulated,
            final boolean savedEpochValid, final boolean hasSavedBlockData) {
        return !remote && terrainPopulated && savedEpochValid && hasSavedBlockData;
    }
}
