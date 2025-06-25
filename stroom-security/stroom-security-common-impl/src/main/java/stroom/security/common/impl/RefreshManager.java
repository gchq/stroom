package stroom.security.common.impl;

import stroom.util.authentication.Refreshable;
import stroom.util.authentication.Refreshable.RefreshMode;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton // Stateful
public class RefreshManager implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RefreshManager.class);

    private final BlockingQueue<DelayedRefreshable> delayQueue = new DelayQueue<>();
    // Quicker to check this than iterating over the queue
    private final Set<String> uuidsInQueue = new ConcurrentSkipListSet<>();
    private final AtomicBoolean isShutdownInProgress = new AtomicBoolean(false);
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private ExecutorService refreshExecutorService = null;

    public RefreshManager() {

    }

    /**
     * Remove this refreshable from being managed by {@link RefreshManager}
     */
    public void remove(final Refreshable refreshable) {
        if (refreshable != null && !isShutdownInProgress.get()) {
            // Lock on this item, so we can ensure there is only one on the queue
            synchronized (refreshable) {
                final boolean didRemove;
                if (uuidsInQueue.remove(refreshable.getUuid())) {
                    didRemove = delayQueue.removeIf(item ->
                            Objects.equals(refreshable.getUuid(), item.getRefreshableUuid()));
                    LOGGER.debug(() -> LogUtil.message("Removed {}", itemToString(refreshable)));
                } else {
                    didRemove = false;
                }
                LOGGER.trace("didRemove: {}", didRemove);
            }
        }
    }

    /**
     * Register the refreshable so that its refreshing gets managed. Can be called many times
     * for the same {@link Refreshable}, e.g. to notify the {@link RefreshManager} that it was
     * refreshed externally.
     */
    public void addOrUpdate(final Refreshable refreshable) {
        // No point adding stuff if we are shutting down
        if (refreshable != null && !isShutdownInProgress.get()) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (refreshable) {
                // Assume this refreshable has the latest state so remove any existing items on the
                // queue for this refreshable
                remove(refreshable);

                // Spawn a new DelayedRefreshable that takes a snapshot of the token's expiry
                // as we can't mutate the times being used by the delay queue
                final DelayedRefreshable delayedRefreshable = new DelayedRefreshable(refreshable);

                delayQueue.add(delayedRefreshable);
                uuidsInQueue.add(refreshable.getUuid());

                LOGGER.debug(() -> LogUtil.message("Added {}", itemToString(delayedRefreshable)));
            }
        }
    }

    private void consumeFromQueue() {
        try {
            // We are called in an infinite while loop so drop out every 2s to allow
            // checking of shutdown state
            final DelayedRefreshable delayedRefreshable = delayQueue.poll(2, TimeUnit.SECONDS);
            if (delayedRefreshable != null) {
                try {
                    final Refreshable refreshable = delayedRefreshable.getRefreshable();
                    // We've removed it from the queue so keep the set in sync with the queue.
                    uuidsInQueue.remove(refreshable.getUuid());


                    if (refreshable.isActive()) {
                        // It is possible that someone getting the token has triggered a synchronous refresh,
                        // so we may not need to do it.
                        LOGGER.debug(() -> LogUtil.message("Consumed {}", itemToString(delayedRefreshable)));
                        final boolean didRefresh = refreshable.refreshIfRequired(RefreshMode.EAGER);
                        LOGGER.debug(() -> LogUtil.message("{} refresh of {}",
                                (didRefresh
                                        ? "Did"
                                        : "Didn't"),
                                itemToString(delayedRefreshable)));

                        // We need to add the refreshable that we may have just refreshed back onto the queue
                        // with its new expiry time
                        addOrUpdate(refreshable);
                    } else {
                        LOGGER.debug(() -> LogUtil.message("Consumed in-active {}", itemToString(delayedRefreshable)));
                    }

                } catch (final Exception e) {
                    LOGGER.error("Error consuming {} - {}",
                            itemToString(delayedRefreshable), LogUtil.exceptionMessage(e), e);
                    // We have to log, swallow and carry on, else our single thread dies
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Refresh delay queue interrupted, assume shutdown is happening so do no more");
        }
    }

    private String itemToString(final DelayedRefreshable delayedRefreshable) {
        if (delayedRefreshable != null) {
            final Refreshable refreshable = delayedRefreshable.getRefreshable();
            return LogUtil.message("item {} ({}) of type {} from refresh queue, " +
                            "expireTime: {} ({}), expireTimeWithBuffer: {} ({}), queue size after: {}, " +
                            "item detail: {}",
                    System.identityHashCode(refreshable),
                    System.identityHashCode(delayedRefreshable),
                    refreshable.getClass().getSimpleName(),
                    LogUtil.instant(refreshable.getExpireTimeEpochMs()),
                    Duration.between(
                            Instant.now(),
                            Instant.ofEpochMilli(refreshable.getExpireTimeEpochMs())),
                    LogUtil.instant(refreshable.getExpireTimeWithBufferEpochMs(RefreshMode.EAGER)),
                    Duration.between(
                            Instant.now(),
                            Instant.ofEpochMilli(refreshable.getExpireTimeWithBufferEpochMs(RefreshMode.EAGER))),
                    delayQueue.size(),
                    refreshable);
        } else {
            return null;
        }
    }

    private String itemToString(final Refreshable refreshable) {
        if (refreshable != null) {
            return LogUtil.message("item {} of type {} from refresh queue, " +
                            "expireTime: {} ({}), expireTimeWithBuffer: {} ({}), queue size after: {}, " +
                            "item detail: {}",
                    System.identityHashCode(refreshable),
                    refreshable.getClass().getSimpleName(),
                    LogUtil.instant(refreshable.getExpireTimeEpochMs()),
                    Duration.between(
                            Instant.now(),
                            Instant.ofEpochMilli(refreshable.getExpireTimeEpochMs())),
                    LogUtil.instant(refreshable.getExpireTimeWithBufferEpochMs(RefreshMode.EAGER)),
                    Duration.between(
                            Instant.now(),
                            Instant.ofEpochMilli(refreshable.getExpireTimeWithBufferEpochMs(RefreshMode.EAGER))),
                    delayQueue.size(),
                    refreshable);
        } else {
            return null;
        }
    }

    @Override
    public void start() throws Exception {
        if (refreshExecutorService == null) {
            LOGGER.info("Initialising RefreshManager executor");
            refreshExecutorService = Executors.newSingleThreadExecutor();
            refreshExecutorService.submit(() -> {
                final Thread currentThread = Thread.currentThread();
                LOGGER.debug(() -> "Started RefreshManager on thread " + currentThread.getName());
                isStarted.set(true);
                while (!currentThread.isInterrupted() && !isShutdownInProgress.get()) {
                    consumeFromQueue();
                }
                LOGGER.info(() -> LogUtil.message(
                        "RefreshManager refresh thread {} finishing (isInterrupted: {}, isShutdownInProgress: {})",
                        currentThread.getName(), currentThread.isInterrupted(), isShutdownInProgress.get()));
            });
        }
    }

    @Override
    public void stop() throws Exception {
        isShutdownInProgress.set(true);
        // If we are shutting down we don't care about items on the queue, so bin them
        delayQueue.clear();
        if (refreshExecutorService != null) {
            LOGGER.info("Shutting down RefreshManager executor");
            refreshExecutorService.shutdownNow();
            // No need to wait for termination the stuff on the queue has no value once
            // we are shutting down
            LOGGER.info("Successfully shut down RefreshManager executor");
        }
    }

    public int size() {
        return delayQueue.size();
    }

    public boolean isEmpty() {
        return delayQueue.isEmpty();
    }


    // --------------------------------------------------------------------------------


    /**
     * A wrapper around a {@link Refreshable} so the {@link Refreshable} can be put on a
     * {@link java.util.concurrent.DelayQueue}.
     */
    private static class DelayedRefreshable implements Delayed {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DelayedRefreshable.class);

        // Should prob use Instant
        private final long expireTimeWithBufferEpochMs;
        private final long expireTimeEpochMs;
        private final Refreshable refreshable;

        DelayedRefreshable(final Refreshable refreshable) {
            Objects.requireNonNull(refreshable);
            this.refreshable = Objects.requireNonNull(refreshable);
            // We need to snapshot the expireTime as that is mutable on the Refreshable
            this.expireTimeEpochMs = refreshable.getExpireTimeEpochMs();

            // We want to refresh items in the background slightly before the buffer threshold is reached
            // as other people will be calling refreshIfRequired, so we want the refresh to have happened already
//            long bufferMs;
//            if (refreshable.getRefreshBufferMs() > 0) {
////                bufferMs = (long) (1.1 * refreshable.getRefreshBufferMs());
//                bufferMs = refreshable.getRefreshBufferMs();
//            } else {
//                bufferMs = 0;
//            }

            this.expireTimeWithBufferEpochMs = refreshable.getExpireTimeWithBufferEpochMs(RefreshMode.EAGER);
            LOGGER.debug(() -> LogUtil.message(
                    "expireTimeEpoch: {}, refreshBuffer: {}, expireTimeWithBufferEpoch: {}",
                    LogUtil.instant(expireTimeEpochMs),
                    Duration.ofMillis(refreshable.getRefreshBufferMs()),
//                    Duration.ofMillis(bufferMs),
                    LogUtil.instant(expireTimeWithBufferEpochMs)));
        }

        public String getRefreshableUuid() {
            return refreshable.getUuid();
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            final long nowMs = System.currentTimeMillis();
            final long diffMs = expireTimeWithBufferEpochMs - nowMs;
            LOGGER.trace(() -> LogUtil.message("now: {}, expireTime: {}, expireTimeWithBuffer: {}, delayMs:  {} ({})",
                    LogUtil.instant(nowMs),
                    LogUtil.instant(expireTimeEpochMs),
                    LogUtil.instant(expireTimeWithBufferEpochMs),
                    ModelStringUtil.formatCsv(diffMs),
                    Duration.ofMillis(diffMs) + ")"));
            return unit.convert(diffMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(final Delayed other) {
            return Long.compare(
                    expireTimeWithBufferEpochMs,
                    ((DelayedRefreshable) other).expireTimeWithBufferEpochMs);
        }

        /**
         * @return The {@link Refreshable} that spawned this {@link DelayedRefreshable}.
         */
        public Refreshable getRefreshable() {
            return refreshable;
        }

        @Override
        public String toString() {
            return "DelayedRefreshable{" +
                    "expireTimeWithBufferEpoch=" + LogUtil.instant(expireTimeWithBufferEpochMs) +
                    "expireTimeEpoch=" + LogUtil.instant(expireTimeWithBufferEpochMs) +
                    ", refreshable=" + refreshable +
                    '}';
        }

        @Override
        public boolean equals(final Object object) {
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            final DelayedRefreshable that = (DelayedRefreshable) object;
            if (this.refreshable == that.refreshable) {
                return true;
            }
            return Objects.equals(this.getRefreshableUuid(), that.getRefreshableUuid());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getRefreshableUuid());
        }
    }
}
