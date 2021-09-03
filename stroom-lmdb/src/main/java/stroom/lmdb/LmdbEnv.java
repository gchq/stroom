package stroom.lmdb;


import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.collect.ImmutableMap;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvInfo;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A wrapper around {@link org.lmdbjava.Env<java.nio.ByteBuffer>} with additional concurrency
 * protection features to control access to the transactions.
 */
public class LmdbEnv implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnv.class);

    private final Path path;
    private final Env<ByteBuffer> env;

    // Lock to ensure only one thread can hold a write txn at once.
    // If doWritesBlockReads is true then will only one thread can hold an open txn
    // of any kind at once.
    private final ReentrantLock writeTxnLock = new ReentrantLock();
    private final Function<Function<Txn<ByteBuffer>, ?>, ?> readTxnMethod;

    public LmdbEnv(final Path path,
                   final Env<ByteBuffer> env) {
        this(path, env, false);
    }

    public LmdbEnv(final Path path,
                   final Env<ByteBuffer> env,
                   final boolean doWritesBlockReads) {
        this.path = path;
        this.env = env;

        if (doWritesBlockReads) {
            // Read txns open concurrently with write txns mean the writes can't reclaim unused space
            // in the db, so can lead to excessive growth of the db file.
            LOGGER.debug("Initialising Environment with doWritesBlockReads: {}",
                    doWritesBlockReads);
            readTxnMethod = this::doWithReadTxnUnderWriteLock;
        } else {
            // Limit concurrent readers java side to ensure we don't get a max readers reached error
            final int maxReaders = env.info().maxReaders;

            LOGGER.debug("Initialising Environment with permits: {}, doWritesBlockReads: {}",
                    maxReaders,
                    doWritesBlockReads);

            final Semaphore activeReadTransactionsSemaphore = new Semaphore(maxReaders);
            readTxnMethod = work ->
                    doWithReadTxnUnderMaxReaderSemaphore(work, activeReadTransactionsSemaphore);
        }
    }

    public Dbi<ByteBuffer> openDbi(final String name,
                                   final DbiFlags... dbiFlags) {

        final DbiFlags[] flags = (dbiFlags != null && dbiFlags.length > 0)
                ? dbiFlags
                : (new DbiFlags[]{DbiFlags.MDB_CREATE});

        LOGGER.debug(() ->
                LogUtil.message("Opening LMDB database with name: {}, flags: {}, path: {}",
                        name,
                        Arrays.toString(flags),
                        path.toAbsolutePath().normalize()));
        try {
            return env.openDbi(name, DbiFlags.MDB_CREATE);
        } catch (final Exception e) {
            final String message = LogUtil.message("Error opening LMDB database '{}' in '{}' ({})",
                    name,
                    FileUtil.getCanonicalPath(path),
                    e.getMessage());

            LOGGER.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    public void doWithWriteTxn(final Consumer<Txn<ByteBuffer>> work) {
        getWithWriteTxn(txn -> {
            work.accept(txn);
            return null;
        });
    }

    /**
     * Get a value using a write transaction. The txn will be committed after
     * the work is complete. work should not commit.
     */
    public <T> T getWithWriteTxn(final Function<Txn<ByteBuffer>, T> work) {

        LOGGER.trace("Acquiring write txn lock");
        try {
            try {
                writeTxnLock.lockInterruptibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrupted while waiting for write lock on "
                        + path.toAbsolutePath().normalize());
            }

            LOGGER.trace("About to open write tx");
            try (final Txn<ByteBuffer> writeTxn = env.txnWrite()) {
                LOGGER.trace("Performing work with write txn");
                T result = work.apply(writeTxn);
                LOGGER.trace("Committing the txn");
                writeTxn.commit();
                return result;
            } catch (RuntimeException e) {
                throw new RuntimeException(LogUtil.message(
                        "Error performing work in read transaction: {}",
                        e.getMessage()), e);
            }
        } finally {
            LOGGER.trace("Releasing the write lock");
            writeTxnLock.unlock();
        }
    }

    /**
     * @return An {@link AutoCloseable} wrapper round the open write txn that also releases
     * the single write lock.
     */
    public ClosableWriteTxn openWriteTxn() {
        try {
            LOGGER.trace("Acquiring write txn lock");
            writeTxnLock.lockInterruptibly();

            LOGGER.trace("Opening new write txn");
            return new ClosableWriteTxn(writeTxnLock, env.txnWrite());
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread interrupted while waiting for write lock on "
                    + path.toAbsolutePath().normalize());
        }
    }

    public <T> T getWithReadTxn(final Function<Txn<ByteBuffer>, T> work) {
        return (T) readTxnMethod.apply(work);
    }

    public void doWithReadTxn(final Consumer<Txn<ByteBuffer>> work) {
        readTxnMethod.apply(txn -> {
            work.accept(txn);
            return null;
        });
    }

    private <T> T doWithReadTxnUnderMaxReaderSemaphore(final Function<Txn<ByteBuffer>, T> work,
                                                       final Semaphore activeReadTransactionsSemaphore) {
        try {
            LOGGER.trace("About to acquire permit");
            activeReadTransactionsSemaphore.acquire();
            LOGGER.trace("Permit acquired");

            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                LOGGER.trace("Performing work with read txn");
                return work.apply(txn);
            } catch (RuntimeException e) {
                throw new RuntimeException(LogUtil.message(
                        "Error performing work in read transaction: {}",
                        e.getMessage()), e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        } finally {
            LOGGER.trace("Releasing permit");
            activeReadTransactionsSemaphore.release();
        }
    }

    public <T> T doWithReadTxnUnderWriteLock(final Function<Txn<ByteBuffer>, T> work) {
        try {
            LOGGER.trace("About to acquire lock");
            writeTxnLock.lockInterruptibly();
            LOGGER.trace("Lock acquired");

            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                LOGGER.trace("Performing work with read txn");
                return work.apply(txn);
            } catch (RuntimeException e) {
                throw new RuntimeException(LogUtil.message(
                        "Error performing work in read transaction: {}",
                        e.getMessage()), e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        } finally {
            LOGGER.trace("Releasing lock");
            writeTxnLock.unlock();
        }
    }

    @Override
    public void close() {
        LOGGER.debug(() -> "Closing LMDB environment at " + path.toAbsolutePath().normalize());
        env.close();
    }

    public void delete() {
        if (!env.isClosed()) {
            throw new RuntimeException(("LMDB environment at {} is still open"));
        }
        if (!FileUtil.deleteDir(path)) {
            throw new RuntimeException("Unable to delete dir: " + FileUtil.getCanonicalPath(path));
        }
    }

    public List<String> getDbiNames() {
        return env.getDbiNames().stream()
                .map(bytes ->
                        new String(bytes, StandardCharsets.UTF_8))
                .collect(Collectors.toList());
    }

    public int getMaxKeySize() {
        return env.getMaxKeySize();
    }

    public EnvInfo info() {
        return env.info();
    }

    public boolean isClosed() {
        return env.isClosed();
    }

    public Stat stat() {
        return env.stat();
    }

    public Map<String, String> getEnvInfo() {
        return getWithReadTxn(txn -> {
            final Map<String, String> statMap = convertStatToMap(env.stat());
            final Map<String, String> envInfo = convertEnvInfoToMap(env.info());

            final String dbNames = String.join(",", getDbiNames());

            return ImmutableMap.<String, String>builder()
                    .putAll(statMap)
                    .putAll(envInfo)
                    .put("maxKeySize", Integer.toString(env.getMaxKeySize()))
                    .put("dbNames", dbNames)
                    .build();
        });
    }

    public Map<String, String> getDbInfo(final Dbi<ByteBuffer> db) {
        return getWithReadTxn(txn -> {
            final Stat stat = db.stat(txn);
            return convertStatToMap(stat);
        });
    }

    private static ImmutableMap<String, String> convertStatToMap(final Stat stat) {
        return ImmutableMap.<String, String>builder()
                .put("pageSize", Integer.toString(stat.pageSize))
                .put("branchPages", Long.toString(stat.branchPages))
                .put("depth", Integer.toString(stat.depth))
                .put("entries", Long.toString(stat.entries))
                .put("leafPages", Long.toString(stat.leafPages))
                .put("overFlowPages", Long.toString(stat.overflowPages))
                .build();
    }

    private static ImmutableMap<String, String> convertEnvInfoToMap(final EnvInfo envInfo) {
        return ImmutableMap.<String, String>builder()
                .put("maxReaders", Integer.toString(envInfo.maxReaders))
                .put("numReaders", Integer.toString(envInfo.numReaders))
                .put("lastPageNumber", Long.toString(envInfo.lastPageNumber))
                .put("lastTransactionId", Long.toString(envInfo.lastTransactionId))
                .put("mapAddress", Long.toString(envInfo.mapAddress))
                .put("mapSize", Long.toString(envInfo.mapSize))
                .build();
    }

    @NotThreadSafe
    public static class ClosableWriteTxn implements AutoCloseable {

        private final ReentrantLock writeLock;
        private Txn<ByteBuffer> writeTxn;

        /**
         * @param writeLock Should already be held by this thread.
         */
        private ClosableWriteTxn(final ReentrantLock writeLock,
                                 final Txn<ByteBuffer> writeTxn) {
            this.writeLock = writeLock;
            this.writeTxn = writeTxn;
        }

        /**
         * @return The write txn object. Do NOT call close() on the returned txn,
         * use {@link ClosableWriteTxn#close()} or a try-with-resources block.
         */
        public Txn<ByteBuffer> getTxn() {
            Objects.requireNonNull(writeTxn, "Transaction is closed");
            return writeTxn;
        }

        /**
         * {@link Txn#abort()}
         */
        public void abort() {
            writeTxn.abort();
        }

        /**
         * {@link Txn#commit()}
         */
        public void commit() {
            writeTxn.commit();
        }

        /**
         * {@link Txn#renew()}
         */
        public void renew() {
            writeTxn.renew();
        }

        /**
         * {@link Txn#reset()}
         */
        public void reset() {
            writeTxn.reset();
        }

        /**
         * Closes the txn and releases the single write lock
         */
        @Override
        public void close() throws Exception {
            Objects.requireNonNull(writeTxn, "Transaction has already been closed");
            try {
                writeTxn.close();
            } finally {
                if (writeLock.isHeldByCurrentThread()) {
                    writeLock.unlock();
                }
                writeTxn = null;
            }
        }
    }
}
