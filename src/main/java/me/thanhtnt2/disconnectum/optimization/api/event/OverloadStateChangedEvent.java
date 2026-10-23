package me.thanhtnt2.disconnectum.optimization.api.event;

import me.thanhtnt2.disconnectum.optimization.api.ServerLoadSnapshot;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/**
 * Posted on the NeoForge game bus, on the server thread, whenever the server enters or leaves the
 * overloaded state. It is informational and cannot be cancelled; use
 * {@link me.thanhtnt2.disconnectum.optimization.api.BackgroundWorkThrottle} to react with actual work.
 */
public class OverloadStateChangedEvent extends Event {

    private final MinecraftServer server;
    private final ServerLoadSnapshot snapshot;
    private final boolean overloaded;

    public OverloadStateChangedEvent(MinecraftServer server, ServerLoadSnapshot snapshot, boolean overloaded) {
        this.server = server;
        this.snapshot = snapshot;
        this.overloaded = overloaded;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public ServerLoadSnapshot getSnapshot() {
        return snapshot;
    }

    /** {@code true} when the server just became overloaded, {@code false} when it just recovered. */
    public boolean isOverloaded() {
        return overloaded;
    }
}
