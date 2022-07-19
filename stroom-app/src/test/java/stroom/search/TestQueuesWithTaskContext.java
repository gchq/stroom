package stroom.search;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.search.extraction.Event;
import stroom.search.extraction.StreamEventMap;
import stroom.search.extraction.StreamEventMap.EventSet;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.api.TerminateHandlerFactory;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.concurrent.CompleteException;
import stroom.util.concurrent.UncheckedInterruptedException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

public class TestQueuesWithTaskContext extends AbstractCoreIntegrationTest {

    private static final long MAX = 1000;

    @Inject
    private TaskContextFactory taskContextFactory;
    @Inject
    private TaskManager taskManager;

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
    void testTaskContext() {
        final int threads = 10;
        final StreamEventMap queue = new StreamEventMap(1000000);
        final AtomicInteger produced = new AtomicInteger();
        final AtomicInteger consumed = new AtomicInteger();
        final CountDownLatch productionCompleteLatch = new CountDownLatch(1);
        final CountDownLatch consumptionCompleteLatch = new CountDownLatch(1);
        final CountDownLatch consumptionStartLatch = new CountDownLatch(1);

        taskContextFactory.context(
                "parent",
                TerminateHandlerFactory.NOOP_FACTORY,
                taskContext -> {

                    // Start consumers.
                    executorService.execute(taskContextFactory.childContext(
                            taskContext,
                            "consumers",
                            TerminateHandlerFactory.NOOP_FACTORY,
                            tc -> {
                                taskManager.terminate(taskContext.getTaskId());
                                consumptionStartLatch.countDown();

                                final CompletableFuture<Void>[] consumers = new CompletableFuture[threads];

                                for (int i = 0; i < threads; i++) {
                                    final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                                        try {
                                            while (!tc.isTerminated()) {
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
                                CompletableFuture.allOf(consumers).join();
                                consumptionCompleteLatch.countDown();
                            }));

                    // Wait for consumption to start.
                    try {
                        consumptionStartLatch.await();
                    } catch (final InterruptedException e) {
                        throw UncheckedInterruptedException.create(e);
                    }

                    // Start producers.
                    executorService.execute(taskContextFactory.childContext(
                            taskContext,
                            "producers",
                            TerminateHandlerFactory.NOOP_FACTORY,
                            tc -> {
                                final CompletableFuture<Void>[] producers = new CompletableFuture[threads];

                                for (int i = 0; i < threads; i++) {
                                    final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                                        boolean run = true;
                                        while (run && !tc.isTerminated()) {
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

                                CompletableFuture.allOf(producers).join();
                                for (int i = 0; i < threads; i++) {
                                    queue.complete();
                                }

                                productionCompleteLatch.countDown();
                            }));

                    try {
                        productionCompleteLatch.await();
                        consumptionCompleteLatch.await();
                    } catch (final InterruptedException e) {
                        throw UncheckedInterruptedException.create(e);
                    }

                }).run();
    }
}
