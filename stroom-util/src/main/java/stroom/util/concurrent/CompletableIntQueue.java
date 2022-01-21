package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;

public class CompletableIntQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CompletableIntQueue.class);
    private static final CompleteException COMPLETE = new CompleteException();

    private final ArrayBlockingQueue<Object> queue;

    public CompletableIntQueue(final int capacity) {
        queue = new ArrayBlockingQueue<>(capacity);
    }

    public void put(final int value) throws InterruptedException {
        queue.put(value);
    }

    public int take() throws InterruptedException, CompleteException {
        final Object object = queue.take();
        if (COMPLETE == object) {
            complete();
            throw COMPLETE;
        }
        return (int) object;
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
