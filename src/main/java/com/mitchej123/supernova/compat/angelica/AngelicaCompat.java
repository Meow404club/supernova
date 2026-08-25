package com.mitchej123.supernova.compat.angelica;

import com.gtnewhorizons.angelica.api.BlockLightProvider;
import com.gtnewhorizons.angelica.api.TintRegistry;
import com.mitchej123.supernova.Supernova;
import com.mitchej123.supernova.client.ColoredLightHelper;
import com.mitchej123.supernova.client.TintBlendMode;

public class AngelicaCompat {

    private static int firstOrdinal;
    private static boolean registered;

    public static void enableColoredLight() {
        BlockLightProvider.enableColoredLight();
    }

    public static void register() {
        BlockLightProvider.register(new SupernovaBlockLightProvider());
        Supernova.LOG.info("Registered Supernova BlockLightProvider for Angelica");

        firstOrdinal = TintRegistry.getModeCount();
        for (TintBlendMode mode : TintBlendMode.values()) {
            // Wrap with the shared per-channel block/sky max combine -- Angelica's applyBlockLightTint calls
            // these modes directly with raw block and sky RGB, bypassing ColoredLightHelper.computeTint.
            TintRegistry.registerMode(
                    mode.name(),
                    (br, bg, bb, sr, sg, sb, out) -> ColoredLightHelper.combineAndCompute(mode::computeTint, br, bg, bb, sr, sg, sb, out));
        }
        // Set the active mode from config
        TintRegistry.setCurrentByOrdinal(firstOrdinal + TintBlendMode.current.ordinal());

        // Redirect supernova's tint lookups through Angelica's registry so keybind cycling stays in sync
        ColoredLightHelper.setActiveTintFunction((br, bg, bb, sr, sg, sb, out) -> TintRegistry.getCurrent().computeTint(br, bg, bb, sr, sg, sb, out));

        registered = true;
        Supernova.LOG.info("Registered {} tint blend modes with Angelica", TintBlendMode.values().length);
    }

    public static void syncTintMode() {
        if (!registered) return;
        TintRegistry.setCurrentByOrdinal(firstOrdinal + TintBlendMode.current.ordinal());
    }
}
