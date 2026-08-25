package com.mitchej123.supernova.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ColoredLightHelperTest {

    private static final float EPSILON = 1e-4f;

    private static float[] tint(float br, float bg, float bb, float sr, float sg, float sb) {
        float[] out = new float[3];
        ColoredLightHelper.computeTint(br, bg, bb, sr, sg, sb, out);
        return out;
    }

    @Test
    void testBothBelowThreshold() {
        float[] result = tint(0.4f, 0.2f, 0.1f, 0.3f, 0.1f, 0.0f);
        assertArrayEquals(new float[] { 1f, 1f, 1f }, result);
    }

    @Test
    void testBothZero() {
        float[] result = tint(0, 0, 0, 0, 0, 0);
        assertArrayEquals(new float[] { 1f, 1f, 1f }, result);
    }

    @Test
    void testPureBlockLightRed() {
        // Only block light, red dominant -- should tint red
        float[] result = tint(15, 2, 0, 0, 0, 0);
        // Normalized block: (1.0, 2/15, 0), sky: (1,1,1) but skyMax=0 so sw≈0
        // Result should be close to (1.0, 2/15, 0)
        assertEquals(1.0f, result[0], EPSILON);
        assertEquals(2f / 15f, result[1], EPSILON);
        assertEquals(0f, result[2], EPSILON);
    }

    @Test
    void testPureSkyLightWhite() {
        // Only sky light, uniform -- should produce white tint
        float[] result = tint(0, 0, 0, 10, 10, 10);
        assertEquals(1.0f, result[0], EPSILON);
        assertEquals(1.0f, result[1], EPSILON);
        assertEquals(1.0f, result[2], EPSILON);
    }

    @Test
    void testPureSkyLightColored() {
        // Only sky light, blue-tinted
        float[] result = tint(0, 0, 0, 5, 5, 10);
        // Normalized sky: (0.5, 0.5, 1.0), bw≈0
        assertEquals(0.5f, result[0], EPSILON);
        assertEquals(0.5f, result[1], EPSILON);
        assertEquals(1.0f, result[2], EPSILON);
    }

    @Test
    void testEqualSourcesUniform() {
        // Both sources equal brightness, both white -> white tint
        float[] result = tint(10, 10, 10, 10, 10, 10);
        assertEquals(1.0f, result[0], EPSILON);
        assertEquals(1.0f, result[1], EPSILON);
        assertEquals(1.0f, result[2], EPSILON);
    }

    @Test
    void testDominantBlockLight() {
        // Block=15 red dominates; the dim ambient sky (2) fills the darker channels through the
        // per-channel max -- a light source may only brighten, never darken a channel (#19).
        float[] result = tint(15, 0, 0, 2, 2, 2);
        assertEquals(1.0f, result[0], 0.01f);
        assertEquals(2f / 15f, result[1], EPSILON);
        assertEquals(2f / 15f, result[2], EPSILON);
    }

    @Test
    void testDominantSkyLight() {
        // Block=2 red, sky=15 white -> sky dominates
        float[] result = tint(2, 0, 0, 15, 15, 15);
        // bm2=4, sm2=225 -> sw≈0.983
        // Result ≈ sky-dominated white
        assertTrue(result[0] > 0.95f, "red should be near 1: " + result[0]);
        assertTrue(result[1] > 0.95f, "green should be near 1: " + result[1]);
        assertTrue(result[2] > 0.95f, "blue should be near 1: " + result[2]);
    }

    @Test
    void testOutputReuse() {
        // Verify that different calls produce correct independent results
        float[] out = new float[3];
        ColoredLightHelper.computeTint(15, 0, 0, 0, 0, 0, out);
        assertEquals(1.0f, out[0], EPSILON);
        assertEquals(0f, out[1], EPSILON);

        ColoredLightHelper.computeTint(0, 0, 0, 10, 10, 10, out);
        assertEquals(1.0f, out[0], EPSILON);
        assertEquals(1.0f, out[1], EPSILON);
        assertEquals(1.0f, out[2], EPSILON);
    }

    @Test
    void testSymmetricColorsEqualWeight() {
        // Block=red at 10, sky=blue at 10 -- the per-channel max keeps both channels at full
        // strength instead of weight-blending them, so the result is pure magenta.
        float[] result = tint(10, 0, 0, 0, 0, 10);
        assertEquals(1.0f, result[0], 0.01f);
        assertEquals(0f, result[1], EPSILON);
        assertEquals(1.0f, result[2], 0.01f);
    }

    @Test
    void testBlockThresholdBoundary() {
        // blockMax exactly 0.5 -> below threshold, treated as white
        float[] result = tint(0.5f, 0, 0, 10, 10, 10);
        // blockMax=0.5, skyMax=10
        // blockMax > 0.5 is false -> invBlock=0 -> bt=(1,1,1)
        // bm2=0.25, sm2=100 -> bw≈0.0025, sw≈0.997
        // result ≈ (1*0.0025+1*0.997, 1*0.0025+1*0.997, 1*0.0025+1*0.997) ≈ (1,1,1)
        assertEquals(1.0f, result[0], 0.01f);
        assertEquals(1.0f, result[1], 0.01f);
        assertEquals(1.0f, result[2], 0.01f);
    }

    @Test
    void testBlockJustAboveThreshold() {
        // blockMax=0.6 -> above threshold, normalize by blockMax
        // sky=0 -> invSky=0 -> st=(1,1,1) but sw≈0 so sky contribution negligible
        float[] result = tint(0.6f, 0.3f, 0, 0, 0, 0);
        // bt = (1.0, 0.5, 0), bw≈0.997; st = (1,1,1), sw≈0.003
        assertEquals(1.0f, result[0], 0.01f);
        assertEquals(0.5f, result[1], 0.01f);
        assertTrue(result[2] < 0.01f, "blue should be near zero: " + result[2]);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
