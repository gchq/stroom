package stroom.proxy.repo;

import stroom.db.util.JooqHelper;

import org.jooq.Field;
import org.jooq.Table;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class WorkQueue {

    private final AtomicLong readPos;
    private final AtomicLong writePos;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    public WorkQueue(final long readPos,
                     final long writePos) {
        this.readPos = new AtomicLong(readPos);
        this.writePos = new AtomicLong(writePos);
    }

    public static WorkQueue createWithJooq(final JooqHelper jooq, final Table<?> table, final Field<Long> idField) {
        final long minId = jooq.getMinId(table, idField).orElse(0L);
        final long maxId = jooq.getMaxId(table, idField).orElse(-1L);
        return new WorkQueue(minId, maxId);
    }

    private <T> T underLock(final Supplier<T> supplier) {
        try {
            lock.lockInterruptibly();
            try {
                return supplier.get();
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void put(final Consumer<AtomicLong> consumer) {
        underLock(() -> {
            consumer.accept(writePos);
            notEmpty.signal();
            return null;
        });
    }

    public <T> Optional<T> get(final Function<Long, Optional<T>> function) {
        return underLock(() -> {
            while (readPos.get() > writePos.get()) {
                try {
                    notEmpty.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            return function.apply(readPos.getAndIncrement());
        });
    }

    public <T> Optional<T> get(final Function<Long, Optional<T>> function,
                               final long timeout,
                               final TimeUnit timeUnit) {
        return underLock(() -> {
            while (readPos.get() > writePos.get()) {
                try {
                    if (!notEmpty.await(timeout, timeUnit)) {
                        return Optional.empty();
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            return function.apply(readPos.getAndIncrement());
        });
    }
}
