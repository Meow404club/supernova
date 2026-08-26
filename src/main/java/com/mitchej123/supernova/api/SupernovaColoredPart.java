package com.mitchej123.supernova.api;

/**
 * Duck interface mixed into third-party multipart light parts (e.g. ProjectRed fixtures and light
 * buttons) that know their own RGB emission, so {@code TileMultipart} aggregation can publish a
 * colored value into {@link TileLightStore} instead of a grayscale one.
 */
public interface SupernovaColoredPart {

    /** @return packed RGB emission (see {@link PackedColorLight}), 0 when not emitting. */
    int supernova$getPackedRGB();
}
