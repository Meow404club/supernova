package com.mitchej123.supernova.light;

import com.mitchej123.supernova.Supernova;
import com.mitchej123.supernova.config.SupernovaConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-world stats accumulator for light engine instrumentation. Dumps to {@code logs/supernova-stats.log} every {@value LOG_INTERVAL_TICKS} ticks.
 */
public final class LightStats {

    private static final int LOG_INTERVAL_TICKS = 20;
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss.SSS");

    private final String side;
    private final PrintWriter writer;

    // Tick tracking
    private long tickCount;
    private long windowStartTick;

    // Worker stats -- written by sky and block worker threads concurrently
    final AtomicLong chunksProcessed = new AtomicLong();
    final AtomicLong initialLightsRun = new AtomicLong();
    final AtomicLong skyWorkerTimeNs = new AtomicLong();
    final AtomicLong blockWorkerTimeNs = new AtomicLong();
    final AtomicLong skyTasksProcessed = new AtomicLong();
    final AtomicLong blockTasksProcessed = new AtomicLong();
    volatile long maxQueueLatencyNs;
    volatile long totalQueueLatencyNs;

    // Backlog snapshots (main thread only)
    volatile int skyBacklog;
    volatile int blockBacklog;

    // Client drain stats (main thread only)
    long drainedSections;
    long drainTimeNs;
    long renderQueueSize;

    // Client sync stats (main thread only)
    long syncBlockLightCalls;
    long syncSkyLightCalls;
    long syncTimeNs;
    public static long engineRenderMarks;

    // Budget yield stats (multi-thread write)
    final AtomicInteger edgeBudgetYields = new AtomicInteger();
    final AtomicInteger blockChangeBudgetYields = new AtomicInteger();
    final AtomicInteger skyChangeBudgetYields = new AtomicInteger();

    // Queue stats (multi-thread write)
    final AtomicInteger chunksQueued = new AtomicInteger();

    // Block change diagnostics (block worker thread)
    public final AtomicLong blockPositionsProcessed = new AtomicLong();

    // Edge check diagnostics (multi-thread write, public for cross-package access from engine)
    public final AtomicLong edgeSectionPairsChecked = new AtomicLong();
    public final AtomicLong edgeSectionPairsSkippedFull = new AtomicLong();
    public final AtomicLong edgeSectionPairsSkippedZero = new AtomicLong();
    public final AtomicLong edgeBlocksTotal = new AtomicLong();
    public final AtomicLong edgeBlocksSkippedTrivial = new AtomicLong();
    public final AtomicLong edgeBlocksSkippedConsistency = new AtomicLong();
    public final AtomicLong edgeBlocksRecalculated = new AtomicLong();
    public final AtomicLong edgeBlocksMismatched = new AtomicLong();

    public LightStats(final boolean isClient) {
        this.side = isClient ? "CLIENT" : "SERVER";
        PrintWriter pw = null;
        if (SupernovaConfig.enableStatsLog) {
            try {
                final File logFile = new File("logs/supernova-stats.log");
                logFile.getParentFile().mkdirs();
                pw = new PrintWriter(new FileWriter(logFile, true), true);
            } catch (final IOException e) {
                Supernova.LOG.error("Failed to open supernova-stats.log", e);
            }
        }
        this.writer = pw;
    }

    /**
     * Called once per tick from the main thread. Triggers periodic dump.
     */
    public void tick(final int skyBacklog, final int blockBacklog) {
        this.skyBacklog = skyBacklog;
        this.blockBacklog = blockBacklog;
        this.tickCount++;
        if (this.tickCount - this.windowStartTick >= LOG_INTERVAL_TICKS) {
            dump();
            reset();
        }
    }

    void recordQueueLatency(final long enqueueTimeNs) {
        final long latency = System.nanoTime() - enqueueTimeNs;
        if (latency > this.maxQueueLatencyNs) {
            this.maxQueueLatencyNs = latency;
        }
        this.totalQueueLatencyNs += latency;
    }

    private void dump() {
        if (this.writer == null) return;

        final long processed = this.chunksProcessed.get();
        final int queued = this.chunksQueued.get();
        final StringBuilder sb = new StringBuilder(256);
        sb.append(TIME_FMT.format(new Date()));
        sb.append(" [").append(this.side).append(']');
        sb.append(" ticks=").append(this.windowStartTick).append('-').append(this.tickCount);
        sb.append(" queued=").append(queued);
        sb.append(" processed=").append(processed);
        sb.append(" initial=").append(this.initialLightsRun.get());
        sb.append(" skyMs=").append(String.format(Locale.US, "%.1f", this.skyWorkerTimeNs.get() / 1_000_000.0));
        sb.append(" blockMs=").append(String.format(Locale.US, "%.1f", this.blockWorkerTimeNs.get() / 1_000_000.0));
        sb.append(" skyTasks=").append(this.skyTasksProcessed.get());
        sb.append(" blockTasks=").append(this.blockTasksProcessed.get());
        sb.append(" blockPos=").append(this.blockPositionsProcessed.get());
        final int skyBl = this.skyBacklog;
        final int blockBl = this.blockBacklog;
        sb.append(" skyBacklog=").append(skyBl);
        sb.append(" blockBacklog=").append(blockBl);
        final int yields = this.edgeBudgetYields.get();
        if (yields > 0) {
            sb.append(" edgeBudgetYields=").append(yields);
        }
        final int blockYields = this.blockChangeBudgetYields.get();
        if (blockYields > 0) {
            sb.append(" blockChangeBudgetYields=").append(blockYields);
        }
        final int skyYields = this.skyChangeBudgetYields.get();
        if (skyYields > 0) {
            sb.append(" skyChangeBudgetYields=").append(skyYields);
        }

        if (processed > 0 && this.totalQueueLatencyNs > 0) {
            sb.append(" avgLatencyMs=").append(String.format(Locale.US, "%.1f", (this.totalQueueLatencyNs / (double) processed) / 1_000_000.0));
        }
        if (this.maxQueueLatencyNs > 0) {
            sb.append(" maxLatencyMs=").append(String.format(Locale.US, "%.1f", this.maxQueueLatencyNs / 1_000_000.0));
        }

        final long edgePairs = this.edgeSectionPairsChecked.get();
        if (edgePairs > 0) {
            sb.append(" edgeStats sectionPairs=").append(edgePairs);
            sb.append(" skippedFull=").append(this.edgeSectionPairsSkippedFull.get());
            sb.append(" skippedZero=").append(this.edgeSectionPairsSkippedZero.get());
            sb.append(" blocks=").append(this.edgeBlocksTotal.get());
            sb.append(" skippedTrivial=").append(this.edgeBlocksSkippedTrivial.get());
            sb.append(" skippedConsistency=").append(this.edgeBlocksSkippedConsistency.get());
            sb.append(" recalc=").append(this.edgeBlocksRecalculated.get());
            sb.append(" mismatched=").append(this.edgeBlocksMismatched.get());
        }

        if ("CLIENT".equals(this.side)) {
            sb.append(" drainedSections=").append(this.drainedSections);
            sb.append(" drainMs=").append(String.format(Locale.US, "%.1f", this.drainTimeNs / 1_000_000.0));
            sb.append(" renderQueue=").append(this.renderQueueSize);
            sb.append(" syncBlock=").append(this.syncBlockLightCalls);
            sb.append(" syncSky=").append(this.syncSkyLightCalls);
            sb.append(" syncMs=").append(String.format(Locale.US, "%.1f", this.syncTimeNs / 1_000_000.0));
            sb.append(" engineMarks=").append(engineRenderMarks);

            sb.append(" | sub=").append(RenderUpdateTracer.clientSub);
            sb.append(" rain=").append(String.format(Locale.US, "%.2f", RenderUpdateTracer.clientRainStrength));
            sb.append(" time=").append(String.format(Locale.US, "%.0f", RenderUpdateTracer.clientCelestialTime));
            sb.append(" nightFires=").append(RenderUpdateTracer.nightTriggerFires);
            sb.append(" marks: drain=").append(RenderUpdateTracer.drainMarks).append('/').append(RenderUpdateTracer.drainSections);
            sb.append(" allchunks=").append(RenderUpdateTracer.allChunkMarks).append('/').append(RenderUpdateTracer.allChunkSections);
            sb.append(" ext=").append(RenderUpdateTracer.externalMarks).append('/').append(RenderUpdateTracer.externalSections);
            sb.append(" sched=").append(RenderUpdateTracer.internalSchedules);

            if (!RenderUpdateTracer.queuePositions.isEmpty()) {
                final var top = RenderUpdateTracer.queuePositions.long2IntEntrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue())).limit(6)
                        .toArray(n -> new it.unimi.dsi.fastutil.longs.Long2IntMap.Entry[n]);
                sb.append(" qtop=");
                for (int i = 0; i < top.length; i++) {
                    final long packed = top[i].getLongKey();
                    if (i > 0) sb.append(',');
                    sb.append(RenderUpdateTracer.unpackX(packed)).append(',').append(RenderUpdateTracer.unpackY(packed))
                            .append(',').append(RenderUpdateTracer.unpackZ(packed)).append('*').append(top[i].getIntValue());
                }
                if (RenderUpdateTracer.queueHistogramCapped) {
                    sb.append(" qCAPPED");
                }
            }
        }

        if ("CLIENT".equals(this.side)) {
            sb.append(" sched=").append(RenderUpdateTracer.internalSchedules);
            if (!RenderUpdateTracer.scheduledSections.isEmpty()) {
                final var top = RenderUpdateTracer.scheduledSections.long2IntEntrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue())).limit(8)
                        .toArray(n -> new it.unimi.dsi.fastutil.longs.Long2IntMap.Entry[n]);
                sb.append(" schedTop=");
                for (int i = 0; i < top.length; i++) {
                    final long packed = top[i].getLongKey();
                    if (i > 0) sb.append(',');
                    sb.append(RenderUpdateTracer.unpackX(packed)).append(',').append(RenderUpdateTracer.unpackY(packed))
                            .append(',').append(RenderUpdateTracer.unpackZ(packed)).append('*').append(top[i].getIntValue());
                }
                if (RenderUpdateTracer.scheduledSectionsCapped) {
                    sb.append(" sCAPPED");
                }
            }
        }

        this.writer.println(sb);

        if ("CLIENT".equals(this.side) && !RenderUpdateTracer.externalOrigins.isEmpty()) {
            RenderUpdateTracer.externalOrigins.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(8)
                    .forEach(e -> this.writer.println("    extOrigin x" + e.getValue() + " " + e.getKey()));
        }
    }

    private void reset() {
        this.windowStartTick = this.tickCount;
        this.chunksProcessed.set(0);
        this.initialLightsRun.set(0);
        this.skyWorkerTimeNs.set(0);
        this.blockWorkerTimeNs.set(0);
        this.skyTasksProcessed.set(0);
        this.blockTasksProcessed.set(0);
        this.maxQueueLatencyNs = 0;
        this.totalQueueLatencyNs = 0;
        this.drainedSections = 0;
        this.drainTimeNs = 0;
        this.renderQueueSize = 0;
        this.syncBlockLightCalls = 0;
        this.syncSkyLightCalls = 0;
        this.syncTimeNs = 0;
        engineRenderMarks = 0;
        RenderUpdateTracer.nightTriggerFires = 0;
        RenderUpdateTracer.drainMarks = 0;
        RenderUpdateTracer.drainSections = 0;
        RenderUpdateTracer.allChunkMarks = 0;
        RenderUpdateTracer.allChunkSections = 0;
        RenderUpdateTracer.externalMarks = 0;
        RenderUpdateTracer.externalSections = 0;
        RenderUpdateTracer.internalSchedules = 0;
        RenderUpdateTracer.scheduledSections.clear();
        RenderUpdateTracer.scheduledSectionsCapped = false;
        RenderUpdateTracer.queuePositions.clear();
        RenderUpdateTracer.queueHistogramCapped = false;
        RenderUpdateTracer.externalOrigins.clear();
        this.edgeBudgetYields.set(0);
        this.blockChangeBudgetYields.set(0);
        this.skyChangeBudgetYields.set(0);
        this.chunksQueued.set(0);
        this.blockPositionsProcessed.set(0);
        this.edgeSectionPairsChecked.set(0);
        this.edgeSectionPairsSkippedFull.set(0);
        this.edgeSectionPairsSkippedZero.set(0);
        this.edgeBlocksTotal.set(0);
        this.edgeBlocksSkippedTrivial.set(0);
        this.edgeBlocksSkippedConsistency.set(0);
        this.edgeBlocksRecalculated.set(0);
        this.edgeBlocksMismatched.set(0);
    }

    public void close() {
        if (this.writer != null) {
            this.writer.close();
        }
    }
}
