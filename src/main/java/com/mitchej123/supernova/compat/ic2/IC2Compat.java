package com.mitchej123.supernova.compat.ic2;

import com.mitchej123.supernova.Supernova;
import com.mitchej123.supernova.api.TileLightRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

/** Post-init registration for IC2 integration, deliberately free of IC2 class references. */
public final class IC2Compat {

    public static void register() {
        if (registerCropBlock(GameRegistry.findBlock("IC2", "blockCrop"))) {
            Supernova.LOG.info("Registered IC2 crop tile-light bridge");
        }
    }

    static boolean registerCropBlock(final Block crop) {
        if (crop == null) return false;
        TileLightRegistry.register(crop);
        return true;
    }

    private IC2Compat() {}
}
