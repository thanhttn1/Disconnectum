package me.thanhtnt2.disconnectum.optimization.throttle;

import java.util.List;

import me.thanhtnt2.disconnectum.optimization.Disconnectum;
import me.thanhtnt2.disconnectum.optimization.config.DisconnectumConfig;

/**
 * Lowers the priority of background worker threads so the server thread and the render thread are
 * scheduled first when the CPU is saturated.
 *
 * <p>Minecraft's shared pool ("Worker-Main-N") is a {@link java.util.concurrent.ForkJoinPool} that creates and
 * retires threads at will, so a one-off change would not last. Instead the thread list is swept periodically.
 * The sweep uses {@link ThreadGroup#enumerate}, which does not pause the JVM, unlike
 * {@link Thread#getAllStackTraces()}.
 *
 * <p>Only ever lowers: a thread already at or below the target is left alone, and nothing is raised.
 * Called from the server thread only; the class keeps no state apart from a log flag.
 */
public final class WorkerThreadGovernor {

    private boolean loggedFirstChange;

    /** @return how many threads had their priority lowered by this sweep */
    public int sweep(DisconnectumConfig.Threads config) {
        if (!config.enabled() || config.namePrefixes().isEmpty()) {
            return 0;
        }
        int target = Math.clamp(config.workerPriority(), Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);
        int lowered = 0;
        for (Thread thread : snapshotThreads()) {
            if (thread == null || !thread.isAlive() || thread.getPriority() <= target) {
                continue;
            }
            if (matches(thread.getName(), config.namePrefixes())) {
                thread.setPriority(target);
                lowered++;
            }
        }
        if (lowered > 0 && !loggedFirstChange) {
            loggedFirstChange = true;
            Disconnectum.LOGGER.info("Lowered {} background thread(s) to priority {}", lowered, target);
        }
        return lowered;
    }

    private static boolean matches(String threadName, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (threadName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static Thread[] snapshotThreads() {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        // enumerate() silently truncates, so retry with a bigger buffer until everything fits.
        Thread[] buffer = new Thread[root.activeCount() + 32];
        int count = root.enumerate(buffer, true);
        while (count == buffer.length) {
            buffer = new Thread[buffer.length * 2];
            count = root.enumerate(buffer, true);
        }
        return buffer;
    }
}
