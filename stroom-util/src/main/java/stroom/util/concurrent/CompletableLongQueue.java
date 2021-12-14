package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;

public class CompletableLongQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CompletableLongQueue.class);
    private static final CompleteException COMPLETE = new CompleteException();

    private final ArrayBlockingQueue<Object> queue;

    public CompletableLongQueue(final int capacity) {
        queue = new ArrayBlockingQueue<>(capacity);
    }

    public void put(final long value) throws InterruptedException {
        queue.put(value);
    }

    public long take() throws InterruptedException, CompleteException {
        final Object object = queue.take();
        if (COMPLETE == object) {
            complete();
            throw COMPLETE;
        }
        return (long) object;
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

    public int size() {
        return queue.size();
    }
}
