package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CompletableQueue<T> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CompletableQueue.class);
    private static final CompleteException COMPLETE = new CompleteException();

    private final ArrayBlockingQueue<Object> queue;

    public CompletableQueue(final int capacity) {
        queue = new ArrayBlockingQueue<>(capacity);
    }

    public void put(final T value) throws InterruptedException {
        queue.put(value);
    }

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException, CompleteException {
        final Object object = queue.take();
        if (COMPLETE == object) {
            complete();
            throw COMPLETE;
        }
        return (T) object;
    }

    @SuppressWarnings("unchecked")
    public T poll() throws InterruptedException, CompleteException {
        final Object object = queue.poll();
        if (object == null) {
            return null;
        }

        if (COMPLETE == object) {
            complete();
            throw COMPLETE;
        }

        return (T) object;
    }

    @SuppressWarnings("unchecked")
    public T poll(final long timeout, final TimeUnit unit) throws InterruptedException, CompleteException {
        final Object object = queue.poll(timeout, unit);
        if (object == null) {
            return null;
        }

        if (COMPLETE == object) {
            complete();
            throw COMPLETE;
        }

        return (T) object;
    }

    public void complete() {
        try {
            queue.put(COMPLETE);
        } catch (final InterruptedException e) {
            LOGGER.trace(e::getMessage, e);
            // Keep interrupting this thread.
            Thread.currentThread().interrupt();
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
