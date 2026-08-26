package com.mitchej123.supernova.core;

import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;

public enum TargetedMods implements ITargetMod {

    ANGELICA(null, "angelica"),
    // modId detection only -- see the comment on TargetedMod for why targetClass detection is
    // unsafe before mod jars are attached (and these are LATE-phase anyway, where modId works).
    FMP(null, "ForgeMultipart"),
    PROJRED_ILLUMINATION(null, "ProjRed|Illumination");

    private final TargetModBuilder builder;

    TargetedMods(String coreModClass, String modId) {
        this.builder = new TargetModBuilder().setCoreModClass(coreModClass).setModId(modId);
    }

    @Override
    public TargetModBuilder getBuilder() {
        return builder;
    }
}
