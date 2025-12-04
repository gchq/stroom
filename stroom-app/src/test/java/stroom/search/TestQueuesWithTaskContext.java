/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.search;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.search.extraction.Event;
import stroom.search.extraction.StreamEventMap;
import stroom.search.extraction.StreamEventMap.EventSet;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.api.TerminateHandlerFactory;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.concurrent.CompleteException;
import stroom.util.concurrent.UncheckedInterruptedException;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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

                                final CompletableFuture<?>[] consumers = new CompletableFuture[threads];

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
                                final CompletableFuture<?>[] producers = new CompletableFuture[threads];

                                for (int i = 0; i < threads; i++) {
                                    final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                                        boolean run = true;
                                        while (run && !tc.isTerminated()) {
                                            final int id = produced.incrementAndGet();
                                            if (id > MAX) {
                                                run = false;
                                            } else {
                                                try {
                                                    queue.put(new Event(1, id, Val.of(
                                                            ValString.create("test"),
                                                            ValString.create("test"))));
                                                } catch (final CompleteException e) {
                                                    throw new RuntimeException(e);
                                                }
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
