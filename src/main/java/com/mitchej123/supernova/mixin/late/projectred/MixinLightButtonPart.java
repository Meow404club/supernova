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
 * Duck for {@code mrtjp.projectred.illumination.LightButtonPart} (illuminated buttons). Same
 * grayscale-vs-color situation as the fixture parts: {@code getLightValue} is {@code 5} when lit
 * with no color, the dye index lives in {@code getColor}.
 */
@Mixin(targets = "mrtjp.projectred.illumination.LightButtonPart", remap = false)
public abstract class MixinLightButtonPart implements SupernovaColoredPart {

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
        if (supernova$isForceButton()) return;
        MultipartLightBridge.refreshAndRelight((codechicken.multipart.TMultiPart) (Object) this);
    }

    @Inject(method = "readDesc", at = @At("RETURN"), require = 0)
    private void supernova$onReadDesc(final codechicken.lib.data.MCDataInput input, final CallbackInfo ci) {
        if (supernova$isForceButton()) return;
        MultipartLightBridge.refreshAndRelight((codechicken.multipart.TMultiPart) (Object) this);
    }

    @Inject(method = "read(Lcodechicken/lib/data/MCDataInput;I)V", at = @At("RETURN"), require = 0)
    private void supernova$onReadUpdate(final codechicken.lib.data.MCDataInput input, final int key,
            final CallbackInfo ci) {
        if (supernova$isForceButton()) return;
        MultipartLightBridge.refreshAndRelight((codechicken.multipart.TMultiPart) (Object) this);
    }

    private boolean supernova$isForceButton() {
        // FLightButtonPart owns an additional powered field and updates it only after its calls to
        // these superclass methods return; its dedicated mixin publishes the final combined state.
        return this.getClass().getName().equals("mrtjp.projectred.illumination.FLightButtonPart");
    }
}
