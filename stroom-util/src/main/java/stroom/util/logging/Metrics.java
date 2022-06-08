package stroom.util.logging;

import stroom.util.shared.ModelStringUtil;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Metrics {

    private static final Map<String, Metric> map = new ConcurrentHashMap<>();
    private static boolean enabled = false;

    public static <R> R measure(final String name, final Supplier<R> runnable) {
        if (enabled) {
            return map.computeIfAbsent(name, k -> new Metric(name)).call(runnable);
        } else {
            return runnable.get();
        }
    }

    public static void measure(final String name, final Runnable runnable) {
        if (enabled) {
            map.computeIfAbsent(name, k -> new Metric(name)).call(() -> {
                runnable.run();
                return null;
            });
        } else {
            runnable.run();
        }
    }

    public static void report() {
        System.out.println(map
                .values()
                .stream()
                .map(Metric::snapshot)
                .sorted(Comparator.comparing(Snapshot::getDeltaCallsPerSecond))
                .map(Snapshot::toString)
                .collect(Collectors.joining("\n")) + "\n\n");
    }

    public static void reset() {
        map.clear();
    }

    public static void setEnabled(final boolean enabled) {
        Metrics.enabled = enabled;
    }

    private static class Metric {

        private final String name;
        private final AtomicLong lastElapsed = new AtomicLong();
        private final AtomicLong lastCalls = new AtomicLong();
        private final ThreadLocal<Call> localCall = new ThreadLocal<>();
        private final Map<Thread, Call> currentCalls = new ConcurrentHashMap<>();

        public Metric(final String name) {
            this.name = name;
        }

        public <R> R call(final Supplier<R> supplier) {
            Call call = localCall.get();
            if (call == null) {
                call = currentCalls.computeIfAbsent(Thread.currentThread(), k -> new Call());
                localCall.set(call);
            }
            return call.call(supplier);
        }

        public Snapshot snapshot() {
            final LongAdder totalElapsedNanos = new LongAdder();
            final LongAdder activeCalls = new LongAdder();
            final LongAdder totalCalls = new LongAdder();

            currentCalls.values().forEach(call -> {
                final State state = call.getState();
                totalElapsedNanos.add(state.elapsed);
                totalCalls.add(state.calls);
                if (state.calling) {
                    activeCalls.increment();
                }
            });

            final long lastCalls = this.lastCalls.get();
            final long lastElapsed = this.lastElapsed.get();
            final long calls = totalCalls.longValue();
            final long elapsed = totalElapsedNanos.longValue();
            final long deltaCalls = calls - lastCalls + activeCalls.longValue();
            final long deltaElapsed = elapsed - lastElapsed;
            this.lastCalls.set(calls);
            this.lastElapsed.set(elapsed);

            return new Snapshot(
                    name,
                    deltaCalls,
                    deltaElapsed,
                    calls,
                    elapsed);
        }

    }

    private static class Snapshot {

        private final String name;
        private final long deltaCalls;
        private final long deltaElapsed;
        private final long calls;
        private final long elapsed;
        private final long deltaCallsPerSecond;
        private final long callsPerSecond;

        public Snapshot(
                final String name,
                final long deltaCalls,
                final long deltaElapsed,
                final long calls,
                final long elapsed) {
            this.name = name;
            this.deltaCalls = deltaCalls;
            this.deltaElapsed = deltaElapsed;
            this.calls = calls;
            this.elapsed = elapsed;

            if (deltaElapsed > 0) {
                deltaCallsPerSecond = (long) (deltaCalls / (deltaElapsed / 1000000000D));
            } else {
                deltaCallsPerSecond = 0;
            }

            if (elapsed > 0) {
                callsPerSecond = (long) (calls / (elapsed / 1000000000D));
            } else {
                callsPerSecond = 0;
            }
        }

        public String getName() {
            return name;
        }

        public long getDeltaCalls() {
            return deltaCalls;
        }

        public long getDeltaElapsed() {
            return deltaElapsed;
        }

        public long getDeltaCallsPerSecond() {
            return deltaCallsPerSecond;
        }

        public long getCalls() {
            return calls;
        }

        public long getElapsed() {
            return elapsed;
        }

        public long getCallsPerSecond() {
            return callsPerSecond;
        }

        @Override
        public String toString() {
            final String elapsedString = ModelStringUtil.formatDurationString(elapsed / 1000000);
            final String deltaElapsedString = ModelStringUtil.formatDurationString(deltaElapsed / 1000000);
            return name +
                    ": " +
                    "Delta " + deltaCalls + " in " + deltaElapsedString + " " + deltaCallsPerSecond + "cps " +
                    "Total " + calls + " in " + elapsedString + " " + callsPerSecond + "cps";
        }
    }

    private static class State {

        private final boolean calling;
        private final long calls;
        private final long elapsed;

        public State(final boolean calling,
                     final long calls,

                     final long elapsed) {
            this.calling = calling;
            this.calls = calls;
            this.elapsed = elapsed;
        }
    }

    private static class Call {

        private volatile boolean calling = false;
        private volatile long calls = 0;
        private volatile long elapsed = 0;
        private long startTime;

        public <R> R call(final Supplier<R> supplier) {
            synchronized (this) {
                calling = true;
                calls++;
                startTime = System.nanoTime();
            }
            R r = supplier.get();
            final long delta = System.nanoTime() - startTime;
            synchronized (this) {
                calling = false;
                elapsed += delta;
            }
            return r;
        }

        public synchronized State getState() {
            if (calling) {
                return new State(calling, calls, elapsed + (System.nanoTime() - startTime));
            }
            return new State(calling, calls, elapsed);
        }
    }
}
