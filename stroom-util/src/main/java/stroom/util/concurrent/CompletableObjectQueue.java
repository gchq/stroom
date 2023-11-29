package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

public class CompletableObjectQueue<T> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CompletableObjectQueue.class);
    private final CompletableQueue<T> queue;

    public CompletableObjectQueue(final int capacity) {
        queue = new CompletableQueue<>(capacity);
    }

    public void put(final T value) {
        try {
            queue.put(value);
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public T take() {
        try {
            return queue.take();
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        } catch (final CompleteException e) {
            LOGGER.trace("Complete");
        }
        return null;
    }

    public void complete() {
        queue.complete();
    }

    public void terminate() {
        queue.terminate();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
