package stroom.util.concurrent;

import java.util.OptionalInt;
import java.util.concurrent.LinkedBlockingQueue;

public class CompletableIntQueue {

    private final LinkedBlockingQueue<OptionalInt> queue;

    public CompletableIntQueue(final int capacity) {
        queue = new LinkedBlockingQueue<>(capacity);
    }

    public void put(final int value) throws InterruptedException {
        queue.put(OptionalInt.of(value));
    }

    public int take() throws InterruptedException, CompleteException {
        final OptionalInt optional = queue.take();
        if (optional.isEmpty()) {
            complete();
            throw new CompleteException();
        }
        return optional.getAsInt();
    }

    public void complete() throws InterruptedException {
        queue.put(OptionalInt.empty());
    }

    public int size() {
        return queue.size();
    }
}
