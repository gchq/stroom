package stroom.proxy.repo.dao.lmdb;

import stroom.bytebuffer.PooledByteBuffer;
import stroom.proxy.repo.dao.lmdb.serde.ExtendedSerde;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.Flushable;

import org.lmdbjava.KeyRangeType;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class LmdbQueue<FK> implements Clearable, Flushable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbQueue.class);

    private static final long DELETE_BATCH_SIZE = 10_000;
    private final LmdbEnv env;
    private final String queueName;
    private final Db<Long, FK> db;

    private final ReentrantLock readLock = new ReentrantLock();
    private final Condition notEmpty = readLock.newCondition();

    private final AtomicLong writeId = new AtomicLong();
    private long currentWriteId;
    private long currentReadId;
    private final AtomicLong lastDeleteId = new AtomicLong();

    private final ByteBuffer writeIdByteBuffer;

    public LmdbQueue(final LmdbEnv env,
                     final String queueName,
                     final ExtendedSerde<FK> fkSerde) {
        try {
            this.env = env;
            this.queueName = queueName;
//            this.keySerde = keySerde;
//            this.fkSerde = fkSerde;
            writeIdByteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
            this.db = env.openDb(queueName, new LongSerde(), fkSerde);

            // Get the current maximum write id and set.
            initPositions();

        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public void put(final FK fk) {


//        // Update the current write position.
//        readLock.lock();
//        try {
            final long id = writeId.incrementAndGet();
////            writeIdByteBuffer.putLong(id);
////            writeIdByteBuffer.flip();
//            currentWriteId = id;

            db.putAsync(id, fk);

//            notEmpty.signalAll();
//        } finally {
//            readLock.unlock();
//        }




//        final PooledByteBuffer valueBuffer = db.serializeValue(fk);
//        db.putAsync(() -> {
//            final long id = writeId.incrementAndGet();
//            writeIdByteBuffer.putLong(id);
//            writeIdByteBuffer.flip();
//            final PooledByteBuffer pooledByteBuffer = new PooledByteBuffer() {
//                @Override
//                public ByteBuffer getByteBuffer() {
//                    return writeIdByteBuffer;
//                }
//
//                @Override
//                public void doWithByteBuffer(final Consumer<ByteBuffer> byteBufferConsumer) {
//
//                }
//
//                @Override
//                public void release() {
//
//                }
//
//                @Override
//                public void clear() {
//
//                }
//
//                @Override
//                public void close() {
//
//                }
//
//                @Override
//                public Optional<Integer> getCapacity() {
//                    return Optional.empty();
//                }
//            };
//            return pooledByteBuffer;
//        },                () -> valueBuffer);
////        final PooledByteBuffer fkBuffer = fkSerde.serialize(fk);
////        env.writeAsync(txn -> {
////            final long id = writeId.incrementAndGet();
////            writeIdByteBuffer.putLong(id);
////            writeIdByteBuffer.flip();
////            dbi.put(txn, writeIdByteBuffer, fkBuffer.getByteBuffer(), PutFlags.MDB_APPEND);
////            fkBuffer.release();
////        });
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
        final FK fk = db.get(id);

//        final PooledByteBuffer keyBuffer = keySerde.serialize(id);
//        final FK fk = env.readResult(txn -> {
//            final ByteBuffer valueBuffer = dbi.get(txn, keyBuffer.getByteBuffer());
//            if (valueBuffer == null) {
//                LOGGER.error(() -> "ERROR: " + id);
//                LOGGER.error(() -> "Current write: " + currentWriteId);
//                LOGGER.error(() -> "Current read: " + currentReadId);
//            }
//            return fkSerde.deserialize(valueBuffer);
//        });
//        keyBuffer.release();

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
        db.delete(KeyRangeType.FORWARD_CLOSED, from, to);

//        final PooledByteBuffer fromByteBuffer = db.serializeKey(from);
//        final PooledByteBuffer toByteBuffer = db.serializeKey(to);
//        final KeyRange<ByteBuffer> keyRange = KeyRange.closed(
//                fromByteBuffer.getByteBuffer(),
//                toByteBuffer.getByteBuffer());
//
//        env.writeAsync(txn -> {
//            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn, keyRange)) {
//                final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
//                while (iterator.hasNext()) {
//                    iterator.next();
//                    iterator.remove();
//                }
//            } finally {
//                fromByteBuffer.release();
//                toByteBuffer.release();
//            }
//        });
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
        db.clear();
        initPositions();
    }

    @Override
    public void flush() {
//        env.writeFunctionAsync(txn -> {
//            // Record current id.
//            final long id = writeId.get();
//            if (currentWriteId != id) {
//                // Commit the current transaction.
//                txn.commit();
//                txn.close();
//
//                // Update the current write position.
//                readLock.lock();
//                try {
//                    currentWriteId = id;
//                    notEmpty.signalAll();
//                } finally {
//                    readLock.unlock();
//                }
//
//                return null;
//
//            } else {
//                return txn;
//            }
//        });
    }

    public long size() {
        return db.count();
    }

    Optional<Long> getMinId() {
        return db.getMinKey();
    }

    Optional<Long> getMaxId() {
        return db.getMaxKey();
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
