package com.mitchej123.supernova.core;

import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;

public enum TargetedMods implements ITargetMod {

    ANGELICA(null, "angelica");

    private final TargetModBuilder builder;

    TargetedMods(String coreModClass, String modId) {
        this.builder = new TargetModBuilder().setCoreModClass(coreModClass).setModId(modId);
    }

    @Override
    public TargetModBuilder getBuilder() {
        return builder;
    }
}
