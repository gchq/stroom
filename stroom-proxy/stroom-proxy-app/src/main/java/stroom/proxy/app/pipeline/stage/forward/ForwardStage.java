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

package stroom.proxy.app.pipeline.stage.forward;

import stroom.proxy.app.handler.Destination;
import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.handler.Refused;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * One destination's forwarding: claim a group, deliver it, or fail it, or give up on it
 * ({@code designs/stages/forward.md}).
 * <p>
 * A worker over this processor acknowledges the item when {@link #process} returns and fails it
 * when it throws, which is the whole of F1 and F3: a group stays claimed and in its store until the
 * destination has accepted it or the stage has given up on it, and a transient failure sends the
 * message to the tail of the queue with the attempt counted. The stage owns no durable state (F2):
 * what it knows about the destination's health is a failure count in memory, and losing it costs
 * an attempt, not data.
 * </p>
 * <p>
 * Back-off is the destination's, not the group's (F6). After a transient failure every thread of
 * this destination's loop waits out the delay at the start of its next attempt, with its group
 * claimed throughout, so a destination that has just failed is not hammered by every thread in turn.
 * The failures that count are rounds, not threads: several threads failing inside one wait are one
 * failure, so the delay grows once per round of attempts and reads as the configuration says.
 * </p>
 * <p>
 * The one thing the stage writes beside a group is {@code error.log}, at give-up, before the group
 * is delivered to the give-up destination. Its presence on redelivery means a give-up was decided
 * and not completed - the delivery threw, or the process died before the input was deleted - and
 * the give-up is resumed rather than the forward destination tried again: the decision stands,
 * since a refusal is permanent and an age only grows.
 * </p>
 */
public final class ForwardStage implements FileGroupQueueItemProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardStage.class);

    /** The wait is served in slices so that a stop is noticed. */
    static final Duration WAIT_SLICE = Duration.ofSeconds(1);

    private final FileStoreRegistry stores;
    private final Destination destination;
    private final GiveUp giveUp;
    private final ForwardBounds bounds;
    private final BooleanSupplier stopping;

    private final Object backOff = new Object();
    private int consecutiveFailures;
    private volatile long notBeforeMillis;

    /**
     * @param stopping True once the process is stopping, so that a back-off wait ends early; the
     *                 group then goes back on the queue with the attempt counted.
     */
    public ForwardStage(final FileStoreRegistry stores,
                        final Destination destination,
                        final GiveUp giveUp,
                        final ForwardBounds bounds,
                        final BooleanSupplier stopping) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.giveUp = Objects.requireNonNull(giveUp, "giveUp");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.stopping = Objects.requireNonNull(stopping, "stopping");
    }

    public Destination getDestination() {
        return destination;
    }

    @Override
    public void process(final FileGroupQueueItem item) throws Exception {
        final FileGroupQueueMessage message = Objects.requireNonNull(item.getMessage(), "item.message");
        // Absence propagates: the group was delivered by an earlier attempt, so the worker acknowledges.
        final Path group = stores.resolve(message);
        new FileGroup(group).requireComplete(
                "Forward input '" + message.messageId() + "' for '" + destination.getName() + "'");

        final int attempt = item.getDeliveryAttempt();
        final Duration age = Duration.between(message.createdTime(), Instant.now());
        if (Files.isRegularFile(group.resolve(FileGroup.ERROR_LOG_FILE_NAME))) {
            LOGGER.info(() -> LogUtil.message(
                    "Resuming the give-up of {} (feed {}) for '{}' on attempt {}: it carries an error.log from a "
                    + "give-up that did not complete", message.messageId(), message.feed(), destination.getName(),
                    attempt));
            giveUp.resume(group, message, destination.getName(), attempt, age);
            deleteInput(message, group);
            return;
        }

        awaitBackOff();

        try {
            destination.deliver(group);
        } catch (final Refused e) {
            LOGGER.warn(() -> LogUtil.message(
                    "'{}' refused {} (feed {}) on attempt {}: {}. Giving up on it.",
                    destination.getName(), message.messageId(), message.feed(), attempt, e.getMessage()));
            giveUp.giveUp(group, message, destination.getName(), attempt, age, e);
            deleteInput(message, group);
            return;
        } catch (final IOException | RuntimeException e) {
            if (age.compareTo(bounds.maxRetryAge()) > 0) {
                LOGGER.warn(() -> LogUtil.message(
                        "Attempt {} to deliver {} (feed {}) to '{}' failed and it is {} old, past maxRetryAge {}. "
                        + "Giving up on it: {}",
                        attempt, message.messageId(), message.feed(), destination.getName(), age,
                        bounds.maxRetryAge(), LogUtil.exceptionMessage(e)));
                giveUp.giveUp(group, message, destination.getName(), attempt, age, e);
                deleteInput(message, group);
                return;
            }
            final Duration delay = recordFailure();
            LOGGER.warn(() -> LogUtil.message(
                    "Attempt {} to deliver {} (feed {}, {} old) to '{}' failed: {}. It goes back on the queue; "
                    + "the next attempt on this destination is not before {} from now.",
                    attempt, message.messageId(), message.feed(), age, destination.getName(),
                    LogUtil.exceptionMessage(e), delay));
            throw e;
        }

        recordSuccess();
        deleteInput(message, group);
        LOGGER.debug(() -> LogUtil.message(
                "Delivered {} (feed {}) to '{}' on attempt {}",
                message.messageId(), message.feed(), destination.getName(), attempt));
    }

    /**
     * Count a transient failure against the destination and set when its next attempt may start.
     * A failure that lands while a wait set by another thread is still running is part of the same
     * round and does not count again, so the delay grows once per round of attempts.
     *
     * @return The wait now in force.
     */
    private Duration recordFailure() {
        synchronized (backOff) {
            final long now = System.currentTimeMillis();
            if (now < notBeforeMillis) {
                return Duration.ofMillis(notBeforeMillis - now);
            }
            consecutiveFailures++;
            final Duration delay = bounds.delayAfter(consecutiveFailures);
            notBeforeMillis = now + delay.toMillis();
            return delay;
        }
    }

    private void recordSuccess() {
        synchronized (backOff) {
            consecutiveFailures = 0;
            notBeforeMillis = 0;
        }
    }

    /**
     * Wait out the destination's back-off, if it is in one. The wait is served here, with the group
     * claimed, rather than by failing the item at once: failing it would burn an attempt on every
     * pass over the queue while the destination was known to be down.
     */
    private void awaitBackOff() throws IOException {
        long remaining;
        while ((remaining = notBeforeMillis - System.currentTimeMillis()) > 0) {
            if (stopping.getAsBoolean()) {
                throw new IOException(LogUtil.message(
                        "Stopping while waiting out the back-off of '{}'; the group goes back on the queue",
                        destination.getName()));
            }
            try {
                Thread.sleep(Math.min(remaining, WAIT_SLICE.toMillis()));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(LogUtil.message(
                        "Interrupted while waiting out the back-off of '{}'; the group goes back on the queue",
                        destination.getName()), e);
            }
        }
    }

    /**
     * Housekeeping after the durable step: the destination has the group, or the give-up
     * destination has. A failure here is logged, naming what is left behind, and the message is
     * still acknowledged: throwing would deliver the group again on every redelivery for as long
     * as the delete kept failing, and an input no message names is what the store's sweep, or the
     * start-up sweep in local mode, reclaims.
     */
    private void deleteInput(final FileGroupQueueMessage message, final Path group) {
        try {
            stores.requireFileStore(message.fileStoreLocation().storeName()).delete(message.fileStoreLocation());
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(() -> LogUtil.message(
                    "Delivered {} (feed {}) but failed to delete its input at {}: {}. The message is acknowledged; "
                    + "the group is disk left behind, reclaimed by the store's sweep, rather than data at risk.",
                    message.messageId(), message.feed(), message.fileStoreLocation().uri(),
                    LogUtil.exceptionMessage(e)), e);
            return;
        }
        // The path the store lent: the same directory on a filesystem store, gone already or moved
        // out by a file destination; a per-resolve download on an object store, which is ours to
        // remove.
        if (Files.exists(group) && !FileUtil.deleteDir(group)) {
            LOGGER.warn(() -> LogUtil.message(
                    "Failed to delete the resolved copy of forwarded group {} at {}. It is disk left behind, "
                    + "cleared at the next start, rather than data at risk.", message.messageId(), group));
        }
    }

    /**
     * @return How many deliveries in a row have failed transiently; zero after a success.
     */
    int consecutiveFailures() {
        synchronized (backOff) {
            return consecutiveFailures;
        }
    }

    /**
     * @return When the next attempt may start, as epoch millis; zero when not backing off.
     */
    long notBeforeMillis() {
        return notBeforeMillis;
    }
}
