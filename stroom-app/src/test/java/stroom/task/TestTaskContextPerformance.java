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

package stroom.task;


import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@Disabled
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
        final CompletableFuture[] futures = new CompletableFuture[10];
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
        final CompletableFuture[] futures = new CompletableFuture[10];
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
        final CompletableFuture[] futures = new CompletableFuture[10];
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
