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

package stroom.planb.impl.db;

import stroom.lmdb.LmdbConfig;
import stroom.lmdb2.LmdbEnvDir;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.CopyFlags;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.EnvInfo;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class PlanBEnv implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PlanBEnv.class);

    private static final int CONCURRENT_READERS = 1023;
    private final Semaphore concurrentReaderSemaphore;
    protected final Env<ByteBuffer> env;
    private final ReentrantLock writeTxnLock = new ReentrantLock();
    private final ReentrantLock dbCommitLock = new ReentrantLock();
    private final Path path;
    private final boolean readOnly;
    private final HashClashCommitRunnable commitRunnable;

    public PlanBEnv(final Path path,
                    final Long mapSize,
                    final int maxDbs,
                    final boolean readOnly,
                    final HashClashCommitRunnable commitRunnable) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(path, true);
        this.path = path;
        this.readOnly = readOnly;
        this.commitRunnable = commitRunnable;
        concurrentReaderSemaphore = new Semaphore(CONCURRENT_READERS);

        if (readOnly) {
            LOGGER.debug(() -> "Opening: " + path);
        } else {
            LOGGER.debug(() -> "Creating: " + path);
        }

        final Env.Builder<ByteBuffer> builder = Env.create()
                .setMapSize(mapSize == null
                        ? LmdbConfig.DEFAULT_MAX_STORE_SIZE.getBytes()
                        : mapSize)
                .setMaxDbs(maxDbs)
                .setMaxReaders(CONCURRENT_READERS + 1); // We use another reader for some writes.

        if (readOnly) {
            env = builder.open(lmdbEnvDir.getEnvDir().toFile(),
                    EnvFlags.MDB_NOTLS,
                    EnvFlags.MDB_NOLOCK,
                    EnvFlags.MDB_RDONLY_ENV);
        } else {
            env = builder.open(lmdbEnvDir.getEnvDir().toFile(),
                    EnvFlags.MDB_NOTLS);
        }
    }

    public Dbi<ByteBuffer> openDbi(final String name, final DbiFlags... flags) {
        return env.openDbi(name.getBytes(StandardCharsets.UTF_8), flags);
    }

    public final LmdbWriter createWriter() {
        return new LmdbWriter(env, dbCommitLock, commitRunnable, writeTxnLock);
    }

    public final <T> T write(final Function<LmdbWriter, T> function) {
        try (final LmdbWriter writer = createWriter()) {
            try {
                return function.apply(writer);
            } catch (final Throwable e) {
                fail(writer, e);
                throw e;
            }
        }
    }

    public final <T> T readAndWrite(final BiFunction<Txn<ByteBuffer>, LmdbWriter, T> function) {
        try (final LmdbWriter writer = createWriter()) {
            try (final Txn<ByteBuffer> readTxn = env.txnRead()) {
                return function.apply(readTxn, writer);
            } catch (final Throwable e) {
                fail(writer, e);
                throw e;
            }
        }
    }

    public final void write(final Consumer<LmdbWriter> consumer) {
        try (final LmdbWriter writer = createWriter()) {
            try {
                consumer.accept(writer);
            } catch (final Throwable e) {
                fail(writer, e);
                throw e;
            }
        }
    }

    /**
     * Runs one operation against a writer that the caller holds across many operations, aborting it
     * on failure. Such a writer must not be left to commit on close: its txn is already dead, so
     * committing runs the commit listener on it and reports that secondary failure instead of the
     * real cause.
     */
    public final void writeWith(final LmdbWriter writer, final Runnable operation) {
        try {
            operation.run();
        } catch (final Throwable e) {
            fail(writer, e);
            throw e;
        }
    }

    /** Aborts the writer and translates a map-full failure. Callers rethrow. */
    private void fail(final LmdbWriter writer, final Throwable e) {
        abortQuietly(writer, e);
        throwIfStoreFull(e);
    }

    private void throwIfStoreFull(final Throwable e) {
        if (e instanceof Env.MapFullException) {
            throw new PlanBStoreFullException(path, getUsage(), e);
        }
    }

    private static void abortQuietly(final LmdbWriter writer, final Throwable cause) {
        try {
            writer.abort();
        } catch (final Throwable e) {
            cause.addSuppressed(e);
        }
    }

    public final void lock(final Runnable runnable) {
        dbCommitLock.lock();
        try {
            runnable.run();
        } finally {
            dbCommitLock.unlock();
        }
    }

    public final <R> R read(final Function<Txn<ByteBuffer>, R> function) {
        try {
            concurrentReaderSemaphore.acquire();
            try {
                try (final Txn<ByteBuffer> readTxn = env.txnRead()) {
                    return function.apply(readTxn);
                }
            } finally {
                concurrentReaderSemaphore.release();
            }
        } catch (final InterruptedException e) {
            LOGGER.error(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public void copy(final File dest, final CopyFlags... flags) {
        env.copy(dest, flags);
    }

    public final boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public final void close() {
        env.close();
    }

    /**
     * How much of the map LMDB has allocated pages for. Pages freed by deletes are included
     * because LMDB never shrinks the file, so this, not the live data size, is what determines
     * whether another write can succeed.
     */
    public Usage getUsage() {
        final EnvInfo info = env.info();
        final long usedBytes = (info.lastPageNumber + 1) * env.stat().pageSize;
        return new Usage(usedBytes, info.mapSize);
    }

    public EnvInf getInfo() {
        final List<String> dbNames = getDbNames();
        return new EnvInf(env.stat(), env.info(), env.getMaxKeySize(), dbNames);
    }

    public List<String> getDbNames() {
        return env
                .getDbiNames()
                .stream()
                .map(String::new)
                .sorted()
                .toList();
    }

    public record EnvInf(Stat stat, EnvInfo envInfo, int maxKeySize, List<String> dbNames) {

    }

    public record Usage(long usedBytes, long mapSize) {

        public double fraction() {
            return mapSize == 0
                    ? 0
                    : (double) usedBytes / mapSize;
        }
    }
}
