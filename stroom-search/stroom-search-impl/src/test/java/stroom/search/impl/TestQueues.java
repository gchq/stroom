package stroom.search.impl;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.query.api.v2.QueryKey;
import stroom.search.extraction.Event;
import stroom.search.extraction.StoredDataQueue;
import stroom.search.extraction.StreamEventMap;
import stroom.search.extraction.StreamEventMap.EventSet;
import stroom.search.impl.shard.DocIdQueue;
import stroom.search.impl.shard.ShardIdQueue;
import stroom.util.concurrent.CompleteException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestQueues {

    private static final long MAX = 1000;

    private ExecutorService executorService;

    @BeforeEach
    void beforeEach() {
        executorService = Executors.newCachedThreadPool();
    }

    @AfterEach
    void afterEach() {
        executorService.shutdown();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testShardIdQueue() {
        final int threads = 10;
        final ShardIdQueue queue = new ShardIdQueue(LongStream
                .rangeClosed(1, 1000)
                .boxed()
                .collect(Collectors.toList()));
        final AtomicLong consumed = new AtomicLong();

        final CompletableFuture<Void>[] futures = new CompletableFuture[threads];
        for (int i = 0; i < threads; i++) {
            final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                boolean complete = false;
                while (!complete) {
                    final Long id = queue.next();
                    if (id != null) {
                        consumed.incrementAndGet();
                    } else {
                        complete = true;
                    }
                }
            }, executorService);
            futures[i] = future;
        }
        CompletableFuture.allOf(futures).join();

        assertThat(consumed.get()).isEqualTo(1000);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDocIdQueue() {
        final int threads = 10;
        final DocIdQueue queue = new DocIdQueue(1000000);
        final AtomicInteger produced = new AtomicInteger();
        final AtomicInteger consumed = new AtomicInteger();

        // Producer.
        final CompletableFuture<Void>[] producers = new CompletableFuture[threads];
        for (int i = 0; i < threads; i++) {
            final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                boolean run = true;
                while (run) {
                    final int id = produced.incrementAndGet();
                    if (id > MAX) {
                        run = false;
                    } else {
                        queue.put(id);
                    }
                }
            }, executorService);
            producers[i] = future;
        }

        final CompletableFuture<Void>[] consumers = new CompletableFuture[threads];
        for (int i = 0; i < threads; i++) {
            final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                boolean done = false;
                while (!done) {
                    if (queue.take() == null) {
                        done = true;
                    } else {
                        consumed.incrementAndGet();
                    }
                }
            }, executorService);
            consumers[i] = future;
        }

        CompletableFuture.allOf(producers).join();
        for (int i = 0; i < threads; i++) {
            queue.complete();
        }
        CompletableFuture.allOf(consumers).join();

        assertThat(consumed.get()).isEqualTo(MAX);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStoredDataQueue() {
        final int threads = 10;
        final StoredDataQueue queue = new StoredDataQueue(new QueryKey(UUID.randomUUID().toString()), 1000000);
        final AtomicInteger produced = new AtomicInteger();
        final AtomicInteger consumed = new AtomicInteger();

        // Producer.
        final CompletableFuture<Void>[] producers = new CompletableFuture[threads];
        for (int i = 0; i < threads; i++) {
            final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                boolean run = true;
                while (run) {
                    final int id = produced.incrementAndGet();
                    if (id > MAX) {
                        run = false;
                    } else {
                        queue.put(new Val[]{ValString.create("test"), ValString.create("test")});
                    }
                }
            }, executorService);
            producers[i] = future;
        }

        final CompletableFuture<Void>[] consumers = new CompletableFuture[threads];
        for (int i = 0; i < threads; i++) {
            final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                boolean done = false;
                while (!done) {
                    if (queue.take() == null) {
                        done = true;
                    } else {
                        consumed.incrementAndGet();
                    }
                }
            }, executorService);
            consumers[i] = future;
        }

        CompletableFuture.allOf(producers).join();
        for (int i = 0; i < threads; i++) {
            queue.complete();
        }
        CompletableFuture.allOf(consumers).join();

        assertThat(consumed.get()).isEqualTo(MAX);
    }

    @Test
    @RepeatedTest(100000)
    @SuppressWarnings("unchecked")
    void testStreamEventMap() {
        final int threads = 10;
        final StreamEventMap queue = new StreamEventMap(1000000);
        final AtomicInteger produced = new AtomicInteger();
        final AtomicInteger consumed = new AtomicInteger();

        // Producer.
        final CompletableFuture<Void>[] producers = new CompletableFuture[threads];
        for (int i = 0; i < threads; i++) {
            final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                boolean run = true;
                while (run) {
                    final int id = produced.incrementAndGet();
                    if (id > MAX) {
                        run = false;
                    } else {
                        queue.put(new Event(1, id,
                                new Val[]{ValString.create("test"), ValString.create("test")}));
                    }
                }
            }, executorService);
            producers[i] = future;
        }

        final CompletableFuture<Void>[] consumers = new CompletableFuture[threads];
        for (int i = 0; i < threads; i++) {
            final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    while (true) {
                        final EventSet eventSet = queue.take();
                        if (eventSet != null) {
                            consumed.addAndGet(eventSet.size());
                        }
                    }
                } catch (final CompleteException e) {
                    // Ignore.
                }
            }, executorService);
            consumers[i] = future;
        }

        CompletableFuture.allOf(producers).join();
        for (int i = 0; i < threads; i++) {
            queue.complete();
        }
        CompletableFuture.allOf(consumers).join();

        assertThat(consumed.get()).isEqualTo(MAX);
    }
}
