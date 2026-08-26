package com.mitchej123.supernova.compat.fmp;

import codechicken.multipart.TMultiPart;
import codechicken.multipart.TileMultipart;
import com.mitchej123.supernova.api.PackedColorLight;
import com.mitchej123.supernova.api.SupernovaColoredPart;
import com.mitchej123.supernova.api.TileLightStore;
import java.util.List;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Aggregates the emission of every part of a {@link TileMultipart} into a single packed RGB value
 * in {@link TileLightStore}. Parts implementing {@link SupernovaColoredPart} contribute their own
 * color; all other parts fall back to their grayscale {@code getLightValue}.
 *
 * <p>{@link #refresh(World, int, int, int)} must be called from the main thread only (it performs a
 * live {@code getTileEntity} lookup); it lets the engine entry points re-sync the store whenever a
 * relight is requested at a multipart position -- part state changes (redstone toggles) do not fire
 * {@code notifyPartChange}, so without this the store would go stale until the next chunk load.
 */
public final class MultipartLightBridge {

    private MultipartLightBridge() {}

    public static void refresh(final World world, final int x, final int y, final int z) {
        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileMultipart) {
            publish((TileMultipart) te);
        }
    }

    /** Re-publish after a client description/update packet changes part state, then relight it. */
    public static void refreshAndRelight(final TMultiPart part) {
        final TileMultipart tile = part.tile();
        final World world = part.world();
        if (tile == null || world == null) return;
        publish(tile);
        world.updateLightByType(net.minecraft.world.EnumSkyBlock.Block, part.x(), part.y(), part.z());
    }

    public static void publish(final TileMultipart tile) {
        final World world = tile.getWorldObj();
        if (world == null) return;

        int r = 0, g = 0, b = 0;
        final List<TMultiPart> parts = tile.jPartList();
        for (final TMultiPart part : parts) {
            int packed = 0;
            if (part instanceof SupernovaColoredPart) {
                packed = ((SupernovaColoredPart) part).supernova$getPackedRGB();
            } else {
                final int v = part.getLightValue() & 0xF;
                if (v > 0) packed = PackedColorLight.pack(v, v, v);
            }
            if (packed == 0) continue;
            r = Math.max(r, (packed & PackedColorLight.RED_MASK) >> PackedColorLight.RED_SHIFT);
            g = Math.max(g, (packed & PackedColorLight.GREEN_MASK) >> PackedColorLight.GREEN_SHIFT);
            b = Math.max(b, (packed & PackedColorLight.BLUE_MASK) >> PackedColorLight.BLUE_SHIFT);
        }

        final int dim = world.provider.dimensionId;
        final int x = tile.xCoord, y = tile.yCoord, z = tile.zCoord;
        if (r == 0 && g == 0 && b == 0) {
            TileLightStore.remove(dim, x, y, z);
        } else {
            TileLightStore.put(dim, x, y, z, PackedColorLight.pack(r, g, b));
        }
    }
}
