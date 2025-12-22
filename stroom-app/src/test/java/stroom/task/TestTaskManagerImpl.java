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
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.api.TaskTerminatedException;
import stroom.task.shared.TaskId;
import stroom.task.shared.ThreadPool;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TestTaskManagerImpl extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestTaskManagerImpl.class);

    @Inject
    private ExecutorProvider executorProvider;
    @Inject
    private TaskContextFactory taskContextFactory;
    @Inject
    private TaskManager taskManager;

    @Test
    void testCompletedExceptionally() {
        testCompletedExceptionally(executorProvider, false);
    }

    private void testCompletedExceptionally(final ExecutorProvider executorProvider, final boolean nested) {
        final AtomicBoolean terminated = new AtomicBoolean();
        final AtomicBoolean completedNormally = new AtomicBoolean();
        final AtomicBoolean completedExceptionally = new AtomicBoolean();
        RuntimeException exception = null;

        try {
            final Executor executor = executorProvider.get();
            final Runnable runnable = taskContextFactory.context("Running task", taskContext -> {
                if (Thread.currentThread().isInterrupted()) {
                    terminated.set(true);
                }
                throw new RuntimeException("Expected");
            });
            CompletableFuture.runAsync(runnable, executor)
                    .thenRun(() -> completedNormally.set(true))
                    .exceptionally(t -> {
                        completedExceptionally.set(true);
                        return null;
                    })
                    .join();
        } catch (final RuntimeException t) {
            exception = t;
            LOGGER.error(t.getMessage(), t);
            completedExceptionally.set(true);
        }

        assertThat(completedNormally.get()).isFalse();
        assertThat(completedExceptionally.get()).isTrue();

        if (terminated.get()) {
            throw new TaskTerminatedException();
        }

        if (nested && (exception != null || completedExceptionally.get())) {
            if (completedExceptionally.get()) {
                throw new RuntimeException("Error");
            }
            throw exception;
        }
    }

    @Test
    void testCompletedNormally() {
        final AtomicBoolean completedNormally = new AtomicBoolean();
        final AtomicBoolean completedExceptionally = new AtomicBoolean();

        final Executor executor = executorProvider.get();

        final Runnable runnable = taskContextFactory.context("Running task", taskContext -> {
        });
        CompletableFuture.runAsync(runnable, executor)
                .thenRun(() -> completedNormally.set(true))
                .exceptionally(t -> {
                    completedExceptionally.set(true);
                    return null;
                })
                .join();

        assertThat(completedNormally.get()).isTrue();
        assertThat(completedExceptionally.get()).isFalse();
    }

    @Test
    void testCompletedExceptionallyNested() {
        final AtomicBoolean completedNormally = new AtomicBoolean();
        final AtomicBoolean completedExceptionally = new AtomicBoolean();

        final Executor executor = executorProvider.get();

        final Runnable runnable = taskContextFactory.context("Running task", taskContext -> {
            terminateCurrentTask(taskContext);
            testCompletedExceptionally(executorProvider, true);
        });

        CompletableFuture.runAsync(runnable, executor)
                .thenRun(() -> completedNormally.set(true))
                .exceptionally(t -> {
                    completedExceptionally.set(true);
                    return null;
                })
                .join();

        assertThat(completedNormally.get()).isFalse();
        assertThat(completedExceptionally.get()).isTrue();
    }

    private void testCompletedExceptionallyNested(final ExecutorProvider executorProviderOuter,
                                                  final ExecutorProvider executorProviderInner) {
        final AtomicBoolean completedNormally = new AtomicBoolean();
        final AtomicBoolean completedExceptionally = new AtomicBoolean();

        final Executor executor = executorProviderOuter.get();
        final Runnable runnable = taskContextFactory.context("Running task", taskContext -> {
            terminateCurrentTask(taskContext);
            testCompletedExceptionally(executorProviderInner, true);
        });

        CompletableFuture.runAsync(runnable, executor)
                .thenRun(() -> completedNormally.set(true))
                .exceptionally(t -> {
                    completedExceptionally.set(true);
                    return null;
                })
                .join();

        assertThat(completedNormally.get()).isFalse();
        assertThat(completedExceptionally.get()).isTrue();
    }

    @Test
    void testCompletedNormallyNested() {
        final AtomicBoolean completedNormally = new AtomicBoolean();
        final AtomicBoolean completedExceptionally = new AtomicBoolean();

        final Executor executor = executorProvider.get();
        final Runnable runnable = taskContextFactory.context("Running task",
                taskContext -> testCompletedNormally());

        CompletableFuture.runAsync(runnable, executor)
                .thenRun(() -> completedNormally.set(true))
                .exceptionally(t -> {
                    completedExceptionally.set(true);
                    return null;
                })
                .join();

        assertThat(completedNormally.get()).isTrue();
        assertThat(completedExceptionally.get()).isFalse();
    }

    @Test
    void testRejectedExecution() {
        testCompletedExceptionally(createRejectedExecutorProvider(), false);
    }

    @Test
    void testRejectedNestedExecution() {
        testCompletedExceptionallyNested(executorProvider, createRejectedExecutorProvider());
    }

    private ExecutorProvider createRejectedExecutorProvider() {
        final Executor executor = command -> {
            throw new RejectedExecutionException("Expected");
        };
        return new ExecutorProvider() {
            @Override
            public Executor get() {
                return executor;
            }

            @Override
            public Executor get(final ThreadPool threadPool) {
                return executor;
            }
        };
    }

    private void terminateCurrentTask(final TaskContext taskContext) {
        final TaskId taskId = taskContext.getTaskId();
        taskManager.terminate(taskId);
    }
}
