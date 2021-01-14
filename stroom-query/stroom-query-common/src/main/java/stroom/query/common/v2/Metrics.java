package stroom.query.common.v2;

import stroom.util.shared.ModelStringUtil;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class Metrics {
    private static final Map<String, Metric> map = new ConcurrentHashMap<>();

    public static <R> R measure(final String name, final Supplier<R> runnable) {
        long start = System.nanoTime();
        R result = runnable.get();
        long elapsed = System.nanoTime() - start;
        map.computeIfAbsent(name, k -> new Metric()).increment(elapsed);
        return result;
    }

    public static void measure(final String name, final Runnable runnable) {
        long start = System.nanoTime();
        runnable.run();
        long elapsed = System.nanoTime() - start;
        map.computeIfAbsent(name, k -> new Metric()).increment(elapsed);
    }

    public static void report() {
        map
                .entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .forEach(e ->
                        System.out.println(e.getKey() + " in: " + e.getValue().toString()));
        map.clear();
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
