package stroom.util.logging;

import stroom.util.shared.ModelStringUtil;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class Metrics {

    private static final Map<String, Metric> map = new ConcurrentHashMap<>();
    private static boolean enabled;
    private static AtomicBoolean periodicReport = new AtomicBoolean();

    public static <R> R measure(final String name, final Supplier<R> runnable) {
        if (enabled) {
            long start = System.nanoTime();
            R result = runnable.get();
            long elapsed = System.nanoTime() - start;
            map.computeIfAbsent(name, k -> new Metric()).increment(elapsed);
            return result;
        } else {
            return runnable.get();
        }
    }

    public static void measure(final String name, final Runnable runnable) {
        if (enabled) {
            long start = System.nanoTime();
            runnable.run();
            long elapsed = System.nanoTime() - start;
            map.computeIfAbsent(name, k -> new Metric()).increment(elapsed);
        } else {
            runnable.run();
        }
    }

    public static void startPeriodicReport(final long ms) {
        if (enabled && periodicReport.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    while (true) {
                        Thread.sleep(ms);
                        report();
                    }
                } catch (final InterruptedException e) {
                    // Ignore.
                }
            });
        }
    }

    public static void report() {
        map
                .entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .forEach(e ->
                        System.out.println(e.getKey() + " in: " + e.getValue().toString()));
    }

    public static void reset() {
        map.clear();
    }

    public static void setEnabled(final boolean enabled) {
        Metrics.enabled = enabled;
    }

    private static class Metric {

        private final AtomicLong elapsedNanos = new AtomicLong();
        private final AtomicLong calls = new AtomicLong();

        public void increment(final long nanos) {
            elapsedNanos.addAndGet(nanos);
            calls.incrementAndGet();
        }

        @Override
        public String toString() {
            return ModelStringUtil.formatDurationString(elapsedNanos.get() / 1000000) + " (" + calls.get() + ")";
        }
    }
}
