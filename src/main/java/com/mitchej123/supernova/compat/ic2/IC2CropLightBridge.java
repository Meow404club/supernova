package com.mitchej123.supernova.compat.ic2;

import com.mitchej123.supernova.api.PackedColorLight;
import com.mitchej123.supernova.api.TileLightStore;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** Main-thread bridge from IC2 crop tile state to Supernova's worker-readable store. */
public final class IC2CropLightBridge {

    public static void refresh(final World world, final int x, final int y, final int z) {
        final TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof IC2CropLightAccess) {
            publish(tile, ((IC2CropLightAccess) tile).supernova$getEmittedLight());
        } else {
            TileLightStore.remove(world.provider.dimensionId, x, y, z);
        }
    }

    public static void publish(final TileEntity tile, final int emittedLight) {
        final World world = tile.getWorldObj();
        if (world == null) return;
        publish(world.provider.dimensionId, tile.xCoord, tile.yCoord, tile.zCoord, emittedLight);
    }

    static void publish(final int dimension, final int x, final int y, final int z, final int emittedLight) {
        TileLightStore.put(dimension, x, y, z, packedWhite(emittedLight));
    }

    public static void remove(final TileEntity tile) {
        final World world = tile.getWorldObj();
        if (world == null) return;
        TileLightStore.remove(world.provider.dimensionId, tile.xCoord, tile.yCoord, tile.zCoord);
    }

    static int packedWhite(final int emittedLight) {
        final int level = Math.max(0, Math.min(15, emittedLight));
        return PackedColorLight.pack(level, level, level);
    }

    private IC2CropLightBridge() {}
}
