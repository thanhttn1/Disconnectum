package me.thanhtnt2.disconnectum.optimization;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import me.thanhtnt2.disconnectum.optimization.api.DisconnectumApi;
import me.thanhtnt2.disconnectum.optimization.command.DisconnectumCommand;
import me.thanhtnt2.disconnectum.optimization.config.DisconnectumConfig;
import me.thanhtnt2.disconnectum.optimization.monitor.ServerLoadEvents;
import me.thanhtnt2.disconnectum.optimization.monitor.ServerLoadMonitor;
import me.thanhtnt2.disconnectum.optimization.throttle.ChunkyThrottle;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Disconnectum: keeps a heavy modpack from kicking you out of your own world.
 *
 * <p>The mod has no blocks or items, so there are no registries; it only reacts to server ticks, throttles
 * background work, and guards keep-alive packets. See {@code api} for the addon-facing part.
 */
@Mod(Disconnectum.MOD_ID)
public final class Disconnectum {

    public static final String MOD_ID = "disconnectum";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Name of the JVM property Chunky reads once, when its generation task class is first loaded. */
    private static final String CHUNKY_MAX_WORKING_PROPERTY = "chunky.maxWorkingCount";

    private final ChunkyThrottle chunkyThrottle = new ChunkyThrottle();

    public Disconnectum(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, DisconnectumConfig.SPEC);
        modEventBus.addListener(DisconnectumConfig::onConfigEvent);
        modEventBus.addListener(this::onCommonSetup);

        DisconnectumApi.bindLoadView(ServerLoadMonitor.INSTANCE);
        DisconnectumApi.registerThrottle(ResourceLocation.fromNamespaceAndPath(MOD_ID, "chunky"), chunkyThrottle);

        NeoForge.EVENT_BUS.register(new ServerLoadEvents());
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        applyChunkyConcurrencyCap();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        DisconnectumCommand.register(event.getDispatcher(), chunkyThrottle);
    }

    /**
     * Chunky keeps up to 50 chunk requests in flight by default, far too many for a laptop that also hosts the
     * game. Its limit is read from a system property once, so it has to be set before the first task is created.
     * A value set by the player (for example in the launcher's JVM arguments) always wins.
     */
    private static void applyChunkyConcurrencyCap() {
        DisconnectumConfig.Settings settings = DisconnectumConfig.get();
        int cap = settings.chunky().maxWorkingCount();
        if (!settings.enabled() || cap <= 0 || System.getProperty(CHUNKY_MAX_WORKING_PROPERTY) != null) {
            return;
        }
        System.setProperty(CHUNKY_MAX_WORKING_PROPERTY, Integer.toString(cap));
        LOGGER.info("Limited Chunky to {} simultaneous chunk requests", cap);
    }
}
