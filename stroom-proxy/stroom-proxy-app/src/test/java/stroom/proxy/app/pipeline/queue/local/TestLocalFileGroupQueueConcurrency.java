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
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Concurrency and id-allocation guarantees for {@link LocalFileGroupQueue}.
 * <p>
 * Sequence allocation was originally guarded by a {@link java.nio.channels.FileLock}
 * on {@code sequence.txt}. File locks are held per JVM rather than per thread, so
 * concurrent publishers hit {@code OverlappingFileLockException} and lost messages.
 * The allocator also trusted the persisted counter blindly, and because
 * {@code ATOMIC_MOVE} silently replaces its target, a counter that had regressed
 * would overwrite live queued messages without any error.
 * </p>
 */
class TestLocalFileGroupQueueConcurrency extends StroomUnitTest {

    private static final String QUEUE_NAME = "preAggregateInput";

    private static FileGroupQueueMessage message(final String fileGroupId) {
        return FileGroupQueueMessage.create(
                QUEUE_NAME,
                fileGroupId,
                FileStoreLocation.localFileSystem("receiveStore", Path.of("/tmp/store/0000000001")),
                "receive",
                "test-node",
                null,
                Map.of());
    }

    @Test
    void testConcurrentPublishersAllSucceed() throws Exception {
        final LocalFileGroupQueue queue =
                new LocalFileGroupQueue(QUEUE_NAME, getCurrentTestDir().resolve("queue"));

        final int threads = 8;
        final int perThread = 25;
        final CountDownLatch start = new CountDownLatch(1);
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            queue.publish(message("fg-" + threadId + "-" + i));
                        }
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
        assertThat(queue.getApproximatePendingCount()).isEqualTo((long) threads * perThread);
        assertThat(queue.getApproximateFailedCount()).isZero();
    }

    @Test
    void testConcurrentPublishAllocatesUniqueIds() throws Exception {
        final Path root = getCurrentTestDir().resolve("queue");
        final LocalFileGroupQueue queue = new LocalFileGroupQueue(QUEUE_NAME, root);

        final int threads = 8;
        final int perThread = 25;
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            queue.publish(message("fg-" + i));
                        }
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

        // Every publish must have produced its own file - no id reuse, no clobbering.
        try (final var stream = Files.list(root.resolve("pending"))) {
            assertThat(stream.count()).isEqualTo((long) threads * perThread);
        }
    }

    @Test
    void testConcurrentConsumersEachGetDistinctItems() throws Exception {
        final LocalFileGroupQueue queue =
                new LocalFileGroupQueue(QUEUE_NAME, getCurrentTestDir().resolve("queue"));

        final int total = 200;
        for (int i = 0; i < total; i++) {
            queue.publish(message("fg-" + i));
        }

        final int threads = 8;
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
                            final Optional<FileGroupQueueItem> item = queue.next();
                            if (item.isEmpty()) {
                                return;
                            }
                            claimed.add(item.get().getMessage().fileGroupId());
                            item.get().acknowledge();
                        }
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
        assertThat(claimed).hasSize(total);
        assertThat(claimed).doesNotHaveDuplicates();
        assertThat(queue.getApproximatePendingCount()).isZero();
        assertThat(queue.getApproximateInFlightCount()).isZero();
    }

    @Test
    void testRegressedSequenceCounterDoesNotDestroyQueuedMessages() throws Exception {
        final Path root = getCurrentTestDir().resolve("queue");

        final LocalFileGroupQueue first = new LocalFileGroupQueue(QUEUE_NAME, root);
        for (int i = 0; i < 5; i++) {
            first.publish(message("original-" + i));
        }
        assertThat(first.getApproximatePendingCount()).isEqualTo(5);

        // Simulate the counter being lost or restored out of step with the queue
        // contents - a wiped file, a partial restore, a fresh volume.
        Files.write(root.resolve("sequence.txt"), "0\n".getBytes(StandardCharsets.UTF_8));

        final LocalFileGroupQueue reopened = new LocalFileGroupQueue(QUEUE_NAME, root);
        for (int i = 0; i < 5; i++) {
            reopened.publish(message("new-" + i));
        }

        // The originals must survive alongside the new messages.
        assertThat(reopened.getApproximatePendingCount()).isEqualTo(10);

        final List<String> drained = drain(reopened);
        assertThat(drained).hasSize(10);
        assertThat(drained).contains(
                "original-0", "original-1", "original-2", "original-3", "original-4",
                "new-0", "new-1", "new-2", "new-3", "new-4");
    }

    @Test
    void testIdsRemainMonotonicAcrossRestart() throws Exception {
        final Path root = getCurrentTestDir().resolve("queue");

        final LocalFileGroupQueue first = new LocalFileGroupQueue(QUEUE_NAME, root);
        first.publish(message("a"));
        first.publish(message("b"));
        first.close();

        // Drain everything so a scan alone would restart ids from zero.
        assertThat(drain(first)).hasSize(2);

        final LocalFileGroupQueue reopened = new LocalFileGroupQueue(QUEUE_NAME, root);
        reopened.publish(message("c"));

        try (final var stream = Files.list(root.resolve("pending"))) {
            final String onlyId = stream.findFirst().orElseThrow().getFileName().toString();
            assertThat(onlyId).isEqualTo("00000000000000000003.json");
        }
    }

    private static List<String> drain(final LocalFileGroupQueue queue) throws IOException {
        final List<String> ids = new java.util.ArrayList<>();
        while (true) {
            final Optional<FileGroupQueueItem> item = queue.next();
            if (item.isEmpty()) {
                return ids;
            }
            ids.add(item.get().getMessage().fileGroupId());
            item.get().acknowledge();
        }
    }
}
