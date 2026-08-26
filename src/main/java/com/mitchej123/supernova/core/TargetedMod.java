package com.mitchej123.supernova.core;

import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;

import javax.annotation.Nonnull;

public enum TargetedMod implements ITargetMod {

    ANGELICA("com.gtnewhorizons.angelica.loading.AngelicaTweaker", "angelica"),
    // modId detection only: targetClass detection Class.forName's the class during the EARLY phase,
    // before FML attaches mod jars to the launch classloader -- the failed lookup poisons
    // LaunchClassLoader.invalidClasses and every later load of the class throws a cached CNFE.
    FMP(null, "ForgeMultipart"),
    PROJRED_ILLUMINATION(null, "ProjRed|Illumination");

    private final TargetModBuilder builder;


    TargetedMod(String targetClass) {
        this.builder = new TargetModBuilder().setTargetClass(targetClass);
    }
    TargetedMod(String coreModClass, String modId) {
        this.builder = new TargetModBuilder().setCoreModClass(coreModClass).setModId(modId);
    }

    @Nonnull
    @Override
    public TargetModBuilder getBuilder() {
        return builder;
    }
}
