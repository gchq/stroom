package stroom.proxy.repo;

import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class FrequencyExecutor implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrequencyExecutor.class);

    private final ScheduledExecutorService executorService;
    private final Supplier<Runnable> runnableSupplier;
    private final long frequency;

    public FrequencyExecutor(final String threadName,
                             final Supplier<Runnable> runnableSupplier,
                             final long frequency) {
        this.runnableSupplier = runnableSupplier;
        this.frequency = frequency;
        final ThreadFactory threadFactory = new CustomThreadFactory(
                threadName + " ",
                StroomThreadGroup.instance(),
                Thread.NORM_PRIORITY - 1);
        executorService = Executors.newScheduledThreadPool(1, threadFactory);
    }

    @Override
    public void start() {
        // Start.
        executorService.schedule(this::run, 0, TimeUnit.MILLISECONDS);
    }

    private void run() {
        if (!Thread.currentThread().isInterrupted()) {
            try {
                final Runnable runnable = runnableSupplier.get();
                runnable.run();
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            if (!Thread.currentThread().isInterrupted()) {
                executorService.schedule(this::run, frequency, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
    }
}
