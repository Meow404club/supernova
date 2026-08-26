package com.mitchej123.supernova.compat.colors;

import com.mitchej123.supernova.Supernova;
import com.mitchej123.supernova.api.LightColors;
import com.mitchej123.supernova.api.LightColorRegistry;
import com.mitchej123.supernova.api.TileLightRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

/**
 * ProjectRed Illumination -- lamps and floating light blocks. Meta 0-15 follows wool metadata order (White, Orange, Magenta, ..., Black).
 * <p>
 * Full-block lamps keep lit state in their tile entity ({@code inverted != powered}), never in the
 * block metadata (bit 4 is only the inverted flag), so they are routed through
 * {@link TileLightRegistry}/{@code TileLightStore} (published by {@code MixinProjectRedLamp} and
 * {@code MixinTileLamp}) instead of static color registration.
 */
public final class ProjectRedColors {

    private static Block[] lampBlocks = new Block[0];

    public static void register() {
        final Block lamp = GameRegistry.findBlock("ProjRed|Illumination", "projectred.illumination.lamp");
        final Block airous = GameRegistry.findBlock("ProjRed|Illumination", "projectred.illumination.airousLight");

        if (lamp != null) {
            TileLightRegistry.register(lamp);
            lampBlocks = new Block[] {lamp};
        } else {
            lampBlocks = new Block[0];
        }

        // Airous lights are invisible blocks a fixture scatters while it is on; PR itself removes
        // them (setBlockToAir) when the source turns off, so the block's existence IS the lit
        // state and its metadata IS the wool-order color -- static registration is exact here.
        // They previously sat in the TileLightRegistry with no publisher and never lit.
        int count = 0;
        if (airous != null) {
            for (int meta = 0; meta < 16; meta++) {
                LightColorRegistry.register(airous, meta, LightColors.BRIGHT_DYE_PALETTE[meta]);
                count++;
            }
        }

        if (count > 0 || lampBlocks.length > 0) {
            Supernova.LOG.info("Registered {} ProjectRed Illumination light sources", lampBlocks.length + count);
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
