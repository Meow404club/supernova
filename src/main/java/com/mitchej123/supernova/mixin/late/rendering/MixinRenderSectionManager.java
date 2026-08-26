package com.mitchej123.supernova.mixin.late.rendering;

import com.mitchej123.supernova.light.RenderUpdateTracer;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic only: attributes every Celeritas section rebuild request to its caller so residual
 * chunk-update storms that bypass World.markBlockRangeForRenderUpdate can be traced. Active only
 * while SupernovaConfig.enableStatsLog is on.
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class MixinRenderSectionManager {

    @Inject(method = "scheduleSectionForRebuild", at = @At("HEAD"), require = 0)
    private void supernova$traceScheduleRebuild(int x, int y, int z, boolean important, CallbackInfo ci) {
        RenderUpdateTracer.onInternalSchedule(x, y, z);
    }
}
