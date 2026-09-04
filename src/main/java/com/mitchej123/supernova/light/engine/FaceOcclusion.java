package com.mitchej123.supernova.light.engine;

import com.mitchej123.supernova.Supernova;
import com.mitchej123.supernova.api.FaceLightOcclusion;
import com.mitchej123.supernova.api.PackedColorLight;
import com.mitchej123.supernova.api.PositionalColoredTranslucency;
import com.mitchej123.supernova.api.TranslucencyRegistry;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.BitSet;

/**
 * Per-face light occlusion registry and lookup.
 * <p>
 * Resolution order during propagation:
 * 1. {@code block instanceof FaceLightOcclusion} -> dynamic per-face opacity
 * 2. {@link #hasSidedTransparency(Block)} -> precomputed isSideSolid table
 * 3. Neither -> scalar getLightOpacity() (current behavior)
 */
public final class FaceOcclusion {

    /** Indexed by block ID. Set for blocks with per-face transparency (both interface and scan). */
    private static final BitSet HAS_SIDED_TRANSPARENCY = new BitSet();

    /**
     * Packed face solidity for non-interface blocks. {@code [blockId]} -> long[2] holding 96 bits. Bit index = meta * 6 + axisDirectionOrdinal. 1 = solid
     * (blocks light), 0 = transparent. Only populated for scan-discovered blocks, not FaceLightOcclusion implementors.
     */
    private static long[][] FACE_SOLIDITY = new long[0][];

    /** AxisDirection ordinal -> ForgeDirection for isSideSolid calls. */
    private static final ForgeDirection[] AXIS_TO_FORGE = {
            ForgeDirection.EAST,   // 0: POSITIVE_X
            ForgeDirection.WEST,   // 1: NEGATIVE_X
            ForgeDirection.SOUTH,  // 2: POSITIVE_Z
            ForgeDirection.NORTH,  // 3: NEGATIVE_Z
            ForgeDirection.UP,     // 4: POSITIVE_Y
            ForgeDirection.DOWN,   // 5: NEGATIVE_Y
    };

    private FaceOcclusion() {}

    public static boolean hasSidedTransparency(final Block block) {
        final int id = Block.getIdFromBlock(block);
        return id >= 0 && HAS_SIDED_TRANSPARENCY.get(id);
    }

    /**
     * True when sky cannot continue down through this face. Opaque cubes and solid
     * sided faces stop the column. Fluids and other uniformly non-solid blocks do not.
     */
    public static boolean blocksSkyColumn(final Block block, final int meta, final int axisDir) {
        if (block.isOpaqueCube()) {
            return true;
        }
        return hasSidedTransparency(block) && isFaceSolid(block, meta, axisDir);
    }

    /**
     * Returns true if the given face of a non-interface block is solid (blocks light).
     * Only valid when {@link #hasSidedTransparency(Block)} returns true and the block does NOT implement {@link FaceLightOcclusion}.
     */
    public static boolean isFaceSolid(final Block block, final int meta, final int axisDir) {
        final int id = Block.getIdFromBlock(block);
        if (id < 0 || id >= FACE_SOLIDITY.length) return true;
        final long[] bits = FACE_SOLIDITY[id];
        if (bits == null) return true;
        // Table only covers meta 0-15 = Extended-meta blocks must implement FaceLightOcclusion for per-face control; default to solid here.
        if (meta > 15) return true;
        final int bitIndex = meta * 6 + axisDir;
        return (bits[bitIndex >> 6] & (1L << (bitIndex & 63))) != 0;
    }

    /**
     * Full resolution for per-channel absorption: interface -> precomputed table -> registry/vanilla.
     *
     * @param block      destination block
     * @param meta       block metadata
     * @param rawOpacity block.getLightOpacity() value
     * @param axisDir    AxisDirection ordinal of the face being checked
     * @return packed absorption ({@code 0x0R0G0B})
     */
    public static int getDirectionalAbsorption(final Block block, final int meta, final int rawOpacity, final int axisDir) {
        return getDirectionalAbsorption(Block.getIdFromBlock(block), block, meta, rawOpacity, axisDir);
    }

    public static int getDirectionalAbsorption(final int blockId, final Block block, final int meta, final int rawOpacity, final int axisDir) {
        if (block instanceof FaceLightOcclusion) {
            return ((FaceLightOcclusion) block).getDirectionalLightAbsorption(meta, AXIS_TO_FORGE[axisDir]);
        }
        if (isFaceSolid(block, meta, axisDir)) {
            return TranslucencyRegistry.getPackedAbsorptionNoInterface(blockId, block, meta);
        }
        // Face not solid -- minimum decay (1 per channel)
        return PackedColorLight.pack(1, 1, 1);
    }

    /**
     * Full absorption resolution: cached non-directional > positional interface > sided transparency > registry/vanilla.
     */
    public static int resolveAbsorption(final IBlockAccess world, final Block block, final int meta, final int dirOrdinal, final int x, final int y, final int z) {
        final int id = Block.getIdFromBlock(block);
        // Fast path: non-directional cached absorption (covers static ColoredTranslucency, registry, vanilla)
        final int cached = TranslucencyRegistry.getPackedAbsorptionCached(id, block, meta);
        if (cached >= 0) return cached;

        // Positional interface (uncacheable -- needs world + position)
        if (block instanceof PositionalColoredTranslucency) {
            return PackedColorLight.transmittanceToAbsorption(((PositionalColoredTranslucency) block).getColoredTransmittance(world, meta, x, y, z));
        }

        // Directional: FaceLightOcclusion or sided transparency
        final int rawOpacity = block.getLightOpacity();
        if (rawOpacity > 1 && hasSidedTransparency(block)) {
            return getDirectionalAbsorption(id, block, meta, rawOpacity, dirOrdinal);
        }

        return TranslucencyRegistry.getPackedAbsorptionNoInterface(id, block, meta);
    }

    /**
     * Scalar absorption resolution for non-RGB mode. Returns a single int (1-15) instead of packed RGB.
     * Uses vanilla {@code Block.getLightOpacity()} and directional face checks, bypassing TranslucencyRegistry entirely.
     */
    public static int resolveScalarAbsorption(final Block block, final int meta, final int dirOrdinal, final int x, final int y, final int z) {
        if (block instanceof FaceLightOcclusion) {
            return Math.max(1, PackedColorLight.maxComponent(((FaceLightOcclusion) block).getDirectionalLightAbsorption(meta, AXIS_TO_FORGE[dirOrdinal])));
        }
        final int opacity = block.getLightOpacity();
        if (opacity > 1 && hasSidedTransparency(block)) {
            return isFaceSolid(block, meta, dirOrdinal) ? opacity : 1;
        }
        return Math.max(1, opacity);
    }

    /**
     * Scan all registered blocks at postInit. For blocks where {@code !isOpaqueCube() && getLightOpacity() > 0}, probe isSideSolid for all 16 metas × 6 faces.
     */
    @SuppressWarnings("unchecked")
    public static void registerDefaults() {
        int count = 0;
        final FakeBlockAccess fake = new FakeBlockAccess();

        for (final Block block : (Iterable<Block>) GameData.getBlockRegistry()) {
            final int id = Block.getIdFromBlock(block);
            if (id < 0) continue;

            // Interface implementors: just mark the BitSet, no table needed
            if (block instanceof FaceLightOcclusion) {
                HAS_SIDED_TRANSPARENCY.set(id);
                count++;
                continue;
            }

            // Scan candidates: not fully opaque but has opacity
            if (block.isOpaqueCube() || block.getLightOpacity() <= 0) {
                continue;
            }

            // Probe meta 0-15 × 6 faces. Blocks with meta > 15 (EndlessIDs) must implement FaceLightOcclusion for per-face control; isFaceSolid defaults to true.
            fake.setBlock(block);
            boolean anySidedDifference = false;
            long bits0 = 0, bits1 = 0;

            for (int meta = 0; meta < 16; meta++) {
                fake.setMeta(meta);
                for (int dir = 0; dir < 6; dir++) {
                    final boolean solid = block.isSideSolid(fake, 0, 0, 0, AXIS_TO_FORGE[dir]);
                    if (solid) {
                        final int bitIndex = meta * 6 + dir;
                        if (bitIndex < 64) {
                            bits0 |= 1L << bitIndex;
                        } else {
                            bits1 |= 1L << (bitIndex - 64);
                        }
                    } else {
                        anySidedDifference = true;
                    }
                }
            }

            // Mixed solid/open faces only (stairs, slabs). Water/ice have no solid faces
            // and must keep vanilla opacity (3), not the fence-style 1-per-block decay.
            if (anySidedDifference && (bits0 != 0 || bits1 != 0)) {
                if (id >= FACE_SOLIDITY.length) {
                    final long[][] old = FACE_SOLIDITY;
                    FACE_SOLIDITY = new long[Math.max(id + 1, old.length * 2)][];
                    System.arraycopy(old, 0, FACE_SOLIDITY, 0, old.length);
                }
                FACE_SOLIDITY[id] = new long[] { bits0, bits1 };
                HAS_SIDED_TRANSPARENCY.set(id);
                count++;
            }
        }

        Supernova.LOG.info("FaceOcclusion: registered {} blocks with per-face transparency", count);

        // Mark directional blocks as uncacheable in TranslucencyRegistry so its cache skips them
        for (int id = HAS_SIDED_TRANSPARENCY.nextSetBit(0); id >= 0; id = HAS_SIDED_TRANSPARENCY.nextSetBit(id + 1)) {
            TranslucencyRegistry.markUncacheable(id);
        }
    }

    /**
     * Minimal IBlockAccess for probing isSideSolid at registration time. Returns the configured block/meta at (0,0,0), air everywhere else.
     */
    private static final class FakeBlockAccess implements IBlockAccess {

        private Block block = Blocks.air;
        private int meta;

        void setBlock(final Block block) {this.block = block;}

        void setMeta(final int meta) {this.meta = meta;}

        @Override
        public Block getBlock(int x, int y, int z) {
            return (x == 0 && y == 0 && z == 0) ? block : Blocks.air;
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {return null;}

        @Override
        public int getLightBrightnessForSkyBlocks(int x, int y, int z, int lb) {return 0;}

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return (x == 0 && y == 0 && z == 0) ? meta : 0;
        }

        @Override
        public int isBlockProvidingPowerTo(int x, int y, int z, int side) {return 0;}

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            return !(x == 0 && y == 0 && z == 0);
        }

        @Override
        public BiomeGenBase getBiomeGenForCoords(int x, int z) {return BiomeGenBase.plains;}

        @Override
        public int getHeight() {return 256;}

        @Override
        public boolean extendedLevelsInChunkCache() {return false;}

        @Override
        public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean def) {
            if (x == 0 && y == 0 && z == 0) {
                return block.isSideSolid(this, x, y, z, side);
            }
            return def;
        }
    }
}
