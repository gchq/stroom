package stroom.util.concurrent;

import java.util.OptionalLong;
import java.util.concurrent.LinkedBlockingQueue;

public class CompletableLongQueue {

    private final LinkedBlockingQueue<OptionalLong> queue;

    public CompletableLongQueue(final int capacity) {
        queue = new LinkedBlockingQueue<>(capacity);
    }

    public void put(final long value) throws InterruptedException {
        queue.put(OptionalLong.of(value));
    }

    public long take() throws InterruptedException, CompleteException {
        final OptionalLong optional = queue.take();
        if (optional.isEmpty()) {
            complete();
            throw new CompleteException();
        }
        return optional.getAsLong();
    }

    public void complete() throws InterruptedException {
        queue.put(OptionalLong.empty());
    }

    public int size() {
        return queue.size();
    }
}
