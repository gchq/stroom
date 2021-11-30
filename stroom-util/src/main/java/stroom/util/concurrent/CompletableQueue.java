package stroom.util.concurrent;

import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CompletableQueue<T> {

    private final LinkedBlockingQueue<Optional<T>> queue;

    public CompletableQueue(final int capacity) {
        queue = new LinkedBlockingQueue<>(capacity);
    }

    public void put(final T value) throws InterruptedException {
        queue.put(Optional.of(value));
    }

    public T take() throws InterruptedException, CompleteException {
        final Optional<T> optional = queue.take();
        if (optional.isEmpty()) {
            complete();
            throw new CompleteException();
        }
        return optional.get();
    }

    public T poll() throws InterruptedException, CompleteException {
        final Optional<T> optional = queue.poll();
        if (optional == null) {
            return null;
        }

        if (optional.isEmpty()) {
            complete();
            throw new CompleteException();
        }
        return optional.get();
    }

    public T poll(final long timeout, final TimeUnit unit) throws InterruptedException, CompleteException {
        final Optional<T> optional = queue.poll(timeout, unit);
        if (optional == null) {
            return null;
        }

        if (optional.isEmpty()) {
            complete();
            throw new CompleteException();
        }
        return optional.get();
    }

    public void complete() throws InterruptedException {
        queue.put(Optional.empty());
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
