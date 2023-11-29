package stroom.util.concurrent;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class CompletableQueue<T> {

    private static final CompleteException COMPLETE = new CompleteException();

    /**
     * The queued items
     */
    private final Object[] items;

    /**
     * items index for next take, poll, peek or remove
     */
    private int takeIndex;

    /**
     * items index for next put, offer, or add
     */
    private int putIndex;

    /**
     * Number of elements in the queue
     */
    private int count;

    private boolean complete;

    /**
     * Main lock guarding all access
     */
    private final ReentrantLock lock;

    /**
     * Condition for waiting takes
     */
    private final Condition notEmpty;

    /**
     * Condition for waiting puts
     */
    private final Condition notFull;

    public CompletableQueue(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException();
        }
        this.items = new Object[capacity];
        lock = new ReentrantLock();
        notEmpty = lock.newCondition();
        notFull = lock.newCondition();
    }

    public void put(final T value) throws InterruptedException {
        Objects.requireNonNull(value);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (!complete && count == items.length) {
                notFull.await();
            }
            if (!complete) {
                enqueue(value);
            } else {
                notEmpty.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException, CompleteException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (!complete && count == 0) {
                notEmpty.await();
            }
            if (complete) {
                notFull.signal();
                throw COMPLETE;
            }
            final Object object = dequeue();
            if (COMPLETE == object) {
                complete = true;
                throw COMPLETE;
            }
            return (T) object;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T poll() throws InterruptedException, CompleteException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            if (complete) {
                notFull.signal();
                throw COMPLETE;
            }
            if (count == 0) {
                return null;
            }
            final Object object = dequeue();
            if (COMPLETE == object) {
                complete = true;
                throw COMPLETE;
            }
            return (T) object;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T poll(final long timeout, final TimeUnit unit) throws InterruptedException, CompleteException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (!complete && count == 0) {
                if (nanos <= 0L) {
                    return null;
                }
                nanos = notEmpty.awaitNanos(nanos);
            }
            if (complete) {
                notFull.signal();
                throw COMPLETE;
            }
            final Object object = dequeue();
            if (COMPLETE == object) {
                complete = true;
                throw COMPLETE;
            }
            return (T) object;
        } finally {
            lock.unlock();
        }
    }

    public void complete() {
        final ReentrantLock lock = this.lock;
        try {
            lock.lockInterruptibly();
            while (!complete && count == items.length) {
                notFull.await();
            }
            if (!complete) {
                enqueue(COMPLETE);
            } else {
                notEmpty.signal();
            }
        } catch (final InterruptedException e) {
            complete = true;
            terminate();
        } finally {
            lock.unlock();
        }
    }

    public void terminate() {
        // Make sure we don't try to add any more items.
        complete = true;

        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // Make sure we don't try to add any more items.
            complete = true;
            int k;
            if ((k = count) > 0) {
                circularClear(items, takeIndex, putIndex);
                takeIndex = putIndex;
                count = 0;
                for (; k > 0 && lock.hasWaiters(notFull); k--) {
                    notFull.signal();
                }
            }

            notFull.signalAll();
            notEmpty.signalAll();

        } finally {
            lock.unlock();
        }
    }

    private static void circularClear(final Object[] items, int i, int end) {
        for (int to = (i < end)
                ? end
                : items.length; ; i = 0, to = end) {
            for (; i < to; i++) {
                items[i] = null;
            }
            if (to == end) {
                break;
            }
        }
    }

    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Inserts element at current put position, advances, and signals.
     * Call only when holding lock.
     */
    private void enqueue(final Object o) {
        final Object[] items = this.items;
        items[putIndex] = o;
        if (++putIndex == items.length) {
            putIndex = 0;
        }
        count++;
        notEmpty.signal();
    }

    /**
     * Extracts element at current take position, advances, and signals.
     * Call only when holding lock.
     */
    private Object dequeue() {
        final Object[] items = this.items;
        final Object o = items[takeIndex];
        items[takeIndex] = null;
        if (++takeIndex == items.length) {
            takeIndex = 0;
        }
        count--;
        notFull.signal();
        return o;
    }
}
