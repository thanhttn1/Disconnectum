package me.thanhtnt2.disconnectum.optimization.monitor;

import me.thanhtnt2.disconnectum.optimization.api.ServerLoadSnapshot;
import me.thanhtnt2.disconnectum.optimization.api.ServerLoadView;
import me.thanhtnt2.disconnectum.optimization.config.DisconnectumConfig;

/**
 * Measures server load and decides, with hysteresis, when the server counts as overloaded.
 *
 * <h2>Thread safety</h2>
 * All mutating methods must be called from the server thread only; the fields they touch are plain and
 * unsynchronised on purpose. Other threads (network, render, Chunky) only read through
 * {@link #snapshot()} and {@link #currentTickElapsedMillis()}, which are backed by volatile fields.
 *
 * <h2>Two signals</h2>
 * <ul>
 *   <li><b>Tick time</b>: duration of the tick itself, measured from the first to the last tick listener.</li>
 *   <li><b>Loop lag</b>: how far consecutive tick starts drift beyond 50 ms. Chunk work that vanilla runs on the
 *       server thread between ticks never shows up in tick time, but it delays the next tick and shows up here.</li>
 * </ul>
 */
public final class ServerLoadMonitor implements ServerLoadView {

    /** Shared instance; holds no world or server references, so it cannot leak a level. */
    public static final ServerLoadMonitor INSTANCE = new ServerLoadMonitor();

    /** What changed after feeding the monitor a tick. */
    public enum Transition { NONE, ENTERED_OVERLOAD, LEFT_OVERLOAD }

    private static final long TARGET_TICK_NANOS = 50_000_000L;
    private static final double NANOS_PER_MILLI = 1_000_000.0D;
    private static final double PEAK_DECAY_PER_TICK = 0.98D;

    private volatile boolean inTick;
    private volatile long tickStartNanos;
    private volatile ServerLoadSnapshot snapshot = ServerLoadSnapshot.IDLE;

    private boolean hasPreviousTickStart;
    private long previousTickStartNanos;
    private double smoothedTickMillis;
    private double smoothedLagMillis;
    private double peakTickMillis;

    private boolean overloaded;
    private long overloadedSinceNanos;
    private int calmTicks;
    private boolean holdActive;
    private long holdUntilNanos;

    private ServerLoadMonitor() {
    }

    @Override
    public ServerLoadSnapshot snapshot() {
        return snapshot;
    }

    /**
     * How long the tick that is running right now has been running; 0 outside a tick.
     * The keep-alive guard uses this to tell "the server was stuck" from "the client went silent".
     */
    public double currentTickElapsedMillis() {
        if (!inTick) {
            return 0.0D;
        }
        return (System.nanoTime() - tickStartNanos) / NANOS_PER_MILLI;
    }

    public void onTickStart(long nowNanos, DisconnectumConfig.Monitor config) {
        if (hasPreviousTickStart) {
            long loopNanos = nowNanos - previousTickStartNanos;
            // A huge gap means the game was paused or the whole process froze, which is not load we can relieve.
            if (loopNanos <= config.lagSampleCapMillis() * 1_000_000L) {
                double lagMillis = Math.max(0L, loopNanos - TARGET_TICK_NANOS) / NANOS_PER_MILLI;
                smoothedLagMillis = smooth(smoothedLagMillis, lagMillis, config.smoothingFactor());
            }
        }
        hasPreviousTickStart = true;
        previousTickStartNanos = nowNanos;
        tickStartNanos = nowNanos;
        inTick = true;
    }

    public Transition onTickEnd(long nowNanos, DisconnectumConfig.Monitor config) {
        if (!inTick) {
            return Transition.NONE;
        }
        double tickMillis = (nowNanos - tickStartNanos) / NANOS_PER_MILLI;
        inTick = false;

        smoothedTickMillis = smooth(smoothedTickMillis, tickMillis, config.smoothingFactor());
        peakTickMillis = Math.max(peakTickMillis * PEAK_DECAY_PER_TICK, tickMillis);

        Transition transition = evaluate(nowNanos, tickMillis >= config.spikeTickMillis(), config);
        publish(nowNanos);
        return transition;
    }

    /**
     * Forces the overloaded state for a fixed time, used right after the server starts so that auto-resumed
     * background jobs wait for the world to settle. The normal calm-down rules still apply afterwards.
     */
    public Transition beginStartupGrace(long nowNanos, long durationMillis) {
        holdActive = true;
        holdUntilNanos = nowNanos + durationMillis * 1_000_000L;
        if (overloaded) {
            return Transition.NONE;
        }
        enterOverload(nowNanos);
        publish(nowNanos);
        return Transition.ENTERED_OVERLOAD;
    }

    /** Forgets everything; called when the server stops so a new world starts from a clean slate. */
    public void reset() {
        inTick = false;
        hasPreviousTickStart = false;
        smoothedTickMillis = 0.0D;
        smoothedLagMillis = 0.0D;
        peakTickMillis = 0.0D;
        overloaded = false;
        calmTicks = 0;
        holdActive = false;
        snapshot = ServerLoadSnapshot.IDLE;
    }

    private Transition evaluate(long nowNanos, boolean spike, DisconnectumConfig.Monitor config) {
        if (!overloaded) {
            boolean pressure = spike
                    || smoothedTickMillis >= config.enterTickMillis()
                    || smoothedLagMillis >= config.enterLagMillis();
            if (!pressure) {
                return Transition.NONE;
            }
            enterOverload(nowNanos);
            return Transition.ENTERED_OVERLOAD;
        }

        // Exit thresholds may never sit above the enter thresholds, otherwise the state would flap.
        double exitTick = Math.min(config.exitTickMillis(), config.enterTickMillis());
        double exitLag = Math.min(config.exitLagMillis(), config.enterLagMillis());
        boolean calm = !spike && smoothedTickMillis < exitTick && smoothedLagMillis < exitLag;
        calmTicks = calm ? calmTicks + 1 : 0;

        boolean heldLongEnough = nowNanos - overloadedSinceNanos >= config.minThrottleMillis() * 1_000_000L;
        boolean holdOver = !holdActive || nowNanos - holdUntilNanos >= 0L;
        if (calmTicks < config.recoveryTicks() || !heldLongEnough || !holdOver) {
            return Transition.NONE;
        }
        overloaded = false;
        holdActive = false;
        return Transition.LEFT_OVERLOAD;
    }

    private void enterOverload(long nowNanos) {
        overloaded = true;
        overloadedSinceNanos = nowNanos;
        calmTicks = 0;
    }

    private void publish(long nowNanos) {
        long overloadedForMillis = overloaded ? (nowNanos - overloadedSinceNanos) / 1_000_000L : 0L;
        snapshot = new ServerLoadSnapshot(smoothedTickMillis, smoothedLagMillis, peakTickMillis,
                overloaded, overloadedForMillis);
    }

    private static double smooth(double previous, double sample, double factor) {
        return previous + (sample - previous) * factor;
    }
}
