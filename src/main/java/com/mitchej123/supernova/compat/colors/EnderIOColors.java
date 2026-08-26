package com.mitchej123.supernova.compat.colors;

import com.mitchej123.supernova.Supernova;
import com.mitchej123.supernova.api.LightColorRegistry;
import com.mitchej123.supernova.api.LightColors;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

/**
 * Ender IO -- electric lights and their invisible light nodes.
 * <p>
 * Both {@code BlockElectricLight} and {@code BlockLightNode} report their emission only through
 * the positional {@code getLightValue} overload ({@code meta > 0 ? 15 : 0}) with a static light
 * value of 0, and they carry no color registration of their own. The full-chunk scan's static
 * filter therefore skipped them and only opportunistic incremental checkBlocks lit them,
 * producing partial fields that later decreases eroded. Their lit state lives in block metadata
 * (unlike ProjectRed lamps), so plain per-meta static registration makes them visible to every
 * engine path.
 */
public final class EnderIOColors {

    private static final String MOD = "EnderIO";

    public static void register() {
        int count = 0;

        // meta 0 = off (explicit zero), meta 1 = lit at full white to match the vanilla scalar.
        count += registerLitBlock("blockElectricLight");
        count += registerLitBlock("blockLightNode");

        if (count > 0) {
            Supernova.LOG.info("Registered {} Ender IO light blocks", count);
        }
    }

    private static int registerLitBlock(String name) {
        final Block block = GameRegistry.findBlock(MOD, name);
        if (block == null) return 0;
        LightColorRegistry.register(block, 0, 0, 0, 0);
        LightColorRegistry.register(block, 1, LightColors.DYE_WHITE);
        return 1;
    }

    private EnderIOColors() {}
}
