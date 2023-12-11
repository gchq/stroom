package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.proxy.repo.dao.lmdb.serde.NativeLongSerde;
import stroom.proxy.repo.dao.lmdb.serde.PooledByteBuffer;
import stroom.proxy.repo.dao.lmdb.serde.Serde;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class LmdbQueue<FK> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbQueue.class);

    private static final long DELETE_BATCH_SIZE = 10_000;
    private final LmdbEnv env;
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
                     final Serde<FK> fkSerde) {
        this(env, queueName, fkSerde, false);
    }

    public LmdbQueue(final LmdbEnv env,
                     final String queueName,
                     final Serde<FK> fkSerde,
                     final boolean nativeByteOrder) {
        try {
            this.env = env;
            this.fkSerde = fkSerde;
            writeIdByteBuffer = ByteBuffer.allocateDirect(Long.BYTES);

            if (nativeByteOrder) {
                this.dbi = env.openDbi(queueName, DbiFlags.MDB_CREATE, DbiFlags.MDB_INTEGERKEY);
                keySerde = new NativeLongSerde();
                writeIdByteBuffer.order(ByteOrder.nativeOrder());
            } else {
                this.dbi = env.openDbi(queueName, DbiFlags.MDB_CREATE);
                keySerde = new LongSerde();
            }

            // Get the current maximum write id and set.
            initPositions();
            env.addPostCommitHook(() -> {
                readLock.lock();
                try {
                    currentWriteId = writeId.get();
                    notEmpty.signalAll();
                } finally {
                    readLock.unlock();
                }
            });

        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public void put(final FK fk) {
        final PooledByteBuffer fkBuffer = fkSerde.serialise(fk);
        env.write(txn -> {
            final long id = writeId.incrementAndGet();
            writeIdByteBuffer.putLong(id);
            writeIdByteBuffer.flip();
            dbi.put(txn, writeIdByteBuffer, fkBuffer.get(), PutFlags.MDB_APPEND);
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
        final PooledByteBuffer keyBuffer = keySerde.serialise(id);
        final FK fk = env.readResult(txn -> {
            final ByteBuffer valueBuffer = dbi.get(txn, keyBuffer.get());
            return fkSerde.deserialise(valueBuffer);
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
        final PooledByteBuffer fromByteBuffer = keySerde.serialise(from);
        final PooledByteBuffer toByteBuffer = keySerde.serialise(to);
        final KeyRange<ByteBuffer> keyRange = KeyRange.closed(fromByteBuffer.get(), toByteBuffer.get());

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
            final Optional<Long> minId = getMinId();
            minId.ifPresent(id -> {
                currentReadId = id;
                lastDeleteId.set(id);
            });
            final Optional<Long> maxId = getMaxId();
            maxId.ifPresent(writeId::set);
        } finally {
            readLock.unlock();
        }
    }

    public void clear() {
        env.clear(dbi);
        initPositions();
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
}
