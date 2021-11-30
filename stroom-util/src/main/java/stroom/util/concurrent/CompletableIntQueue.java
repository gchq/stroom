package stroom.util.concurrent;

import java.util.concurrent.ArrayBlockingQueue;

public class CompletableIntQueue {

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

    public void complete() throws InterruptedException {
        queue.put(COMPLETE);
    }

    public int size() {
        return queue.size();
    }
}
