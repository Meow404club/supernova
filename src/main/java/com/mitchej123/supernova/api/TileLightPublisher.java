package com.mitchej123.supernova.api;

import net.minecraft.world.World;

/**
 * Main-thread hook for blocks whose tile-driven emission must be published before Supernova queues
 * a relight. Kept free of optional-mod types so the core World mixin remains loadable without those
 * mods installed.
 */
public interface TileLightPublisher {

    void supernova$refreshTileLight(World world, int x, int y, int z);
}
