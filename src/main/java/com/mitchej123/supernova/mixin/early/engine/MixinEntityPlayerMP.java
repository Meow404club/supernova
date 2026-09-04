package com.mitchej123.supernova.mixin.early.engine;

import com.mitchej123.supernova.light.SupernovaChunk;
import com.mitchej123.supernova.light.WorldLightManager;
import com.mitchej123.supernova.world.SupernovaWorld;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.7.10 analogue of Pulsar's PlayerChunkMapEntry.sendToPlayers gate.
 * Returning false leaves the coord in {@code loadedChunks} so vanilla retries next tick.
 */
@Mixin(EntityPlayerMP.class)
public abstract class MixinEntityPlayerMP {
    @Redirect(
            method = "onUpdate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;func_150802_k()Z"))
    private boolean supernova$gateChunkSendOnLight(final Chunk chunk) {
        if (!chunk.func_150802_k()) {
            return false;
        }
        if (!((SupernovaChunk) chunk).isLightReady()) {
            return false;
        }
        final World world = chunk.worldObj;
        if (world == null) {
            return true;
        }
        final WorldLightManager mgr = ((SupernovaWorld) world).supernova$getLightManager();
        return mgr == null || mgr.areNeighboursLightReady(chunk.xPosition, chunk.zPosition);
    }
}
