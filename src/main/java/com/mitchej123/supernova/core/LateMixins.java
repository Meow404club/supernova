package com.mitchej123.supernova.core;

import com.gtnewhorizon.gtnhmixins.builders.IBaseTransformer;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

import javax.annotation.Nonnull;

public enum LateMixins implements IMixins {
    ANGELICA_CELERITAS_TRACE(new MixinBuilder()
            .setPhase(IBaseTransformer.Phase.LATE)
            .addRequiredMod(TargetedMods.ANGELICA)
            .addClientMixins("rendering.MixinRenderSectionManager")),

    // Tile-light bridges must stay LATE: modId-only detection is only supported after mod jars are
    // attached to the launch classloader (see the note in Mixins).
    FORGE_MULTIPART(new MixinBuilder("Forge Multipart tile-light bridge")
            .setPhase(IBaseTransformer.Phase.LATE)
            .addRequiredMod(TargetedMods.FMP)
            .addCommonMixins(
                    "fmp.MixinTileMultipart",
                    "fmp.MixinBlockMultipart")),

    PROJECTRED_ILLUMINATION(new MixinBuilder("ProjectRed Illumination tile-light bridge")
            .setPhase(IBaseTransformer.Phase.LATE)
            .addRequiredMod(TargetedMods.PROJRED_ILLUMINATION)
            .addCommonMixins(
                    "projectred.MixinProjectRedLamp",
                    "projectred.MixinTileLamp",
                    "projectred.MixinBaseLightPart",
                    "projectred.MixinLightButtonPart",
                    "projectred.MixinFLightButtonPart")),


    IC2_CROPS(new MixinBuilder("IC2 crop tile-light bridge")
            .setPhase(IBaseTransformer.Phase.LATE)
            .addRequiredMod(TargetedMods.IC2)
            .addCommonMixins(
                    "ic2.MixinBlockCrop",
                    "ic2.MixinTileEntityCrop")),
    ;

    private final MixinBuilder builder;

    LateMixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
