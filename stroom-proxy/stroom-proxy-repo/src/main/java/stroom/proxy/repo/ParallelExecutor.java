package stroom.proxy.repo;

import stroom.util.NullSafe;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import io.dropwizard.lifecycle.Managed;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ParallelExecutor implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ParallelExecutor.class);

    private final ExecutorService executorService;
    private final Supplier<Runnable> runnableSupplier;
    private final int threadCount;
    private final String threadName;

    public ParallelExecutor(final String threadName,
                            final Supplier<Runnable> runnableSupplier,
                            final int threadCount) {
        this.runnableSupplier = runnableSupplier;
        this.threadCount = threadCount;
        this.threadName = threadName;
        final ThreadFactory threadFactory = new CustomThreadFactory(
                threadName + " ",
                StroomThreadGroup.instance(),
                Thread.NORM_PRIORITY - 1);
        executorService = Executors.newFixedThreadPool(threadCount, threadFactory);
    }

    @Override
    public synchronized void start() {
        LOGGER.debug("Starting parallel executor with threadName: {}, threadCount: {}", threadName, threadCount);
        // Start.
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(this::run);
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        LOGGER.debug("Stopping parallel executor with threadName: {}, threadCount: {}", threadName, threadCount);
        executorService.shutdownNow();
        final boolean didTerminate = executorService.awaitTermination(1, TimeUnit.DAYS);
        LOGGER.debug("Stopped parallel executor with threadName: {}, threadCount: {}, didTerminate: {}",
                threadName, threadCount, didTerminate);
    }

    private void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final Runnable runnable = runnableSupplier.get();
                NullSafe.run(runnable);
            }
        } catch (final UncheckedInterruptedException e) {
            LOGGER.debug(e::getMessage, e);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    @Override
    public String toString() {
        return "ParallelExecutor{" +
               "executorService=" + executorService +
               ", threadCount=" + threadCount +
               ", threadName='" + threadName + '\'' +
               '}';
    }
}
