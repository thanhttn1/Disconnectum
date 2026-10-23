package me.thanhtnt2.disconnectum.optimization.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;

/**
 * Entry point for addon authors.
 *
 * <p>Typical use: implement {@link BackgroundWorkThrottle} for your own heavy background job and register it
 * once during mod setup. To only observe load changes, subscribe to
 * {@link me.thanhtnt2.disconnectum.optimization.api.event.OverloadStateChangedEvent} on the NeoForge game bus.
 */
public final class DisconnectumApi {

    private static final Map<ResourceLocation, BackgroundWorkThrottle> THROTTLES = new ConcurrentHashMap<>();
    private static volatile ServerLoadView loadView = () -> ServerLoadSnapshot.IDLE;

    private DisconnectumApi() {
    }

    /** Latest load snapshot; callable from any thread. */
    public static ServerLoadSnapshot getLoadSnapshot() {
        return loadView.snapshot();
    }

    public static boolean isServerOverloaded() {
        return loadView.isOverloaded();
    }

    /**
     * Registers a throttle under a unique id.
     *
     * @return {@code false} when the id is already taken, in which case nothing changes
     */
    public static boolean registerThrottle(ResourceLocation id, BackgroundWorkThrottle throttle) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(throttle, "throttle");
        return THROTTLES.putIfAbsent(id, throttle) == null;
    }

    public static boolean unregisterThrottle(ResourceLocation id) {
        return THROTTLES.remove(id) != null;
    }

    /** Copy of the registered throttles; safe to iterate while addons (un)register. */
    public static Collection<BackgroundWorkThrottle> getThrottles() {
        return List.copyOf(THROTTLES.values());
    }

    /** Internal: called once by the Disconnectum core. Addons must not call this. */
    public static void bindLoadView(ServerLoadView view) {
        loadView = Objects.requireNonNull(view, "view");
    }
}
