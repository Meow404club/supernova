package com.mitchej123.supernova.mixin.early.engine;

import com.mitchej123.supernova.light.SupernovaChunk;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilChunkLoader.class)
public abstract class MixinAnvilChunkLoader {

    @Inject(method = "writeChunkToNBT", at = @At("RETURN"))
    private void supernova$writeLightEpoch(Chunk chunk, World world, NBTTagCompound nbt, CallbackInfo ci) {
        nbt.setInteger("SNLightEpoch", ((SupernovaChunk) chunk).getLightEpoch());
    }

    @Inject(method = "readChunkFromNBT", at = @At("RETURN"))
    private void supernova$readLightEpoch(World world, NBTTagCompound nbt, CallbackInfoReturnable<Chunk> cir) {
        final Chunk chunk = cir.getReturnValue();
        if (chunk != null) {
            ((SupernovaChunk) chunk).setLightEpoch(nbt.getInteger("SNLightEpoch"));
        }
    }
}
