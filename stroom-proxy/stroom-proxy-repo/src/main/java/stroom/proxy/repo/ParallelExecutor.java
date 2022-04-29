package stroom.proxy.repo;

import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

public class ParallelExecutor implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelExecutor.class);

    private final ExecutorService executorService;
    private final Supplier<Runnable> runnableSupplier;
    private final int threadCount;

    public ParallelExecutor(final String threadName,
                            final Supplier<Runnable> runnableSupplier,
                            final int threadCount) {
        this.runnableSupplier = runnableSupplier;
        this.threadCount = threadCount;
        final ThreadFactory threadFactory = new CustomThreadFactory(
                threadName + " ",
                StroomThreadGroup.instance(),
                Thread.NORM_PRIORITY - 1);
        executorService = Executors.newFixedThreadPool(threadCount, threadFactory);
    }

    @Override
    public void start() {
        // Start.
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(this::run);
        }
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                final Runnable runnable = runnableSupplier.get();
                runnable.run();
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
    }
}
