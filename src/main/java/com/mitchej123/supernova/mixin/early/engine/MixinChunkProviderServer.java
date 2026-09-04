package com.mitchej123.supernova.mixin.early.engine;

import com.mitchej123.supernova.light.InitialLightPolicy;
import com.mitchej123.supernova.light.SupernovaChunk;
import com.mitchej123.supernova.light.WorldLightManager;
import com.mitchej123.supernova.light.engine.SupernovaEngine;
import com.mitchej123.supernova.world.SupernovaWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * 1.7.10 calls {@code Chunk.func_150809_p} BEFORE {@code ChunkProviderGenerate.populate}
 * (lakes, ores, trees). Queue the first full BFS only after that worldgen finishes.
 */
@Mixin(ChunkProviderServer.class)
public abstract class MixinChunkProviderServer {

    @Inject(
            method = "populate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;setChunkModified()V", shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void supernova$queueLightAfterWorldgen(final IChunkProvider provider, final int x, final int z,
            final CallbackInfo ci, final Chunk chunk) {
        if (chunk.worldObj == null) return;
        if (!InitialLightPolicy.shouldQueueFullLight(chunk.worldObj.isRemote, chunk.isTerrainPopulated,
                ((SupernovaChunk) chunk).isLightReady())) {
            return;
        }
        final WorldLightManager iface = ((SupernovaWorld) chunk.worldObj).supernova$getLightManager();
        if (iface == null) return;
        iface.queueChunkLight(chunk.xPosition, chunk.zPosition, chunk, SupernovaEngine.getEmptySectionsForChunk(chunk));
        iface.scheduleUpdate();
    }
}
