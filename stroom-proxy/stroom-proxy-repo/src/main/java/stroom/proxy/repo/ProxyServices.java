package stroom.proxy.repo;

import stroom.proxy.repo.queue.Batch;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import io.dropwizard.lifecycle.Managed;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ProxyServices implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyServices.class);

    private final List<Managed> services = new ArrayList<>();

    public void addParallelExecutor(final String threadName,
                                    final Supplier<Runnable> runnableSupplier,
                                    final int threadCount) {
        LOGGER.info("Creating parallel executor '{}', threadCount: {}", threadName, threadCount);
        final ParallelExecutor executor = new ParallelExecutor(
                threadName,
                runnableSupplier,
                threadCount);
        addManaged(executor);
    }

    public void addFrequencyExecutor(final String threadName,
                                     final Supplier<Runnable> runnableSupplier,
                                     final long frequencyMs) {
        LOGGER.info("Creating frequency executor  '{}', frequencyMs: {}",
                threadName,
                ModelStringUtil.formatCsv(frequencyMs));
        final FrequencyExecutor executor = new FrequencyExecutor(
                threadName,
                runnableSupplier,
                frequencyMs);
        addManaged(executor);
    }

    public <T> void addBatchExecutor(final String threadName,
                                     final int threadCount,
                                     final Supplier<Batch<T>> supplier,
                                     final Consumer<T> consumer) {
        LOGGER.info("Creating batch executor      '{}', threadCount: {}", threadName, threadCount);
        final BatchExecutor<T> executor = new BatchExecutor<>(
                threadName,
                threadCount,
                supplier,
                consumer);
        addManaged(executor);
    }

    private void addManaged(final Managed managed) {
        services.add(managed);
    }

    @Override
    public void start() throws Exception {
        LOGGER.info("Starting Stroom Proxy");

        for (final Managed service : services) {
            service.start();
        }

        LOGGER.info("Started Stroom Proxy");
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping Stroom Proxy");

        for (int i = services.size() - 1; i >= 0; i--) {
            try {
                services.get(i).stop();
            } catch (final InterruptedException | UncheckedInterruptedException e) {
                LOGGER.debug(e::getMessage, e);
            } catch (final Exception e) {
                LOGGER.error("error", e);
            }
        }

        // This method is part of DW  Managed which is managed by Jersey so we need to ensure any interrupts
        // are cleared before it goes back to Jersey
        final boolean interrupted = Thread.interrupted();
        LOGGER.debug("Was interrupted = " + interrupted);

        LOGGER.info("Stopped Stroom Proxy");
    }
}
