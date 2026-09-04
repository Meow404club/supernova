package com.mitchej123.supernova.compat.angelica;

import com.gtnewhorizons.angelica.api.BlockLightProvider;
import com.gtnewhorizons.angelica.api.SectionLightData;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import com.mitchej123.supernova.light.ChunkLightHelper;
import com.mitchej123.supernova.light.SWMRNibbleArray;
import com.mitchej123.supernova.light.SupernovaChunk;
import com.mitchej123.supernova.util.WorldUtil;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Implements Angelica's {@link BlockLightProvider}
 */
public class SupernovaBlockLightProvider implements BlockLightProvider {

    @Override
    public SectionLightData prepareSectionData(Chunk chunk, int sectionY) {
        final int minLight = WorldUtil.getMinLightSection();
        final int maxLight = WorldUtil.getMaxLightSection();
        if (sectionY < minLight || sectionY > maxLight) return null;

        final SupernovaChunk ext = (SupernovaChunk) chunk;
        final int idx = sectionY - minLight;

        final SWMRNibbleArray r = getChannel(ext.getBlockNibblesR(), idx);
        final SWMRNibbleArray g = getChannel(ext.getBlockNibblesG(), idx);
        final SWMRNibbleArray b = getChannel(ext.getBlockNibblesB(), idx);

        final SWMRNibbleArray skyR = getSkyNibble(ext.getSkyNibblesR(), idx);
        final SWMRNibbleArray skyG = getSkyNibble(ext.getSkyNibblesG(), idx);
        final SWMRNibbleArray skyB = getSkyNibble(ext.getSkyNibblesB(), idx);

        if (r == null && g == null && b == null && skyR == null && skyG == null && skyB == null) return null;
        return new SupernovaSectionLightData(r, g, b, skyR, skyG, skyB);
    }

    @Override
    public int getBlockLightRGB(IBlockAccess blockAccess, int x, int y, int z) {
        if (blockAccess instanceof WorldSlice ws) {
            final SectionLightData data = ws.getSectionLightData(x, y, z);
            if (data == null) return -1;
            return data.getRGB(x & 15, y & 15, z & 15);
        }

        // Main-thread fallback via World
        if (!(blockAccess instanceof World world)) return -1;

        final int cx = x >> 4;
        final int cz = z >> 4;
        if (!world.getChunkProvider().chunkExists(cx, cz)) return -1;

        final Chunk chunk = world.getChunkProvider().provideChunk(cx, cz);
        if (chunk == null) return -1;

        final int sectionY = y >> 4;
        final int minLight = WorldUtil.getMinLightSection();
        final int maxLight = WorldUtil.getMaxLightSection();
        if (sectionY < minLight || sectionY > maxLight) return -1;

        final SupernovaChunk ext = (SupernovaChunk) chunk;
        final int idx = sectionY - minLight;

        final int r = readNibble(ext.getBlockNibblesR(), idx, x, y, z);
        final int g = readNibble(ext.getBlockNibblesG(), idx, x, y, z);
        final int b = readNibble(ext.getBlockNibblesB(), idx, x, y, z);

        return (r << 8) | (g << 4) | b;
    }

    @Override
    public int getSkyLightRGB(IBlockAccess blockAccess, int x, int y, int z) {
        if (blockAccess instanceof WorldSlice ws) {
            final SectionLightData data = ws.getSectionLightData(x, y, z);
            if (data == null) return -1;
            return data.getSkyRGB(x & 15, y & 15, z & 15);
        }

        if (!(blockAccess instanceof World world)) return -1;

        final int cx = x >> 4;
        final int cz = z >> 4;
        if (!world.getChunkProvider().chunkExists(cx, cz)) return -1;

        final Chunk chunk = world.getChunkProvider().provideChunk(cx, cz);
        if (chunk == null) return -1;

        final int sectionY = y >> 4;
        final int minLight = WorldUtil.getMinLightSection();
        final int maxLight = WorldUtil.getMaxLightSection();
        if (sectionY < minLight || sectionY > maxLight) return -1;

        final SupernovaChunk ext = (SupernovaChunk) chunk;
        final int idx = sectionY - minLight;

        final int r = readSkyNibble(ext.getSkyNibblesR(), idx, x, y, z);
        final int g = readSkyNibble(ext.getSkyNibblesG(), idx, x, y, z);
        final int b = readSkyNibble(ext.getSkyNibblesB(), idx, x, y, z);

        return (r << 8) | (g << 4) | b;
    }

    private static SWMRNibbleArray getChannel(SWMRNibbleArray[] nibbles, int idx) {
        if (nibbles == null) return null;
        if (idx < 0 || idx >= nibbles.length) return null;
        final SWMRNibbleArray nib = nibbles[idx];
        if (nib == null || nib.isNullNibbleVisible()) return null;
        return nib;
    }

    private static SWMRNibbleArray getSkyNibble(SWMRNibbleArray[] nibbles, int idx) {
        if (nibbles == null) return null;
        if (idx < 0 || idx >= nibbles.length) return null;
        final SWMRNibbleArray nib = nibbles[idx];
        if (nib == null || nib.isNullNibbleVisible()) return null;
        return nib;
    }

    private static int readNibble(SWMRNibbleArray[] nibbles, int idx, int x, int y, int z) {
        if (nibbles == null) return 0;
        final SWMRNibbleArray nib = nibbles[idx];
        if (nib == null || nib.isNullNibbleVisible()) return 0;
        return nib.getVisible(x, y, z);
    }

    private static int readSkyNibble(SWMRNibbleArray[] nibbles, int idx, int x, int y, int z) {
        return ChunkLightHelper.readSkyNibble(nibbles, idx, x, y, z);
    }
}
