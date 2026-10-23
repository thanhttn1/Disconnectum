package me.thanhtnt2.disconnectum.optimization.config;

import java.util.Locale;
import java.util.Optional;

/**
 * Ready-made option sets applied by {@code /disconnectum preset <name>}.
 *
 * <p>A preset only changes the values listed in {@link Values}; every other option keeps what the player set.
 */
public enum ConfigPreset {

    /** For a capable PC: late to complain, Chunky runs freely unless the server really struggles. */
    RELAXED(new Values(true, 50.0D, 40.0D, 30.0D, 5.0D, 2500.0D, 0, ChunkyMode.ASK, 16, 4, 5000)),
    /** The default; tuned for a heavy modpack on an average laptop. */
    BALANCED(new Values(true, 45.0D, 35.0D, 15.0D, 3.0D, 1500.0D, 0, ChunkyMode.ASK, 8, 3, 3000)),
    /** For a weak PC: reacts early, pauses Chunky by itself and keeps its parallelism very low. */
    STRICT(new Values(true, 40.0D, 30.0D, 10.0D, 2.0D, 1000.0D, 30, ChunkyMode.AUTO, 4, 2, 2000)),
    /** Turns the whole mod off without removing it. */
    OFF(new Values(false, 45.0D, 35.0D, 15.0D, 3.0D, 1500.0D, 0, ChunkyMode.OFF, 0, 5, 3000));

    private final Values values;

    ConfigPreset(Values values) {
        this.values = values;
    }

    public Values settings() {
        return values;
    }

    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ConfigPreset> byName(String name) {
        for (ConfigPreset preset : values()) {
            if (preset.commandName().equalsIgnoreCase(name)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    /** The options a preset controls. {@code chunkyMaxWorkingCount} needs a game restart to take effect. */
    public record Values(boolean enabled,
                         double enterTickMillis, double exitTickMillis,
                         double enterLagMillis, double exitLagMillis,
                         double spikeTickMillis, int startupGraceSeconds,
                         ChunkyMode chunkyMode, int chunkyMaxWorkingCount,
                         int workerPriority, int serverStallMillis) {
    }
}
