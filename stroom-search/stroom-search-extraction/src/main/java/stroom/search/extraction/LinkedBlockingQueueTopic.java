package stroom.search.extraction;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;

class LinkedBlockingQueueTopic<T> implements Topic<T> {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LinkedBlockingQueueTopic.class);

    private final LinkedBlockingQueue<T> queue;

    LinkedBlockingQueueTopic(final int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    @Override
    public void accept(final T t) {
        try {
            queue.put(t);
        } catch (final InterruptedException e) {
            // Continue to interrupt.
            Thread.currentThread().interrupt();

            LOGGER.debug(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
    }

    @Override
    public T get() {
        T value = null;
        try {
            value = queue.take();
        } catch (final InterruptedException e) {
            // Continue to interrupt.
            Thread.currentThread().interrupt();

            LOGGER.debug(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
        return value;
    }
}
