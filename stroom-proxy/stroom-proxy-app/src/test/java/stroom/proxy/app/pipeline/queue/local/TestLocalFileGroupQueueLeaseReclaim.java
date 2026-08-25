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

package stroom.proxy.app.pipeline.queue.local;

import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recovering work that a consumer took and then walked away from.
 * <p>
 * {@code FileGroupQueueWorker} logs and rethrows if {@code acknowledge()} or
 * {@code fail()} throws, which leaves the message sitting in {@code in-flight}
 * with nobody left to finish it. Recovery used to happen only in this class's
 * constructor, so that work stopped until somebody restarted the proxy. The
 * queue now reclaims such messages while running.
 * </p>
 * <p>
 * This is deliberately <strong>not</strong> a visibility timeout. The local
 * queue is confined to one process, so it can know exactly which in-flight
 * messages are still held by a live consumer rather than guessing from elapsed
 * time - which means it can never take work away from a consumer that is merely
 * slow. SQS and Kafka have to guess, and their tuning problems come from that.
 * </p>
 */
class TestLocalFileGroupQueueLeaseReclaim extends StroomUnitTest {

    /**
     * Scan on every empty poll, so the tests do not have to wait out the
     * production interval.
     */
    private LocalFileGroupQueue eagerQueue(final String name) throws IOException {
        return new LocalFileGroupQueue(
                name,
                getCurrentTestDir().resolve(name),
                new FileGroupQueueMessageCodec(),
                Duration.ZERO);
    }

    private static FileGroupQueueMessage message(final LocalFileGroupQueue queue,
                                                 final String fileGroupId) {
        return FileGroupQueueMessage.create(
                queue.getName(),
                fileGroupId,
                FileStoreLocation.localFileSystem("store", Path.of("/tmp", fileGroupId)),
                "test",
                "test-node",
                null,
                Map.of());
    }

    @Test
    void testAnAbandonedLeaseIsReturnedToPending() throws Exception {
        final LocalFileGroupQueue queue = eagerQueue("abandoned");
        queue.publish(message(queue, "fg-1"));

        // Take it and walk away - no acknowledge(), no fail().
        final FileGroupQueueItem leased = queue.next().orElseThrow();
        leased.close();

        assertThat(queue.getActiveLeaseCount())
                .as("closing the item releases the lease")
                .isZero();

        final Optional<FileGroupQueueItem> redelivered = queue.next();

        assertThat(redelivered).isPresent();
        assertThat(redelivered.orElseThrow().getMessage().fileGroupId()).isEqualTo("fg-1");
    }

    @Test
    void testALeaseHeldByALiveConsumerIsNotReclaimed() throws Exception {
        final LocalFileGroupQueue queue = eagerQueue("held");
        queue.publish(message(queue, "fg-1"));

        final FileGroupQueueItem held = queue.next().orElseThrow();

        assertThat(queue.getActiveLeaseCount()).isEqualTo(1);
        assertThat(queue.reclaimAbandonedLeases())
                .as("a consumer that is merely slow must keep its work")
                .isZero();
        assertThat(queue.getApproximateInFlightCount()).isEqualTo(1);

        held.acknowledge();
        held.close();
    }

    @Test
    void testAnAcknowledgedItemIsNotResurrected() throws Exception {
        final LocalFileGroupQueue queue = eagerQueue("acked");
        queue.publish(message(queue, "fg-1"));

        try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
            item.acknowledge();
        }

        assertThat(queue.reclaimAbandonedLeases()).isZero();
        assertThat(queue.next()).isEmpty();
        assertThat(queue.getApproximateInFlightCount()).isZero();
        assertThat(queue.getApproximatePendingCount()).isZero();
    }

    @Test
    void testAFailedItemGoesBackToPendingExactlyOnce() throws Exception {
        final LocalFileGroupQueue queue = eagerQueue("failed");
        queue.publish(message(queue, "fg-1"));

        try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
            item.fail(new IllegalStateException("processing blew up"));
        }

        // fail() already returned it, so there is nothing left to reclaim.
        assertThat(queue.reclaimAbandonedLeases()).isZero();
        assertThat(queue.getApproximatePendingCount()).isEqualTo(1);
        assertThat(queue.getApproximateInFlightCount()).isZero();
    }

    @Test
    void testAnAbandonedLeaseThatWouldCollideWithPendingIsQuarantined() throws Exception {
        final LocalFileGroupQueue queue = eagerQueue("collide");
        queue.publish(message(queue, "fg-1"));

        final FileGroupQueueItem leased = queue.next().orElseThrow();

        // Put a file back in pending under the same name, so returning the
        // in-flight one would overwrite it.
        final Path inFlightFile = Files.list(queue.getInFlightDir()).findFirst().orElseThrow();
        Files.copy(inFlightFile, queue.getPendingDir().resolve(inFlightFile.getFileName()));

        leased.close();

        assertThat(queue.reclaimAbandonedLeases()).isEqualTo(1);
        assertThat(queue.getApproximateInFlightCount()).isZero();
        assertThat(queue.getApproximatePendingCount())
                .as("the pending copy is left alone")
                .isEqualTo(1);
        assertThat(queue.getApproximateFailedCount())
                .as("the colliding copy is quarantined rather than overwriting it")
                .isEqualTo(1);
    }

    @Test
    void testTheScanIsRateLimitedSoIdlePollingIsCheap() throws Exception {
        final LocalFileGroupQueue queue = new LocalFileGroupQueue(
                "rate-limited",
                getCurrentTestDir().resolve("rate-limited"),
                new FileGroupQueueMessageCodec(),
                Duration.ofHours(1));

        // Use up the first scan, which always runs.
        assertThat(queue.next()).isEmpty();

        queue.publish(message(queue, "fg-1"));
        queue.next().orElseThrow().close();

        assertThat(queue.next())
                .as("an empty poll inside the interval must not pay for a scan")
                .isEmpty();
        assertThat(queue.getApproximateInFlightCount()).isEqualTo(1);

        // The work is not lost, just not noticed yet.
        assertThat(queue.reclaimAbandonedLeases()).isEqualTo(1);
        assertThat(queue.getApproximatePendingCount()).isEqualTo(1);
    }

    /**
     * Reclaiming must not race the claiming.
     * <p>
     * Every consumer that sees a pending file claims its lease before attempting
     * the move, because the reclaim scan has to be blocked from the instant the
     * in-flight file could appear. Only the consumer that wins the move owns the
     * lease though - when a loser also released it, the winner's claim vanished
     * and the very next scan handed its live item to somebody else, delivering it
     * twice.
     * </p>
     * <p>
     * Detection is probabilistic - it needs a lost claim race and a scan to land
     * in the same short window. Measured against the unfixed code, one round at
     * eight threads caught it about one run in three; the four rounds at twelve
     * threads used here caught it about two in three. A pass is therefore good
     * evidence rather than proof; a failure is conclusive.
     * </p>
     */
    @RepeatedTest(4)
    void testConcurrentConsumersDoNotStealEachOthersLeases(final RepetitionInfo repetitionInfo) throws Exception {
        final LocalFileGroupQueue queue = eagerQueue("lease-race-" + repetitionInfo.getCurrentRepetition());

        final int total = 300;
        for (int i = 0; i < total; i++) {
            queue.publish(message(queue, "fg-" + i));
        }

        final int threads = 12;
        final CountDownLatch start = new CountDownLatch(1);
        final List<String> claimed = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        while (true) {
                            final Optional<FileGroupQueueItem> next = queue.next();
                            if (next.isEmpty()) {
                                return null;
                            }
                            try (final FileGroupQueueItem item = next.get()) {
                                claimed.add(item.getMessage().fileGroupId());
                                item.acknowledge();
                            }
                        }
                    } catch (final Throwable e) {
                        errors.add(e);
                        return null;
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(errors).isEmpty();
        assertThat(claimed)
                .as("a scan running alongside the consumers must not redeliver live work")
                .hasSize(total)
                .doesNotHaveDuplicates();
        assertThat(queue.getApproximateInFlightCount()).isZero();
        assertThat(queue.getApproximatePendingCount()).isZero();
    }

    @Test
    void testAnAcknowledgementFailureNoLongerStrandsTheItem() throws Exception {
        final LocalFileGroupQueue queue = eagerQueue("ack-fails");
        queue.publish(message(queue, "fg-1"));

        // Exactly what the worker does when acknowledge() throws: log, rethrow,
        // and close the item in a finally.
        final FileGroupQueueItem item = queue.next().orElseThrow();
        try {
            throw new IOException("acknowledgement could not be persisted");
        } catch (final IOException expected) {
            // The worker rethrows; nothing marks the item complete.
        } finally {
            item.close();
        }

        assertThat(queue.getApproximateInFlightCount())
                .as("the item is still in flight immediately after the failure")
                .isEqualTo(1);

        final Optional<FileGroupQueueItem> redelivered = queue.next();

        assertThat(redelivered)
                .as("but the queue reclaims it without needing a restart")
                .isPresent();
        assertThat(redelivered.orElseThrow().getMessage().fileGroupId()).isEqualTo("fg-1");
    }

    /**
     * Audit ledger H8. The reclaim scan used to be reachable only from the empty-poll branch of
     * {@code next()}, so a queue that always had something pending never ran it and an in-flight item
     * whose lease was released without an acknowledge or a fail stayed in {@code in-flight/} for the
     * life of the process. That is the busy-system case, which is why a quiet test never caught it.
     */
    @Test
    void testAnAbandonedLeaseIsReclaimedEvenWhileTheQueueIsBacklogged() throws Exception {
        final LocalFileGroupQueue queue = eagerQueue("backlogged");
        queue.publish(message(queue, "fg-1"));
        queue.publish(message(queue, "fg-2"));

        // Take fg-1 and walk away. fg-2 stays pending, so the queue is never empty from here on.
        final FileGroupQueueItem abandoned = queue.next().orElseThrow();
        assertThat(abandoned.getMessage().fileGroupId()).isEqualTo("fg-1");
        abandoned.close();

        assertThat(queue.getApproximateInFlightCount())
                .as("fg-1 is in-flight with no holder")
                .isEqualTo(1);
        assertThat(queue.getApproximatePendingCount())
                .as("and the queue still has work, so an empty poll never happens")
                .isEqualTo(1);

        final FileGroupQueueItem reclaimed = queue.next().orElseThrow();

        assertThat(reclaimed.getMessage().fileGroupId())
                .as("the abandoned item is recovered rather than stranded behind the backlog")
                .isEqualTo("fg-1");
        reclaimed.acknowledge();

        final FileGroupQueueItem remaining = queue.next().orElseThrow();
        assertThat(remaining.getMessage().fileGroupId()).isEqualTo("fg-2");
        remaining.acknowledge();

        assertThat(queue.getApproximateInFlightCount()).isZero();
        assertThat(queue.getApproximatePendingCount()).isZero();
    }

}
