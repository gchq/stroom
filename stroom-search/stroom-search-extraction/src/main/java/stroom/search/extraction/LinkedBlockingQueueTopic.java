package stroom.search.extraction;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasTerminate;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class LinkedBlockingQueueTopic<T> implements Topic<T> {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LinkedBlockingQueueTopic.class);

    private final LinkedBlockingQueue<T> queue;
    private final HasTerminate hasTerminate;

    LinkedBlockingQueueTopic(final int capacity,
                                    final HasTerminate hasTerminate) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.hasTerminate = hasTerminate;
    }

    @Override
    public void accept(final T t) {
        try {
            boolean stored = false;
            while (!hasTerminate.isTerminated() && !stored) {
                // Loop until item is added or we terminate.
                stored = queue.offer(t, 1, TimeUnit.SECONDS);
            }
        } catch (final InterruptedException e) {
            // Continue to interrupt.
            Thread.currentThread().interrupt();

            LOGGER.debug(e::getMessage, e);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
    }

    @Override
    public T get() {
        T value = null;
        try {
            while (!hasTerminate.isTerminated() && value == null) {
                // Loop until item is added or we terminate.
                value = queue.poll(1, TimeUnit.SECONDS);
            }
        } catch (final InterruptedException e) {
            // Continue to interrupt.
            Thread.currentThread().interrupt();

            LOGGER.debug(e::getMessage, e);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
        return value;
    }
}
