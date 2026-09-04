package com.mitchej123.supernova.mixin.late.fmp;

import codechicken.multipart.TMultiPart;
import codechicken.multipart.TileMultipart;
import com.mitchej123.supernova.api.TileLightStore;
import com.mitchej123.supernova.compat.fmp.MultipartLightBridge;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes the aggregate part emission of this {@code TileMultipart} into {@link TileLightStore}
 * whenever the part composition changes, so the worker-thread block-light engine can read the
 * emission without performing an off-thread {@code World.getTileEntity} lookup.
 */
@Mixin(value = TileMultipart.class, remap = false)
public abstract class MixinTileMultipart {

    // HEAD: publish before notifyPartChange queues the relight via func_147451_t,
    // so the worker thread never observes a stale TileLightStore value for the new partList.
    @Inject(method = "notifyPartChange", at = @At("HEAD"))
    private void supernova$onNotifyPartChange(final TMultiPart part, final CallbackInfo ci) {
        supernova$publish();
    }

    @Inject(method = "onChunkLoad", at = @At("RETURN"))
    private void supernova$onChunkLoad(final CallbackInfo ci) {
        supernova$publish();
    }

    // validate/invalidate are vanilla TileEntity overrides: at runtime they carry SRG names
    // (func_145829_t / func_145843_s), so these injectors must remap through the refmap. The
    // mixin-level remap=false only disables the class target mapping (codechicken is a mod class).
    @Inject(method = "validate", at = @At("RETURN"), remap = true, require = 0, expect = 0)
    private void supernova$onValidate(final CallbackInfo ci) {
        supernova$publish();
    }

    @Inject(method = "invalidate", at = @At("RETURN"), remap = true, require = 0, expect = 0)
    private void supernova$onInvalidate(final CallbackInfo ci) {
        final TileEntity self = (TileEntity) (Object) this;
        if (self.getWorldObj() == null) return;
        TileLightStore.remove(self.getWorldObj().provider.dimensionId, self.xCoord, self.yCoord, self.zCoord);
    }

    private void supernova$publish() {
        final TileEntity self = (TileEntity) (Object) this;
        if (self.getWorldObj() == null) return;
        MultipartLightBridge.publish((TileMultipart) (Object) this);
    }
}
