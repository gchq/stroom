package stroom.proxy.repo.queue;

import stroom.proxy.repo.dao.SqliteJooqHelper;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.shared.Flushable;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class RecordQueue implements Flushable {

    private final SqliteJooqHelper jooq;
    private final List<WriteQueue> writeQueues;
    private final List<ReadQueue<?>> readQueues;
    private final int batchSize;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final Condition writeLockCondition = writeLock.newCondition();
    private final ReentrantLock readLock = new ReentrantLock();

    public RecordQueue(final SqliteJooqHelper jooq,
                       final List<WriteQueue> writeQueues,
                       final List<ReadQueue<?>> readQueues,
                       final int batchSize) {
        this.jooq = jooq;
        this.writeQueues = writeQueues;
        this.readQueues = readQueues;
        this.batchSize = batchSize;
    }

    public void clear() {
        try {
            readLock.lockInterruptibly();
            try {
                writeLock.lockInterruptibly();
                try {
                    for (final WriteQueue writeQueue : writeQueues) {
                        writeQueue.clear();
                    }
                    for (final ReadQueue<?> readQueue : readQueues) {
                        readQueue.clear();
                    }
                } finally {
                    writeLock.unlock();
                }
            } finally {
                readLock.unlock();
            }
        } catch (final InterruptedException e) {
            UncheckedInterruptedException.resetAndThrow(e);
        }
    }

    private int getMaxQueueSize() {
        return writeQueues.stream().mapToInt(WriteQueue::size).max().orElse(0);
    }

    /**
     * Add items to queues under lock.
     *
     * @param runnable The runnable that will add items to queues.
     */
    public void add(final Runnable runnable) {
        try {
            writeLock.lockInterruptibly();
            try {
                runnable.run();
                final int maxQueueSize = getMaxQueueSize();
                if (maxQueueSize > batchSize) {
                    flushInternal(jooq);
                }
            } finally {
                writeLock.unlock();
            }
        } catch (final InterruptedException e) {
            UncheckedInterruptedException.resetAndThrow(e);
        }
    }

    @Override
    public void flush() {
        try {
            writeLock.lockInterruptibly();
            try {
                final int maxQueueSize = getMaxQueueSize();
                if (maxQueueSize > 0) {
                    flushInternal(jooq);
                }
            } finally {
                writeLock.unlock();
            }
        } catch (final InterruptedException e) {
            UncheckedInterruptedException.resetAndThrow(e);
        }
    }

    private void flushInternal(final SqliteJooqHelper jooq) {
        jooq.transaction(context -> {
            for (final WriteQueue writeQueue : writeQueues) {
                writeQueue.flush(context);
            }
        });
        for (final WriteQueue writeQueue : writeQueues) {
            writeQueue.clear();
        }
        writeLockCondition.signalAll();
    }

    public <T> Batch<T> getBatch(final ReadQueue<T> readQueue) {
        Batch<T> batch = Batch.emptyBatch();
        try {
            readLock.lockInterruptibly();
            try {
                batch = readQueue.getBatch();
                while (batch.isEmpty()) {
                    writeLock.lockInterruptibly();
                    try {
                        readQueue.fill();
                        if (readQueue.size() == 0) {
                            // Wait for new inserts.
                            writeLockCondition.await();
                        }
                    } finally {
                        writeLock.unlock();
                    }
                    batch = readQueue.getBatch();
                }
            } finally {
                readLock.unlock();
            }
        } catch (final InterruptedException e) {
            UncheckedInterruptedException.resetAndThrow(e);
        }
        return batch;
    }

    public <T> Batch<T> getBatch(final ReadQueue<T> readQueue,
                                 final long timeout,
                                 final TimeUnit timeUnit) {
        Batch<T> batch = Batch.emptyBatch();
        try {
            readLock.lockInterruptibly();
            try {
                batch = readQueue.getBatch();
                while (batch.isEmpty()) {
                    writeLock.lockInterruptibly();
                    try {
                        readQueue.fill();
                        if (readQueue.size() == 0) {
                            // Wait for new inserts.
                            if (!writeLockCondition.await(timeout, timeUnit)) {
                                return batch;
                            }
                        }
                    } finally {
                        writeLock.unlock();
                    }
                    batch = readQueue.getBatch();
                }
            } finally {
                readLock.unlock();
            }
        } catch (final InterruptedException e) {
            UncheckedInterruptedException.resetAndThrow(e);
        }
        return batch;
    }
}
