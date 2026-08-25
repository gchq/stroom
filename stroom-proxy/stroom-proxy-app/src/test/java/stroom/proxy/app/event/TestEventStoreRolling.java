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

package stroom.proxy.app.event;

import stroom.cache.impl.CacheManagerImpl;
import stroom.meta.api.AttributeMap;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.handler.ReceiverFactory;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.store.FileStores;
import stroom.security.mock.MockCommonSecurityContext;
import stroom.test.common.MockMetrics;
import stroom.util.concurrent.UniqueId;
import stroom.util.concurrent.UniqueId.NodeType;
import stroom.util.metrics.Metrics;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rolling must not block inside {@code stores.compute()}.
 * <p>
 * The forward queue is bounded, so {@code put} blocks once it is full. That is the
 * intended backpressure, but it used to happen inside the {@code ConcurrentHashMap}
 * remapping function, which holds that bin's lock - so a stalled forwarder froze
 * every other feed whose {@link FeedKey} hashed to the same bin, not just the feed
 * being rolled. {@code ConcurrentHashMap} explicitly requires the remapping function
 * to be short and non-blocking.
 * </p>
 */
class TestEventStoreRolling {

    private static EventStoreConfig eventStoreConfig(final Long maxEventCount,
                                                     final int forwardQueueSize) {
        return new EventStoreConfig(null, null, maxEventCount, null, null, forwardQueueSize);
    }

    private static EventStore newEventStore(final Path dir, final EventStoreConfig config) {
        final Metrics metrics = new MockMetrics();
        final DataDirProvider dataDirProvider = () -> dir;
        return new EventStore(
                Mockito.mock(ReceiverFactory.class),
                MockCommonSecurityContext.getInstance(),
                () -> config,
                dataDirProvider,
                new FileStores(metrics),
                new CacheManagerImpl(() -> metrics),
                metrics);
    }

    private static void consume(final EventStore eventStore,
                                final FeedKey feedKey,
                                final int sequence) {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Feed", feedKey.feed());
        attributeMap.put("Type", feedKey.type());
        eventStore.consume(
                attributeMap,
                new UniqueId(System.currentTimeMillis(), sequence, NodeType.PROXY, "test-proxy"),
                "event-" + sequence);
    }

    @Test
    void testAWriterBlockedOnAFullForwardQueueDoesNotHoldTheMapLock() throws Exception {
        final Path dir = Files.createTempDirectory("stroom-roll");

        // Roll on every event with a forward queue of one, and nothing draining it, so
        // the second roll blocks. What matters is *where* it blocks.
        final EventStore eventStore = newEventStore(dir, eventStoreConfig(1L, 1));

        final AtomicInteger written = new AtomicInteger();
        final Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    consume(eventStore, new FeedKey("Test", "Raw Events"), i);
                    written.incrementAndGet();
                }
            } catch (final RuntimeException e) {
                // Interrupted during shutdown - expected.
            }
        }, "event-writer");
        writer.setDaemon(true);
        writer.start();

        try {
            // Wait for it to wedge on the full queue.
            final long deadline = System.currentTimeMillis() + 30_000;
            StackTraceElement[] stack = new StackTraceElement[0];
            boolean blockedOnPut = false;
            while (System.currentTimeMillis() < deadline && !blockedOnPut) {
                stack = writer.getStackTrace();
                blockedOnPut = containsFrame(stack, "LinkedBlockingQueue", "put");
                if (!blockedOnPut) {
                    Thread.sleep(20);
                }
            }

            assertThat(blockedOnPut)
                    .as("writer should end up blocked enqueuing to the full forward queue")
                    .isTrue();

            // The point of the fix: it blocks in put(), not inside the map's remapping
            // function. Blocking there holds the bin lock and stalls unrelated feeds.
            assertThat(containsFrame(stack, "ConcurrentHashMap", "compute"))
                    .as("blocked writer must not be inside ConcurrentHashMap.compute; stack was:\n"
                        + format(stack))
                    .isFalse();

            assertThat(written.get())
                    .as("some events were written before the queue filled")
                    .isGreaterThan(0);
        } finally {
            writer.interrupt();
            writer.join(10_000);
        }
    }

    private static boolean containsFrame(final StackTraceElement[] stack,
                                         final String classNamePart,
                                         final String methodName) {
        for (final StackTraceElement e : stack) {
            if (e.getClassName().contains(classNamePart) && e.getMethodName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private static String format(final StackTraceElement[] stack) {
        final StringBuilder sb = new StringBuilder();
        for (final StackTraceElement e : stack) {
            sb.append("    at ").append(e).append('\n');
        }
        return sb.toString();
    }

    @Test
    void testRolledFilesReachTheForwardQueue() throws Exception {
        final Path dir = Files.createTempDirectory("stroom-roll");
        final Path eventDir = dir.resolve("event");

        final EventStoreConfig config = eventStoreConfig(1L, 100);
        final EventStore eventStore = newEventStore(dir, config);
        final FeedKey feedKey = new FeedKey("Test", "Raw Events");

        for (int i = 0; i < 5; i++) {
            consume(eventStore, feedKey, i);
        }

        // Rolling out of compute() must still produce the files on disk.
        assertThat(Files.isDirectory(eventDir)).isTrue();
        try (final var stream = Files.list(eventDir)) {
            assertThat(stream.count())
                    .as("each rolled event produces a file")
                    .isGreaterThan(0);
        }
    }

    @Test
    void testRollClosesEveryOpenAppender() throws Exception {
        final Path dir = Files.createTempDirectory("stroom-roll");
        final EventStoreConfig config = eventStoreConfig(null, 100);
        final EventStore eventStore = newEventStore(dir, config);

        for (int f = 0; f < 4; f++) {
            consume(eventStore, new FeedKey("Feed" + f, "Raw Events"), f);
        }

        // roll() enqueues outside compute() too; it must not lose or duplicate files.
        eventStore.roll();

        try (final var stream = Files.list(dir.resolve("event"))) {
            assertThat(stream.count())
                    .as("one rolled file per feed")
                    .isEqualTo(4);
        }
    }

    @Test
    void testForwardNextHandlesOneFileAndReturns() throws Exception {
        final Path dir = Files.createTempDirectory("stroom-roll");
        final EventStoreConfig config = eventStoreConfig(null, 100);
        final EventStore eventStore = newEventStore(dir, config);

        consume(eventStore, new FeedKey("Test", "Raw Events"), 0);
        eventStore.roll();

        // forwardNext() must return after a single file rather than looping forever -
        // it is driven by a ParallelExecutor which re-invokes it.
        final ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            final var future = pool.submit(eventStore::forwardNext);
            future.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }
}
