/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.execution;

import stroom.proxy.app.execution.LoopTask.Outcome;
import stroom.proxy.app.execution.WorkRegistry.Backoff;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The execution contract, E1-E7 in {@code designs/infrastructure/execution.md}.
 */
class TestWorkRegistry {

    private static final Backoff FAST =
            new Backoff(Duration.ofMillis(10), Duration.ofMillis(40), Duration.ofMillis(10));
    private static final Duration WAIT = Duration.ofSeconds(5);

    private final WorkRegistry registry = new WorkRegistry(FAST, Duration.ofSeconds(2));

    @AfterEach
    void stopRegistry() {
        registry.stop();
    }

    @Test
    void testLoopRunsOnlyBetweenStartAndStop() throws Exception {
        final AtomicInteger runs = new AtomicInteger();
        final Loop loop = registry.loop("work", Phase.FORWARD, 1, () -> {
            runs.incrementAndGet();
            Thread.sleep(1);
            return Outcome.PROCESSED;
        });

        Thread.sleep(50);
        assertThat(runs.get()).as("nothing runs before start").isZero();
        assertThat(loop.liveThreads()).isZero();

        registry.start();
        assertThat(registry.isStarted()).isTrue();
        await(() -> runs.get() > 0);
        await(() -> loop.liveThreads() == 1);

        registry.stop();
        assertThat(registry.isStarted()).isFalse();
        assertThat(loop.liveThreads()).as("stop waits for the thread to leave").isZero();
        final int afterStop = runs.get();
        Thread.sleep(50);
        assertThat(runs.get()).as("nothing runs after stop").isEqualTo(afterStop);
    }

    @Test
    void testLoopRunsItsTaskOnEveryConfiguredThread() {
        final Set<String> threads = ConcurrentHashMap.newKeySet();
        final Loop loop = registry.loop("work", Phase.FORWARD, 3, () -> {
            threads.add(Thread.currentThread().getName());
            Thread.sleep(1);
            return Outcome.NOTHING;
        });

        registry.start();

        await(() -> threads.size() == 3);
        await(() -> loop.liveThreads() == 3);
        assertThat(loop.configuredThreads()).isEqualTo(3);
        assertThat(threads).allSatisfy(name -> assertThat(name).startsWith("work-"));
    }

    @Test
    void testStartAndStopAreIdempotent() {
        final Loop loop = registry.loop("work", Phase.FORWARD, 2, () -> {
            Thread.sleep(1);
            return Outcome.NOTHING;
        });

        registry.start();
        registry.start();
        await(() -> loop.liveThreads() == 2);
        Thread.yield();
        assertThat(loop.liveThreads()).as("a second start adds no threads").isEqualTo(2);

        registry.stop();
        registry.stop();
        assertThat(loop.liveThreads()).isZero();
    }

    @Test
    void testRegisteringAfterStartStartsAtOnce() {
        registry.start();
        final AtomicInteger runs = new AtomicInteger();
        registry.loop("late", Phase.FORWARD, 1, () -> {
            runs.incrementAndGet();
            Thread.sleep(1);
            return Outcome.NOTHING;
        });
        await(() -> runs.get() > 0);
    }

    @Test
    void testRegisteringDuringShutdownIsRefused() {
        registry.start();
        registry.stop();
        assertThatThrownBy(() -> registry.loop("late", Phase.FORWARD, 1, () -> Outcome.NOTHING))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(registry::start)
                .as("a stopped registry cannot be started again")
                .isInstanceOf(IllegalStateException.class);
        assertThat(registry.isShuttingDown()).isTrue();
    }

    @Test
    void testScheduleRegisteredAfterStartRunsAtOnce() {
        registry.start();
        final AtomicInteger runs = new AtomicInteger();
        registry.schedule("late-tick", Phase.HOUSEKEEPING, Duration.ofMillis(10), runs::incrementAndGet);
        await(() -> runs.get() > 0);
    }

    @Test
    void testAStrayInterruptRetiresTheThreadVisibly() {
        final AtomicInteger runs = new AtomicInteger();
        final Loop loop = registry.loop("work", Phase.FORWARD, 1, () -> {
            // Something other than stop interrupting the thread: the loop lets it leave, and health
            // shows live below configured. The ERROR log for it is not asserted here.
            runs.incrementAndGet();
            Thread.currentThread().interrupt();
            return Outcome.NOTHING;
        });
        registry.start();
        await(() -> runs.get() == 1 && loop.liveThreads() == 0);
        assertThat(registry.isStarted()).isTrue();
        assertThat(loop.configuredThreads()).isEqualTo(1);
        registry.stop();
    }

    @Test
    void testRejectsBadRegistrations() {
        assertThatThrownBy(() -> registry.loop("work", Phase.FORWARD, 0, () -> Outcome.NOTHING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.loop(" ", Phase.FORWARD, 1, () -> Outcome.NOTHING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.schedule("tick", Phase.HOUSEKEEPING, Duration.ZERO, () -> {
        }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Backoff(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .as("a zero failure backoff is a spin")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Backoff(Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .as("the cap must not be below the first wait")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testLoopContinuesAfterItsTaskThrows() {
        final AtomicInteger runs = new AtomicInteger();
        final Loop loop = registry.loop("work", Phase.FORWARD, 1, () -> {
            final int run = runs.incrementAndGet();
            if (run == 1) {
                throw new IllegalStateException("an exception");
            }
            if (run == 2) {
                // An Error must not retire the thread either.
                throw new AssertionError("an error");
            }
            Thread.sleep(1);
            return Outcome.PROCESSED;
        });

        registry.start();

        await(() -> runs.get() >= 4);
        assertThat(loop.liveThreads()).isEqualTo(1);
    }

    @Test
    void testFailedOutcomeBacksOffInsteadOfHotLooping() throws Exception {
        final WorkRegistry slow = new WorkRegistry(
                new Backoff(Duration.ofMillis(50), Duration.ofMillis(200), Duration.ofMillis(10)),
                Duration.ofSeconds(2));
        final AtomicInteger runs = new AtomicInteger();
        slow.loop("failing", Phase.FORWARD, 1, () -> {
            runs.incrementAndGet();
            return Outcome.FAILED;
        });

        slow.start();
        try {
            Thread.sleep(1000);
        } finally {
            slow.stop();
        }

        // 50, 100, 200, 200... - roughly six attempts fit in a second; a hot loop manages millions.
        assertThat(runs.get()).isGreaterThan(1).isLessThan(50);
    }

    @Test
    void testFailureBackoffDoublesToItsCapAndNothingOutcomeDoesNotWait() throws Exception {
        final Backoff backoff = new Backoff(Duration.ofMillis(100), Duration.ofMillis(350), Duration.ofSeconds(1));
        assertThat(backoff.forFailures(0)).isEqualTo(Duration.ofMillis(100));
        assertThat(backoff.forFailures(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(backoff.forFailures(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(backoff.forFailures(3)).isEqualTo(Duration.ofMillis(350));
        assertThat(backoff.forFailures(60)).as("no overflow").isEqualTo(Duration.ofMillis(350));

        // A task that reports NOTHING has already waited; the loop must not sleep on top of that.
        final AtomicInteger runs = new AtomicInteger();
        registry.loop("idle", Phase.FORWARD, 1, () -> {
            runs.incrementAndGet();
            return Outcome.NOTHING;
        });
        registry.start();
        Thread.sleep(100);
        assertThat(runs.get()).isGreaterThan(100);
    }

    @Test
    void testPauseHoldsTheLoopAndResumeReleasesIt() throws Exception {
        final AtomicInteger runs = new AtomicInteger();
        final Loop loop = registry.loop("work", Phase.FORWARD, 2, () -> {
            runs.incrementAndGet();
            Thread.sleep(1);
            return Outcome.PROCESSED;
        });
        registry.start();
        await(() -> runs.get() > 0);

        loop.pause();
        assertThat(loop.isPaused()).isTrue();
        Thread.sleep(50);
        final int whilePaused = runs.get();
        Thread.sleep(100);
        assertThat(runs.get()).as("no runs start while paused").isEqualTo(whilePaused);
        assertThat(loop.liveThreads()).as("paused threads are still live").isEqualTo(2);

        loop.resume();
        assertThat(loop.isPaused()).isFalse();
        await(() -> runs.get() > whilePaused);

        // Stopping a paused loop must not wait for a resume that never comes.
        loop.pause();
        final Instant before = Instant.now();
        registry.stop();
        assertThat(Duration.between(before, Instant.now())).isLessThan(Duration.ofSeconds(1));
        assertThat(loop.liveThreads()).isZero();
        assertThat(loop.isPaused()).as("stop leaves the loop resumed").isFalse();
    }

    @Test
    void testStopsProducersBeforeConsumers() {
        final List<String> stopped = new CopyOnWriteArrayList<>();
        final WorkRegistry ordered = new WorkRegistry(FAST, Duration.ofMillis(20));
        // Registered out of phase order, and two in one phase to pin registration order within it.
        for (final String name : List.of("HOUSEKEEPING", "INGRESS-a", "FORWARD", "INGRESS-b")) {
            final Phase phase = Phase.valueOf(name.split("-")[0]);
            ordered.loop(name, phase, 1, () -> {
                try {
                    Thread.sleep(60_000);
                } catch (final InterruptedException e) {
                    // The interrupt is stop reaching this loop; record when it arrived.
                    stopped.add(name);
                    throw e;
                }
                return Outcome.NOTHING;
            });
        }
        ordered.start();
        await(() -> ordered.loops().stream().allMatch(loop -> loop.liveThreads() == 1));

        ordered.stop();

        assertThat(stopped).containsExactly("INGRESS-a", "INGRESS-b", "FORWARD", "HOUSEKEEPING");
        assertThat(ordered.loops()).allSatisfy(loop -> assertThat(loop.liveThreads()).isZero());
    }

    @Test
    void testStopIsBoundedWhenATaskIgnoresInterruption() {
        final AtomicBoolean release = new AtomicBoolean();
        final WorkRegistry bounded = new WorkRegistry(FAST, Duration.ofMillis(50));
        final Loop loop = bounded.loop("stuck", Phase.FORWARD, 1, () -> {
            while (!release.get()) {
                Thread.onSpinWait();
            }
            return Outcome.NOTHING;
        });
        bounded.start();
        await(() -> loop.liveThreads() == 1);

        final Instant before = Instant.now();
        bounded.stop();
        final Duration took = Duration.between(before, Instant.now());
        release.set(true);

        assertThat(took)
                .as("stop timeout plus the forced-stop grace, then the thread is abandoned")
                .isGreaterThanOrEqualTo(WorkRegistry.FORCED_STOP_GRACE)
                .isLessThan(WorkRegistry.FORCED_STOP_GRACE.plusSeconds(3));
        assertThat(bounded.isStarted()).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void testScheduleRunsRepeatedlyAndSurvivesAThrow() throws Exception {
        final AtomicInteger runs = new AtomicInteger();
        registry.schedule("tick", Phase.HOUSEKEEPING, Duration.ofMillis(10), () -> {
            if (runs.incrementAndGet() == 1) {
                throw new IllegalStateException("first run fails");
            }
        });
        registry.start();
        await(() -> runs.get() >= 3);
        registry.stop();
        final int afterStop = runs.get();
        Thread.sleep(50);
        assertThat(runs.get()).as("nothing runs after stop").isEqualTo(afterStop);
    }

    private static void await(final BooleanSupplier condition) {
        final Instant deadline = Instant.now().plus(WAIT);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Condition not met within " + WAIT);
            }
            Thread.onSpinWait();
        }
    }
}
