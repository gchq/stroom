package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.dao.lmdb.serde.Serde;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class LmdbEnv implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnv.class);
    private static final Commit COMMIT = new Commit();

    private final Env<ByteBuffer> env;
    private final ArrayBlockingQueue<WriteCommand> writeQueue = new ArrayBlockingQueue<>(1000);
    private volatile AutoCommit autoCommit;
    private final List<Runnable> preCommitHooks = new ArrayList<>();
    private final List<Runnable> postCommitHooks = new ArrayList<>();
    private volatile boolean writing;
    private volatile Thread writingThread;
    private volatile CompletableFuture<Void> completableFuture;

    LmdbEnv(final Env<ByteBuffer> env) {
        this.env = env;
    }

    @Override
    public synchronized void start() {
        if (!writing) {
            writing = true;
            completableFuture = CompletableFuture.runAsync(() -> {
                try {
                    LOGGER.info(() -> "Starting write thread");
                    writingThread = Thread.currentThread();
                    Txn<ByteBuffer> writeTxn = env.txnWrite();
                    try {
                        while (writing) {
                            try {
                                final WriteCommand writeCommand = writeQueue.poll(10, TimeUnit.SECONDS);
                                boolean committed = false;
                                if (writeCommand != null) {
                                    writeCommand.accept(writeTxn);

                                    if (writeCommand.commit()) {
                                        writeTxn = commit(env, writeTxn);
                                        committed = true;
                                    }
                                }

                                if (!committed && autoCommit != null) {
                                    if (autoCommit.shouldCommit()) {
                                        writeTxn = commit(env, writeTxn);
                                    }
                                }
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
                        writeTxn.commit();
                        writeTxn.close();
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

    public void addPreCommitHook(final Runnable runnable) {
        preCommitHooks.add(runnable);
    }

    public void addPostCommitHook(final Runnable runnable) {
        postCommitHooks.add(runnable);
    }

    public Dbi<ByteBuffer> openDbi(final String dbName) {
        if (writing) {
            throw new RuntimeException("Unable to open db while writing.");
        }

        return env.openDbi(dbName, DbiFlags.MDB_CREATE);
    }

    public Dbi<ByteBuffer> openDbi(final String dbName, final DbiFlags... flags) {
        if (writing) {
            throw new RuntimeException("Unable to open db while writing.");
        }

        return env.openDbi(dbName, flags);
    }

    private Txn<ByteBuffer> commit(final Env<ByteBuffer> env, final Txn<ByteBuffer> writeTxn) {
        // Run pre commit hooks.
        for (final Runnable runnable : preCommitHooks) {
            runnable.run();
        }
        writeTxn.commit();
        writeTxn.close();
        // Run post commit hooks.
        for (final Runnable runnable : postCommitHooks) {
            runnable.run();
        }
        // Create a new write transaction.
        return env.txnWrite();
    }

    public void setAutoCommit(final AutoCommit autoCommit) {
        this.autoCommit = autoCommit;
    }

    public void write(final WriteCommand writeCommand) {
        try {
            writeQueue.put(writeCommand);
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
            write(sync);
            LOGGER.info("Synchronizing DB");
            sync.await();
            env.sync(true);
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public void commit() {
        write(COMMIT);
    }

    public long count(final Dbi<ByteBuffer> dbi) {
        read(txn -> LOGGER.info(dbi.stat(txn).toString()));

        return readResult(txn -> {
            long count = 0;
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn)) {
                for (final KeyVal<ByteBuffer> kv : cursor) {
                    count++;
                }
            }
            return count;
        });
    }

    public void clear(final Dbi<ByteBuffer> dbi) {
        write(txn -> dbi.drop(txn, false));
        sync();
    }

    public <T> Optional<T> getMinKey(final Dbi<ByteBuffer> dbi,
                                     final Serde<T> serde) {
        KeyRange<ByteBuffer> keyRange = KeyRange.all();
        return getNextKey(dbi, serde, keyRange);
    }

    public <T> Optional<T> getMaxKey(final Dbi<ByteBuffer> dbi,
                                     final Serde<T> serde) {
        KeyRange<ByteBuffer> keyRange = KeyRange.allBackward();
        return getNextKey(dbi, serde, keyRange);
    }

    public <T> Optional<T> getNextKey(final Dbi<ByteBuffer> dbi,
                                      final Serde<T> serde,
                                      final KeyRange<ByteBuffer> keyRange) {
        return readResult(txn -> {
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn, keyRange)) {
                for (final KeyVal<ByteBuffer> kv : cursor) {
                    return Optional.of(serde.deserialise(kv.key()));
                }
            }
            return Optional.empty();
        });
    }

    private static class Sync implements WriteCommand {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Sync.class);

        final CountDownLatch complete = new CountDownLatch(1);

        @Override
        public void accept(final Txn<ByteBuffer> txn) {
            LOGGER.info(() -> "sync countdown before");
            complete.countDown();
            LOGGER.info(() -> "sync countdown after");
        }

        public void await() throws InterruptedException {
            LOGGER.info(() -> "sync await before");
            complete.await();
            LOGGER.info(() -> "sync await after");
        }

        @Override
        public boolean commit() {
            return true;
        }
    }

    private static class Commit implements WriteCommand {

        @Override
        public void accept(final Txn<ByteBuffer> txn) {

        }

        @Override
        public boolean commit() {
            return true;
        }
    }

    public interface WriteCommand {

        void accept(Txn<ByteBuffer> txn);

        default boolean commit() {
            return false;
        }
    }
}
