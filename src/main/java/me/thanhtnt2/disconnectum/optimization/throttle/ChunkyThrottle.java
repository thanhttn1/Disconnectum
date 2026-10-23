package me.thanhtnt2.disconnectum.optimization.throttle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.thanhtnt2.disconnectum.optimization.Disconnectum;
import me.thanhtnt2.disconnectum.optimization.api.BackgroundWorkThrottle;
import me.thanhtnt2.disconnectum.optimization.api.ServerLoadSnapshot;
import me.thanhtnt2.disconnectum.optimization.config.ChunkyMode;
import me.thanhtnt2.disconnectum.optimization.config.DisconnectumConfig;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/**
 * Keeps Chunky from pushing an overloaded server into a disconnect, without taking control away from the player.
 *
 * <ul>
 *   <li>{@link ChunkyMode#ASK} (default): never touches Chunky by itself. While the server stays overloaded and
 *       a task is running, a clickable warning offers to continue or to stop.</li>
 *   <li>{@link ChunkyMode#AUTO}: pauses running tasks while overloaded and continues them afterwards.</li>
 *   <li>{@link ChunkyMode#OFF}: does nothing.</li>
 * </ul>
 *
 * <p>Only tasks this class paused are ever continued, so a task the player paused or cancelled by hand is never
 * revived. All methods run on the server thread; the map is concurrent only so the status command can read it.
 */
public final class ChunkyThrottle implements BackgroundWorkThrottle {

    private static final String CHUNKY_MOD_ID = "chunky";
    /** After this many failed resume attempts (one per second) the world is forgotten. */
    private static final int MAX_RESUME_ATTEMPTS = 15;

    /** World name to number of failed resume attempts so far. */
    private final Map<String, Integer> heldWorlds = new ConcurrentHashMap<>();
    private ChunkyBridge bridge;
    private boolean bridgeFailed;

    private boolean muted;
    private long mutedUntilNanos;
    private boolean warned;
    private long nextWarningNanos;

    @Override
    public void onOverloadBegin(MinecraftServer server, ServerLoadSnapshot snapshot) {
        if (DisconnectumConfig.get().chunky().mode() == ChunkyMode.AUTO && !isMuted()) {
            pauseRunningTasks();
        }
    }

    @Override
    public void onOverloadEnd(MinecraftServer server, ServerLoadSnapshot snapshot) {
        warned = false;
        resumeHeldTasks();
    }

    @Override
    public void onPeriodicTick(MinecraftServer server, ServerLoadSnapshot snapshot) {
        ChunkyMode mode = DisconnectumConfig.get().chunky().mode();
        if (mode == ChunkyMode.OFF) {
            return;
        }
        if (isMuted() && !heldWorlds.isEmpty()) {
            // The player asked to continue, but the task was still winding down last time: retry.
            resumeHeldTasks();
        }
        if (!snapshot.overloaded()) {
            if (!heldWorlds.isEmpty()) {
                resumeHeldTasks();
            }
            return;
        }
        if (mode == ChunkyMode.AUTO) {
            if (!isMuted()) {
                pauseRunningTasks();
            }
            return;
        }
        warnIfChunkyIsRunning(server, snapshot);
    }

    @Override
    public void onServerStopped() {
        heldWorlds.clear();
        muted = false;
        warned = false;
    }

    /**
     * The player chose "Continue Chunky": release anything we hold and stay quiet for the configured time.
     *
     * @return number of tasks continued
     */
    public int continueAndMute() {
        muted = true;
        mutedUntilNanos = System.nanoTime() + DisconnectumConfig.get().chunky().ignoreMinutes() * 60_000_000_000L;
        int before = heldWorlds.size();
        resumeHeldTasks();
        return before - heldWorlds.size();
    }

    /**
     * The player chose "Stop Chunky": pause every running task and keep it held until the server recovers
     * (or until {@link #continueAndMute()} is used).
     *
     * @return number of tasks paused
     */
    public int stopRunningTasks() {
        muted = false;
        return pauseRunningTasks();
    }

    /** Worlds whose Chunky task is currently held back by Disconnectum. */
    public List<String> heldWorlds() {
        return List.copyOf(heldWorlds.keySet());
    }

    private boolean isMuted() {
        if (muted && System.nanoTime() - mutedUntilNanos >= 0L) {
            muted = false;
        }
        return muted;
    }

    private void warnIfChunkyIsRunning(MinecraftServer server, ServerLoadSnapshot snapshot) {
        DisconnectumConfig.Chunky config = DisconnectumConfig.get().chunky();
        long now = System.nanoTime();
        // Cheap checks first: this runs once per second while overloaded.
        if (snapshot.overloadedForMillis() < config.warnAfterSeconds() * 1000L
                || isMuted()
                || (warned && now - nextWarningNanos < 0L)) {
            return;
        }
        ChunkyBridge chunky = bridge();
        if (chunky == null || chunky.runningWorlds().isEmpty()) {
            return;
        }
        warned = true;
        nextWarningNanos = now + config.warnRepeatSeconds() * 1_000_000_000L;
        ChunkyWarning.broadcast(server, snapshot, config.ignoreMinutes());
    }

    private int pauseRunningTasks() {
        ChunkyBridge chunky = bridge();
        if (chunky == null) {
            return 0;
        }
        int paused = 0;
        for (String world : chunky.runningWorlds()) {
            if (chunky.pause(world)) {
                paused++;
                if (heldWorlds.putIfAbsent(world, 0) == null) {
                    Disconnectum.LOGGER.info("Paused Chunky task for {} while the server catches up", world);
                }
            }
        }
        return paused;
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
