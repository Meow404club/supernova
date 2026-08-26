package com.mitchej123.supernova.compat.colors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mitchej123.supernova.api.PackedColorLight;
import org.junit.jupiter.api.Test;

class ThaumicHorizonsColorsTest {

    @Test
    void everyPaletteEntryPeaksAtOrbLightLevel() {
        final int[] palette = ThaumicHorizonsColors.DYE_DAMAGE_PALETTE;
        for (int i = 0; i < palette.length; i++) {
            assertEquals(14, PackedColorLight.maxComponent(palette[i]), "meta " + i + " must peak at the orbs' light level 14");
        }
    }

    @Test
    void warmColorsAreNotWhite() {
        // Regression: an earlier hand-written table mapped yellow and lime to plain white.
        final int[] palette = ThaumicHorizonsColors.DYE_DAMAGE_PALETTE;
        assertRedGreenDominant("yellow", palette[11], 12, 10);
        assertTrue(PackedColorLight.green(palette[10]) == 14
                && PackedColorLight.red(palette[10]) < 14
                && PackedColorLight.blue(palette[10]) < 14, "lime must be green-dominant");
    }

    private static void assertRedGreenDominant(final String name, final int packed, final int minR, final int minG) {
        assertTrue(PackedColorLight.red(packed) >= minR && PackedColorLight.green(packed) >= minG
                && PackedColorLight.blue(packed) <= 5, name + " must be red+green dominant");
    }
}
