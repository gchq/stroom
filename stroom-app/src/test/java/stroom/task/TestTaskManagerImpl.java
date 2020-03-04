package stroom.task;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.task.api.TaskTerminatedException;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.impl.CurrentTaskState;
import stroom.task.shared.TaskId;
import stroom.task.shared.ThreadPool;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestTaskManagerImpl extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTaskManagerImpl.class);

    @Inject
    private ExecutorProvider executorProvider;
    @Inject
    private Provider<TaskContext> taskContextProvider;
    @Inject
    private TaskManager taskManager;

    @Test
    void testMoreItemsThanThreads_boundedPool() throws ExecutionException, InterruptedException {
        LOGGER.info("Starting");
        int poolSize = 4;
        ThreadPool threadPool = new ThreadPoolImpl(getClass().getName(),
                3,
                poolSize,
                poolSize);

        final Executor executor = executorProvider.get(threadPool);
        final TaskContext taskContext = taskContextProvider.get();

        CompletableFuture.runAsync(taskContext.subTask(() ->
                LOGGER.info("Warming up thread pool"))).get();

        AtomicInteger counter = new AtomicInteger();
        final Queue<Thread> threadsUsed = new ConcurrentLinkedQueue<>();

        final int taskCount = 10;
        CompletableFuture[] futures = IntStream.rangeClosed(1, taskCount)
                .mapToObj(i ->
                        CompletableFuture.runAsync(taskContext.subTask(() -> {
                                    try {
                                        Thread.sleep(50);
                                        LOGGER.info("Running task {}", i);
                                        counter.incrementAndGet();
                                        threadsUsed.add(Thread.currentThread());
                                    } catch (final InterruptedException e) {
                                        LOGGER.error(e.getMessage(), e);

                                        // Continue to interrupt this thread.
                                        Thread.currentThread().interrupt();
                                    }
                                }),
                                executor))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get();

        assertThat(counter.get()).isEqualTo(taskCount);

        long distinctThreads = threadsUsed.stream()
                .distinct()
                .count();

        LOGGER.info("Threads used: {}", distinctThreads);

        assertThat(distinctThreads <= poolSize).isTrue();

        LOGGER.info("Finished");
    }

    @Test
    void testMoreItemsThanThreads_unBoundedPool() throws ExecutionException, InterruptedException {

        LOGGER.info("Starting");
        int poolSize = Integer.MAX_VALUE; //unbounded
        ThreadPool threadPool = new ThreadPoolImpl(this.getClass().getName(),
                3,
                0,
                poolSize);

        final Executor executor = executorProvider.get(threadPool);
        final TaskContext taskContext = taskContextProvider.get();

        AtomicInteger counter = new AtomicInteger();
        final Queue<Thread> threadsUsed = new ConcurrentLinkedQueue<>();

        final int taskCount = 10;
        CompletableFuture[] futures = IntStream.rangeClosed(1, taskCount)
                .mapToObj(i ->
                        CompletableFuture.runAsync(taskContext.subTask(() -> {
                                    try {
                                        Thread.sleep(50);
                                        LOGGER.info("Running task {}", i);
                                        counter.incrementAndGet();
                                        threadsUsed.add(Thread.currentThread());
                                    } catch (final InterruptedException e) {
                                        LOGGER.error(e.getMessage(), e);

                                        // Continue to interrupt this thread.
                                        Thread.currentThread().interrupt();
                                    }
                                }),
                                executor))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get();

        assertThat(counter.get()).isEqualTo(taskCount);

        long distinctThreads = threadsUsed.stream()
                .distinct()
                .count();

        LOGGER.info("Threads used: {}", distinctThreads);

        assertThat(distinctThreads <= poolSize).isTrue();
        LOGGER.info("Finished");
    }

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
            final Runnable runnable = () -> {
                if (Thread.currentThread().isInterrupted()) {
                    terminated.set(true);
                }
                throw new RuntimeException("Expected");
            };

            CompletableFuture.runAsync(taskContextProvider.get().subTask(runnable), executor)
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

        final Runnable runnable = () -> {
        };

        CompletableFuture.runAsync(taskContextProvider.get().subTask(runnable), executor)
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

        final Runnable runnable = () -> {
            terminateCurrentTask();
            testCompletedExceptionally(executorProvider, true);
        };

        CompletableFuture.runAsync(taskContextProvider.get().subTask(runnable), executor)
                .thenRun(() -> completedNormally.set(true))
                .exceptionally(t -> {
                    completedExceptionally.set(true);
                    return null;
                })
                .join();

        assertThat(completedNormally.get()).isFalse();
        assertThat(completedExceptionally.get()).isTrue();
    }

    private void testCompletedExceptionallyNested(final ExecutorProvider executorProviderOuter, final ExecutorProvider executorProviderInner) {
        final AtomicBoolean completedNormally = new AtomicBoolean();
        final AtomicBoolean completedExceptionally = new AtomicBoolean();

        final Executor executor = executorProviderOuter.get();
        final Runnable runnable = () -> {
            terminateCurrentTask();
            testCompletedExceptionally(executorProviderInner, true);
        };

        CompletableFuture.runAsync(taskContextProvider.get().subTask(runnable), executor)
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
        final Runnable runnable = this::testCompletedNormally;

        CompletableFuture.runAsync(taskContextProvider.get().subTask(runnable), executor)
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

    private void terminateCurrentTask() {
        final TaskId taskId = CurrentTaskState.currentTaskId();
        taskManager.terminate(taskId);
    }
}
