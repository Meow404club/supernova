package com.mitchej123.supernova.mixin.early.engine;

import com.mitchej123.supernova.api.ExtendedChunk;
import com.mitchej123.supernova.api.TileLightPublisher;
import com.mitchej123.supernova.light.RenderUpdateTracer;
import com.mitchej123.supernova.light.WorldLightManager;
import com.mitchej123.supernova.light.SupernovaChunk;
import com.mitchej123.supernova.world.SupernovaWorld;
import net.minecraft.block.Block;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class MixinWorld implements SupernovaWorld {

    @Final
    @Shadow
    public WorldProvider provider;

    @Unique
    private WorldLightManager supernova$lightInterface;

    /**
     * Set after the World constructor returns. Prevents dispatching to Supernova during the constructor body, when the world isn't fully initialized yet.
     */
    @Unique
    private boolean supernova$ready;

    /** True while a player-initiated block place/break is in progress (client main thread only). */
    @Unique
    private boolean supernova$playerAction;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void supernova$onWorldInit(CallbackInfo ci) {
        this.supernova$ready = true;
    }

    @Override
    public Chunk supernova$getAnyChunkImmediately(int chunkX, int chunkZ) {
        final WorldLightManager iface = this.supernova$lightInterface;
        if (iface != null) {
            return iface.getLoadedChunk(chunkX, chunkZ);
        }
        return null;
    }

    @Override
    public boolean supernova$hasChunkPendingLight(int cx, int cz) {
        final WorldLightManager iface = this.supernova$lightInterface;
        return iface != null && iface.hasChunkPendingLight(cx, cz);
    }

    @Override
    public void supernova$shutdown() {
        if (this.supernova$lightInterface != null) {
            this.supernova$lightInterface.shutdown();
            this.supernova$lightInterface = null;
        }
    }

    @Override
    public void supernova$setPlayerAction(boolean value) {
        this.supernova$playerAction = value;
    }

    @Override
    public boolean supernova$isPlayerAction() {
        return this.supernova$playerAction;
    }

    @Override
    public WorldLightManager supernova$getLightManager() {
        if (this.supernova$lightInterface == null && this.provider != null) {
            this.supernova$lightInterface = new WorldLightManager((World) (Object) this, !this.provider.hasNoSky, true);
        }
        return this.supernova$lightInterface;
    }

    @Inject(method = "updateEntities", at = @At("HEAD"))
    private void supernova$drainClientRenderUpdates(CallbackInfo ci) {
        if (((World) (Object) this).isRemote) {
            final WorldLightManager iface = this.supernova$lightInterface;
            if (iface != null) {
                iface.processClientRenderUpdates();
            }
        }
    }

    /**
     * @author Supernova
     * @reason Replace vanilla BFS light propagation with Supernova engine dispatch. Both client and server enqueue to LightQueue for async worker processing.
     * Client calls scheduleUpdate() immediately to minimize visual latency.
     */
    @Overwrite
    public boolean updateLightByType(EnumSkyBlock type, int x, int y, int z) {
        if (!this.supernova$ready) return false;
        final WorldLightManager iface = this.supernova$getLightManager();
        if (iface == null) return false;

        if (supernova$skipUnpopulatedLight(x, z)) {
            return true;
        }

        // Main-thread resync of tile-driven emission: part state changes (redstone toggles) relight
        // without firing notifyPartChange, so TileLightStore would otherwise go stale.
        final World currentWorld = (World) (Object) this;
        final Block changedBlock = currentWorld.getBlock(x, y, z);
        if (changedBlock instanceof TileLightPublisher) {
            ((TileLightPublisher) changedBlock).supernova$refreshTileLight(currentWorld, x, y, z);
        }

        if (!currentWorld.isRemote) {
            // Server: enqueue -- worker runs both engines for the position.
            iface.queueBlockChange(x, y, z);
            return true;
        }

        // Client: player actions run BFS synchronously for instant feedback;
        // server-sent changes (explosions, pistons, etc.) go async to avoid stalls.
        final Chunk chunk = this.supernova$getAnyChunkImmediately(x >> 4, z >> 4);
        if (chunk == null || !((ExtendedChunk) chunk).isLightReady()) {
            return false;
        }
        if (this.supernova$playerAction) {
            iface.blockChange(x, y, z);
        } else {
            iface.queueBlockChange(x, y, z);
            iface.scheduleUpdate();
        }
        return true;
    }

    /**
     * @author Supernova
     * @reason Dispatch checkLight (called from World.setBlock) to Supernova engines. Server enqueues to LightQueue; client dispatches sync for player actions,
     * async otherwise.
     */
    @Overwrite
    public boolean func_147451_t(int x, int y, int z) {
        if (!this.supernova$ready) return false;
        final WorldLightManager iface = this.supernova$getLightManager();
        if (iface == null) return false;

        if (supernova$skipUnpopulatedLight(x, z)) {
            return true;
        }

        final World currentWorld = (World) (Object) this;
        final Block changedBlock = currentWorld.getBlock(x, y, z);
        if (changedBlock instanceof TileLightPublisher) {
            ((TileLightPublisher) changedBlock).supernova$refreshTileLight(currentWorld, x, y, z);
        }

        if (!currentWorld.isRemote) {
            iface.queueBlockChange(x, y, z);
            return true;
        }

        final Chunk chunk = this.supernova$getAnyChunkImmediately(x >> 4, z >> 4);
        if (chunk == null || !((ExtendedChunk) chunk).isLightReady()) {
            return true;
        }
        if (this.supernova$playerAction) {
            iface.blockChange(x, y, z);
        } else {
            iface.queueBlockChange(x, y, z);
            iface.scheduleUpdate();
        }
        return true;
    }

    /**
     * Vanilla never rechecks light for metadata-only changes (setBlockMetadataWithNotify only
     * marks renders / notifies neighbors), so blocks that toggle emission purely through metadata
     * -- e.g. Ender IO electric lights and their light nodes -- never reach the engine. Queue a
     * block change after a successful metadata write; checkBlock is a no-op when nothing changed.
     */
    @Inject(method = "setBlockMetadataWithNotify", at = @At("RETURN"))
    private void supernova$onMetadataChanged(final int x, final int y, final int z, final int meta, final int flags,
            final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        final WorldLightManager iface = this.supernova$getLightManager();
        if (iface == null) return;
        iface.queueBlockChange(x, y, z);
        iface.scheduleUpdate();
    }

    @Unique
    private boolean supernova$skipUnpopulatedLight(final int x, final int z) {
        final Chunk chunk = this.supernova$getAnyChunkImmediately(x >> 4, z >> 4);
        return chunk != null && !((SupernovaChunk) chunk).isLightReady();
    }

    // Kill the random per-tick playerCheckLight fixup in setActivePlayerChunksAndCheckLight.
    @Redirect(method = "setActivePlayerChunksAndCheckLight", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;func_147451_t(III)Z"))
    private boolean supernova$skipPlayerCheckLight(World world, int x, int y, int z) {
        return true;
    }

    @Inject(method = "markBlockRangeForRenderUpdate", at = @At("HEAD"))
    private void supernova$traceRenderMark(int x1, int y1, int z1, int x2, int y2, int z2, CallbackInfo ci) {
        RenderUpdateTracer.onRenderMark(((World) (Object) this).isRemote, x1, y1, z1, x2, y2, z2);
    }
}
