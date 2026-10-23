package me.thanhtnt2.disconnectum.optimization.monitor;

import me.thanhtnt2.disconnectum.optimization.Disconnectum;
import me.thanhtnt2.disconnectum.optimization.api.BackgroundWorkThrottle;
import me.thanhtnt2.disconnectum.optimization.api.DisconnectumApi;
import me.thanhtnt2.disconnectum.optimization.api.ServerLoadSnapshot;
import me.thanhtnt2.disconnectum.optimization.api.event.OverloadStateChangedEvent;
import me.thanhtnt2.disconnectum.optimization.config.DisconnectumConfig;
import me.thanhtnt2.disconnectum.optimization.monitor.ServerLoadMonitor.Transition;
import me.thanhtnt2.disconnectum.optimization.throttle.WorkerThreadGovernor;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-bus glue: feeds tick timings to the monitor and turns its decisions into throttle calls.
 *
 * <p>The pre-tick listener runs first and the post-tick listener last, so the measured tick includes the work
 * of every other mod's tick listeners. Runs on the server thread only.
 */
public final class ServerLoadEvents {

    private static final int PERIODIC_INTERVAL_TICKS = 20;

    private final ServerLoadMonitor monitor = ServerLoadMonitor.INSTANCE;
    private final WorkerThreadGovernor governor = new WorkerThreadGovernor();
    private long tickCounter;
    private boolean startupGraceStarted;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTickStart(ServerTickEvent.Pre event) {
        DisconnectumConfig.Settings settings = DisconnectumConfig.get();
        if (!settings.enabled()) {
            return;
        }
        monitor.onTickStart(System.nanoTime(), settings.monitor());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTickEnd(ServerTickEvent.Post event) {
        DisconnectumConfig.Settings settings = DisconnectumConfig.get();
        if (!settings.enabled()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Transition transition = monitor.onTickEnd(System.nanoTime(), settings.monitor());
        if (transition != Transition.NONE) {
            dispatch(server, transition, settings);
        }

        tickCounter++;
        if (tickCounter % settings.threads().scanIntervalTicks() == 0) {
            governor.sweep(settings.threads());
        }
        if (tickCounter % PERIODIC_INTERVAL_TICKS == 0) {
            runPeriodicHooks(server);
        }
    }

    /** Runs after Chunky's own listener, so tasks it auto-resumed are visible and can be paused right away. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerStarting(ServerStartingEvent event) {
        beginStartupGrace(event.getServer());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        beginStartupGrace(event.getServer());
        governor.sweep(DisconnectumConfig.get().threads());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        monitor.reset();
        startupGraceStarted = false;
        tickCounter = 0L;
        for (BackgroundWorkThrottle throttle : DisconnectumApi.getThrottles()) {
            try {
                throttle.onServerStopped();
            } catch (RuntimeException | LinkageError exception) {
                Disconnectum.LOGGER.error("Throttle {} failed while the server stopped", throttle, exception);
            }
        }
    }

    private void beginStartupGrace(MinecraftServer server) {
        DisconnectumConfig.Settings settings = DisconnectumConfig.get();
        int graceSeconds = settings.monitor().startupGraceSeconds();
        if (!settings.enabled() || graceSeconds <= 0 || startupGraceStarted) {
            return;
        }
        startupGraceStarted = true;
        Transition transition = monitor.beginStartupGrace(System.nanoTime(), graceSeconds * 1000L);
        if (transition != Transition.NONE) {
            dispatch(server, transition, settings);
        }
    }

    private void dispatch(MinecraftServer server, Transition transition, DisconnectumConfig.Settings settings) {
        boolean overloaded = transition == Transition.ENTERED_OVERLOAD;
        ServerLoadSnapshot snapshot = monitor.snapshot();
        if (settings.logTransitions()) {
            Disconnectum.LOGGER.info("Server {} (tick {} ms, lag {} ms)",
                    overloaded ? "overloaded, holding back background work" : "recovered, releasing background work",
                    Math.round(snapshot.smoothedTickMillis()), Math.round(snapshot.smoothedLagMillis()));
        }
        for (BackgroundWorkThrottle throttle : DisconnectumApi.getThrottles()) {
            try {
                if (overloaded) {
                    throttle.onOverloadBegin(server, snapshot);
                } else {
                    throttle.onOverloadEnd(server, snapshot);
                }
            } catch (RuntimeException | LinkageError exception) {
                Disconnectum.LOGGER.error("Throttle {} failed", throttle, exception);
            }
        }
        NeoForge.EVENT_BUS.post(new OverloadStateChangedEvent(server, snapshot, overloaded));
        if (overloaded) {
            governor.sweep(settings.threads());
        }
    }

    private void runPeriodicHooks(MinecraftServer server) {
        ServerLoadSnapshot snapshot = monitor.snapshot();
        for (BackgroundWorkThrottle throttle : DisconnectumApi.getThrottles()) {
            try {
                throttle.onPeriodicTick(server, snapshot);
            } catch (RuntimeException | LinkageError exception) {
                Disconnectum.LOGGER.error("Throttle {} failed", throttle, exception);
            }
        }
    }
}
