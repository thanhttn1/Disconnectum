package me.thanhtnt2.disconnectum.optimization.throttle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.thanhtnt2.disconnectum.optimization.Disconnectum;
import me.thanhtnt2.disconnectum.optimization.api.BackgroundWorkThrottle;
import me.thanhtnt2.disconnectum.optimization.api.ServerLoadSnapshot;
import me.thanhtnt2.disconnectum.optimization.config.DisconnectumConfig;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/**
 * Pauses Chunky while the server is overloaded and continues it once the server has recovered.
 *
 * <p>Only tasks that this class paused are ever continued, so a task the player paused or cancelled by hand
 * is never revived. If the player pauses a task manually while we are holding it, it will still be continued
 * after recovery; that is the one case this class cannot tell apart.
 *
 * <p>All methods run on the server thread. The map is concurrent only so that the status command and the
 * API can read it safely.
 */
public final class ChunkyThrottle implements BackgroundWorkThrottle {

    private static final String CHUNKY_MOD_ID = "chunky";
    /** After this many failed resume attempts (one per second) the world is forgotten. */
    private static final int MAX_RESUME_ATTEMPTS = 15;

    /** World name to number of failed resume attempts so far. */
    private final Map<String, Integer> heldWorlds = new ConcurrentHashMap<>();
    private ChunkyBridge bridge;
    private boolean bridgeFailed;

    @Override
    public void onOverloadBegin(MinecraftServer server, ServerLoadSnapshot snapshot) {
        pauseRunningTasks();
    }

    @Override
    public void onOverloadEnd(MinecraftServer server, ServerLoadSnapshot snapshot) {
        resumeHeldTasks();
    }

    @Override
    public void onPeriodicTick(MinecraftServer server, ServerLoadSnapshot snapshot) {
        if (snapshot.overloaded()) {
            // Also catches tasks the player starts while we are already holding the server back.
            pauseRunningTasks();
        } else if (!heldWorlds.isEmpty()) {
            resumeHeldTasks();
        }
    }

    @Override
    public void onServerStopped() {
        heldWorlds.clear();
    }

    /** Worlds whose Chunky task is currently held back by Disconnectum. */
    public List<String> heldWorlds() {
        return List.copyOf(heldWorlds.keySet());
    }

    private void pauseRunningTasks() {
        if (!DisconnectumConfig.get().chunky().pauseWhenOverloaded()) {
            return;
        }
        ChunkyBridge chunky = bridge();
        if (chunky == null) {
            return;
        }
        for (String world : chunky.runningWorlds()) {
            if (chunky.pause(world) && heldWorlds.putIfAbsent(world, 0) == null) {
                Disconnectum.LOGGER.info("Paused Chunky task for {} while the server catches up", world);
            }
        }
    }

    private void resumeHeldTasks() {
        ChunkyBridge chunky = bridge();
        if (chunky == null) {
            heldWorlds.clear();
            return;
        }
        for (String world : new ArrayList<>(heldWorlds.keySet())) {
            if (chunky.resume(world)) {
                heldWorlds.remove(world);
                Disconnectum.LOGGER.info("Continued Chunky task for {}", world);
                continue;
            }
            // The task is probably still winding down after the pause; try again on the next periodic tick.
            int attempts = heldWorlds.merge(world, 1, Integer::sum);
            if (attempts >= MAX_RESUME_ATTEMPTS) {
                heldWorlds.remove(world);
                Disconnectum.LOGGER.warn("Gave up continuing Chunky task for {}; use /chunky continue", world);
            }
        }
    }

    /** Resolves the bridge lazily; a failure is logged once and disables the integration. */
    private ChunkyBridge bridge() {
        if (bridge != null || bridgeFailed) {
            return bridge;
        }
        if (!ModList.get().isLoaded(CHUNKY_MOD_ID)) {
            bridgeFailed = true;
            return null;
        }
        try {
            bridge = ChunkyBridge.create(ChunkyThrottle.class.getClassLoader());
        } catch (ReflectiveOperationException | LinkageError exception) {
            bridgeFailed = true;
            Disconnectum.LOGGER.warn("Chunky is installed but its API could not be used; throttling disabled", exception);
        }
        return bridge;
    }
}
