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

package stroom.proxy.app.pipeline.stage;

import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStagePublisher;
import stroom.proxy.app.pipeline.store.local.LocalFileStore;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Concurrent receives must all succeed, and must not leave file groups committed to
 * the store with no queue message referencing them.
 * <p>
 * The publisher commits to the file store before publishing, so a failing publish
 * used to orphan the committed data permanently - nothing ever sweeps the store.
 * </p>
 */
class TestReceiveStagePublisherConcurrency extends StroomUnitTest {

    @Test
    void testConcurrentReceivesAllSucceedAndOrphanNothing() throws Exception {
        final Path base = getCurrentTestDir();
        final LocalFileStore receiveStore = new LocalFileStore("receiveStore", base.resolve("store"));
        final LocalFileGroupQueue outputQueue =
                new LocalFileGroupQueue("preAggregateInput", base.resolve("queue"));

        final ReceiveStagePublisher publisher = new ReceiveStagePublisher(
                receiveStore, outputQueue, null, "test-node", 5);

        final int concurrent = 20;
        final CountDownLatch start = new CountDownLatch(1);
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final ExecutorService pool = Executors.newFixedThreadPool(concurrent);

        try {
            for (int i = 0; i < concurrent; i++) {
                final int id = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        publisher.accept(createReceivedDir(base, id));
                    } catch (final Throwable e) {
                        errors.add(e);
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
        assertThat(outputQueue.getApproximatePendingCount()).isEqualTo(concurrent);
        assertThat(countCommittedFileGroups(base.resolve("store"))).isEqualTo(concurrent);
    }

    @Test
    void testConcurrencyIsBoundedByMaxConcurrentReceives() throws Exception {
        final Path base = getCurrentTestDir();
        final LocalFileStore receiveStore = new LocalFileStore("receiveStore", base.resolve("store"));
        final LocalFileGroupQueue outputQueue =
                new LocalFileGroupQueue("preAggregateInput", base.resolve("queue"));

        final int limit = 3;
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();

        // Observe concurrency through the store, which is entered inside the semaphore.
        final LocalFileStore observedStore = new LocalFileStore("receiveStore", base.resolve("store")) {
            @Override
            public stroom.proxy.app.pipeline.store.FileStoreWrite newWrite() throws IOException {
                final int now = inFlight.incrementAndGet();
                peak.accumulateAndGet(now, Math::max);
                try {
                    Thread.sleep(25);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try {
                    return super.newWrite();
                } finally {
                    inFlight.decrementAndGet();
                }
            }
        };

        final ReceiveStagePublisher publisher = new ReceiveStagePublisher(
                observedStore, outputQueue, null, "test-node", limit);

        final int concurrent = 15;
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(concurrent);

        try {
            for (int i = 0; i < concurrent; i++) {
                final int id = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        publisher.accept(createReceivedDir(base, id));
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(peak.get())
                .as("peak concurrent receives must not exceed maxConcurrentReceives")
                .isLessThanOrEqualTo(limit);
        assertThat(outputQueue.getApproximatePendingCount()).isEqualTo(concurrent);

        // Unused, but keeps the plain store referenced for symmetry with the fixture.
        assertThat(receiveStore.getName()).isEqualTo("receiveStore");
    }

    @Test
    void testInvalidMaxConcurrentReceivesIsRejected() throws Exception {
        final Path base = getCurrentTestDir();
        final LocalFileStore receiveStore = new LocalFileStore("receiveStore", base.resolve("store"));
        final LocalFileGroupQueue outputQueue =
                new LocalFileGroupQueue("preAggregateInput", base.resolve("queue"));

        assertThatThrownBy(() -> new ReceiveStagePublisher(
                receiveStore, outputQueue, null, "test-node", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrentReceives");
    }

    private static Path createReceivedDir(final Path base, final int id) throws IOException {
        final Path received = base.resolve("received-" + id);
        Files.createDirectories(received);
        Files.writeString(received.resolve("proxy.meta"), "Feed:TEST\n");
        Files.writeString(received.resolve("proxy.zip"), "zip-" + id);
        Files.writeString(received.resolve("proxy.entries"), "TEST,RAW_EVENTS,1\n");
        return received;
    }

    private static long countCommittedFileGroups(final Path storeRoot) throws IOException {
        long count = 0;
        try (final Stream<Path> writers = Files.list(storeRoot)) {
            for (final Path writerDir : writers.filter(Files::isDirectory).toList()) {
                if ("writing".equals(writerDir.getFileName().toString())) {
                    continue;
                }
                try (final Stream<Path> groups = Files.list(writerDir)) {
                    count += groups.filter(Files::isDirectory).count();
                }
            }
        }
        return count;
    }
}
