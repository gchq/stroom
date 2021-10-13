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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A wrapper around {@link org.lmdbjava.Env<java.nio.ByteBuffer>} with additional concurrency
 * protection features to control the number of concurrent read and write transactions.
 */
public class LmdbEnv implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnv.class);

    private final Path localDir;
    private final Env<ByteBuffer> env;

    // Lock to ensure only one thread can hold a write txn at once.
    // If doWritesBlockReads is true then will only one thread can hold an open txn
    // of any kind at once.
    private final Lock writeTxnLock;
    private final Function<Function<Txn<ByteBuffer>, ?>, ?> readTxnGetMethod;
    private final ReadWriteLock readWriteLock;
    private final Semaphore activeReadTransactionsSemaphore;

    LmdbEnv(final Path localDir,
            final Env<ByteBuffer> env) {
        this(localDir, env, false);
    }

    LmdbEnv(final Path localDir,
            final Env<ByteBuffer> env,
            final boolean isReaderBlockedByWriter) {
        this.localDir = localDir;
        this.env = env;

        // Limit concurrent readers java side to ensure we don't get a max readers reached error
        final int maxReaders = env.info().maxReaders;
        activeReadTransactionsSemaphore = new Semaphore(maxReaders);

        if (isReaderBlockedByWriter) {
            // Read/write lock enforces writes block reads and the semphore ensures we don't have
            // too many readers.
            readWriteLock = new StampedLock().asReadWriteLock();
            writeTxnLock = readWriteLock.writeLock();
            // Read txns open concurrently with write txns mean the writes can't reclaim unused space
            // in the db, so can lead to excessive growth of the db file.
            LOGGER.debug("Initialising Environment with isReaderBlockedByWriter: {}",
                    isReaderBlockedByWriter);
            readTxnGetMethod = work ->
                    getWithReadTxnUnderReadWriteLock(work, readWriteLock.readLock());
        } else {
            // No lock for readers, only the sempaphor to enforce max concurrent readers
            // Simple re-entrant lock to enforce max one concurrent writer
            readWriteLock = null;
            writeTxnLock = new ReentrantLock();

            LOGGER.debug("Initialising Environment with permits: {}, isReaderBlockedByWriter: {}",
                    maxReaders,
                    isReaderBlockedByWriter);

            readTxnGetMethod = this::getWithReadTxnUnderMaxReaderSemaphore;
        }
    }

    public Path getLocalDir() {
        return localDir;
    }

    /**
     * @link Env#sync
     */
    public void sync(final boolean force) {
        env.sync(force);
    }

    /**
     * Opens a database with the supplied name. If no dbiFlags are supplied then
     * {@link DbiFlags#MDB_CREATE} is used to create the database if it doesn't exist.
     */
    public Dbi<ByteBuffer> openDbi(final String name,
                                   final DbiFlags... dbiFlags) {

        final DbiFlags[] flags = (dbiFlags != null && dbiFlags.length > 0)
                ? dbiFlags
                : (new DbiFlags[]{DbiFlags.MDB_CREATE});

        LOGGER.debug(() ->
                LogUtil.message("Opening LMDB database with name: {}, flags: {}, path: {}",
                        name,
                        Arrays.toString(flags),
                        localDir.toAbsolutePath().normalize()));
        try {
            return env.openDbi(name, DbiFlags.MDB_CREATE);
        } catch (final Exception e) {
            final String message = LogUtil.message("Error opening LMDB database '{}' in '{}' ({})",
                    name,
                    FileUtil.getCanonicalPath(localDir),
                    e.getMessage());

            LOGGER.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    /**
     * Perform the supplied work using a new write transaction. The transaction will
     * be committed and closed after performing the work.
     * The txn should be left in a state where a commit is permitted.
     */
    public void doWithWriteTxn(final Consumer<Txn<ByteBuffer>> work) {
        getWithWriteTxn(txn -> {
            work.accept(txn);
            return null;
        });
    }

    /**
     * Get a value using a write transaction. The txn will be committed and closed after
     * the work is complete.
     * The txn should be left in a state where a commit is permitted.
     */
    public <T> T getWithWriteTxn(final Function<Txn<ByteBuffer>, T> work) {

        LOGGER.trace("Acquiring write txn lock");
        try {
            writeTxnLock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting for write lock on "
                    + localDir.toAbsolutePath().normalize());
        }
        try {
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
     * the single write lock. A call to this method will result in a write lock being obtained.
     */
    public WriteTxnWrapper openWriteTxn() {
        try {
            LOGGER.trace("Acquiring write txn lock");
            writeTxnLock.lockInterruptibly();

            LOGGER.trace("Opening new write txn");
            return new WriteTxnWrapper(writeTxnLock, env.txnWrite());
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread interrupted while waiting for write lock on "
                    + localDir.toAbsolutePath().normalize());
        }
    }

    /**
     * @param batchSize
     * @return An {@link AutoCloseable} wrapper that can provide multiple write txns all while holding
     * the single write lock. Useful for large jobs that need to commit periodically but don't want to release
     * the lock to avoid the risk of deadlocks.
     * A call to this method will result in a write lock being obtained.
     */
    public BatchingWriteTxnWrapper openBatchingWriteTxn(final int batchSize) {
        try {
            LOGGER.trace("Acquiring write txn lock");
            writeTxnLock.lockInterruptibly();

            return new BatchingWriteTxnWrapper(writeTxnLock, env::txnWrite, batchSize);
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread interrupted while waiting for write lock on "
                    + localDir.toAbsolutePath().normalize());
        }
    }

    public <T> T getWithReadTxn(final Function<Txn<ByteBuffer>, T> work) {
        return (T) readTxnGetMethod.apply(work);
    }

    public void doWithReadTxn(final Consumer<Txn<ByteBuffer>> work) {
        readTxnGetMethod.apply(txn -> {
            work.accept(txn);
            return null;
        });
    }

    private <T> T getWithReadTxnUnderMaxReaderSemaphore(final Function<Txn<ByteBuffer>, T> work) {
        LOGGER.trace("About to acquire permit");
        try {
            activeReadTransactionsSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        try {
            LOGGER.trace(() ->
                    LogUtil.message("Permit acquired, remaining {}, queue length {}",
                            activeReadTransactionsSemaphore.availablePermits(),
                            activeReadTransactionsSemaphore.getQueueLength()));

            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                LOGGER.trace("Performing work with read txn");
                return work.apply(txn);
            } catch (RuntimeException e) {
                throw new RuntimeException(LogUtil.message(
                        "Error performing work in read transaction: {}",
                        e.getMessage()), e);
            }
        } finally {
            LOGGER.trace("Releasing permit");
            activeReadTransactionsSemaphore.release();
        }
    }

    public <T> T getWithReadTxnUnderReadWriteLock(final Function<Txn<ByteBuffer>, T> work,
                                                  final Lock readLock) {
        try {
            LOGGER.trace("About to acquire lock");
            // Wait for writers to finish
            readLock.lockInterruptibly();
            LOGGER.trace("Lock acquired");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted", e);
        }

        try {
            return getWithReadTxnUnderMaxReaderSemaphore(work);
        } finally {
            LOGGER.trace("Releasing lock");
            readLock.unlock();
        }
    }

    @Override
    public void close() {
        LOGGER.debug(() -> "Closing LMDB environment at " + localDir.toAbsolutePath().normalize());
        env.close();
    }

    /**
     * Deletes {@link LmdbEnv} from the filesystem if it is already closed.
     */
    public void delete() {
        if (!env.isClosed()) {
            throw new RuntimeException(("LMDB environment at {} is still open"));
        }
        if (!FileUtil.deleteDir(localDir)) {
            throw new RuntimeException("Unable to delete dir: " + FileUtil.getCanonicalPath(localDir));
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
    public static class WriteTxnWrapper implements AutoCloseable {

        private final Lock writeLock;
        private Txn<ByteBuffer> writeTxn;

        /**
         * @param writeLock Should already be held by this thread.
         */
        private WriteTxnWrapper(final Lock writeLock,
                                final Txn<ByteBuffer> writeTxn) {
            this.writeLock = writeLock;
            this.writeTxn = writeTxn;
        }

        /**
         * @return The write txn object. Do NOT call close() on the returned txn,
         * use {@link WriteTxnWrapper#close()} or a try-with-resources block.
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
         * Closes the txn and releases the single write lock
         */
        @Override
        public void close() throws Exception {
            Objects.requireNonNull(writeTxn, "Transaction has already been closed");
            try {
                writeTxn.close();
            } finally {
                writeLock.unlock();
                writeTxn = null;
            }
        }
    }

    /**
     * Creates a write txn on calls to {@link BatchingWriteTxnWrapper#getTxn()}
     */
    @NotThreadSafe
    public static class BatchingWriteTxnWrapper implements AutoCloseable {

        private final Lock writeLock;
        private Supplier<Txn<ByteBuffer>> writeTxnSupplier;
        private Txn<ByteBuffer> writeTxn;

        private final int maxBatchSize;
        private int batchCounter = 0;
        private final BooleanSupplier commitFunc;

        /**
         * @param writeLock    Should already be held by this thread.
         * @param maxBatchSize
         */
        private BatchingWriteTxnWrapper(final Lock writeLock,
                                        final Supplier<Txn<ByteBuffer>> writeTxnSupplier,
                                        final int maxBatchSize) {
            this.writeLock = writeLock;
            this.writeTxnSupplier = writeTxnSupplier;
            this.maxBatchSize = maxBatchSize == 0
                    ? Integer.MAX_VALUE
                    : maxBatchSize;

            if (maxBatchSize == 0) {
                commitFunc = () -> {
                    // a max batch size of zero means don't commit
                    return false;
                };
            } else {
                commitFunc = this::commitWithBatchCheck;
            }
        }

        /**
         * @return The write txn object. Do NOT call close() on the returned txn,
         * use {@link WriteTxnWrapper#close()} or a try-with-resources block.
         */
        public Txn<ByteBuffer> getTxn() {
            if (writeTxn == null) {
                Objects.requireNonNull(writeTxnSupplier, "Has already been closed");
                writeTxn = writeTxnSupplier.get();
            }
            return writeTxn;
        }

        /**
         * {@link Txn#abort()}
         */
        public void abort() {
            if (writeTxn != null) {
                writeTxn.abort();
                writeTxn = null;
            }
        }

        /**
         * Increment the count of items processed in the batch
         *
         * @return True if the batch is full, false if not.
         */
        public boolean incrementBatchCount() {
            return (++batchCounter >= maxBatchSize);
        }

        /**
         * Force a commit regardless of batch size
         * {@link Txn#commit()}
         */
        public boolean commit() {
            if (writeTxn != null) {
                LOGGER.trace("Committing txn with batchCounter: {}", batchCounter);
                writeTxn.commit();
                writeTxn.close();
                writeTxn = null;
                batchCounter = 0;
                return true;
            } else {
                return false;
            }
        }

        /**
         * Commit if the batch has reach its max size
         */
        public boolean commitIfRequired() {
            return commitFunc.getAsBoolean();
        }

        private boolean commitWithBatchCheck() {
            if (++batchCounter >= maxBatchSize) {
                return commit();
            } else {
                return false;
            }
        }

        /**
         * Closes the txn and releases the single write lock
         */
        @Override
        public void close() throws Exception {
            try {
                if (writeTxn != null) {
                    try {
                        writeTxn.close();
                    } finally {
                        writeTxn = null;
                        writeTxnSupplier = null;
                    }
                }
            } finally {
                // whatever happens we must release the lock
                writeLock.unlock();
            }
        }

        @Override
        public String toString() {
            return "BatchingWriteTxnWrapper{" +
                    "maxBatchSize=" + maxBatchSize +
                    ", batchCounter=" + batchCounter +
                    '}';
        }
    }
}
