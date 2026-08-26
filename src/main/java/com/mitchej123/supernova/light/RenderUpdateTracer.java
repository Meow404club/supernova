package com.mitchej123.supernova.light;

import com.mitchej123.supernova.config.SupernovaConfig;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Diagnostic attribution for {@code World.markBlockRangeForRenderUpdate} calls and light-queue feeds.
 * Active only while {@link SupernovaConfig#enableStatsLog} is on; otherwise every entry point costs a single
 * static field read. Counters cover one stats window and are reset by {@link LightStats}.
 */
public final class RenderUpdateTracer {

    private static final int SELF_NONE = 0;
    private static final int SELF_DRAIN = 1;
    private static final int SELF_ALL_CHUNKS = 2;

    private static final ThreadLocal<Integer> selfTag = ThreadLocal.withInitial(() -> SELF_NONE);

    // Window counters (written from the client main thread; worker threads only add externals)
    public static long drainMarks;
    public static long drainSections;
    public static long allChunkMarks;
    public static long allChunkSections;
    public static long externalMarks;
    public static long externalSections;
    public static int nightTriggerFires;

    // Client environment snapshot for correlating night-trigger storms (updated per tick by ClientProxy)
    public static volatile int clientSub;
    public static volatile float clientRainStrength;
    public static volatile float clientCelestialTime;

    /** Exact-position histogram of queueBlockChange feeds: packed coord -> count within the window. */
    public static final Long2IntOpenHashMap queuePositions = new Long2IntOpenHashMap();
    public static boolean queueHistogramCapped;

    /** External mark origin (caller frames) -> count within the window. */
    public static final Map<String, Integer> externalOrigins = new HashMap<>();

    // Celeritas-internal rebuild schedules (captured by the late mixin into RenderSectionManager)
    public static long internalSchedules;
    public static final Long2IntOpenHashMap scheduledSections = new Long2IntOpenHashMap();
    public static boolean scheduledSectionsCapped;
    private RenderUpdateTracer() {}

    public static boolean enabled() {
        return SupernovaConfig.enableStatsLog;
    }

    public static void beginDrain() {
        selfTag.set(SELF_DRAIN);
    }

    public static void beginAllChunks() {
        selfTag.set(SELF_ALL_CHUNKS);
    }

    public static void endSelf() {
        selfTag.set(SELF_NONE);
    }

    public static void onRenderMark(final boolean remote, final int x1, final int y1, final int z1, final int x2, final int y2,
            final int z2) {
        if (!SupernovaConfig.enableStatsLog || !remote) return;
        final int sections = ((x2 >> 4) - (x1 >> 4) + 1) * ((y2 >> 4) - (y1 >> 4) + 1) * ((z2 >> 4) - (z1 >> 4) + 1);
        switch (selfTag.get()) {
            case SELF_DRAIN:
                drainMarks++;
                drainSections += sections;
                return;
            case SELF_ALL_CHUNKS:
                allChunkMarks++;
                allChunkSections += sections;
                return;
            default:
                externalMarks++;
                externalSections += sections;
                mergeOrigin(originKey(3));
        }
    }

    public static void recordQueuePosition(final int x, final int y, final int z) {
        if (queuePositions.size() >= 8192) {
            queueHistogramCapped = true;
            return;
        }
        final long packed = ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
        queuePositions.addTo(packed, 1);
    }

    public static void onInternalSchedule(final int x, final int y, final int z) {
        if (!SupernovaConfig.enableStatsLog) return;
        internalSchedules++;
        final long packed = ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
        if (scheduledSections.size() >= 8192 && !scheduledSections.containsKey(packed)) {
            scheduledSectionsCapped = true;
        } else {
            scheduledSections.addTo(packed, 1);
        }
    }

    public static int unpackX(final long packed) {
        return (int) (packed >> 38);
    }

    public static int unpackZ(final long packed) {
        return (int) ((packed << 26) >> 38);
    }

    public static int unpackY(final long packed) {
        return (int) ((packed << 52) >> 52);
    }

    private static void mergeOrigin(final String key) {
        if (externalOrigins.size() >= 64 && !externalOrigins.containsKey(key)) {
            externalOrigins.merge("(overflow)", 1, Integer::sum);
            return;
        }
        externalOrigins.merge(key, 1, Integer::sum);
    }

    /**
     * Caller frames leading to the traced entry point, for attributing third-party rebuild/mark storms.
     * Skips JDK internals, our own classes, and mixin handler frames; keeps up to {@code maxFrames}
     * meaningful frames so the initiating mod code is visible in context.
     */
    private static String originKey(final int maxFrames) {
        final StackTraceElement[] frames = new Throwable().getStackTrace();
        final StringBuilder sb = new StringBuilder(128);
        int taken = 0;
        for (int i = 1; i < frames.length && taken < maxFrames; i++) {
            final String cn = frames[i].getClassName();
            final String mn = frames[i].getMethodName();
            if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("sun.")) continue;
            if (cn.startsWith("com.mitchej123.supernova.")) continue;
            if (mn.contains("$")) continue; // mixin handler / lambda bridges
            if (taken > 0) sb.append(" <- ");
            sb.append(cn.substring(cn.lastIndexOf('.') + 1)).append('.').append(mn);
            taken++;
        }
        return sb.length() == 0 ? "(unknown)" : sb.toString();
    }
}
