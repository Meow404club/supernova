package com.mitchej123.supernova.mixin.late.projectred;

import com.mitchej123.supernova.api.LightColorRegistry;
import com.mitchej123.supernova.api.TileLightStore;
import com.mitchej123.supernova.compat.colors.ProjectRedColors;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges GTNH ProjectRed Illumination lights into {@link TileLightStore}.
 * <p>
 * Lit-lamp state lives in {@code LampLightCache}, a {@code WeakHashMap} keyed by {@code World}
 * identity -- unresolvable from Supernova's worker threads (and the positional
 * {@code getLightValue} throws on a non-World accessor, which the emission chain swallows into a
 * zero-light fallback). Every lamp variant (plain lamps, airous lights, light microblocks and
 * buttons) funnels through the {@code BlockLamp$} singleton below, so mirroring those two methods
 * publishes the full lifecycle: recomputes, redstone toggles, chunk load/unload and removal.
 */
@Mixin(targets = "mrtjp.projectred.illumination.BlockLamp$", remap = false)
public abstract class MixinProjectRedLamp {

    @org.spongepowered.asm.mixin.Unique
    private static boolean supernova$loggedFirstPublish;

    @Inject(method = "setLightValue", at = @At("RETURN"), require = 0, expect = 0)
    private void supernova$onSetLightValue(final World world, final int x, final int y, final int z, final int level,
            final int color, final CallbackInfo ci) {
        if (world == null) return;
        // Lamp turned off (or dimmed to zero): drop the stale entry, otherwise the engine keeps
        // emitting the old color until the block is broken. TileLamp.recomputeLight always routes
        // through here with the new level; clearLightValue only covers removal paths.
        if (level <= 0) {
            TileLightStore.remove(world.provider.dimensionId, x, y, z);
            return;
        }
        if (!supernova$loggedFirstPublish) {
            supernova$loggedFirstPublish = true;
            com.mitchej123.supernova.Supernova.LOG.info(
                    "ProjectRed lamp bridge published first entry at ({}, {}, {})", x, y, z);
        }
        final Block block = world.getBlock(x, y, z);
        if (!ProjectRedColors.isLampBlock(block)) return;
        final int packed = LightColorRegistry.scaleToVanillaLight(ProjectRedColors.colorForVariant(color), level);
        TileLightStore.put(world.provider.dimensionId, x, y, z, packed);
    }

    @Inject(method = "clearLightValue", at = @At("RETURN"), require = 0, expect = 0)
    private void supernova$onClearLightValue(final World world, final int x, final int y, final int z, final CallbackInfo ci) {
        if (world == null) return;
        TileLightStore.remove(world.provider.dimensionId, x, y, z);
    }
}
