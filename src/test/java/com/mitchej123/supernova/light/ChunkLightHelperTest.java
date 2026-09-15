package com.mitchej123.supernova.light;

import com.mitchej123.supernova.util.WorldUtil;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkLightHelperTest {

    // With bounds (0,15): minLightSection=-1, maxLightSection=16, totalLightSections=18
    private static final int TOTAL_LIGHT_SECTIONS = 18;
    // Section Y=4 -> nibble index 5 (4 - (-1))
    private static final int TEST_SECTION_Y = 4;
    private static final int TEST_NIBBLE_IDX = 5;

    @BeforeAll
    static void initBounds() {
        WorldUtil.setBounds(0, 15);
    }

    private static ExtendedBlockStorage[] makeStorageArrays() {
        ExtendedBlockStorage[] arr = new ExtendedBlockStorage[16];
        arr[TEST_SECTION_Y] = new ExtendedBlockStorage(TEST_SECTION_Y << 4, true);
        return arr;
    }

    private static SWMRNibbleArray[] makeNullNibbles() {
        SWMRNibbleArray[] arr = new SWMRNibbleArray[TOTAL_LIGHT_SECTIONS];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = new SWMRNibbleArray(null, true); // NULL state
        }
        return arr;
    }

    private static SWMRNibbleArray[] makeEmptyNibbles() {
        SWMRNibbleArray[] arr = new SWMRNibbleArray[TOTAL_LIGHT_SECTIONS];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = new SWMRNibbleArray(); // UNINIT state
        }
        return arr;
    }

    @Nested
    class HasSavedBlockData {

        @Test
        void returnsFalseWhenAllNull() {
            SWMRNibbleArray[] blockR = makeNullNibbles();
            assertFalse(ChunkLightHelper.hasSavedBlockData(blockR, makeStorageArrays()));
        }

        @Test
        void returnsTrueWhenSectionHasData() {
            SWMRNibbleArray[] blockR = makeNullNibbles();
            SWMRNibbleArray nib = new SWMRNibbleArray();
            nib.set(0, 0, 0, 5);
            nib.updateVisible();
            blockR[TEST_NIBBLE_IDX] = nib;
            assertTrue(ChunkLightHelper.hasSavedBlockData(blockR, makeStorageArrays()));
        }

        @Test
        void ignoresSectionsWithoutStorage() {
            SWMRNibbleArray[] blockR = makeEmptyNibbles(); // UNINIT = not null-visible
            ExtendedBlockStorage[] storage = new ExtendedBlockStorage[16]; // all null
            assertFalse(ChunkLightHelper.hasSavedBlockData(blockR, storage));
        }
    }

    @Nested
    class ImportVanillaSky {

        @Test
        void importsUnconditionally() {
            SWMRNibbleArray[] skyR = makeNullNibbles();
            SWMRNibbleArray[] skyG = makeNullNibbles();
            SWMRNibbleArray[] skyB = makeNullNibbles();
            ExtendedBlockStorage[] storage = makeStorageArrays();

            // Set a value in vanilla sky
            storage[TEST_SECTION_Y].getSkylightArray().set(3, 5, 7, 12);

            ChunkLightHelper.importVanillaSky(skyR, skyG, skyB, storage, false);

            assertEquals(12, skyR[TEST_NIBBLE_IDX].getVisible(3, 5, 7));
            assertEquals(12, skyG[TEST_NIBBLE_IDX].getVisible(3, 5, 7));
            assertEquals(12, skyB[TEST_NIBBLE_IDX].getVisible(3, 5, 7));
        }

        @Test
        void onlyWhereNullSkipsExistingData() {
            SWMRNibbleArray[] skyR = makeNullNibbles();
            SWMRNibbleArray[] skyG = makeNullNibbles();
            SWMRNibbleArray[] skyB = makeNullNibbles();

            // Pre-populate R with existing data (not null)
            SWMRNibbleArray existing = new SWMRNibbleArray();
            existing.set(3, 5, 7, 9);
            existing.updateVisible();
            skyR[TEST_NIBBLE_IDX] = existing;

            ExtendedBlockStorage[] storage = makeStorageArrays();
            storage[TEST_SECTION_Y].getSkylightArray().set(3, 5, 7, 12);

            ChunkLightHelper.importVanillaSky(skyR, skyG, skyB, storage, true);

            // R should retain existing value (9), not vanilla (12)
            assertEquals(9, skyR[TEST_NIBBLE_IDX].getVisible(3, 5, 7));
        }

        @Test
        void onlyWhereNullImportsNullSections() {
            SWMRNibbleArray[] skyR = makeNullNibbles();
            SWMRNibbleArray[] skyG = makeNullNibbles();
            SWMRNibbleArray[] skyB = makeNullNibbles();
            ExtendedBlockStorage[] storage = makeStorageArrays();
            storage[TEST_SECTION_Y].getSkylightArray().set(0, 0, 0, 10);

            ChunkLightHelper.importVanillaSky(skyR, skyG, skyB, storage, true);

            // Null section should be imported
            assertEquals(10, skyR[TEST_NIBBLE_IDX].getVisible(0, 0, 0));
        }

        @Test
        void handlesNullGBArrays() {
            SWMRNibbleArray[] skyR = makeNullNibbles();
            ExtendedBlockStorage[] storage = makeStorageArrays();
            storage[TEST_SECTION_Y].getSkylightArray().set(1, 2, 3, 8);

            // Scalar mode: G/B are null
            ChunkLightHelper.importVanillaSky(skyR, null, null, storage, false);

            assertEquals(8, skyR[TEST_NIBBLE_IDX].getVisible(1, 2, 3));
        }
    }

    @Nested
    class ImportVanillaBlock {

        @Test
        void importsBlockLight() {
            SWMRNibbleArray[] blockR = makeNullNibbles();
            SWMRNibbleArray[] blockG = makeNullNibbles();
            SWMRNibbleArray[] blockB = makeNullNibbles();
            ExtendedBlockStorage[] storage = makeStorageArrays();
            storage[TEST_SECTION_Y].getBlocklightArray().set(2, 3, 4, 14);

            ChunkLightHelper.importVanillaBlock(blockR, blockG, blockB, storage);

            assertEquals(14, blockR[TEST_NIBBLE_IDX].getVisible(2, 3, 4));
            assertEquals(14, blockG[TEST_NIBBLE_IDX].getVisible(2, 3, 4));
            assertEquals(14, blockB[TEST_NIBBLE_IDX].getVisible(2, 3, 4));
        }
    }

    @Nested
    class SyncSkyToVanilla {

        @Test
        void copiesVisibleDataToVanilla() {
            SWMRNibbleArray[] skyNibbles = makeEmptyNibbles();
            skyNibbles[TEST_NIBBLE_IDX].set(5, 10, 3, 11);
            skyNibbles[TEST_NIBBLE_IDX].updateVisible();

            ExtendedBlockStorage[] storage = makeStorageArrays();
            ChunkLightHelper.syncSkyToVanilla(skyNibbles, storage);

            assertEquals(11, storage[TEST_SECTION_Y].getSkylightArray().get(5, 10, 3));
        }

        @Test
        void fillsNullNibblesWithFF() {
            SWMRNibbleArray[] skyNibbles = makeNullNibbles();
            ExtendedBlockStorage[] storage = makeStorageArrays();

            // Zero out vanilla sky first
            NibbleArray vanilla = storage[TEST_SECTION_Y].getSkylightArray();
            java.util.Arrays.fill(vanilla.data, (byte) 0);

            ChunkLightHelper.syncSkyToVanilla(skyNibbles, storage);

            // NULL nibble -> 0xFF fill (sky=15 for both nibbles in each byte)
            for (byte b : vanilla.data) {
                assertEquals((byte) 0xFF, b);
            }
        }
    }

    @Nested
    class SyncBlockToVanilla {

        @Test
        void computesMaxRGB() {
            SWMRNibbleArray[] blockR = makeEmptyNibbles();
            SWMRNibbleArray[] blockG = makeEmptyNibbles();
            SWMRNibbleArray[] blockB = makeEmptyNibbles();

            // R=5, G=12, B=8 at (1,2,3) -> vanilla should be 12
            blockR[TEST_NIBBLE_IDX].set(1, 2, 3, 5);
            blockG[TEST_NIBBLE_IDX].set(1, 2, 3, 12);
            blockB[TEST_NIBBLE_IDX].set(1, 2, 3, 8);
            blockR[TEST_NIBBLE_IDX].updateVisible();
            blockG[TEST_NIBBLE_IDX].updateVisible();
            blockB[TEST_NIBBLE_IDX].updateVisible();

            ExtendedBlockStorage[] storage = makeStorageArrays();
            ChunkLightHelper.syncBlockToVanilla(blockR, blockG, blockB, storage);

            assertEquals(12, storage[TEST_SECTION_Y].getBlocklightArray().get(1, 2, 3));
        }

        @Test
        void handlesNullGBArrays() {
            SWMRNibbleArray[] blockR = makeEmptyNibbles();
            blockR[TEST_NIBBLE_IDX].set(4, 5, 6, 7);
            blockR[TEST_NIBBLE_IDX].updateVisible();

            ExtendedBlockStorage[] storage = makeStorageArrays();
            ChunkLightHelper.syncBlockToVanilla(blockR, null, null, storage);

            // Scalar: vanilla = R value
            assertEquals(7, storage[TEST_SECTION_Y].getBlocklightArray().get(4, 5, 6));
        }
    }

    @Nested
    class GetBlockLight {

        @Test
        void returnsMaxRGB() {
            SWMRNibbleArray[] blockR = makeEmptyNibbles();
            SWMRNibbleArray[] blockG = makeEmptyNibbles();
            SWMRNibbleArray[] blockB = makeEmptyNibbles();

            // world Y=68 -> section 4, local Y=4
            blockR[TEST_NIBBLE_IDX].set(7, 4, 9, 3);
            blockG[TEST_NIBBLE_IDX].set(7, 4, 9, 14);
            blockB[TEST_NIBBLE_IDX].set(7, 4, 9, 6);
            blockR[TEST_NIBBLE_IDX].updateVisible();
            blockG[TEST_NIBBLE_IDX].updateVisible();
            blockB[TEST_NIBBLE_IDX].updateVisible();

            assertEquals(14, ChunkLightHelper.getBlockLight(blockR, blockG, blockB, 7, 68, 9));
        }

        @Test
        void scalarModeReturnsROnly() {
            SWMRNibbleArray[] blockR = makeEmptyNibbles();
            blockR[TEST_NIBBLE_IDX].set(0, 4, 0, 10);
            blockR[TEST_NIBBLE_IDX].updateVisible();

            assertEquals(10, ChunkLightHelper.getBlockLight(blockR, null, null, 0, 68, 0));
        }

        @Test
        void returnsZeroForOutOfRange() {
            SWMRNibbleArray[] blockR = makeEmptyNibbles();
            assertEquals(0, ChunkLightHelper.getBlockLight(blockR, null, null, 0, -32, 0));
            assertEquals(0, ChunkLightHelper.getBlockLight(blockR, null, null, 0, 300, 0));
        }
    }

    @Nested
    class GetSkyLight {

        @Test
        void returnsMaxRGB() {
            SWMRNibbleArray[] skyR = makeEmptyNibbles();
            SWMRNibbleArray[] skyG = makeEmptyNibbles();
            SWMRNibbleArray[] skyB = makeEmptyNibbles();

            skyR[TEST_NIBBLE_IDX].set(2, 4, 5, 8);
            skyG[TEST_NIBBLE_IDX].set(2, 4, 5, 13);
            skyB[TEST_NIBBLE_IDX].set(2, 4, 5, 5);
            skyR[TEST_NIBBLE_IDX].updateVisible();
            skyG[TEST_NIBBLE_IDX].updateVisible();
            skyB[TEST_NIBBLE_IDX].updateVisible();

            assertEquals(13, ChunkLightHelper.getSkyLight(skyR, skyG, skyB, 2, 68, 5));
        }

        @Test
        void returns15ForAboveMax() {
            SWMRNibbleArray[] skyR = makeEmptyNibbles();
            assertEquals(15, ChunkLightHelper.getSkyLight(skyR, null, null, 0, 300, 0));
        }

        @Test
        void returns0ForBelowMin() {
            SWMRNibbleArray[] skyR = makeEmptyNibbles();
            assertEquals(0, ChunkLightHelper.getSkyLight(skyR, null, null, 0, -32, 0));
        }

        @Test
        void returns15ForNullArray() {
            assertEquals(15, ChunkLightHelper.getSkyLight(null, null, null, 0, 64, 0));
        }

        @Test
        void returns15ForNullNibble() {
            SWMRNibbleArray[] skyR = makeNullNibbles();
            assertEquals(15, ChunkLightHelper.getSkyLight(skyR, null, null, 0, 64, 0));
        }

        @Test
        void returns0ForUninitNibble() {
            SWMRNibbleArray[] skyR = makeEmptyNibbles(); // UNINIT
            assertEquals(0, ChunkLightHelper.getSkyLight(skyR, null, null, 0, 64, 0));
        }

        @Test
        void nullNibbleExtrudesBottomLayerOfSectionAbove() {
            SWMRNibbleArray[] skyR = makeNullNibbles();
            skyR[TEST_NIBBLE_IDX].setNonNull();
            skyR[TEST_NIBBLE_IDX].set(3, 0, 7, 4);
            skyR[TEST_NIBBLE_IDX].updateVisible();
            // Y=47 is section 2, nibble idx 3; section 4 (idx 5) sits above.
            assertEquals(4, ChunkLightHelper.getSkyLight(skyR, null, null, 3, 47, 7));
        }

        @Test
        void scalarModeReturnsROnly() {
            SWMRNibbleArray[] skyR = makeEmptyNibbles();
            skyR[TEST_NIBBLE_IDX].set(0, 4, 0, 7);
            skyR[TEST_NIBBLE_IDX].updateVisible();

            assertEquals(7, ChunkLightHelper.getSkyLight(skyR, null, null, 0, 68, 0));
        }
    }
}
