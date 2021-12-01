package stroom.task;


import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.test.AbstractCoreIntegrationTest;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

class TestTaskContextPerformance extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestTaskContextPerformance.class);

    @Inject
    private ExecutorProvider executorProvider;
    @Inject
    private TaskContextFactory taskContextFactory;

    @Test
    void testWithoutTaskContext() {
        final Executor executor = executorProvider.get();
        final AtomicInteger count = new AtomicInteger();

        final long start = System.nanoTime();
        CompletableFuture[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            final CompletableFuture future = CompletableFuture.runAsync(() -> {
                while (count.incrementAndGet() < 1000000000) {
                    // Do nothing.
                }
            }, executor);
            futures[i] = future;
        }
        CompletableFuture.allOf(futures).join();
        final long end = System.nanoTime();
        LOGGER.info("Elapsed = " + (end - start) + " nanos");
    }

    @Test
    void testWithTaskContext() {
        final Executor executor = executorProvider.get();
        final AtomicInteger count = new AtomicInteger();

        final long start = System.nanoTime();
        CompletableFuture[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            final CompletableFuture future = CompletableFuture.runAsync(() -> {
                while (count.incrementAndGet() < 1000000000) {
                    taskContextFactory.context("Test", tc -> {
                        // Do nothing.
                    }).run();
                }
            }, executor);
            futures[i] = future;
        }
        CompletableFuture.allOf(futures).join();
        final long end = System.nanoTime();
        LOGGER.info("Elapsed = " + (end - start) + " nanos");
    }

    @Test
    void testWithTaskContextTerminationCheck() {
        final Executor executor = executorProvider.get();
        final AtomicInteger count = new AtomicInteger();

        final long start = System.nanoTime();
        CompletableFuture[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            final CompletableFuture future = CompletableFuture.runAsync(() -> {
                taskContextFactory.context("Test", tc -> {
                    while (!tc.isTerminated() && count.incrementAndGet() < 1000000000) {
                        // Do nothing.
                    }
                }).run();
            }, executor);
            futures[i] = future;
        }
        CompletableFuture.allOf(futures).join();
        final long end = System.nanoTime();
        LOGGER.info("Elapsed = " + (end - start) + " nanos");
    }
}
