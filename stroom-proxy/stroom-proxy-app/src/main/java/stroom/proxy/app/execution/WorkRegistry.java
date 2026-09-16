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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The one owner of every background thread in the proxy. A component registers a loop or a
 * schedule naming its {@link Phase}; the registry starts them with consumers before producers,
 * stops them with producers before consumers, bounds every stop, and never lets a thread die
 * silently. The contract in full is {@code designs/infrastructure/execution.md} §2.
 */
@Singleton
public class WorkRegistry {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(WorkRegistry.class);

    /** How long a stop waits for a registration's threads before interrupting them. */
    public static final Duration DEFAULT_STOP_TIMEOUT = Duration.ofSeconds(30);
    /** How long a stop waits after interrupting before abandoning the threads. */
    static final Duration FORCED_STOP_GRACE = Duration.ofSeconds(5);

    private final Backoff backoff;
    private final Duration stopTimeout;
    private final List<Work> work = new ArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    public WorkRegistry() {
        this(Backoff.DEFAULT, DEFAULT_STOP_TIMEOUT);
    }

    public WorkRegistry(final Backoff backoff, final Duration stopTimeout) {
        this.backoff = Objects.requireNonNull(backoff, "backoff");
        this.stopTimeout = Objects.requireNonNull(stopTimeout, "stopTimeout");
    }

    /**
     * Register a loop: {@code threads} threads each running {@code task} again and again. If the
     * registry is already started the loop starts now.
     */
    public synchronized Loop loop(final String name, final Phase phase, final int threads, final LoopTask task) {
        final LoopWork loop = new LoopWork(name, phase, threads, task);
        register(loop);
        return loop;
    }

    /**
     * Register a schedule: one thread running {@code task} at a fixed {@code delay}, from start.
     */
    public synchronized void schedule(final String name, final Phase phase, final Duration delay, final Runnable task) {
        register(new ScheduledWork(name, phase, delay, task));
    }

    private void register(final Work item) {
        if (shuttingDown.get()) {
            throw new IllegalStateException("Cannot register '" + item.name() + "': the registry is shutting down");
        }
        work.add(item);
        LOGGER.debug(() -> LogUtil.message("Registered {} '{}' in phase {}", item.kind(), item.name(), item.phase()));
        if (started.get()) {
            item.start();
        }
    }

    /**
     * Start everything, last phase first, so a consumer is draining before its producer feeds it.
     */
    public synchronized void start() {
        if (shuttingDown.get()) {
            throw new IllegalStateException("The registry has been stopped and cannot be started again");
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        final List<Work> inStartOrder = new ArrayList<>(work);
        inStartOrder.sort(Comparator.comparing(Work::phase).reversed());
        LOGGER.info(() -> LogUtil.message("Starting {} registered piece(s) of work", inStartOrder.size()));
        inStartOrder.forEach(Work::start);
    }

    /**
     * Stop everything, first phase first, so a producer has stopped before its consumer. Bounded
     * per registration, never throws, always reaches every registration.
     */
    public void stop() {
        final List<Work> inStopOrder;
        // The lock covers the snapshot only, so health can still read the loops during a long stop.
        synchronized (this) {
            shuttingDown.set(true);
            if (!started.compareAndSet(true, false)) {
                return;
            }
            inStopOrder = new ArrayList<>(work);
        }
        inStopOrder.sort(Comparator.comparing(Work::phase));
        LOGGER.info(() -> LogUtil.message("Stopping {} registered piece(s) of work", inStopOrder.size()));
        for (final Work item : inStopOrder) {
            try {
                item.stop();
            } catch (final RuntimeException e) {
                LOGGER.error(() -> LogUtil.message("Error stopping {} '{}'", item.kind(), item.name()), e);
            }
        }
        // Dropwizard's Managed.stop() runs on a Jersey thread; hand it back with no interrupt pending.
        if (Thread.interrupted()) {
            LOGGER.debug(() -> "Interrupt cleared after stopping registered work");
        }
    }

    /**
     * True from the moment stop begins. A task that would start a long piece of work can decline.
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    public boolean isStarted() {
        return started.get();
    }

    public synchronized List<Loop> loops() {
        return work.stream()
                .filter(item -> item instanceof LoopWork)
                .map(item -> (Loop) item)
                .toList();
    }

    private static ThreadFactory threadFactory(final String name) {
        final ThreadFactory factory = new CustomThreadFactory(
                name + "-", StroomThreadGroup.instance(), Thread.NORM_PRIORITY - 1);
        return runnable -> {
            final Thread thread = factory.newThread(runnable);
            thread.setDaemon(true);
            return thread;
        };
    }

    private boolean awaitStop(final ExecutorService executor, final String kind, final String name) {
        executor.shutdown();
        try {
            if (executor.awaitTermination(stopTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return true;
            }
            LOGGER.warn(() -> LogUtil.message(
                    "{} '{}' did not stop within {}; interrupting its threads and waiting {} more",
                    kind, name, stopTimeout, FORCED_STOP_GRACE));
            executor.shutdownNow();
            if (executor.awaitTermination(FORCED_STOP_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                return true;
            }
            LOGGER.error(() -> LogUtil.message(
                    "{} '{}' still has threads running after being interrupted. They are abandoned to the "
                    + "process exit; nothing depends on them having stopped, but a task that ignores "
                    + "interruption is worth investigating.", kind, name));
            return false;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            return false;
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * The waits a loop applies. Constants in production; tests shorten them.
     *
     * @param failure The first wait after a task reports {@link Outcome#FAILED}, doubling per
     *                consecutive failure on that thread.
     * @param maxFailure The cap on that doubling.
     * @param error The wait after a task throws.
     */
    public record Backoff(Duration failure, Duration maxFailure, Duration error) {

        public static final Backoff DEFAULT =
                new Backoff(Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofSeconds(1));

        public Backoff {
            requirePositive(failure, "failure");
            requirePositive(error, "error");
            Objects.requireNonNull(maxFailure, "maxFailure");
            if (maxFailure.compareTo(failure) < 0) {
                throw new IllegalArgumentException("maxFailure " + maxFailure + " is less than failure " + failure);
            }
        }

        Duration forFailures(final int consecutiveFailures) {
            if (consecutiveFailures <= 1) {
                return failure;
            }
            final long millis = failure.toMillis() << Math.min(consecutiveFailures - 1, 32);
            return millis < 0 || millis > maxFailure.toMillis()
                    ? maxFailure
                    : Duration.ofMillis(millis);
        }

        private static void requirePositive(final Duration duration, final String name) {
            Objects.requireNonNull(duration, name);
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive, or a failure would spin: " + duration);
            }
        }
    }


    // --------------------------------------------------------------------------------


    private interface Work {

        String name();

        Phase phase();

        String kind();

        void start();

        void stop();
    }


    // --------------------------------------------------------------------------------


    private final class LoopWork implements Work, Loop {

        private final String name;
        private final Phase phase;
        private final int threads;
        private final LoopTask task;
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicInteger live = new AtomicInteger();
        private final Object gate = new Object();
        private volatile boolean paused;
        private volatile ExecutorService executor;

        private LoopWork(final String name, final Phase phase, final int threads, final LoopTask task) {
            this.name = requireNonBlank(name);
            this.phase = Objects.requireNonNull(phase, "phase");
            if (threads < 1) {
                throw new IllegalArgumentException("Loop '" + name + "' needs at least one thread, got " + threads);
            }
            this.threads = threads;
            this.task = Objects.requireNonNull(task, "task");
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Phase phase() {
            return phase;
        }

        @Override
        public Phase getPhase() {
            return phase;
        }

        @Override
        public String kind() {
            return "loop";
        }

        @Override
        public void start() {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            LOGGER.info(() -> LogUtil.message(
                    "Starting loop '{}' with {} thread(s) in phase {}", name, threads, phase));
            executor = Executors.newFixedThreadPool(threads, threadFactory(name));
            for (int i = 0; i < threads; i++) {
                executor.execute(this::runLoop);
            }
        }

        @Override
        public void stop() {
            if (!running.compareAndSet(true, false)) {
                return;
            }
            // A paused loop's threads are waiting at the gate and must be let out to leave.
            synchronized (gate) {
                paused = false;
                gate.notifyAll();
            }
            final ExecutorService current = executor;
            if (current != null) {
                awaitStop(current, kind(), name);
            }
        }

        @Override
        public void pause() {
            synchronized (gate) {
                if (!paused) {
                    paused = true;
                    LOGGER.info(() -> LogUtil.message("Paused loop '{}'", name));
                }
            }
        }

        @Override
        public void resume() {
            synchronized (gate) {
                if (paused) {
                    paused = false;
                    gate.notifyAll();
                    LOGGER.info(() -> LogUtil.message("Resumed loop '{}'", name));
                }
            }
        }

        @Override
        public boolean isPaused() {
            return paused;
        }

        @Override
        public int liveThreads() {
            return live.get();
        }

        @Override
        public int configuredThreads() {
            return threads;
        }

        private void runLoop() {
            live.incrementAndGet();
            int consecutiveFailures = 0;
            try {
                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    if (!awaitResumed()) {
                        break;
                    }
                    try {
                        final Outcome outcome = task.run();
                        if (outcome == Outcome.FAILED) {
                            consecutiveFailures++;
                            sleep(backoff.forFailures(consecutiveFailures));
                        } else {
                            consecutiveFailures = 0;
                        }
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (final Throwable e) {
                        if (stopping()) {
                            // Stop interrupted the task, or the task declined because of it.
                            LOGGER.debug(() -> LogUtil.message("Loop '{}' task threw while stopping", name), e);
                        } else {
                            // Throwable, not Exception: an Error must not retire the thread either.
                            LOGGER.error(() -> LogUtil.message(
                                    "Loop '{}' task threw; continuing after {}", name, backoff.error()), e);
                            sleep(backoff.error());
                        }
                    }
                }
            } finally {
                live.decrementAndGet();
                if (running.get()) {
                    // Only stop may end a thread; something else interrupted this one.
                    LOGGER.error(() -> LogUtil.message(
                            "Loop '{}' thread {} left the loop while the loop is still running; it was "
                            + "interrupted by something other than stop", name, Thread.currentThread().getName()));
                } else {
                    LOGGER.info(() -> LogUtil.message(
                            "Loop '{}' thread {} stopped", name, Thread.currentThread().getName()));
                }
            }
        }

        private boolean stopping() {
            return shuttingDown.get() || !running.get() || Thread.currentThread().isInterrupted();
        }

        /**
         * Wait out a pause. False when the loop stopped or the thread was interrupted meanwhile.
         */
        private boolean awaitResumed() {
            synchronized (gate) {
                while (paused && running.get()) {
                    try {
                        gate.wait();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return running.get();
            }
        }

        private void sleep(final Duration duration) {
            try {
                Thread.sleep(duration.toMillis());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }


    // --------------------------------------------------------------------------------


    private final class ScheduledWork implements Work {

        private final String name;
        private final Phase phase;
        private final Duration delay;
        private final Runnable task;
        private volatile ScheduledExecutorService executor;

        private ScheduledWork(final String name, final Phase phase, final Duration delay, final Runnable task) {
            this.name = requireNonBlank(name);
            this.phase = Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(delay, "delay");
            if (delay.isZero() || delay.isNegative()) {
                throw new IllegalArgumentException(LogUtil.message(
                        "Schedule '{}' was given a delay of {}; it must be positive - zero is a busy loop, not a "
                        + "schedule", name, delay));
            }
            this.delay = delay;
            this.task = Objects.requireNonNull(task, "task");
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Phase phase() {
            return phase;
        }

        @Override
        public String kind() {
            return "schedule";
        }

        @Override
        public void start() {
            if (executor != null) {
                return;
            }
            LOGGER.info(() -> LogUtil.message("Starting schedule '{}' every {} in phase {}", name, delay, phase));
            executor = Executors.newSingleThreadScheduledExecutor(threadFactory(name));
            executor.scheduleWithFixedDelay(this::runOnce, 0, delay.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void runOnce() {
            try {
                task.run();
            } catch (final Throwable e) {
                if (shuttingDown.get() || Thread.currentThread().isInterrupted()) {
                    LOGGER.debug(() -> LogUtil.message("Schedule '{}' task threw while stopping", name), e);
                } else {
                    LOGGER.error(() -> LogUtil.message(
                            "Schedule '{}' task threw; it will run again in {}", name, delay), e);
                }
            }
        }

        @Override
        public void stop() {
            final ScheduledExecutorService current = executor;
            if (current != null) {
                executor = null;
                awaitStop(current, kind(), name);
            }
        }
    }

    private static String requireNonBlank(final String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }
}
