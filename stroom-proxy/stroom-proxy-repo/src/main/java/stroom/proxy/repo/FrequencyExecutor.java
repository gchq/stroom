package stroom.proxy.repo;

import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import io.dropwizard.lifecycle.Managed;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class FrequencyExecutor implements Managed {

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
        executorService.scheduleWithFixedDelay(runnableSupplier.get(), 0, frequency, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
    }
}
