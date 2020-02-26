package stroom.task;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskManager;
import stroom.task.api.TaskTerminatedException;
import stroom.task.impl.CurrentTaskState;
import stroom.task.shared.TaskId;
import stroom.task.shared.ThreadPool;
import stroom.task.api.ThreadPoolImpl;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;
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
    private TaskManager taskManager;

    @Test
    void testMoreItemsThanThreads_boundedPool() throws ExecutionException, InterruptedException {
        LOGGER.info("Starting");
        int poolSize = 4;
        ThreadPool threadPool = new ThreadPoolImpl(getClass().getName(),
                3,
                poolSize,
                poolSize);

        final Executor executor = executorProvider.getExecutor(threadPool);

        CompletableFuture.runAsync(() ->
                LOGGER.info("Warming up thread pool")).get();

        AtomicInteger counter = new AtomicInteger();
        final Queue<Thread> threadsUsed = new ConcurrentLinkedQueue<>();

        final int taskCount = 10;
        CompletableFuture[] futures = IntStream.rangeClosed(1, taskCount)
                .mapToObj(i ->
                        CompletableFuture.runAsync(() -> {
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
                                },
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

        final Executor executor = executorProvider.getExecutor(threadPool);

        AtomicInteger counter = new AtomicInteger();
        final Queue<Thread> threadsUsed = new ConcurrentLinkedQueue<>();

        final int taskCount = 10;
        CompletableFuture[] futures = IntStream.rangeClosed(1, taskCount)
                .mapToObj(i ->
                        CompletableFuture.runAsync(() -> {
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
                                },
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
            final Executor executor = executorProvider.getExecutor();
            CompletableFuture.runAsync(() -> {
                if (Thread.currentThread().isInterrupted()) {
                    terminated.set(true);
                }
                throw new RuntimeException("Expected");
            }, executor)
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

        if (nested && exception != null) {
            throw exception;
        }
    }

    @Test
    void testCompletedNormally() {
        final AtomicBoolean completedNormally = new AtomicBoolean();
        final AtomicBoolean completedExceptionally = new AtomicBoolean();

        final Executor executor = executorProvider.getExecutor();
        CompletableFuture.runAsync(() -> {
        }, executor)
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

        final Executor executor = executorProvider.getExecutor();
        CompletableFuture.runAsync(() -> {
            terminateCurrentTask();
            testCompletedExceptionally();
        }, executor)
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

        final Executor executor = executorProviderOuter.getExecutor();
        CompletableFuture.runAsync(() -> {
            terminateCurrentTask();
            testCompletedExceptionally(executorProviderInner, true);
        }, executor)
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

        final Executor executor = executorProvider.getExecutor();
        CompletableFuture.runAsync(() -> {
            terminateCurrentTask();
            testCompletedNormally();
        }, executor)
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
            public Executor getExecutor() {
                return executor;
            }

            @Override
            public Executor getExecutor(final ThreadPool threadPool) {
                return executor;
            }
        };
    }

    private void terminateCurrentTask() {
        final TaskId taskId = CurrentTaskState.currentTask().getId();
        taskManager.terminate(taskId);
//            Thread.currentThread().interrupt();
    }
}
