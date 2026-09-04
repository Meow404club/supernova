package com.mitchej123.supernova.mixin.late.projectred;

import com.mitchej123.supernova.compat.fmp.MultipartLightBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Synchronizes the extra powered state owned by {@code FLightButtonPart}. */
@Mixin(targets = "mrtjp.projectred.illumination.FLightButtonPart", remap = false)
public abstract class MixinFLightButtonPart {

    @Inject(method = "onAdded", at = @At("RETURN"), require = 0, expect = 0)
    private void supernova$onAdded(final CallbackInfo ci) {
        supernova$refresh();
    }

    @Inject(method = "checkAndUpdatePower", at = @At("RETURN"), require = 0, expect = 0)
    private void supernova$onPowerChanged(final CallbackInfo ci) {
        supernova$refresh();
    }

    @Inject(method = "readDesc", at = @At("RETURN"), require = 0, expect = 0)
    private void supernova$onReadDesc(final codechicken.lib.data.MCDataInput input, final CallbackInfo ci) {
        supernova$refresh();
    }

    @Inject(method = "read(Lcodechicken/lib/data/MCDataInput;I)V", at = @At("RETURN"), require = 0, expect = 0)
    private void supernova$onReadUpdate(final codechicken.lib.data.MCDataInput input, final int key,
            final CallbackInfo ci) {
        supernova$refresh();
    }

    private void supernova$refresh() {
        MultipartLightBridge.refreshAndRelight((codechicken.multipart.TMultiPart) (Object) this);
    }
}
