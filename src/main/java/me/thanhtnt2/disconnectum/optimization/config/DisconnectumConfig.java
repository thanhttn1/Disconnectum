package me.thanhtnt2.disconnectum.optimization.config;

import java.util.List;
import java.util.function.Supplier;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config ({@code config/disconnectum-common.toml}).
 *
 * <p>Hot paths never touch {@link ModConfigSpec} values directly. They read an immutable {@link Settings}
 * snapshot that is rebuilt whenever the file is loaded or edited, which keeps per-tick reads allocation-free
 * and safe on any thread, even before the config has been loaded.
 */
public final class DisconnectumConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Master switch. When false, Disconnectum does nothing at all.")
            .define("enabled", true);
    private static final ModConfigSpec.BooleanValue LOG_TRANSITIONS = BUILDER
            .comment("Log one line each time the server enters or leaves the overloaded state.")
            .define("logTransitions", true);

    // ---- load monitor ----
    private static final ModConfigSpec.DoubleValue ENTER_TICK_MILLIS;
    private static final ModConfigSpec.DoubleValue EXIT_TICK_MILLIS;
    private static final ModConfigSpec.DoubleValue ENTER_LAG_MILLIS;
    private static final ModConfigSpec.DoubleValue EXIT_LAG_MILLIS;
    private static final ModConfigSpec.DoubleValue SMOOTHING_FACTOR;
    private static final ModConfigSpec.DoubleValue SPIKE_TICK_MILLIS;
    private static final ModConfigSpec.IntValue RECOVERY_TICKS;
    private static final ModConfigSpec.IntValue MIN_THROTTLE_MILLIS;
    private static final ModConfigSpec.IntValue LAG_SAMPLE_CAP_MILLIS;
    private static final ModConfigSpec.IntValue STARTUP_GRACE_SECONDS;

    // ---- chunky ----
    private static final ModConfigSpec.BooleanValue CHUNKY_PAUSE_WHEN_OVERLOADED;
    private static final ModConfigSpec.IntValue CHUNKY_MAX_WORKING_COUNT;

    // ---- threads ----
    private static final ModConfigSpec.BooleanValue THREADS_ENABLED;
    private static final ModConfigSpec.IntValue THREADS_PRIORITY;
    private static final ModConfigSpec.IntValue THREADS_SCAN_INTERVAL_TICKS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> THREADS_NAME_PREFIXES;

    // ---- keep alive ----
    private static final ModConfigSpec.BooleanValue KEEP_ALIVE_FORGIVE_OWNER;
    private static final ModConfigSpec.BooleanValue KEEP_ALIVE_FORGIVE_STALLS;
    private static final ModConfigSpec.IntValue KEEP_ALIVE_STALL_MILLIS;
    private static final ModConfigSpec.BooleanValue KEEP_ALIVE_CLIENT_FAST_REPLY;

    static {
        BUILDER.comment("When the server counts as overloaded. Background work is held back while it is.")
                .push("monitor");
        ENTER_TICK_MILLIS = BUILDER
                .comment("Smoothed tick duration (ms) at or above which the server counts as overloaded. 50 ms is the vanilla limit.")
                .defineInRange("enterTickMillis", 45.0D, 5.0D, 5000.0D);
        EXIT_TICK_MILLIS = BUILDER
                .comment("Smoothed tick duration (ms) the server must drop below before it counts as recovered.")
                .defineInRange("exitTickMillis", 35.0D, 1.0D, 5000.0D);
        ENTER_LAG_MILLIS = BUILDER
                .comment("Smoothed loop lag (ms behind schedule) at or above which the server counts as overloaded.",
                        "Catches load that happens between ticks, such as chunk work queued on the server thread.")
                .defineInRange("enterLagMillis", 15.0D, 1.0D, 5000.0D);
        EXIT_LAG_MILLIS = BUILDER
                .comment("Smoothed loop lag (ms) the server must drop below before it counts as recovered.")
                .defineInRange("exitLagMillis", 3.0D, 0.0D, 5000.0D);
        SMOOTHING_FACTOR = BUILDER
                .comment("Weight of the newest tick in the moving average. Lower is calmer, higher reacts faster.")
                .defineInRange("smoothingFactor", 0.1D, 0.01D, 1.0D);
        SPIKE_TICK_MILLIS = BUILDER
                .comment("A single tick at least this long (ms) triggers overload immediately, without waiting for the average.")
                .defineInRange("spikeTickMillis", 1500.0D, 100.0D, 60000.0D);
        RECOVERY_TICKS = BUILDER
                .comment("Consecutive calm ticks required before the overload ends (20 ticks = 1 second).")
                .defineInRange("recoveryTicks", 40, 1, 2400);
        MIN_THROTTLE_MILLIS = BUILDER
                .comment("Minimum time (ms) background work stays paused once the server became overloaded.",
                        "Prevents rapid pause/resume flapping.")
                .defineInRange("minThrottleMillis", 3000, 0, 600000);
        LAG_SAMPLE_CAP_MILLIS = BUILDER
                .comment("Gaps between ticks longer than this (ms) are ignored by the lag average.",
                        "They are almost always the game being paused, not real load. Long ticks are still caught by spikeTickMillis.")
                .defineInRange("lagSampleCapMillis", 2000, 100, 60000);
        STARTUP_GRACE_SECONDS = BUILDER
                .comment("Treat the server as overloaded for this many seconds after it starts, so Chunky and other",
                        "background jobs wait until the world has finished loading. 0 disables the grace period.")
                .defineInRange("startupGraceSeconds", 30, 0, 600);
        BUILDER.pop();

        BUILDER.comment("Integration with the Chunky pre-generator (optional dependency).")
                .push("chunky");
        CHUNKY_PAUSE_WHEN_OVERLOADED = BUILDER
                .comment("Pause running Chunky tasks while the server is overloaded and continue them afterwards.",
                        "Progress is kept by Chunky, nothing is cancelled.")
                .define("pauseWhenOverloaded", true);
        CHUNKY_MAX_WORKING_COUNT = BUILDER
                .comment("Maximum number of chunks Chunky requests at the same time (Chunky's own default is 50).",
                        "Applied through the 'chunky.maxWorkingCount' system property only if you have not set it yourself.",
                        "0 leaves Chunky untouched. Takes effect after a full game restart.")
                .gameRestart()
                .defineInRange("maxWorkingCount", 8, 0, 512);
        BUILDER.pop();

        BUILDER.comment("Lower the priority of background worker threads so the server and render threads win the CPU.")
                .push("threads");
        THREADS_ENABLED = BUILDER
                .comment("Enable worker thread priority lowering.")
                .define("lowerWorkerPriority", true);
        THREADS_PRIORITY = BUILDER
                .comment("Java priority given to matching threads (1 lowest, 5 normal). 3 maps to 'below normal' on Windows.",
                        "On Linux and macOS the JVM ignores Java thread priorities unless started with special flags.")
                .defineInRange("workerPriority", 3, 1, 5);
        THREADS_SCAN_INTERVAL_TICKS = BUILDER
                .comment("How often (ticks) to look for new worker threads. The pool creates and retires threads over time.")
                .defineInRange("scanIntervalTicks", 100, 20, 2400);
        THREADS_NAME_PREFIXES = BUILDER
                .comment("Threads whose name starts with one of these prefixes are lowered. Minecraft's shared background pool",
                        "(chunk generation among other things) is called 'Worker-Main-N'; Chunky threads start with 'Chunky-'.")
                .defineListAllowEmpty("threadNamePrefixes", List.of("Worker-Main-", "Chunky-"), () -> "Worker-Main-",
                        value -> value instanceof String text && !text.isBlank());
        BUILDER.pop();

        BUILDER.comment("Protection against 'Timed out' disconnects caused by lag instead of a broken connection.")
                .push("keepAlive");
        KEEP_ALIVE_FORGIVE_OWNER = BUILDER
                .comment("Never disconnect the singleplayer owner (or LAN host) for a keep-alive timeout.",
                        "Inside one JVM there is no network, so a missed reply can only mean the game was starved of CPU or memory.")
                .define("forgiveSingleplayerOwner", true);
        KEEP_ALIVE_FORGIVE_STALLS = BUILDER
                .comment("Do not time other players out when the timeout check runs inside a server tick that has been",
                        "running for at least serverStallMillis. The server thread could not process their reply during that time.")
                .define("forgiveServerStalls", true);
        KEEP_ALIVE_STALL_MILLIS = BUILDER
                .comment("How long (ms) the current server tick must have been running for the stall protection to apply.")
                .defineInRange("serverStallMillis", 3000, 500, 14000);
        KEEP_ALIVE_CLIENT_FAST_REPLY = BUILDER
                .comment("Client side, remote servers only: answer keep-alive packets straight from the network thread so a",
                        "frozen render thread cannot make the server drop you. Singleplayer is not affected.")
                .define("clientFastReply", true);
        BUILDER.pop();
    }

    /** The compiled spec; register it with {@code ModContainer#registerConfig}. */
    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile Settings current = buildSettings();

    private DisconnectumConfig() {
    }

    /** Immutable snapshot of every option; never null and safe to read from any thread. */
    public static Settings get() {
        return current;
    }

    /** Rebuilds the snapshot after the config file was loaded or edited. */
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        current = buildSettings();
    }

    private static Settings buildSettings() {
        Monitor monitor = new Monitor(
                read(ENTER_TICK_MILLIS), read(EXIT_TICK_MILLIS),
                read(ENTER_LAG_MILLIS), read(EXIT_LAG_MILLIS),
                read(SMOOTHING_FACTOR), read(SPIKE_TICK_MILLIS),
                read(RECOVERY_TICKS), read(MIN_THROTTLE_MILLIS),
                read(LAG_SAMPLE_CAP_MILLIS), read(STARTUP_GRACE_SECONDS));
        Chunky chunky = new Chunky(read(CHUNKY_PAUSE_WHEN_OVERLOADED), read(CHUNKY_MAX_WORKING_COUNT));
        Threads threads = new Threads(
                read(THREADS_ENABLED), read(THREADS_PRIORITY), read(THREADS_SCAN_INTERVAL_TICKS),
                List.copyOf(read(THREADS_NAME_PREFIXES)));
        KeepAlive keepAlive = new KeepAlive(
                read(KEEP_ALIVE_FORGIVE_OWNER), read(KEEP_ALIVE_FORGIVE_STALLS),
                read(KEEP_ALIVE_STALL_MILLIS), read(KEEP_ALIVE_CLIENT_FAST_REPLY));
        return new Settings(read(ENABLED), read(LOG_TRANSITIONS), monitor, chunky, threads, keepAlive);
    }

    /** Falls back to the default while the file is not loaded yet, so reads never throw. */
    private static <T> T read(ModConfigSpec.ConfigValue<T> value) {
        Supplier<T> source = SPEC.isLoaded() ? value::get : value::getDefault;
        return source.get();
    }

    public record Settings(boolean enabled, boolean logTransitions,
                           Monitor monitor, Chunky chunky, Threads threads, KeepAlive keepAlive) {
    }

    public record Monitor(double enterTickMillis, double exitTickMillis,
                          double enterLagMillis, double exitLagMillis,
                          double smoothingFactor, double spikeTickMillis,
                          int recoveryTicks, int minThrottleMillis,
                          int lagSampleCapMillis, int startupGraceSeconds) {
    }

    public record Chunky(boolean pauseWhenOverloaded, int maxWorkingCount) {
    }

    public record Threads(boolean enabled, int workerPriority, int scanIntervalTicks, List<String> namePrefixes) {
    }

    public record KeepAlive(boolean forgiveSingleplayerOwner, boolean forgiveServerStalls,
                            int serverStallMillis, boolean clientFastReply) {
    }
}
