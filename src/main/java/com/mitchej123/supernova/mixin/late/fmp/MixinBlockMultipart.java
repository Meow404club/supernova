package com.mitchej123.supernova.mixin.late.fmp;

import codechicken.multipart.BlockMultipart;
import com.mitchej123.supernova.api.PositionalColoredLightSource;
import com.mitchej123.supernova.api.TileLightPublisher;
import com.mitchej123.supernova.api.TileLightStore;
import com.mitchej123.supernova.compat.fmp.MultipartLightBridge;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Makes {@code BlockMultipart} a {@link PositionalColoredLightSource} sourced from {@link TileLightStore}.
 * Causes {@code LightColorRegistry.buildCache} to mark this block UNCACHEABLE and routes worker-thread
 * emission queries through the side store instead of {@code World.getTileEntity}.
 */
@Mixin(value = BlockMultipart.class, remap = false)
public abstract class MixinBlockMultipart implements PositionalColoredLightSource, TileLightPublisher {

    @Override
    public int getColoredLightEmission(final IBlockAccess world, final int meta, final int x, final int y, final int z) {
        final int dim = TileLightStore.dimensionOf(world);
        return dim == Integer.MIN_VALUE ? 0 : TileLightStore.get(dim, x, y, z);
    }

    @Override
    public int getColoredLightEmission(final int meta) {
        return 0;
    }

    @Override
    public void supernova$refreshTileLight(final World world, final int x, final int y, final int z) {
        MultipartLightBridge.refresh(world, x, y, z);
    }
}
