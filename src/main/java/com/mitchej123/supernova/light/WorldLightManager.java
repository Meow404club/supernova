package com.mitchej123.supernova.light;

import com.google.common.util.concurrent.SettableFuture;
import com.mitchej123.supernova.Supernova;
import com.mitchej123.supernova.config.SupernovaConfig;
import com.mitchej123.supernova.light.engine.ScalarBlockEngine;
import com.mitchej123.supernova.light.engine.ScalarSkyEngine;
import com.mitchej123.supernova.light.engine.SupernovaBlockEngine;
import com.mitchej123.supernova.light.engine.SupernovaEngine;
import com.mitchej123.supernova.light.engine.SupernovaSkyEngine;
import com.mitchej123.supernova.util.CoordinateUtils;
import com.mitchej123.supernova.util.SnapshotChunkMap;
import com.mitchej123.supernova.util.WorldUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Per-World light manager managing Supernova's light engine pools.
 */
public final class WorldLightManager {

    private final World world;
    private final boolean hasSkyLight;
    private final boolean hasBlockLight;

    private final ConcurrentLinkedDeque<SupernovaEngine> cachedSkyPropagators;
    private final ConcurrentLinkedDeque<SupernovaEngine> cachedBlockPropagators;
    private final Supplier<SupernovaEngine> skyEngineFactory;
    private final Supplier<SupernovaEngine> blockEngineFactory;

    private final SnapshotChunkMap loadedChunkMap = new SnapshotChunkMap();

    // Tracks in-flight light work per chunk -- used by awaitPendingWork to ensure chunk save reads post-BFS data on unload.
    // Stores the shared completion future so we wait for both engines.
    private final Long2ObjectOpenHashMap<SettableFuture<Void>> pendingWork = new Long2ObjectOpenHashMap<>();

    // Separate worker threads + queues for sky and block light
    private final LightQueue skyQueue;
    private final LightQueue blockQueue;
    private final Thread skyWorkerThread;
    private final Thread blockWorkerThread;
    private volatile boolean running = true;

    // Per-tick stats instrumentation
    private final LightStats stats;

    // Client-only: render update coordinates queued by worker threads for main-thread drain.
    // Each long packs (cx << 32) | ((cz & 0xFFFF) << 16) | (cy & 0xFFFF).
    private final RenderUpdateQueue pendingRenderUpdates = new RenderUpdateQueue(4096);

    // Coordination for initial chunk lighting: both engines must finish before setLightReady(true).
    // Maps chunk coordinate -> countdown + shared future. Accessed from both worker threads.
    private final Long2ObjectOpenHashMap<ChunkLightCompletion> initialLightCompletions = new Long2ObjectOpenHashMap<>();

    private static final int MAX_RELIGHT_ATTEMPTS = 2;
    private static final long EDGE_CHECK_BUDGET_NS = 10_000_000L; // 10ms wall-clock budget for edge check phase
    private static final long BLOCK_CHANGE_BUDGET_NS = 5_000_000L; // 5ms budget for phase-1 block change drain

    public WorldLightManager(final World world, final boolean hasSkyLight, final boolean hasBlockLight) {
        this.world = world;
        this.hasSkyLight = hasSkyLight;
        this.hasBlockLight = hasBlockLight;
        this.cachedSkyPropagators = hasSkyLight ? new ConcurrentLinkedDeque<>() : null;
        this.cachedBlockPropagators = hasBlockLight ? new ConcurrentLinkedDeque<>() : null;

        this.skyEngineFactory = hasSkyLight ? (SupernovaConfig.isScalarMode() ? () -> new ScalarSkyEngine(world, this.loadedChunkMap) : () -> new SupernovaSkyEngine(world)) : null;
        this.blockEngineFactory = hasBlockLight ? (SupernovaConfig.isScalarMode() ? () -> new ScalarBlockEngine(world, this.loadedChunkMap) : () -> new SupernovaBlockEngine(world, this.loadedChunkMap)) : null;

        this.skyQueue = hasSkyLight ? new LightQueue() : null;
        this.blockQueue = hasBlockLight ? new LightQueue() : null;
        this.stats = new LightStats(world.isRemote);
        if (this.skyQueue != null) this.skyQueue.setStats(this.stats);
        if (this.blockQueue != null) this.blockQueue.setStats(this.stats);

        if (this.cachedSkyPropagators != null) {
            this.cachedSkyPropagators.addFirst(this.skyEngineFactory.get());
            this.cachedSkyPropagators.addFirst(this.skyEngineFactory.get());
        }
        if (this.cachedBlockPropagators != null) {
            this.cachedBlockPropagators.addFirst(this.blockEngineFactory.get());
            this.cachedBlockPropagators.addFirst(this.blockEngineFactory.get());
        }

        if (hasSkyLight) {
            this.skyWorkerThread = new Thread(
                    () -> {
                        while (this.running) {
                            if (this.skyQueue.isEmpty()) {
                                try {
                                    this.skyQueue.waitForWork();
                                } catch (final InterruptedException e) {
                                    break;
                                }
                            }
                            this.propagateSkyChanges();
                        }
                    }, "Supernova-Sky");
            this.skyWorkerThread.setDaemon(true);
            this.skyWorkerThread.start();
        } else {
            this.skyWorkerThread = null;
        }

        if (hasBlockLight) {
            this.blockWorkerThread = new Thread(
                    () -> {
                        while (this.running) {
                            if (this.blockQueue.isEmpty()) {
                                try {
                                    this.blockQueue.waitForWork();
                                } catch (final InterruptedException e) {
                                    break;
                                }
                            }
                            this.propagateBlockChanges();
                        }
                    }, "Supernova-Block");
            this.blockWorkerThread.setDaemon(true);
            this.blockWorkerThread.start();
        } else {
            this.blockWorkerThread = null;
        }
    }

    public void registerChunk(final Chunk chunk) {
        this.loadedChunkMap.put(CoordinateUtils.getChunkKey(chunk.xPosition, chunk.zPosition), chunk);
    }

    /**
     * Mark every loaded chunk fully dirty for re-meshing. Used when a value baked into vertex tints at mesh time changes globally (Angelica's cached
     * skylightSubtracted); unlike {@code renderGlobal.loadRenderers()} these marks reach Angelica's Celeritas pipeline, which rebuilds on them.
     */
    public void markAllChunksForRenderUpdate() {
        if (!this.world.isRemote) return;
        for (final Chunk chunk : this.loadedChunkMap.values()) {
            this.world.markBlockRangeForRenderUpdate(
                    chunk.xPosition << 4,
                    WorldUtil.getMinBlockY(),
                    chunk.zPosition << 4,
                    (chunk.xPosition << 4) | 15,
                    WorldUtil.getMaxBlockY(),
                    (chunk.zPosition << 4) | 15);
        }
    }

    public void unregisterChunk(final int cx, final int cz) {
        this.loadedChunkMap.remove(CoordinateUtils.getChunkKey(cx, cz));
    }

    public Chunk getLoadedChunk(final int chunkX, final int chunkZ) {
        return this.loadedChunkMap.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));
    }

    private static SupernovaEngine getEngine(final ConcurrentLinkedDeque<SupernovaEngine> cache,
            final Supplier<SupernovaEngine> factory) {
        if (cache == null) return null;
        final SupernovaEngine ret = cache.pollFirst();
        return ret != null ? ret : factory.get();
    }

    private static void releaseEngine(final ConcurrentLinkedDeque<SupernovaEngine> cache, final SupernovaEngine engine) {
        if (cache == null || engine == null) return;
        engine.suppressRenderNotify = false;
        engine.pendingRenderTarget = null;
        if (cache.size() < 4) {
            cache.addFirst(engine);
        }
    }

    private SupernovaEngine getSkyLightEngine() {
        return getEngine(this.cachedSkyPropagators, this.skyEngineFactory);
    }

    private SupernovaEngine getBlockLightEngine() {
        return getEngine(this.cachedBlockPropagators, this.blockEngineFactory);
    }

    private void releaseSkyLightEngine(final SupernovaEngine engine) {
        releaseEngine(this.cachedSkyPropagators, engine);
    }

    private void releaseBlockLightEngine(final SupernovaEngine engine) {
        releaseEngine(this.cachedBlockPropagators, engine);
    }

    public void queueBlockChange(final int x, final int y, final int z) {
        if (this.skyQueue != null) this.skyQueue.queueBlockChange(x, y, z);
        if (this.blockQueue != null) this.blockQueue.queueBlockChange(x, y, z);
    }

    public void queueChunkLight(final int cx, final int cz, final Chunk chunk, final Boolean[] emptySections) {
        final int engineCount = (this.hasSkyLight ? 1 : 0) + (this.hasBlockLight ? 1 : 0);
        final ChunkLightCompletion completion = new ChunkLightCompletion(engineCount, chunk);
        final long key = CoordinateUtils.getChunkKey(cx, cz);

        synchronized (this.initialLightCompletions) {
            this.initialLightCompletions.put(key, completion);
        }

        if (this.skyQueue != null) this.skyQueue.queueChunkLight(cx, cz, chunk, emptySections);
        if (this.blockQueue != null) this.blockQueue.queueChunkLight(cx, cz, chunk, emptySections);
    }

    public void removeChunkFromQueues(final int cx, final int cz) {
        if (this.skyQueue != null) this.skyQueue.removeChunk(cx, cz);
        if (this.blockQueue != null) this.blockQueue.removeChunk(cx, cz);
        final long key = CoordinateUtils.getChunkKey(cx, cz);
        synchronized (this.initialLightCompletions) {
            this.initialLightCompletions.remove(key);
        }
    }

    public boolean hasUpdates() {
        return (this.skyQueue != null && !this.skyQueue.isEmpty()) || (this.blockQueue != null && !this.blockQueue.isEmpty());
    }

    public boolean hasChunkPendingLight(final int cx, final int cz) {
        return (this.skyQueue != null && this.skyQueue.hasPendingWork(cx, cz)) || (this.blockQueue != null && this.blockQueue.hasPendingWork(cx, cz));
    }

    public void processClientRenderUpdates() {
        final long startNs = System.nanoTime();
        final int count = this.pendingRenderUpdates.drain(v -> {
            final int bx = (int) (v >> 32) << 4;
            final int bz = (short) ((v >> 16) & 0xFFFF) << 4;
            final int by = (short) (v & 0xFFFF) << 4;
            this.world.markBlockRangeForRenderUpdate(bx, by, bz, bx + 15, by + 15, bz + 15);
        });
        if (count > 0) {
            this.stats.drainedSections += count;
            this.stats.drainTimeNs += System.nanoTime() - startNs;
        }
        final int skySize = this.skyQueue != null ? this.skyQueue.size() : 0;
        final int blockSize = this.blockQueue != null ? this.blockQueue.size() : 0;
        this.stats.tick(skySize, blockSize);
    }

    public void scheduleUpdate() {
        final int skySize = this.skyQueue != null ? this.skyQueue.size() : 0;
        final int blockSize = this.blockQueue != null ? this.blockQueue.size() : 0;
        this.stats.tick(skySize, blockSize);
    }

    private void propagateSkyChanges() {
        this.propagateChanges(this.skyQueue, this.cachedSkyPropagators, this.skyEngineFactory,
                this::processSkyTask, this.stats.skyChangeBudgetYields, "propagateSkyChanges");
    }

    private void propagateBlockChanges() {
        this.propagateChanges(this.blockQueue, this.cachedBlockPropagators, this.blockEngineFactory,
                this::processBlockTask, this.stats.blockChangeBudgetYields, "propagateBlockChanges");
    }


    private void propagateChanges(final LightQueue queue, final ConcurrentLinkedDeque<SupernovaEngine> cache, final Supplier<SupernovaEngine> factory, final BiConsumer<ChunkTasks, SupernovaEngine> taskProcessor, final AtomicInteger changeBudgetYield, final String label) {
        final SupernovaEngine engine = getEngine(cache, factory);
        if (engine == null) return;
        if (this.world.isRemote) {
            engine.suppressRenderNotify = true;
            engine.pendingRenderTarget = this.pendingRenderUpdates;
        }
        try {
            // 1. Block changes (highest priority) -- budget-limited to avoid multi-second bursts
            final long changeBudget = System.nanoTime() + BLOCK_CHANGE_BUDGET_NS;
            ChunkTasks task;
            while ((task = queue.removeFirstBlockChangeTask()) != null) {
                taskProcessor.accept(task, engine);
                if (System.nanoTime() > changeBudget) {
                    changeBudgetYield.incrementAndGet();
                    break;
                }
            }

            // Loop phases 2+3 so initial lights can preempt edge checks
            boolean moreWork = true;
            while (moreWork) {
                moreWork = false;
                // 2. Initial light tasks
                while ((task = queue.removeFirstInitialLightTask()) != null) {
                    taskProcessor.accept(task, engine);
                    // Interleave block changes
                    ChunkTasks priorityTask;
                    while ((priorityTask = queue.removeFirstBlockChangeTask()) != null) {
                        taskProcessor.accept(priorityTask, engine);
                    }
                }
                // 3. Edge checks -- preempt if initial light arrives, budget-limited
                final long edgeDeadline = System.nanoTime() + EDGE_CHECK_BUDGET_NS;
                while ((task = queue.removeFirstTask()) != null) {
                    taskProcessor.accept(task, engine);
                    ChunkTasks priorityTask;
                    while ((priorityTask = queue.removeFirstBlockChangeTask()) != null) {
                        taskProcessor.accept(priorityTask, engine);
                    }
                    if (queue.hasInitialLightTask()) {
                        moreWork = true;
                        break;
                    }
                    if (System.nanoTime() > edgeDeadline) {
                        this.stats.edgeBudgetYields.incrementAndGet();
                        break;
                    }
                }
            }
        } catch (final Throwable t) {
            Supernova.LOG.error("Exception in " + label, t);
        } finally {
            releaseEngine(cache, engine);
        }
    }

    private void processSkyTask(final ChunkTasks task, final SupernovaEngine skyEngine) {
        final long t0 = System.nanoTime();
        final int cx = CoordinateUtils.getChunkX(task.chunkCoordinate);
        final int cz = CoordinateUtils.getChunkZ(task.chunkCoordinate);

        if (this.loadedChunkMap.get(task.chunkCoordinate) == null) {
            this.completeInitialLighting(task.chunkCoordinate);
            return;
        }

        this.stats.chunksProcessed.incrementAndGet();
        this.stats.recordQueueLatency(task.enqueueTimeNs);
        skyEngine.setStats(this.stats);

        try {
            // 1. Initial chunk lighting (deferred edges -- neighbor-aware)
            if (task.initialLightChunk != null) {
                this.stats.initialLightsRun.incrementAndGet();
                skyEngine.light(task.initialLightChunk, task.initialLightEmptySections, false);
                this.completeInitialLighting(task.chunkCoordinate);

                // Edge coalescing: only queue edge checks for the newly-lit chunk, not neighbors. Neighbors will get edge-checked when THEIR next neighbor
                // arrives. Correctness: propagateNeighbourLevels() during lightChunk() already seeds neighbor light into the new chunk during initial BFS.
                this.skyQueue.queueEdgeCheckAllSections(cx, cz, true);
            }

            // 2+3. Combined section + block changes
            if (task.changedSectionSet != null || (task.changedPositions != null && !task.changedPositions.isEmpty())) {
                skyEngine.blocksChangedInChunk(cx, cz, task.changedPositions, task.changedSectionSet);
            }

            // 4. Sky edge checks
            if (task.queuedEdgeChecksSky != null) {
                skyEngine.checkChunkEdges(cx, cz, task.queuedEdgeChecksSky);
            }

            // 5. Overflow requeue
            if (skyEngine.wasQueueOverflowed()) {
                if (task.relightAttempts < MAX_RELIGHT_ATTEMPTS) {
                    final Chunk chunk = this.loadedChunkMap.get(task.chunkCoordinate);
                    if (chunk != null) {
                        this.skyQueue.requeueChunkLight(cx, cz, chunk, SupernovaEngine.getEmptySectionsForChunk(chunk), task.relightAttempts);
                    }
                } else {
                    Supernova.LOG.error("Sky engine: chunk ({}, {}) overflowed BFS queue {} times -- giving up.", cx, cz, task.relightAttempts + 1);
                }
            }
        } catch (final NullPointerException e) {
            this.completeInitialLighting(task.chunkCoordinate);
            if (this.loadedChunkMap.get(task.chunkCoordinate) != null) {
                throw new RuntimeException("Unexpected NPE processing sky task for chunk (" + cx + ", " + cz + ")", e);
            }
            Supernova.LOG.warn("Sky task for chunk ({}, {}) aborted -- chunk unloaded during processing", cx, cz, e);
        }

        skyEngine.setStats(null);
        this.stats.skyWorkerTimeNs.addAndGet(System.nanoTime() - t0);
        this.stats.skyTasksProcessed.incrementAndGet();
    }

    private void processBlockTask(final ChunkTasks task, final SupernovaEngine blockEngine) {
        final long t0 = System.nanoTime();
        final int cx = CoordinateUtils.getChunkX(task.chunkCoordinate);
        final int cz = CoordinateUtils.getChunkZ(task.chunkCoordinate);

        if (this.loadedChunkMap.get(task.chunkCoordinate) == null) {
            this.completeInitialLighting(task.chunkCoordinate);
            return;
        }

        this.stats.chunksProcessed.incrementAndGet();
        this.stats.recordQueueLatency(task.enqueueTimeNs);
        blockEngine.setStats(this.stats);

        long changesNs = 0;
        int changesPos = 0, changesBfsInc = 0, changesBfsDec = 0;
        long edgesNs = 0;
        int edgeSec = 0, edgeBfsInc = 0, edgeBfsDec = 0;

        try {
            // 1. Initial chunk lighting -- use propagateNeighbourLevels (checkEdges=false) so existing
            //    neighbor light seeds into the chunk during BFS. Edge checks are deferred to a subsequent
            //    worker pass when more neighbors are likely available (mirrors the sky engine pattern).
            if (task.initialLightChunk != null) {
                blockEngine.light(task.initialLightChunk, task.initialLightEmptySections, false);

                // Queue per-section render updates for sections with non-zero block light.
                if (this.world.isRemote) {
                    final SupernovaChunk ext = (SupernovaChunk) task.initialLightChunk;
                    final SWMRNibbleArray[] rNibs = ext.getBlockNibblesR();
                    if (rNibs != null) {
                        for (int i = 0; i < rNibs.length; i++) {
                            final SWMRNibbleArray r = rNibs[i];
                            if (r == null || r.isNullNibbleUpdating() || r.isUninitialisedUpdating()) continue;
                            final int sectionY = i + WorldUtil.getMinLightSection();
                            this.pendingRenderUpdates.offer(((long) cx << 32) | ((long) (cz & 0xFFFF) << 16) | (sectionY & 0xFFFFL));
                        }
                    }
                }

                this.completeInitialLighting(task.chunkCoordinate);
                // Enqueue deferred edge checks for all light sections
                this.blockQueue.queueEdgeCheckAllSections(cx, cz, false);
            }

            // 2+3. Combined section + block changes
            if (task.changedSectionSet != null || (task.changedPositions != null && !task.changedPositions.isEmpty())) {
                final long t1 = System.nanoTime();
                blockEngine.blocksChangedInChunk(cx, cz, task.changedPositions, task.changedSectionSet);
                changesNs = System.nanoTime() - t1;
                changesPos = blockEngine.lastPositionsProcessed;
                changesBfsInc = blockEngine.lastBfsIncreaseTotal;
                changesBfsDec = blockEngine.lastBfsDecreaseTotal;
                this.stats.blockPositionsProcessed.addAndGet(changesPos);
            }

            // 4. Block edge checks
            if (task.queuedEdgeChecksBlock != null) {
                blockEngine.lastBfsIncreaseTotal = 0;
                blockEngine.lastBfsDecreaseTotal = 0;
                edgeSec = task.queuedEdgeChecksBlock.size();
                final long t2 = System.nanoTime();
                blockEngine.checkChunkEdges(cx, cz, task.queuedEdgeChecksBlock);
                edgesNs = System.nanoTime() - t2;
                edgeBfsInc = blockEngine.lastBfsIncreaseTotal;
                edgeBfsDec = blockEngine.lastBfsDecreaseTotal;
            }

            // 5. Overflow requeue
            if (blockEngine.wasQueueOverflowed()) {
                if (task.relightAttempts < MAX_RELIGHT_ATTEMPTS) {
                    final Chunk chunk = this.loadedChunkMap.get(task.chunkCoordinate);
                    if (chunk != null) {
                        this.blockQueue.requeueChunkLight(cx, cz, chunk, SupernovaEngine.getEmptySectionsForChunk(chunk), task.relightAttempts);
                    }
                } else {
                    Supernova.LOG.error("Block engine: chunk ({}, {}) overflowed BFS queue {} times -- giving up.", cx, cz, task.relightAttempts + 1);
                }
            }
        } catch (final NullPointerException e) {
            this.completeInitialLighting(task.chunkCoordinate);
            if (this.loadedChunkMap.get(task.chunkCoordinate) != null) {
                throw new RuntimeException("Unexpected NPE processing block task for chunk (" + cx + ", " + cz + ")", e);
            }
            Supernova.LOG.warn("Block task for chunk ({}, {}) aborted -- chunk unloaded during processing", cx, cz, e);
        }

        blockEngine.setStats(null);
        final long totalNs = System.nanoTime() - t0;
        this.stats.blockWorkerTimeNs.addAndGet(totalNs);
        this.stats.blockTasksProcessed.incrementAndGet();

        // Slow task warning (>100ms)
        if (totalNs > 100_000_000L) {
            Supernova.LOG.warn(
                    "Slow block task: chunk ({},{}) total={}ms changes={}ms ({}pos, bfsInc={} bfsDec={}) edges={}ms ({}sec, bfsInc={} bfsDec={})",
                    cx, cz, totalNs / 1_000_000L, changesNs / 1_000_000L, changesPos, changesBfsInc, changesBfsDec, edgesNs / 1_000_000L,
                    edgeSec, edgeBfsInc, edgeBfsDec);
        }
    }

    /**
     * Called by each worker when it finishes initial lighting for a chunk. The last worker to finish sets lightReady=true and completes the pending work
     * future.
     */
    private void completeInitialLighting(final long chunkCoordinate) {
        final ChunkLightCompletion completion;
        synchronized (this.initialLightCompletions) {
            completion = this.initialLightCompletions.get(chunkCoordinate);
        }
        if (completion == null) return;

        if (completion.remaining.decrementAndGet() <= 0) {
            synchronized (this.initialLightCompletions) {
                this.initialLightCompletions.remove(chunkCoordinate);
            }
            // Sync BFS results to vanilla nibbles so chunk packets carry fully-propagated values
            ((SupernovaChunk) completion.chunk).syncLightToVanilla();
            ((SupernovaChunk) completion.chunk).setLightReady(true);
            completion.future.set(null);
        }
    }

    /**
     * Synchronous block change -- runs BFS on the calling thread. Used for player-initiated block place/break on the client so lighting updates are visually
     * instant.
     */
    public void blockChange(final int x, final int y, final int z) {
        if (y < WorldUtil.getMinBlockY() || y > WorldUtil.getMaxBlockY()) return;
        final SupernovaEngine skyEngine = this.getSkyLightEngine();
        final SupernovaEngine blockEngine = this.getBlockLightEngine();
        try {
            if (skyEngine != null) {
                skyEngine.blockChanged(x, y, z);
                if (skyEngine.wasQueueOverflowed()) requeueChunkFromSync(x >> 4, z >> 4);
            }
            if (blockEngine != null) {
                blockEngine.blockChanged(x, y, z);
                if (blockEngine.wasQueueOverflowed()) requeueChunkFromSync(x >> 4, z >> 4);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
        // Also queue to the async workers so that if a concurrent worker task overwrites our sync results with stale data,
        // the workers will self-correct on a subsequent pass.
        this.queueBlockChange(x, y, z);
        this.scheduleUpdate();
    }

    private void requeueChunkFromSync(final int cx, final int cz) {
        final Chunk chunk = this.loadedChunkMap.get(CoordinateUtils.getChunkKey(cx, cz));
        if (chunk == null) return;
        final Boolean[] emptySections = SupernovaEngine.getEmptySectionsForChunk(chunk);
        if (this.skyQueue != null) this.skyQueue.requeueChunkLight(cx, cz, chunk, emptySections, 0);
        if (this.blockQueue != null) this.blockQueue.requeueChunkLight(cx, cz, chunk, emptySections, 0);
        this.scheduleUpdate();
    }

    public boolean forceRelightChunk(final int cx, final int cz) {
        final Chunk chunk = this.loadedChunkMap.get(CoordinateUtils.getChunkKey(cx, cz));
        if (chunk == null) return false;
        ((SupernovaChunk) chunk).setLightReady(false);
        final Boolean[] emptySections = SupernovaEngine.getEmptySectionsForChunk(chunk);
        this.queueChunkLight(cx, cz, chunk, emptySections);
        this.scheduleUpdate();
        return true;
    }

    public void awaitPendingWork(final int cx, final int cz) {
        // Check shared initial lighting completion
        final ChunkLightCompletion completion;
        synchronized (this.initialLightCompletions) {
            completion = this.initialLightCompletions.get(CoordinateUtils.getChunkKey(cx, cz));
        }
        if (completion != null) {
            try {
                completion.future.get(50, TimeUnit.MILLISECONDS);
            } catch (final Exception e) {
                Supernova.LOG.warn("Timed out waiting for initial light work on chunk ({}, {})", cx, cz);
            }
        }
    }

    public void shutdown() {
        this.running = false;
        if (this.skyQueue != null) this.skyQueue.wakeUp();
        if (this.blockQueue != null) this.blockQueue.wakeUp();
        if (this.skyWorkerThread != null) {
            try {
                this.skyWorkerThread.join(1000);
            } catch (final InterruptedException ignored) {
            }
        }
        if (this.blockWorkerThread != null) {
            try {
                this.blockWorkerThread.join(1000);
            } catch (final InterruptedException ignored) {
            }
        }
        this.stats.close();
    }

    /**
     * Coordination object for initial chunk lighting. Both sky and block workers decrement the countdown; the last one to finish sets lightReady and completes
     * the future.
     */
    static final class ChunkLightCompletion {

        final AtomicInteger remaining;
        final SettableFuture<Void> future;
        final Chunk chunk;

        ChunkLightCompletion(final int engineCount, final Chunk chunk) {
            this.remaining = new AtomicInteger(engineCount);
            this.future = SettableFuture.create();
            this.chunk = chunk;
        }
    }
}
