/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.lmdb;


import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import com.google.common.collect.ImmutableMap;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.EnvInfo;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A wrapper around {@link org.lmdbjava.Env<java.nio.ByteBuffer>} with additional concurrency
 * protection features to control the number of concurrent read and write transactions.
 */
public class LmdbEnv implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnv.class);
    private static final String DATA_FILE_NAME = "data.mdb";
    private static final String LOCK_FILE_NAME = "lock.mdb";

    private final Path localDir;
    private final boolean isDedicatedDir;
    private final String name;
    private final Env<ByteBuffer> env;
    private final Set<EnvFlags> envFlags;

    // Lock to ensure only one thread can hold a write txn at once.
    // If doWritesBlockReads is true then will only one thread can hold an open txn
    // of any kind at once.
    private final Lock writeTxnLock;
    private final Function<Function<Txn<ByteBuffer>, ?>, ?> readTxnGetMethod;
    private final ReadWriteLock readWriteLock;
    private final Semaphore activeReadTransactionsSemaphore;

    /**
     * @param localDir                The directory where the LMDB env will be persisted or read from if it
     *                                already exists.
     * @param name                    A name for the environment.
     * @param env                     The actual LMDB env.
     * @param envFlags                The flags used when the LMDB env was created. Mostly for debug purposes.
     * @param isReaderBlockedByWriter Set to true if you want writes to block reads. If false reads can happen
     *                                concurrently with writes. Writes always block other writes.
     * @param isDedicatedDir          True if localDir is dedicated to this LMDB env and contains no other files.
     *                                When {@link LmdbEnv#delete()} is called, if isDedicatedDir is true,
     *                                localDir will be deleted, else it will just delete the LMDB .mdb files and
     *                                leave localDir present.
     */
    LmdbEnv(final Path localDir,
            final String name,
            final Env<ByteBuffer> env,
            final Set<EnvFlags> envFlags,
            final boolean isReaderBlockedByWriter,
            final boolean isDedicatedDir) {
        this.localDir = localDir;
        this.isDedicatedDir = isDedicatedDir;
        this.name = name;
        this.env = env;
        this.envFlags = Collections.unmodifiableSet(envFlags);

        // Limit concurrent readers java side to ensure we don't get a max readers reached error
        final int maxReaders = env.info().maxReaders;
        activeReadTransactionsSemaphore = new Semaphore(maxReaders);

        if (isReaderBlockedByWriter) {
            // Read/write lock enforces writes block reads and the semaphore ensures we don't have
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
            // No lock for readers, only the semaphore to enforce max concurrent readers
            // Simple re-entrant lock to enforce max one concurrent writer
            readWriteLock = null;
            writeTxnLock = new ReentrantLock();

            LOGGER.debug("Initialising Environment with permits: {}, isReaderBlockedByWriter: {}",
                    maxReaders,
                    isReaderBlockedByWriter);

            readTxnGetMethod = this::getWithReadTxnUnderMaxReaderSemaphore;
        }
    }

    public static boolean isLmdbDataFile(final Path file) {
        return file != null
               && (file.endsWith(DATA_FILE_NAME) || file.endsWith(LOCK_FILE_NAME));
    }

    /**
     * @return The number of permits available for new read txns. For info purposes only,
     * not for concurrency control.
     */
    public int getAvailableReadPermitCount() {
        return activeReadTransactionsSemaphore.availablePermits();
    }

    public Path getLocalDir() {
        return localDir;
    }

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    /**
     * @link Env#sync
     */
    public void sync(final boolean force) {
        env.sync(force);
    }

    /**
     * @return This set of {@link EnvFlags} that were used when this env was created.
     */
    public Set<EnvFlags> getEnvFlags() {
        return envFlags;
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
            return env.openDbi(name, flags);
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

        final Runnable postAcquireAction = LOGGER.isDebugEnabled()
                ? createWaitLoggingAction("writeTxnLock")
                : null;

        try {
            writeTxnLock.lockInterruptibly();
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create("Thread interrupted while waiting for write lock on "
                                                       + localDir.toAbsolutePath().normalize(), e);
        }

        if (postAcquireAction != null) {
            postAcquireAction.run();
        }

        try {
            LOGGER.trace("About to open write tx");
            try (final Txn<ByteBuffer> writeTxn = env.txnWrite()) {
                LOGGER.trace("Performing work with write txn");
                final T result = work.apply(writeTxn);
                LOGGER.trace("Committing the txn");
                writeTxn.commit();
                return result;
            } catch (final RuntimeException e) {
                throw new RuntimeException(LogUtil.message(
                        "Error performing work in write transaction: {}",
                        e.getMessage()), e);
            }
        } finally {
            LOGGER.trace("Releasing writeTxnLock");
            writeTxnLock.unlock();
        }
    }

    /**
     * @return An {@link AutoCloseable} wrapper round the open write txn that also releases
     * the single write lock. A call to this method will result in a write lock being obtained.
     */
    public WriteTxn openWriteTxn() {
        try {
            final Runnable postAcquireAction = LOGGER.isDebugEnabled()
                    ? createWaitLoggingAction("writeTxnLock")
                    : null;

            writeTxnLock.lockInterruptibly();

            if (postAcquireAction != null) {
                postAcquireAction.run();
            }

            LOGGER.trace("Opening new write txn");
            return new WriteTxn(writeTxnLock, env.txnWrite());
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create("Thread interrupted while waiting for write lock on "
                                                       + localDir.toAbsolutePath().normalize(), e);
        }
    }

    /**
     * @param batchSize
     * @return An {@link AutoCloseable} wrapper that can provide multiple write txns all while holding
     * the single write lock. Useful for large jobs that need to commit periodically but don't want to release
     * the lock to avoid the risk of deadlocks.
     * A call to this method will result in a write lock being obtained.
     * Should be used in a try-with-resources block to ensure the write lock that it obtains is released.
     */
    public BatchingWriteTxn openBatchingWriteTxn(final int batchSize) {
        try {
            final Runnable postAcquireAction = LOGGER.isDebugEnabled()
                    ? createWaitLoggingAction("writeTxnLock (batching)")
                    : null;

            writeTxnLock.lockInterruptibly();

            if (postAcquireAction != null) {
                postAcquireAction.run();
            }

            return new BatchingWriteTxn(writeTxnLock, env::txnWrite, batchSize);
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create("Thread interrupted while waiting for write lock on "
                                                       + localDir.toAbsolutePath().normalize(), e);
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
        try {
            acquireReadTxnPermit();

            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                LOGGER.trace("Performing work with read txn");
                return work.apply(txn);
            } catch (final RuntimeException e) {
                throw new RuntimeException(LogUtil.message(
                        "Error performing work in read transaction: {}",
                        e.getMessage()), e);
            }
        } finally {
            activeReadTransactionsSemaphore.release();

            LOGGER.trace(() ->
                    LogUtil.message("activeReadTransactionsSemaphore permit released, " +
                                    "remaining {}, queue length {}",
                            activeReadTransactionsSemaphore.availablePermits(),
                            activeReadTransactionsSemaphore.getQueueLength()));
        }
    }

    private void acquireReadTxnPermit() {
        final Runnable postAcquireAction = LOGGER.isDebugEnabled()
                ? createWaitLoggingAction("activeReadTransactionsSemaphore")
                : null;
        boolean havePermit = false;
        try {
            int cnt = 1;
            final int timeoutSecs = 30;
            while (!havePermit) {
                havePermit = activeReadTransactionsSemaphore.tryAcquire(timeoutSecs, TimeUnit.MICROSECONDS);

                if (!havePermit && LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Still waiting for a permit, waited approx {}s so far.", cnt * timeoutSecs);
                    cnt++;
                }
            }

        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create("Thread interrupted while waiting for read permit on "
                                                       + localDir.toAbsolutePath().normalize(), e);
        }

        if (postAcquireAction != null) {
            postAcquireAction.run();
        }

        LOGGER.trace(() ->
                LogUtil.message("activeReadTransactionsSemaphore permit acquired, remaining {}, queue length {}",
                        activeReadTransactionsSemaphore.availablePermits(),
                        activeReadTransactionsSemaphore.getQueueLength()));
    }

    public <T> T getWithReadTxnUnderReadWriteLock(final Function<Txn<ByteBuffer>, T> work,
                                                  final Lock readLock) {
        try {
            final Runnable postAcquireAction = LOGGER.isDebugEnabled()
                    ? createWaitLoggingAction("readLock")
                    : null;

            // Wait for a writer to finish
            readLock.lockInterruptibly();

            if (postAcquireAction != null) {
                postAcquireAction.run();
            }

        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create("Thread interrupted while waiting for read lock on "
                                                       + localDir.toAbsolutePath().normalize(), e);
        }

        try {
            return getWithReadTxnUnderMaxReaderSemaphore(work);
        } finally {
            LOGGER.trace("Releasing readLock");
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

        LOGGER.debug("Deleting LMDB environment {} and all its contents", localDir.toAbsolutePath().normalize());

        // May be useful to see the sizes of db before they are deleted
        LOGGER.doIfDebugEnabled(this::dumpMdbFileSize);

        if (Files.isDirectory(localDir)) {
            if (isDedicatedDir) {
                // Dir dedicated to the env so can delete the whole dir
                if (!FileUtil.deleteDir(localDir)) {
                    throw new RuntimeException("Unable to delete dir: " + FileUtil.getCanonicalPath(localDir));
                }
            } else {
                // Not dedicated dir so just delete the files
                deleteEnvFile(LOCK_FILE_NAME);
                deleteEnvFile(DATA_FILE_NAME);
            }
        }
    }

    private void deleteEnvFile(final String filename) {
        final Path file = localDir.resolve(filename);
        if (Files.isRegularFile(file)) {
            try {
                LOGGER.info("Deleting file {}", file.toAbsolutePath());
                Files.delete(file);
            } catch (final IOException e) {
                throw new RuntimeException("Unable to delete dir: " + FileUtil.getCanonicalPath(localDir));
            }
        } else {
            LOGGER.error("LMDB env file {} doesn't exist", file.toAbsolutePath());
        }
    }

    private Runnable createWaitLoggingAction(final String lockName) {
        final Instant startTime = Instant.now();
        LOGGER.trace("About to acquire {}", lockName);
        return () -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.trace("{} acquired", lockName);
                if (startTime != null) {
                    final Duration waitDuration = Duration.between(startTime, Instant.now());
                    if (waitDuration.getSeconds() >= 1) {
                        LOGGER.debug("Waited {} to acquire {}", waitDuration, lockName);
                    }
                }
            }
        };
    }

    private void dumpMdbFileSize() {
        if (Files.isDirectory(localDir)) {

            try (final Stream<Path> stream = Files.list(localDir)) {
                stream
                        .filter(path ->
                                !Files.isDirectory(path))
                        .filter(file ->
                                file.toString().toLowerCase().endsWith("data.mdb"))
                        .map(file -> {
                            try {
                                final long fileSizeBytes = Files.size(file);
                                return localDir.getFileName().resolve(file.getFileName())
                                       + " - file size: "
                                       + ModelStringUtil.formatIECByteSizeString(fileSizeBytes);
                            } catch (final IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .forEach(LOGGER::debug);

            } catch (final IOException e) {
                LOGGER.debug("Unable to list dir {} due to {}",
                        localDir.toAbsolutePath().normalize(), e.getMessage());
            }
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

    public long getSizeOnDisk() {
        long totalSizeBytes;
        final Path localDir = getLocalDir().toAbsolutePath();
        try (final Stream<Path> fileStream = Files.list(localDir)) {
            totalSizeBytes = fileStream
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .sum();
        } catch (final IOException
                       | RuntimeException e) {
            LOGGER.error("Error calculating disk usage for path {}",
                    localDir.normalize(), e);
            totalSizeBytes = -1;
        }
        return totalSizeBytes;
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

    @Override
    public String toString() {
        return "LmdbEnv{" +
               "localDir=" + localDir +
               ", name='" + name + '\'' +
               ", envFlags=" + envFlags +
               '}';
    }

    // --------------------------------------------------------------------------------


    public static class WriteTxn implements AutoCloseable {

        private final Lock writeLock;
        private Txn<ByteBuffer> writeTxn;

        /**
         * @param writeLock Should already be held by this thread.
         */
        private WriteTxn(final Lock writeLock,
                         final Txn<ByteBuffer> writeTxn) {
            this.writeLock = writeLock;
            this.writeTxn = writeTxn;
        }

        /**
         * @return The write txn object. Do NOT call close() on the returned txn,
         * use {@link WriteTxn#close()} or a try-with-resources block.
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
        public void close() {
            Objects.requireNonNull(writeTxn, "Transaction has already been closed");
            try {
                try {
                    writeTxn.close();
                } finally {
                    writeTxn = null;
                }
            } finally {
                // whatever happens we must release the lock
                writeLock.unlock();
            }
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Creates a write txn on calls to {@link BatchingWriteTxn#getTxn()}
     */
    public static class BatchingWriteTxn implements AutoCloseable {

        private Lock writeLock = null;
        private Supplier<Txn<ByteBuffer>> writeTxnSupplier;
        private Txn<ByteBuffer> writeTxn;

        private final int maxBatchSize;
        private int batchCounter = 0;
        private final BooleanSupplier commitFunc;

        /**
         * @param writeLock    Should already be held by this thread.
         * @param maxBatchSize
         */
        private BatchingWriteTxn(final Lock writeLock,
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
         * use {@link WriteTxn#close()} or a try-with-resources block.
         */
        public Txn<ByteBuffer> getTxn() {
            checkState();
            if (writeTxn == null) {
                Objects.requireNonNull(writeTxnSupplier, "Has already been closed");
                writeTxn = writeTxnSupplier.get();
            }
            return writeTxn;
        }

        /**
         * Aborts the txn but holds the write lock.
         * {@link Txn#abort()}
         */
        public void abort() {
            checkState();
            if (writeTxn != null) {
                writeTxn.abort();
                writeTxn = null;
            }
        }

        /**
         * Increment the count of items processed in the batch.
         * Does not perform a commit so requires the caller to commit based
         * on the return value.
         *
         * @return True if the batch is full, false if not.
         */
        public boolean incrementBatchCount() {
            checkState();
            return (++batchCounter >= maxBatchSize);
        }

        /**
         * Force a commit regardless of batch size
         * {@link Txn#commit()}
         */
        public boolean commit() {
            checkState();
            if (writeTxn != null) {
                LOGGER.trace("Committing txn with batchCounter: {}", batchCounter);
                try {
                    writeTxn.commit();
                } finally {
                    try {
                        writeTxn.close();
                    } finally {
                        writeTxn = null;
                        batchCounter = 0;
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        /**
         * Performs work then increments the batch count by one and commits if the batch size has
         * been reached.
         *
         * @return True if a commit happened
         */
        public boolean processBatchItem(final Consumer<Txn<ByteBuffer>> work) {
            checkState();
            if (work != null) {
                work.accept(getTxn());
                return commitIfRequired();
            } else {
                return false;
            }
        }

        /**
         * If the batch size is > 0 it will increment the current batch count
         * and then commit if the batch has reach its max size.
         * If the batch size is zero then it will never commit and will
         * always return false.
         *
         * @return True if a commit took place.
         */
        public boolean commitIfRequired() {
            checkState();
            return commitFunc.getAsBoolean();
        }

        private boolean commitWithBatchCheck() {
            checkState();
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
        public void close() {
            Objects.requireNonNull(writeTxnSupplier, "Transaction has already been closed");
            try {
                try {
                    if (writeTxn != null) {
                        writeTxn.close();
                    }
                } finally {
                    writeTxn = null;
                    writeTxnSupplier = null;
                }
            } finally {
                // whatever happens we must release the lock
                if (writeLock != null) {
                    writeLock.unlock();
                    writeLock = null;
                }
            }
        }

        public boolean isClosed() {
            return writeTxnSupplier == null;
        }

        private void checkState() {
            Objects.requireNonNull(writeLock, "BatchingWriteTxn is already closed");
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
