package stroom.util.concurrent;

import java.util.concurrent.ArrayBlockingQueue;

public class CompletableLongQueue {

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

    public void complete() throws InterruptedException {
        queue.put(COMPLETE);
    }

    public int size() {
        return queue.size();
    }
}
