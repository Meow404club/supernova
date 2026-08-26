package com.mitchej123.supernova.mixin.late.ic2;

import com.mitchej123.supernova.compat.ic2.IC2CropLightAccess;
import com.mitchej123.supernova.compat.ic2.IC2CropLightBridge;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Adds real vanilla lifecycle overrides because IC2 2.2.827 inherits rather than declares them. */
@Mixin(targets = "ic2.core.crop.TileEntityCrop", remap = false)
public abstract class MixinTileEntityCrop extends TileEntity implements IC2CropLightAccess {

    @Shadow(remap = false)
    public abstract int getEmittedLight();

    @Override
    public int supernova$getEmittedLight() {
        return getEmittedLight();
    }

    // These are intentionally plain overrides, not injectors or @Overwrite methods: IC2 inherits
    // them from TileEntity, so there is no target method to inject into or overwrite. The Java
    // compiler records the TileEntity override relationship and RFG reobfuscates the vanilla names.
    @Override
    public void validate() {
        super.validate();
        IC2CropLightBridge.publish(this, getEmittedLight());
    }

    @Override
    public void invalidate() {
        IC2CropLightBridge.remove(this);
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        IC2CropLightBridge.remove(this);
        super.onChunkUnload();
    }
}
