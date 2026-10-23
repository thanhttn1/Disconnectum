package me.thanhtnt2.disconnectum.optimization.api;

import net.minecraft.server.MinecraftServer;

/**
 * Hook for anything that produces heavy background work (pre-generators, map renderers, ...).
 *
 * <p>Disconnectum calls every registered throttle <b>on the server thread</b>, so implementations may touch
 * the server directly but must return quickly. Exceptions are caught and logged, they never stop the server.
 *
 * @see DisconnectumApi#registerThrottle
 */
public interface BackgroundWorkThrottle {

    /** The server became overloaded: pause or slow down your background work. */
    void onOverloadBegin(MinecraftServer server, ServerLoadSnapshot snapshot);

    /** The server recovered: resume whatever {@link #onOverloadBegin} paused. */
    void onOverloadEnd(MinecraftServer server, ServerLoadSnapshot snapshot);

    /** Called about once per second while the server is ticking; useful for retrying a resume. */
    default void onPeriodicTick(MinecraftServer server, ServerLoadSnapshot snapshot) {
    }

    /** The server stopped; drop every reference to server-owned objects here. */
    default void onServerStopped() {
    }
}
