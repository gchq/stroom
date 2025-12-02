package stroom.index.lucene;

import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocIdQueue extends StroomUnitTest {

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
}
