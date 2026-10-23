package me.thanhtnt2.disconnectum.optimization.network;

import me.thanhtnt2.disconnectum.optimization.config.DisconnectumConfig;
import me.thanhtnt2.disconnectum.optimization.monitor.ServerLoadMonitor;

/**
 * Decides whether a vanilla keep-alive timeout is a real network failure or just lag.
 *
 * <p>Vanilla sends a keep-alive every 15 s and disconnects a player ("Timed out") when the reply has not been
 * processed 15 s later. The reply is handled as a task on the <em>server thread</em>, and the server only runs
 * queued tasks between ticks. If a single tick lasts long enough, the check at the end of that tick fires even
 * though the reply has been sitting in the queue the whole time. This class recognises that situation.
 *
 * <p>Called from the server thread (via the keep-alive mixin) only.
 */
public final class KeepAliveGuard {

    /** Vanilla's {@code keepConnection} timeout; the mixin only asks us once this much time has passed. */
    public static final long VANILLA_TIMEOUT_MILLIS = 15_000L;

    private KeepAliveGuard() {
    }

    /**
     * @param isSingleplayerOwner whether the connection belongs to the integrated server's host
     * @return {@code true} when the timeout must be postponed instead of disconnecting the player
     */
    public static boolean shouldForgiveTimeout(boolean isSingleplayerOwner) {
        DisconnectumConfig.Settings settings = DisconnectumConfig.get();
        if (!settings.enabled()) {
            return false;
        }
        DisconnectumConfig.KeepAlive config = settings.keepAlive();
        if (isSingleplayerOwner && config.forgiveSingleplayerOwner()) {
            return true;
        }
        return config.forgiveServerStalls()
                && ServerLoadMonitor.INSTANCE.currentTickElapsedMillis() >= config.serverStallMillis();
    }
}
