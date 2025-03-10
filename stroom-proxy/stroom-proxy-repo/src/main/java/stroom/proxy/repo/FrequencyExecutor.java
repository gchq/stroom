package stroom.proxy.repo;

import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import io.dropwizard.lifecycle.Managed;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class FrequencyExecutor implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FrequencyExecutor.class);

    private final ScheduledExecutorService executorService;
    private final Supplier<Runnable> runnableSupplier;
    private final long frequency;
    private final String threadNamePrefix;

    public FrequencyExecutor(final String threadNamePrefix,
                             final Supplier<Runnable> runnableSupplier,
                             final long frequency) {
        this.runnableSupplier = runnableSupplier;
        this.frequency = frequency;
        this.threadNamePrefix = threadNamePrefix;
        final ThreadFactory threadFactory = new CustomThreadFactory(
                threadNamePrefix + " ",
                StroomThreadGroup.instance(),
                Thread.NORM_PRIORITY - 1);
        executorService = Executors.newScheduledThreadPool(1, threadFactory);
    }

    @Override
    public void start() {
        final Runnable runnable = () -> {
            try {
                runnableSupplier.get().run();
            } catch (final UncheckedInterruptedException e) {
                LOGGER.debug(e::getMessage, e);
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        };
        LOGGER.debug("Starting frequency executor '{}', frequency: {}", threadNamePrefix, frequency);
        executorService.scheduleWithFixedDelay(runnable, 0, frequency, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        LOGGER.debug("Stopping frequency executor '{}', frequency: {}", threadNamePrefix, frequency);
        executorService.shutdownNow();
    }

    @Override
    public String toString() {
        return "FrequencyExecutor{" +
               "executorService=" + executorService +
               ", frequency=" + frequency +
               ", threadNamePrefix='" + threadNamePrefix + '\'' +
               '}';
    }
}
