package com.mitchej123.supernova.mixin.late.ic2;

import com.mitchej123.supernova.api.TileLightPublisher;
import com.mitchej123.supernova.compat.ic2.IC2CropLightBridge;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/** Publishes IC2 crop emission before Supernova queues a relight. */
@Mixin(targets = "ic2.core.crop.BlockCrop", remap = false)
public abstract class MixinBlockCrop implements TileLightPublisher {

    @Override
    public void supernova$refreshTileLight(final World world, final int x, final int y, final int z) {
        IC2CropLightBridge.refresh(world, x, y, z);
    }
}
