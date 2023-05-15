package stroom.proxy.repo;

import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.BatchUtil;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import io.dropwizard.lifecycle.Managed;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TransferQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BatchExecutor<T> implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BatchExecutor.class);

    private final ExecutorService executorService;
    private final int threadCount;
    private final Supplier<Batch<T>> batchSupplier;
    private final Consumer<T> consumer;
    private final TransferQueue<T> queue = new LinkedTransferQueue<>();

    public BatchExecutor(final String threadName,
                         final int threadCount,
                         final Supplier<Batch<T>> batchSupplier,
                         final Consumer<T> consumer) {
        this.threadCount = threadCount;
        this.batchSupplier = batchSupplier;
        this.consumer = consumer;
        final ThreadFactory threadFactory = new CustomThreadFactory(
                threadName + " ",
                StroomThreadGroup.instance(),
                Thread.NORM_PRIORITY - 1);
        executorService = Executors.newFixedThreadPool(threadCount + 1, threadFactory);
    }

    @Override
    public void start() {
        // Start.
        executorService.execute(this::fillQueue);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(this::process);
        }
    }

    private void fillQueue() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                BatchUtil.transferEach(batchSupplier, item -> {
                    try {
                        queue.put(item);
                    } catch (final InterruptedException e) {
                        throw UncheckedInterruptedException.create(e);
                    }
                });
            }
        } catch (final UncheckedInterruptedException e) {
            LOGGER.debug(e::getMessage, e);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    private void process() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final T t = queue.take();
                    consumer.accept(t);
                } catch (final InterruptedException e) {
                    throw UncheckedInterruptedException.create(e);
                }
            }
        } catch (final UncheckedInterruptedException e) {
            LOGGER.debug(e::getMessage, e);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
    }
}
