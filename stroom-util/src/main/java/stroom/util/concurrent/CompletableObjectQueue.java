package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;

public class CompletableObjectQueue<T> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CompletableObjectQueue.class);
    private static final CompleteException COMPLETE = new CompleteException();

    private final ArrayBlockingQueue<Object> queue;

    public CompletableObjectQueue(final int capacity) {
        queue = new ArrayBlockingQueue<>(capacity);
    }

    public void put(final T value) {
        try {
            queue.put(value);
        } catch (final InterruptedException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    @SuppressWarnings("unchecked")
    public T take() {
        try {
            final Object object = queue.take();
            if (COMPLETE != object) {
                return (T) object;
            }
        } catch (final InterruptedException e) {
            LOGGER.error(e::getMessage, e);
        }
        return null;
    }

    public void complete() {
        try {
            queue.put(COMPLETE);
        } catch (final InterruptedException e) {
            LOGGER.trace(e::getMessage, e);
        }
    }

    public void clear() {
        queue.clear();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
