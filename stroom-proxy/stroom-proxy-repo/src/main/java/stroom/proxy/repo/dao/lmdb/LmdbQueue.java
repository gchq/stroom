package stroom.proxy.repo.dao.lmdb;

import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.serde.Serde;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.Flushable;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class LmdbQueue<FK> implements Clearable, Flushable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbQueue.class);

    private static final long DELETE_BATCH_SIZE = 10_000;
    private final LmdbEnv env;
    private final String queueName;
    private final Dbi<ByteBuffer> dbi;

    private final LongSerde keySerde;
    private final Serde<FK> fkSerde;

    private final ReentrantLock readLock = new ReentrantLock();
    private final Condition notEmpty = readLock.newCondition();

    private final AtomicLong writeId = new AtomicLong();
    private long currentWriteId;
    private long currentReadId;
    private final AtomicLong lastDeleteId = new AtomicLong();

    private final ByteBuffer writeIdByteBuffer;

    public LmdbQueue(final LmdbEnv env,
                     final String queueName,
                     final LongSerde keySerde,
                     final Serde<FK> fkSerde) {
        try {
            this.env = env;
            this.queueName = queueName;
            this.keySerde = keySerde;
            this.fkSerde = fkSerde;
            writeIdByteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
            this.dbi = env.openDbi(queueName, DbiFlags.MDB_CREATE);

            // Get the current maximum write id and set.
            initPositions();

        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public void put(final FK fk) {
        final PooledByteBuffer fkBuffer = fkSerde.serialize(fk);
        env.write(txn -> {
            final long id = writeId.incrementAndGet();
            writeIdByteBuffer.putLong(id);
            writeIdByteBuffer.flip();
            dbi.put(txn, writeIdByteBuffer, fkBuffer.getByteBuffer(), PutFlags.MDB_APPEND);
            fkBuffer.release();
        });
    }

    public FK take() {
        final long id;
        try {
            readLock.lockInterruptibly();
            try {
                while (currentWriteId <= currentReadId) {
                    notEmpty.await();
                }
                id = ++currentReadId;
            } catch (final InterruptedException e) {
                LOGGER.debug(e::getMessage, e);
                Thread.currentThread().interrupt();
                throw new UncheckedInterruptedException(e);
            } finally {
                readLock.unlock();
            }

            return getAndDelete(id);

        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public Optional<FK> take(final long time, final TimeUnit timeUnit) {
        final long id;
        try {
            readLock.lockInterruptibly();
            try {
                if (currentWriteId <= currentReadId) {
                    if (!notEmpty.await(time, timeUnit)) {
                        return Optional.empty();
                    }
                }
                id = ++currentReadId;
            } catch (final InterruptedException e) {
                LOGGER.debug(e::getMessage, e);
                Thread.currentThread().interrupt();
                throw new UncheckedInterruptedException(e);
            } finally {
                readLock.unlock();
            }

            return Optional.of(getAndDelete(id));

        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    private FK getAndDelete(final long id) {
        final PooledByteBuffer keyBuffer = keySerde.serialize(id);
        final FK fk = env.readResult(txn -> {
            final ByteBuffer valueBuffer = dbi.get(txn, keyBuffer.getByteBuffer());
            if (valueBuffer == null) {
                LOGGER.error(() -> "ERROR: " + id);
                LOGGER.error(() -> "Current write: " + currentWriteId);
                LOGGER.error(() -> "Current read: " + currentReadId);
            }
            return fkSerde.deserialize(valueBuffer);
        });
        keyBuffer.release();

        // Delete some old ids.
        final long currentDeleteId = lastDeleteId.get();
        final long nextDeleteId = currentDeleteId + DELETE_BATCH_SIZE;
        if (nextDeleteId < id - DELETE_BATCH_SIZE) {
            if (lastDeleteId.compareAndSet(currentDeleteId, nextDeleteId)) {
                deleteBatch(currentDeleteId + 1, nextDeleteId);
            }
        }

        return fk;
    }

    private void deleteBatch(final long from, final long to) {
        final PooledByteBuffer fromByteBuffer = keySerde.serialize(from);
        final PooledByteBuffer toByteBuffer = keySerde.serialize(to);
        final KeyRange<ByteBuffer> keyRange = KeyRange.closed(
                fromByteBuffer.getByteBuffer(),
                toByteBuffer.getByteBuffer());

        env.write(txn -> {
            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn, keyRange)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                while (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            } finally {
                fromByteBuffer.release();
                toByteBuffer.release();
            }
        });
    }

    private void initPositions() {
        readLock.lock();
        try {
            final long minId = getMinId().orElse(0L);
            currentReadId = minId;
            lastDeleteId.set(minId);
            final long maxId = getMaxId().orElse(0L);
            currentWriteId = maxId;
            writeId.set(maxId);

            LOGGER.info(() -> queueName + " initPositions: " + toString());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void clear() {
        env.clear(dbi);
        initPositions();
    }

    @Override
    public void flush() {
        env.writeFunction(txn -> {
            // Record current id.
            final long id = writeId.get();
            if (currentWriteId != id) {
                // Commit the current transaction.
                txn.commit();
                txn.close();

                // Update the current write position.
                readLock.lock();
                try {
                    currentWriteId = id;
                    notEmpty.signalAll();
                } finally {
                    readLock.unlock();
                }

                return null;

            } else {
                return txn;
            }
        });
    }

    public long size() {
        return env.count(dbi);
    }

    Optional<Long> getMinId() {
        return env.getMinKey(dbi, keySerde);
    }

    Optional<Long> getMaxId() {
        return env.getMaxKey(dbi, keySerde);
    }

    @Override
    public String toString() {
        return "LmdbQueue{" +
                "currentWriteId=" + currentWriteId +
                ", currentReadId=" + currentReadId +
                ", writeId=" + writeId +
                ", lastDeleteId=" + lastDeleteId +
                '}';
    }
}
