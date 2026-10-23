package me.thanhtnt2.disconnectum.optimization.api;

/**
 * Immutable picture of how busy the server thread is right now.
 *
 * <p>Instances are safe to hand to any thread: they never change after creation.
 *
 * @param smoothedTickMillis  exponentially smoothed duration of the tick itself (what vanilla calls MSPT)
 * @param smoothedLagMillis   exponentially smoothed time the server loop ran behind its 50 ms schedule;
 *                            this also captures work done between ticks, which MSPT does not show
 * @param peakTickMillis      slowly decaying worst tick duration seen recently
 * @param overloaded          whether background work should currently be held back
 * @param overloadedForMillis how long the current overload state has lasted, 0 when not overloaded
 */
public record ServerLoadSnapshot(
        double smoothedTickMillis,
        double smoothedLagMillis,
        double peakTickMillis,
        boolean overloaded,
        long overloadedForMillis) {

    /** Reported before the first tick was observed and after the server has stopped. */
    public static final ServerLoadSnapshot IDLE = new ServerLoadSnapshot(0.0D, 0.0D, 0.0D, false, 0L);
}
