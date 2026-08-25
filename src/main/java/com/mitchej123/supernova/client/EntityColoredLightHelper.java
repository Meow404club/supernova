package com.mitchej123.supernova.client;

import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.FloatBuffer;

/**
 * Applies colored light tinting to entities via a GL texture combiner on unit 2. Unit 0 (entity texture) and unit 1 (lightmap) are left untouched. Unit 2
 * multiplies the combined result ({@code GL_PREVIOUS}) by a constant RGB tint derived from Supernova's per-channel block light at the entity's position.
 */
public final class EntityColoredLightHelper {

    private static final float[] NO_TINT = { 1.0f, 1.0f, 1.0f };
    private static final float[] RESULT = new float[3];
    private static final FloatBuffer TINT_BUFFER = BufferUtils.createFloatBuffer(4);

    private static boolean tintActive = false;

    private EntityColoredLightHelper() {}

    /**
     * Compute the RGB tint for an entity at the given world position. Returns {1,1,1} if no colored block light is present or the chunk is unavailable.
     */
    public static float[] getEntityTint(World world, double posX, double posY, double posZ, float eyeHeight) {
        if (world == null) return NO_TINT;

        final int x = (int) Math.floor(posX);
        final int y = (int) Math.floor(posY + eyeHeight);
        final int z = (int) Math.floor(posZ);

        final int packed = ColoredLightHelper.readLight(world, x, y, z);
        if (packed < 0) return NO_TINT;

        final int br = packed & 0xF;
        final int bg = (packed >> 4) & 0xF;
        final int bb = (packed >> 8) & 0xF;
        final int sr = (packed >> 12) & 0xF;
        final int sg = (packed >> 16) & 0xF;
        final int sb = (packed >> 20) & 0xF;

        final int maxBlock = Math.max(br, Math.max(bg, bb));
        if (maxBlock == 0 && sr == 0 && sg == 0 && sb == 0) return NO_TINT;

        // Raw sky -- computeTint applies the time-of-day subtraction itself
        ColoredLightHelper.computeTint(br, bg, bb, sr, sg, sb, RESULT);
        if (RESULT[0] >= 1.0f && RESULT[1] >= 1.0f && RESULT[2] >= 1.0f) return NO_TINT;

        // Square the tint to increase color saturation
        RESULT[0] *= RESULT[0];
        RESULT[1] *= RESULT[1];
        RESULT[2] *= RESULT[2];

        return RESULT;
    }

    /**
     * Enable texture unit 2 as a post-processing step that multiplies the result of units 0+1 (entity texture × lightmap) by a constant RGB tint. Must be
     * paired with {@link #removeEntityTint()}.
     */
    public static void applyEntityTint(float[] tint) {
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL13.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL13.GL_PREVIOUS);

        TINT_BUFFER.clear();
        TINT_BUFFER.put(tint[0]).put(tint[1]).put(tint[2]).put(1.0f);
        TINT_BUFFER.flip();
        GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, TINT_BUFFER);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        tintActive = true;
    }

    /** Disable texture unit 2, restoring normal rendering. */
    public static void removeEntityTint() {
        if (!tintActive) return;
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        tintActive = false;
    }
}
