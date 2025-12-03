/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.logging;

import stroom.util.logging.AsciiTable.Column;
import stroom.util.shared.ModelStringUtil;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public class SimpleMetrics {

    private static final Map<String, Metric> NAME_TO_METRIC_MAP = new ConcurrentHashMap<>();
    public static final int NANOS_IN_ONE_MILLI = 1_000_000;
    public static final int NANOS_IN_ONE_SEC = 1_000_000_000;
    private static boolean enabled = false;

    public static <R> R measure(final String name, final Supplier<R> runnable) {
        if (enabled) {
            return NAME_TO_METRIC_MAP.computeIfAbsent(name, k -> new Metric(name)).call(runnable);
        } else {
            return runnable.get();
        }
    }

    public static void measure(final String name, final Runnable runnable) {
        if (enabled) {
            NAME_TO_METRIC_MAP.computeIfAbsent(name, k -> new Metric(name)).call(() -> {
                runnable.run();
                return null;
            });
        } else {
            runnable.run();
        }
    }

    public static void report() {
        System.out.println(toAsciiTable(NAME_TO_METRIC_MAP) + "\n\n");
    }

    public static void reset() {
        NAME_TO_METRIC_MAP.clear();
    }

    public static void setEnabled(final boolean enabled) {
        SimpleMetrics.enabled = enabled;
    }

    /**
     * Creates a non-static {@link LocalMetrics} for transient use in a class/method.
     *
     * @param isEnabled If false returns an instance that does nothing apart from call
     *                  the passed runnables/suppliers.
     */
    public static LocalMetrics createLocalMetrics(final boolean isEnabled) {
        return isEnabled
                ? new EnabledLocalMetrics()
                : DisabledLocalMetrics.INSTANCE;
    }

    private static String toAsciiTable(final Map<String, Metric> nameToMetricMap) {
        final List<Snapshot> snapshots = nameToMetricMap
                .values()
                .stream()
                .map(Metric::snapshot)
                .sorted(Comparator.comparing(Snapshot::getDeltaCallsPerSecond))
                .toList();

        return AsciiTable.builder(snapshots)
                .withColumn(Column.of("Name", Snapshot::getName))
                .withColumn(Column.integer("Delta calls", Snapshot::getDeltaCalls))
                .withColumn(Column.decimal(
                        "Delta elapsed (ms)",
                        (Snapshot row) -> row.getDeltaElapsed() / (double) NANOS_IN_ONE_MILLI,
                        3))
                .withColumn(Column.integer("Delta call/s", Snapshot::getDeltaCallsPerSecond))
                .withColumn(Column.integer("Total calls", Snapshot::getCalls))
                .withColumn(Column.decimal(
                        "Total elapsed (ms)",
                        (Snapshot row) -> row.getDeltaElapsed() / (double) NANOS_IN_ONE_MILLI,
                        3))
                .withColumn(Column.integer("Total call/s", Snapshot::getCallsPerSecond))
                .build();
    }


    // --------------------------------------------------------------------------------


    public interface LocalMetrics {

        <R> R measure(final String name, final Supplier<R> runnable);

        void measure(final String name, final Runnable runnable);

        void reset();
    }


    // --------------------------------------------------------------------------------


    private static class DisabledLocalMetrics implements LocalMetrics {

        private static final DisabledLocalMetrics INSTANCE = new DisabledLocalMetrics();

        @Override
        public <R> R measure(final String name, final Supplier<R> runnable) {
            return runnable.get();
        }

        @Override
        public void measure(final String name, final Runnable runnable) {
            runnable.run();
        }

        @Override
        public void reset() {
            // no-op
        }
    }


    // --------------------------------------------------------------------------------


    private static class EnabledLocalMetrics implements LocalMetrics {

        private final Map<String, Metric> nameToMetricsMap = new ConcurrentHashMap<>();

        private EnabledLocalMetrics() {
        }

        public <R> R measure(final String name, final Supplier<R> runnable) {
            return nameToMetricsMap.computeIfAbsent(name, k -> new Metric(name)).call(runnable);
        }

        public void measure(final String name, final Runnable runnable) {
            nameToMetricsMap.computeIfAbsent(name, k -> new Metric(name)).call(() -> {
                runnable.run();
                return null;
            });
        }

        @Override
        public String toString() {
            return SimpleMetrics.toAsciiTable(nameToMetricsMap);
        }

        public void reset() {
            nameToMetricsMap.clear();
        }
    }


    // --------------------------------------------------------------------------------


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


    // --------------------------------------------------------------------------------


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
                deltaCallsPerSecond = (long) (deltaCalls / (deltaElapsed / (double) NANOS_IN_ONE_SEC));
            } else {
                deltaCallsPerSecond = 0;
            }

            if (elapsed > 0) {
                callsPerSecond = (long) (calls / (elapsed / (double) NANOS_IN_ONE_SEC));
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
            final String elapsedString = ModelStringUtil.formatDurationString(elapsed / NANOS_IN_ONE_MILLI);
            final String deltaElapsedString = ModelStringUtil.formatDurationString(
                    deltaElapsed / NANOS_IN_ONE_MILLI);
            final String deltaCallsPerSecondStr = ModelStringUtil.formatCsv(deltaCallsPerSecond);
            final String callsPerSecondStr = ModelStringUtil.formatCsv(callsPerSecond);
            return name +
                   ": " +
                   "Delta " + deltaCalls + " in " + deltaElapsedString + " " + deltaCallsPerSecondStr + " calls/sec " +
                   "Total " + calls + " in " + elapsedString + " " + callsPerSecondStr + " calls/sec";
        }
    }


    // --------------------------------------------------------------------------------


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


    // --------------------------------------------------------------------------------


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
            final R r = supplier.get();
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
