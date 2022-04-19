package stroom.util.logging;

import stroom.util.shared.ModelStringUtil;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public class Metrics {

    private static final Map<String, Metric> map = new ConcurrentHashMap<>();
    private static boolean enabled = true;
    private static final AtomicBoolean periodicReport = new AtomicBoolean();

    public static <R> R measure(final String name, final Supplier<R> runnable) {
        if (enabled) {
            return map.computeIfAbsent(name, k -> new Metric()).call(runnable);
        } else {
            return runnable.get();
        }
    }

    public static void measure(final String name, final Runnable runnable) {
        if (enabled) {
            map.computeIfAbsent(name, k -> new Metric()).call(() -> {
                runnable.run();
                return null;
            });
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
        final StringBuilder sb = new StringBuilder();
        map
                .entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .forEach(e -> {
                    sb.append(e.getKey());
                    sb.append(": ");
                    sb.append(e.getValue());
                    sb.append("\n");
                });
        System.out.println(sb);
    }

    public static void reset() {
        map.clear();
    }

    public static void setEnabled(final boolean enabled) {
        Metrics.enabled = enabled;
    }

    private static class Metric {


        private final AtomicLong lastElapsed = new AtomicLong();
        private final AtomicLong lastCalls = new AtomicLong();
        private final ThreadLocal<Call> localCall = new ThreadLocal<>();
        private final Map<Thread, Call> currentCalls = new ConcurrentHashMap<>();

        public <R> R call(final Supplier<R> supplier) {
            Call call = localCall.get();
            if (call == null) {
                call = currentCalls.computeIfAbsent(Thread.currentThread(), k -> new Call());
                localCall.set(call);
            }
            return call.call(supplier);
        }

//        public void increment(final long nanos) {
//            elapsedNanos.add(nanos);
//            calls.increment();
//        }

        @Override
        public String toString() {
            final LongAdder totalElapsedNanos = new LongAdder();
            final LongAdder totalCalls = new LongAdder();
            currentCalls.values().forEach(call -> {
                totalElapsedNanos.add(call.getElapsedNanos());
                totalCalls.add(call.getCalls());
            });

            final long lastCalls = this.lastCalls.get();
            final long lastElapsed = this.lastElapsed.get();
            final long calls = totalCalls.longValue();
            final long elapsed = totalElapsedNanos.longValue();
            final long deltaCalls = calls - lastCalls;
            final long deltaElapsed = elapsed - lastElapsed;
            this.lastCalls.set(calls);
            this.lastElapsed.set(elapsed);
            long deltaCallsPerSecond = 0;
            if (deltaElapsed > 0) {
                deltaCallsPerSecond = (long) (deltaCalls / (deltaElapsed / 1000000000D));
            }
            long callsPerSecond = 0;
            if (elapsed > 0) {
                callsPerSecond = (long) (calls / (elapsed / 1000000000D));
            }
            final String elapsedString = ModelStringUtil.formatDurationString(elapsed / 1000000);
            final String deltaElapsedString = ModelStringUtil.formatDurationString(deltaElapsed / 1000000);
            return "Delta " + deltaCalls + " in " + deltaElapsedString + " " + deltaCallsPerSecond + "cps " +
                    "Total " + calls + " in " + elapsedString + " " + callsPerSecond + "cps";
        }
    }

    private static class Call {

        private volatile long calls = 0;
        private volatile long elapsed = 0;
        private long startTime = -1;

        public <R> R call(final Supplier<R> supplier) {
            synchronized (this) {
                calls++;
                startTime = System.nanoTime();
            }
            R r = supplier.get();
            final long delta = System.nanoTime() - startTime;
            synchronized (this) {
                elapsed += delta;
                startTime = -1;
            }
            return r;
        }

        public synchronized long getCalls() {
            return calls;
        }

        public synchronized long getElapsedNanos() {
            if (startTime != -1) {
                return elapsed + (System.nanoTime() - startTime);
            }
            return elapsed;
        }
    }
}
