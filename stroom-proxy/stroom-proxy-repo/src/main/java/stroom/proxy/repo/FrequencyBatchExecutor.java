package stroom.proxy.repo;

import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.queue.BatchUtil;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TransferQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FrequencyBatchExecutor<T> implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrequencyBatchExecutor.class);

    private final ExecutorService executorService;
    private final int threadCount;
    private final Supplier<Batch<T>> batchSupplier;
    private final Consumer<T> consumer;
    private final long frequency;
    private final TransferQueue<T> queue = new LinkedTransferQueue<>();

    public FrequencyBatchExecutor(final String threadName,
                                  final int threadCount,
                                  final Supplier<Batch<T>> batchSupplier,
                                  final Consumer<T> consumer,
                                  final long frequency) {
        this.threadCount = threadCount;
        this.batchSupplier = batchSupplier;
        this.consumer = consumer;
        this.frequency = frequency;
        final ThreadFactory threadFactory = new CustomThreadFactory(
                threadName + " ",
                StroomThreadGroup.instance(),
                Thread.NORM_PRIORITY - 1);
        executorService = Executors.newFixedThreadPool(threadCount + 1, threadFactory);
    }

    @Override
    public void start() {
        // Start.
//        executorService.schedule(this::fillQueue, 0, TimeUnit.MILLISECONDS);
        executorService.execute(this::fillQueue);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(this::process);
        }
    }

    private void fillQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                BatchUtil.transferEach(batchSupplier, item -> {
                    try {
                        queue.put(item);
                    } catch (final InterruptedException e) {
                        throw UncheckedInterruptedException.create(e);
                    }
                });
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    private void process() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                final T t = queue.take();
                consumer.accept(t);
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
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
