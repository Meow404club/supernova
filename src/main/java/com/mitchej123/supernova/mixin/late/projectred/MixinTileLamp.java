package com.mitchej123.supernova.mixin.late.projectred;

import com.mitchej123.supernova.api.LightColorRegistry;
import com.mitchej123.supernova.api.TileLightStore;
import com.mitchej123.supernova.compat.colors.ProjectRedColors;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes full-block lamp emission into {@link TileLightStore} when the tile entity joins the
 * world, before the light worker can run an initial scan of the freshly loaded chunk. Runtime
 * recomputes stay covered by the {@code BlockLamp$} side-table bridge ({@code MixinProjectRedLamp});
 * without this hook the very first scan after a world load sees an empty store entry and records
 * the chunk as unlit until the lamp's first tick happens to re-notify.
 */
@Mixin(targets = "mrtjp.projectred.illumination.TileLamp", remap = false)
public abstract class MixinTileLamp {

    @Shadow(remap = false)
    public abstract boolean inverted();

    @Shadow(remap = false)
    public abstract boolean powered();

    @Shadow(remap = false)
    public abstract int getColor();

    // Publishing must NOT happen in validate(): tile entities validate during asynchronous chunk
    // deserialization, where getBlockMetadata would re-enter a synchronous load of the very chunk
    // still being read (StackOverflow). PR's validate schedules a tick anyway, and the
    // onScheduledTick hook below then publishes with the chunk fully loaded.
    //
    // PR only recomputes when redstone state CHANGES; after a chunk load the cached powered flag
    // may be stale (signal not yet readable at the scheduled tick) and no further change ever
    // fires, leaving the store dark forever. Re-publish from the settled fields after every
    // scheduled tick so the store converges without relying on PR's own change detection.
    @Inject(method = "onScheduledTick", at = @At("TAIL"), require = 0, expect = 0)
    private void supernova$republishOnScheduledTick(final CallbackInfo ci) {
        this.supernova$publish();
    }

    @Unique
    private void supernova$publish() {
        final TileEntity self = (TileEntity) (Object) this;
        final World world = self.getWorldObj();
        if (world == null) return;
        final int dim = world.provider.dimensionId;
        if (this.inverted() != this.powered()) {
            TileLightStore.put(dim, self.xCoord, self.yCoord, self.zCoord, LightColorRegistry
                    .scaleToVanillaLight(ProjectRedColors.colorForVariant(this.getColor()), 15));
        } else {
            TileLightStore.remove(dim, self.xCoord, self.yCoord, self.zCoord);
        }
    }
}
