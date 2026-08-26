package com.mitchej123.supernova.mixin.late.projectred;

import com.mitchej123.supernova.api.LightColorRegistry;
import com.mitchej123.supernova.api.SupernovaColoredPart;
import com.mitchej123.supernova.compat.fmp.MultipartLightBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Duck for {@code mrtjp.projectred.illumination.BaseLightPart} (lantern/fixture parts and their
 * subclasses). GTNH ProjectRed computes these parts' light as plain grayscale ({@code getLightValue}
 * returns the level only), with the color used solely for its own halo rendering -- the duck lets
 * our TileLightStore bridge publish the colored value instead.
 */
@Mixin(targets = "mrtjp.projectred.illumination.BaseLightPart", remap = false)
public abstract class MixinBaseLightPart implements SupernovaColoredPart {

    @Shadow(remap = false)
    public abstract int getLightValue();

    @Shadow(remap = false)
    public abstract int getColor();

    @Override
    public int supernova$getPackedRGB() {
        final int level = this.getLightValue();
        if (level <= 0) return 0;
        return LightColorRegistry.scaleToVanillaLight(
                com.mitchej123.supernova.compat.colors.ProjectRedColors.colorForVariant(this.getColor()), level);
    }

    @Inject(method = "onAdded", at = @At("RETURN"), require = 0)
    private void supernova$onAdded(final CallbackInfo ci) {
        MultipartLightBridge.refreshAndRelight((codechicken.multipart.TMultiPart) (Object) this);
    }

    @Inject(method = "readDesc", at = @At("RETURN"), require = 0)
    private void supernova$onReadDesc(final codechicken.lib.data.MCDataInput input, final CallbackInfo ci) {
        MultipartLightBridge.refreshAndRelight((codechicken.multipart.TMultiPart) (Object) this);
    }
}
