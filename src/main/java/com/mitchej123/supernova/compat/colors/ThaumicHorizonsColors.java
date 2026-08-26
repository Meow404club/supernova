package com.mitchej123.supernova.compat.colors;

import com.mitchej123.supernova.Supernova;
import com.mitchej123.supernova.api.LightColorRegistry;
import com.mitchej123.supernova.api.LightColors;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

/**
 * Thaumic Horizons -- vats, dynamos, soul beacons, glowing clouds, and miscellaneous blocks.
 */
public final class ThaumicHorizonsColors {

    private static final String MOD = "ThaumicHorizons";

    /**
     * Focus light orbs (light/lightSolar) keep their color as ItemDye damage in metadata 0-15.
     * Values derived from the vanilla fleece color table (EntitySheep.fleeceColorTable, reversed
     * into dye damage order) with each color's brightest channel normalized to the orbs'
     * positional light level of 14. An earlier hand-written table mapped yellow/lime to plain
     * white -- do not eyeball these, recompute from the table when adjusting.
     */
    static final int[] DYE_DAMAGE_PALETTE = {
            0x0C0C0E, // 0 black
            0x0E0504, // 1 red
            0x090E05, // 2 green
            0x0E0905, // 3 brown
            0x03040E, // 4 blue
            0x09040E, // 5 purple
            0x020C0E, // 6 cyan
            0x0E0E0E, // 7 silver
            0x0D0D0E, // 8 gray
            0x0E070A, // 9 pink
            0x090E04, // 10 lime
            0x0E0D03, // 11 yellow
            0x04070E, // 12 light blue
            0x0D030E, // 13 magenta
            0x0E0803, // 14 orange
            0x0E0E0E, // 15 white
    };

    public static void register() {
        int count = 0;

        count += ColorRegistrationHelper.registerPerMeta(MOD, "light", DYE_DAMAGE_PALETTE);
        count += ColorRegistrationHelper.registerPerMeta(MOD, "lightSolar", DYE_DAMAGE_PALETTE);

        // Uniform blocks
        count += ColorRegistrationHelper.registerBlock(MOD, "alchemite", 8, 6, 2);
        count += ColorRegistrationHelper.registerBlock(MOD, "crystalDeep", 12, 15, 15);
        count += ColorRegistrationHelper.registerBlock(MOD, "nodeMonitor", 3, 6, 7);
        count += ColorRegistrationHelper.registerBlock(MOD, "soulBeacon", LightColors.DYE_WHITE);
        count += ColorRegistrationHelper.registerBlock(MOD, "synthNode", 5, 8, 8);
        count += ColorRegistrationHelper.registerBlock(MOD, "vat", 8, 6, 3);
        count += ColorRegistrationHelper.registerBlock(MOD, "vatInterior", 8, 6, 3);
        count += ColorRegistrationHelper.registerBlock(MOD, "vatSolid", 8, 6, 3);
        count += ColorRegistrationHelper.registerBlock(MOD, "voidTH", 1, 0, 3);
        count += ColorRegistrationHelper.registerBlock(MOD, "essentiaDynamo", 7, 7, 7);
        count += ColorRegistrationHelper.registerBlock(MOD, "visDynamo", 5, 8, 8);
        count += ColorRegistrationHelper.registerBlock(MOD, "soulJar", LightColors.DIM_GRAY);

        // cloudGlowingTH -- specific metas only
        Block cloud = GameRegistry.findBlock(MOD, "cloudGlowingTH");
        if (cloud != null) {
            LightColorRegistry.register(cloud, 1, LightColors.DYE_WHITE);
            LightColorRegistry.register(cloud, 4, 4, 4, 4);
            LightColorRegistry.register(cloud, 5, 15, 4, 2);
            LightColorRegistry.register(cloud, 6, 11, 4, 13);
            LightColorRegistry.register(cloud, 8, 10, 15, 10);
            LightColorRegistry.register(cloud, 9, 15, 14, 4);
            count += 6;
        }

        if (count > 0) {
            Supernova.LOG.info("Registered {} Thaumic Horizons light colors", count);
        }
    }

    private ThaumicHorizonsColors() {}
}
