package stroom.task.server;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.shared.ThreadPool;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class TestTaskManagerImpl extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTaskManagerImpl.class);

    @Resource
    ExecutorProvider executorProvider;

    @Test
    public void testMoreItemsThanThreads_boundedPool() throws ExecutionException, InterruptedException {
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
                                    ThreadUtil.sleep(50);
                                    LOGGER.info("Running task {}}", i);
                                    counter.incrementAndGet();
                                    threadsUsed.add(Thread.currentThread());
                                },
                                executor))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get();

        Assert.assertEquals(taskCount, counter.get());

        long distinctThreads = threadsUsed.stream()
                .distinct()
                .count();

        LOGGER.info("Threads used: {}}", distinctThreads);

        Assert.assertTrue(distinctThreads <= poolSize);

        LOGGER.info("Finished");
    }

    @Test
    public void testMoreItemsThanThreads_unBoundedPool() throws ExecutionException, InterruptedException {

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
                                    ThreadUtil.sleep(50);
                                    LOGGER.info("Running task {}", i);
                                    counter.incrementAndGet();
                                    threadsUsed.add(Thread.currentThread());
                                },
                                executor))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get();

        Assert.assertEquals(taskCount, counter.get());

        long distinctThreads = threadsUsed.stream()
                .distinct()
                .count();

        LOGGER.info("Threads used: {}", distinctThreads);

        Assert.assertTrue(distinctThreads <= poolSize);
        LOGGER.info("Finished");
    }

    @Test
    public void testCompletedExceptionally() {
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
                if (CurrentTaskState.isTerminated()) {
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

        Assert.assertFalse(completedNormally.get());
        Assert.assertTrue(completedExceptionally.get());

        if (terminated.get()) {
            throw new TaskTerminatedException();
        }

        if (nested && exception != null) {
            throw exception;
        }
    }

    @Test
    public void testCompletedNormally() {
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

        Assert.assertTrue(completedNormally.get());
        Assert.assertFalse(completedExceptionally.get());
    }

    @Test
    public void testCompletedExceptionallyNested() {
        final AtomicBoolean completedNormally = new AtomicBoolean();
        final AtomicBoolean completedExceptionally = new AtomicBoolean();

        final Executor executor = executorProvider.getExecutor();
        CompletableFuture.runAsync(() -> {
            CurrentTaskState.currentTask().terminate();
            testCompletedExceptionally();
        }, executor)
                .thenRun(() -> completedNormally.set(true))
                .exceptionally(t -> {
                    completedExceptionally.set(true);
                    return null;
                })
                .join();

        Assert.assertFalse(completedNormally.get());
        Assert.assertTrue(completedExceptionally.get());
    }

    private void testCompletedExceptionallyNested(final ExecutorProvider executorProviderOuter, final ExecutorProvider executorProviderInner) {
        final AtomicBoolean completedNormally = new AtomicBoolean();
        final AtomicBoolean completedExceptionally = new AtomicBoolean();

        final Executor executor = executorProviderOuter.getExecutor();
        CompletableFuture.runAsync(() -> {
            CurrentTaskState.currentTask().terminate();
            testCompletedExceptionally(executorProviderInner, true);
        }, executor)
                .thenRun(() -> completedNormally.set(true))
                .exceptionally(t -> {
                    completedExceptionally.set(true);
                    return null;
                })
                .join();

        Assert.assertFalse(completedNormally.get());
        Assert.assertTrue(completedExceptionally.get());
    }

    @Test
    public void testCompletedNormallyNested() {
        final AtomicBoolean completedNormally = new AtomicBoolean();
        final AtomicBoolean completedExceptionally = new AtomicBoolean();

        final Executor executor = executorProvider.getExecutor();
        CompletableFuture.runAsync(() -> {
            CurrentTaskState.currentTask().terminate();
            testCompletedNormally();
        }, executor)
                .thenRun(() -> completedNormally.set(true))
                .exceptionally(t -> {
                    completedExceptionally.set(true);
                    return null;
                })
                .join();

        Assert.assertTrue(completedNormally.get());
        Assert.assertFalse(completedExceptionally.get());
    }

    @Test
    public void testRejectedExecution() {
        testCompletedExceptionally(createRejectedExecutorProvider(), false);
    }

    @Test
    public void testRejectedNestedExecution() {
        testCompletedExceptionallyNested(executorProvider, createRejectedExecutorProvider());
    }

    private ExecutorProvider createRejectedExecutorProvider() {
        final Executor executor = new Executor() {
            @Override
            public void execute(final Runnable command) {
                throw new RejectedExecutionException("Expected");
            }
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
}
