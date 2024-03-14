package stroom.proxy.repo.dao.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.lmdb.serde.Serde;
import stroom.proxy.repo.dao.lmdb.serde.ExtendedSerde;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.dropwizard.lifecycle.Managed;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

public class LmdbEnv implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnv.class);

    private final Env<ByteBuffer> env;
    private final ByteBufferPool byteBufferPool;
    private final ArrayBlockingQueue<WriteFunction> writeQueue = new ArrayBlockingQueue<>(1000);
    private volatile WriteFunction autoCommit = txn -> txn;

    private volatile boolean writing;
    private volatile Thread writingThread;
    private volatile CompletableFuture<Void> completableFuture;

    LmdbEnv(final Env<ByteBuffer> env,
            final ByteBufferPool byteBufferPool) {
        this.env = env;
        this.byteBufferPool = byteBufferPool;
    }

    @Override
    public synchronized void start() {
        if (!writing) {
            writing = true;
            completableFuture = CompletableFuture.runAsync(() -> {
                try {
                    LOGGER.info(() -> "Starting write thread");
                    writingThread = Thread.currentThread();
                    Txn<ByteBuffer> writeTxn = null;
                    try {
                        while (writing) {
                            if (writeTxn == null) {
                                writeTxn = env.txnWrite();
                            }

                            try {
                                final WriteFunction writeCommand = writeQueue.take();
                                writeTxn = writeCommand.apply(writeTxn);
                                writeTxn = autoCommit.apply(writeTxn);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                    } catch (final InterruptedException e) {
                        LOGGER.debug(e::getMessage, e);
                        Thread.currentThread().interrupt();
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    } finally {
                        if (writeTxn != null) {
                            writeTxn.commit();
                            writeTxn.close();
                        }
                        writing = false;
                    }
                    LOGGER.info(() -> "Finished write thread");
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            });
        }
    }

    @Override
    public synchronized void stop() {
        if (writing) {
            writing = false;
            Thread writingThread = this.writingThread;
            if (writingThread != null) {
                writingThread.interrupt();
            }
            completableFuture.join();
            completableFuture = null;
        }
    }

//    public void addPreCommitHook(final Runnable runnable) {
//        preCommitHooks.add(runnable);
//    }
//
//    public void addPostCommitHook(final Runnable runnable) {
//        postCommitHooks.add(runnable);
//    }
//
//    public Dbi<ByteBuffer> openDbi(final String dbName) {
//        final byte[] nameBytes = dbName == null
//                ? null
//                : dbName.getBytes(UTF_8);
//        return writeResult(txn -> env.openDbi(txn, nameBytes, null, DbiFlags.MDB_CREATE));
//    }

    public <K, V> Db<K, V> openDb(final String dbName,
                                  final ExtendedSerde<K> keySerde,
                                  final ExtendedSerde<V> valueSerde) {
        final byte[] nameBytes = dbName == null
                ? null
                : dbName.getBytes(UTF_8);
        final Dbi<ByteBuffer> dbi = writeResult(txn ->
                env.openDbi(txn, nameBytes, null, DbiFlags.MDB_CREATE));
        return new Db<>(this, dbi, byteBufferPool, keySerde, valueSerde);
    }

//    public Dbi<ByteBuffer> openDbi(final String dbName, final DbiFlags... flags) {
//        if (writing) {
//            throw new RuntimeException("Unable to open db while writing.");
//        }
//
//        return env.openDbi(dbName, flags);
//    }

    public void setAutoCommit(final AutoCommit autoCommit) {
        this.autoCommit = autoCommit;
    }

    public void write(final Consumer<Txn<ByteBuffer>> consumer) {
        try {
            final TransferQueue<Boolean> txns = new LinkedTransferQueue<>();
//            final CountDownLatch countDownLatch = new CountDownLatch(1);
            writeFunctionAsync(txn -> {
                consumer.accept(txn);

                try {
                    txns.put(true);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new UncheckedInterruptedException(e);
                }
//                countDownLatch.countDown();
                return txn;
            });
            txns.take();
//            countDownLatch.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public <R> R writeResult(final Function<Txn<ByteBuffer>, R> function) {
        try {
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final AtomicReference<R> ref = new AtomicReference<>();
            writeFunctionAsync(txn -> {
                ref.set(function.apply(txn));
                countDownLatch.countDown();
                return txn;
            });
            countDownLatch.await();
            return ref.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public void writeAsync(final Consumer<Txn<ByteBuffer>> consumer) {
        writeFunctionAsync(txn -> {
            consumer.accept(txn);
            return txn;
        });
    }

    public void writeFunctionAsync(final WriteFunction function) {
        try {
            writeQueue.put(function);
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public void read(final Consumer<Txn<ByteBuffer>> consumer) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            consumer.accept(txn);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public <R> R readResult(final Function<Txn<ByteBuffer>, R> function) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            return function.apply(txn);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public void sync() {
        try {
            final Sync sync = new Sync();
            writeFunctionAsync(sync);
            LOGGER.info("Synchronizing DB");
            sync.await();
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public long count(final Dbi<ByteBuffer> dbi) {
        return readResult(txn -> dbi.stat(txn).entries);
    }

//    public long count(final Dbi<ByteBuffer> dbi) {
//        return readResult(txn -> count(txn, dbi));
//    }
//
//    public long count2(final Dbi<ByteBuffer> dbi) {
//        return readResult(txn -> count2(txn, dbi));
//    }
//
//    public long count(final Txn<ByteBuffer> txn, final Dbi<ByteBuffer> dbi) {
//        long count = 0;
//        try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn)) {
//            for (final KeyVal<ByteBuffer> kv : cursor) {
//                count++;
//            }
//        }
//        return count;
//    }
//
//    public long count2(final Txn<ByteBuffer> txn, final Dbi<ByteBuffer> dbi) {
//        long count = 0;
//        try (final Cursor<ByteBuffer> cursor = dbi.openCursor(txn)) {
//            if (cursor.seek(SeekOp.MDB_FIRST)) {
//                do {
//                    count++;
//                } while (cursor.next());
//            }
//        }
//        return count;
//    }
//
//    public void clear(final Dbi<ByteBuffer> dbi) {
//        read(txn -> LOGGER.info(dbi.stat(txn).toString()));
//        write(txn -> dbi.drop(txn, false));
//        read(txn -> LOGGER.info(dbi.stat(txn).toString()));
////        sync();
//        if (count(dbi) > 0) {
//            throw new RuntimeException("Failed to clear");
//        }
//    }
//
//    public <T> Optional<T> getMinKey(final Dbi<ByteBuffer> dbi,
//                                     final Serde<T> serde) {
//        KeyRange<ByteBuffer> keyRange = KeyRange.all();
//        return getNextKey(dbi, serde, keyRange);
//    }
//
//    public <T> Optional<T> getMaxKey(final Dbi<ByteBuffer> dbi,
//                                     final Serde<T> serde) {
//        KeyRange<ByteBuffer> keyRange = KeyRange.allBackward();
//        return getNextKey(dbi, serde, keyRange);
//    }
//
//    public <T> Optional<T> getNextKey(final Dbi<ByteBuffer> dbi,
//                                      final Serde<T> serde,
//                                      final KeyRange<ByteBuffer> keyRange) {
//        return readResult(txn -> {
//            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn, keyRange)) {
//                for (final KeyVal<ByteBuffer> kv : cursor) {
//                    return Optional.of(serde.deserialize(kv.key()));
//                }
//            }
//            return Optional.empty();
//        });
//    }

    public ByteBufferPool getByteBufferPool() {
        return byteBufferPool;
    }

    private static class Sync implements WriteFunction {

        final CountDownLatch complete = new CountDownLatch(1);

        @Override
        public Txn<ByteBuffer> apply(final Txn<ByteBuffer> txn) {
            txn.commit();
            txn.close();
            complete.countDown();
            return null;
        }

        public void await() throws InterruptedException {
            complete.await();
        }
    }

    public interface WriteFunction extends Function<Txn<ByteBuffer>, Txn<ByteBuffer>> {

    }
}
