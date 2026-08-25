package com.mitchej123.supernova;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.mitchej123.supernova.client.ColoredLightHelper;
import com.mitchej123.supernova.client.TintBlendMode;
import com.mitchej123.supernova.compat.angelica.AngelicaCompat;
import com.mitchej123.supernova.config.SupernovaClientConfig;
import com.mitchej123.supernova.config.SupernovaConfig;
import com.mitchej123.supernova.world.SupernovaWorld;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.launchwrapper.Launch;
import org.lwjgl.input.Keyboard;

public class ClientProxy extends CommonProxy {

    private static boolean isDevEnvironment() {
        return Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"));
    }

    private static KeyBinding tintModeKeyBinding;
    private static boolean angelicaLoaded;
    private int lastSkylightSubtracted = -1;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        try {
            ConfigurationManager.registerConfig(SupernovaClientConfig.class);
        } catch (ConfigException e) {
            throw new RuntimeException("Failed to register Supernova client config", e);
        }
        TintBlendMode.current = SupernovaClientConfig.tintBlendMode;
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        tintModeKeyBinding = new KeyBinding("Cycle Tint Blend Mode", isDevEnvironment() ? Keyboard.KEY_BACKSLASH : Keyboard.KEY_NONE, "Supernova");
        ClientRegistry.registerKeyBinding(tintModeKeyBinding);
        FMLCommonHandler.instance().bus().register(this);

        angelicaLoaded = Loader.isModLoaded("angelica") && !SupernovaConfig.isScalarMode();
        if (angelicaLoaded) {
            AngelicaCompat.register();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        while (tintModeKeyBinding.isPressed()) {
            final TintBlendMode[] modes = TintBlendMode.values();
            final int nextIndex = (TintBlendMode.current.ordinal() + 1) % modes.length;
            TintBlendMode.current = modes[nextIndex];
            SupernovaClientConfig.tintBlendMode = TintBlendMode.current;
            ConfigurationManager.save(SupernovaClientConfig.class);

            // Sync to Angelica's TintRegistry if present
            if (angelicaLoaded) {
                AngelicaCompat.syncTintMode();
            }

            if (Minecraft.getMinecraft().thePlayer != null) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("§e[Supernova]§r Tint blend mode: §b"
                        + TintBlendMode.current.name()));
            }
            if (Minecraft.getMinecraft().renderGlobal != null) {
                Minecraft.getMinecraft().renderGlobal.loadRenderers();
            }
        }

        // Angelica bakes skylightSubtracted into the vertex tint at mesh time (cachedSkylightSubtracted)
        // and never refreshes it, so colored light washes out to white when day turns to night. Mark all
        // loaded chunks dirty whenever the factor changes so meshes rebuild with the current value. Iris
        // solves this by passing held light color and day factor as per-frame uniforms; until Angelica
        // offers an equivalent draw-time hook, periodic rebuilds are the only way to keep tints in sync.
        if (angelicaLoaded) {
            final net.minecraft.world.World world = Minecraft.getMinecraft().theWorld;
            if (world != null) {
                // Track OUR computed factor, not world.skylightSubtracted -- the client-side field is
                // not reliably ticked and never changes, which silently disabled this trigger before.
                final int sub = (int) ColoredLightHelper.currentSkylightSubtracted();
                if (sub != this.lastSkylightSubtracted) {
                    if (this.lastSkylightSubtracted != -1) {
                        ((SupernovaWorld) world).supernova$getLightManager().markAllChunksForRenderUpdate();
                    }
                    this.lastSkylightSubtracted = sub;
                }
            }
        }
    }
}
