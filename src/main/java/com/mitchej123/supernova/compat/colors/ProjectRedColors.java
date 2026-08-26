package com.mitchej123.supernova.compat.colors;

import com.mitchej123.supernova.Supernova;
import com.mitchej123.supernova.api.LightColors;
import com.mitchej123.supernova.api.TileLightRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

/**
 * ProjectRed Illumination -- lamps and floating light blocks. Meta 0-15 follows wool metadata order (White, Orange, Magenta, ..., Black).
 * <p>
 * Lit state is NOT in the metadata: GTNH ProjectRed keeps a per-World side table populated from
 * {@code TileLamp}/{@code LightMicroblock} lifecycle code, so these blocks are routed through
 * {@link TileLightRegistry}/{@code TileLightStore} (published by {@code MixinProjectRedLamp})
 * instead of static color registration.
 */
public final class ProjectRedColors {

    private static Block[] lampBlocks = new Block[0];

    public static void register() {
        final Block lamp = GameRegistry.findBlock("ProjRed|Illumination", "projectred.illumination.lamp");
        final Block airous = GameRegistry.findBlock("ProjRed|Illumination", "projectred.illumination.airousLight");
        int count = 0;
        if (lamp != null) count++;
        if (airous != null) count++;

        lampBlocks = new Block[count];
        int i = 0;
        if (lamp != null) {
            TileLightRegistry.register(lamp);
            lampBlocks[i++] = lamp;
        }
        if (airous != null) {
            TileLightRegistry.register(airous);
            lampBlocks[i] = airous;
        }

        if (count > 0) {
            Supernova.LOG.info("Registered {} ProjectRed Illumination blocks as tile-light sources", count);
        }
    }

    /** True for blocks whose emission is sourced from the ProjectRed lamp cache. */
    public static boolean isLampBlock(final Block block) {
        for (final Block b : lampBlocks) {
            if (b == block) return true;
        }
        return false;
    }

    /** Base packed color for a lamp variant (wool-order color index), unscaled. */
    public static int colorForVariant(final int variant) {
        return LightColors.BRIGHT_DYE_PALETTE[variant & 0xF];
    }

    private ProjectRedColors() {}
}
