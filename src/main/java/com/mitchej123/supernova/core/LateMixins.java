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
