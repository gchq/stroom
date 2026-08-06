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

package stroom.planb.impl.data.shard;


import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.impl.db.PlanBEnv.Usage;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.DurationSetting;
import stroom.planb.shared.HasCondenseSettings;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.RetentionSettings;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.SimpleDurationUtil;

import jakarta.inject.Provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public abstract class AbstractStoreShard implements Shard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractStoreShard.class);

    private static final String COMPACTED_DIR_NAME = "compacted";

    protected final ByteBuffers byteBuffers;
    protected final ByteBufferFactory byteBufferFactory;
    protected final Provider<PlanBConfig> configProvider;
    protected final Path shardDir;
    protected final int shardIndex;

    protected final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    protected final Lock readLock = readWriteLock.readLock();
    protected final Lock exclusiveReadLock = readWriteLock.writeLock();
    protected final Lock writeLock = new ReentrantLock();

    protected final PlanBDocument doc;
    protected volatile Db<?, ?> db;
    protected volatile Instant lastWriteTime;
    protected volatile Instant lastAccessTime;

    protected AbstractStoreShard(final ByteBuffers byteBuffers,
                       final ByteBufferFactory byteBufferFactory,
                       final Provider<PlanBConfig> configProvider,
                       final PlanBPaths planBPaths,
                       final PlanBDocument doc) {
        this(byteBuffers, byteBufferFactory, configProvider, planBPaths, doc, -1);
    }

    protected AbstractStoreShard(final ByteBuffers byteBuffers,
                       final ByteBufferFactory byteBufferFactory,
                       final Provider<PlanBConfig> configProvider,
                       final PlanBPaths planBPaths,
                       final PlanBDocument doc,
                       final int shardIndex) {
        this(byteBuffers, byteBufferFactory, configProvider, planBPaths, doc, shardIndex,
                planBPaths.getShardDir());
    }

    /**
     * Package-private constructor used by subclasses to run merge
     * operations in an isolated directory ({@code mergingDir}) that is completely separate from the
     * long-lived query shard directory ({@code shardDir}).
     *
     * <p>Isolation is required because LMDB's {@code lock.mdb} contains
     * {@code PTHREAD_MUTEX_ROBUST | PTHREAD_PROCESS_SHARED} mutexes. glibc records the mmap'd
     * address of these mutexes in the owning thread's {@code robust_list}. If two
     * {@code mdb_env_open} calls share the same {@code lock.mdb} (same directory), the second
     * open remaps the file at a <em>different</em> virtual address while the first env's address
     * is still recorded in the thread's {@code robust_list}. The next
     * {@code pthread_mutex_lock} then tries to update the stale (now-unmapped) list entry
     * and crashes with {@code SIGSEGV / SEGV_MAPERR}.
     */
    protected AbstractStoreShard(final ByteBuffers byteBuffers,
                       final ByteBufferFactory byteBufferFactory,
                       final Provider<PlanBConfig> configProvider,
                       final PlanBPaths planBPaths,
                       final PlanBDocument doc,
                       final int shardIndex,
                       final Path shardBaseDir) {
        this(byteBuffers, byteBufferFactory, configProvider, planBPaths, doc, shardIndex, shardBaseDir, null);
    }

    /**
     * As above, but places the shard in a per-instance {@code generation} subdir
     * ({@code <base>/<uuid>_<idx>/<generation>}) when {@code generation != null}. Used by
     * {@link stroom.planb.impl.fs.SharedFileStoreShard} read instances so that an idle-evicted
     * (closing) instance and its replacement never share a {@code lock.mdb} directory — avoiding the
     * robust-mutex SIGSEGV hazard above without any create/evict serialisation.
     */
    protected AbstractStoreShard(final ByteBuffers byteBuffers,
                       final ByteBufferFactory byteBufferFactory,
                       final Provider<PlanBConfig> configProvider,
                       final PlanBPaths planBPaths,
                       final PlanBDocument doc,
                       final int shardIndex,
                       final Path shardBaseDir,
                       final String generation) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.configProvider = configProvider;
        this.doc = doc;
        this.shardIndex = shardIndex;
        lastWriteTime = Instant.now();
        lastAccessTime = Instant.now();
        final String dirSuffix = shardIndex >= 0 ? doc.getUuid() + "_" + shardIndex : doc.getUuid();
        final Path identityDir = shardBaseDir.resolve(dirSuffix);
        this.shardDir = generation != null ? identityDir.resolve(generation) : identityDir;

        // Just open the DB.
        try {
            Files.createDirectories(shardDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        open();
    }

    /**
     * Constructor taking a fully-resolved local {@code shardDir} that optionally defers opening. Used by
     * read-only subclasses (e.g. {@link stroom.planb.impl.fs.ArchiveStoreShard}) whose dir identity is
     * not the {@code uuid_idx} form and which must copy {@code data.mdb} in before opening (a read-only
     * LMDB env cannot open an absent {@code data.mdb}). When {@code openNow} is false the caller opens.
     */
    protected AbstractStoreShard(final ByteBuffers byteBuffers,
                       final ByteBufferFactory byteBufferFactory,
                       final Provider<PlanBConfig> configProvider,
                       final PlanBDocument doc,
                       final int shardIndex,
                       final Path resolvedShardDir,
                       final boolean openNow) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.configProvider = configProvider;
        this.doc = doc;
        this.shardIndex = shardIndex;
        lastWriteTime = Instant.now();
        lastAccessTime = Instant.now();
        this.shardDir = resolvedShardDir;
        try {
            Files.createDirectories(shardDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        if (openNow) {
            open();
        }
    }

    /** Whether the LMDB env should be opened read-only. Overridden by read-only shards. */
    protected boolean isReadOnly() {
        return false;
    }

    /**
     * Whether this shard is read by queries, and so needs the query-only structures a store can carry
     * (for traces, the secondary sort indexes). Shards that only accumulate and get merged/published
     * override this to {@code false} so those structures are neither built nor maintained.
     */
    protected boolean isQueryable() {
        return true;
    }

    @Override
    public boolean isIdle() {
        // Store shards are long-lived and don't need idle cleanup.
        return false;
    }

    @Override
    public boolean delete() {
        try {
            writeLock.lockInterruptibly();
            try {
                if (exclusiveReadLock.tryLock()) {
                    try {
                        LOGGER.info(() -> "Deleting data for: " + doc);
                        close();
                        FileUtil.deleteDir(shardDir);
                        return true;
                    } finally {
                        exclusiveReadLock.unlock();
                    }
                } else {
                    return false;
                }
            } finally {
                writeLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    /** Called after a successful mutation while holding the write lock; subclasses may override. */
    protected void afterMutation() {
    }

    @Override
    public void merge(final Path sourceDir) {
        try {
            writeLock.lockInterruptibly();
            try {
                db.merge(sourceDir);
                lastWriteTime = Instant.now();
                afterMutation();
            } finally {
                writeLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    public void mergeComplete() {
        try {
            writeLock.lockInterruptibly();
            try {
                db.mergeComplete();
                lastWriteTime = Instant.now();
            } finally {
                writeLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    @Override
    public long runRetention(final PlanBDocument doc) {
        long result = 0;

        // Find out how old data needs to be before we delete it.
        final RetentionSettings retention = getRetentionSettings(doc);
        final boolean useStateTime = NullSafe.getOrElse(retention, RetentionSettings::getUseStateTime, false);

        final Instant deleteBefore;
        if (retention != null && retention.isEnabled()) {
            deleteBefore = SimpleDurationUtil.minus(Instant.now(), retention.getDuration());
        } else {
            deleteBefore = Instant.MIN;
        }

        // If we are condensing or deleting data then do so.
        if (deleteBefore.isAfter(Instant.MIN)) {
            try {
                writeLock.lockInterruptibly();
                try {
                    result = db.runRetention(deleteBefore, useStateTime);
                    lastWriteTime = Instant.now();
                } finally {
                    writeLock.unlock();
                }
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            }
        }

        if (result > 0) {
            afterMutation();
        }

        return result;
    }

    protected RetentionSettings getRetentionSettings(final PlanBDocument doc) {
        return NullSafe.get(doc, PlanBDocument::getSettings, AbstractPlanBSettings::getRetention);
    }

    @Override
    public long condense(final PlanBDocument doc) {
        long result = 0;
        // Find out how old data needs to be before we condense it.
        final DurationSetting durationSetting = getCondenseDuration(doc);

        final Instant condenseBefore;
        if (durationSetting != null && durationSetting.isEnabled()) {
            condenseBefore = SimpleDurationUtil.minus(Instant.now(), durationSetting.getDuration());
        } else {
            condenseBefore = Instant.MIN;
        }

        // If we are condensing or deleting data then do so.
        if (condenseBefore.isAfter(Instant.MIN)) {
            try {
                writeLock.lockInterruptibly();
                try {
                    result = db.condense(condenseBefore);
                    lastWriteTime = Instant.now();
                } finally {
                    writeLock.unlock();
                }
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            }
        }

        if (result > 0) {
            afterMutation();
        }

        return result;
    }

    protected static DurationSetting getCondenseDuration(final PlanBDocument doc) {
        if (doc.getSettings() instanceof final HasCondenseSettings hasCondenseSettings) {
            return hasCondenseSettings.getCondense();
        }
        return null;
    }

    @Override
    public void compact() {
        final Path dataFile = shardDir.resolve(PlanBConstants.DATA_FILE_NAME);
        final Path compactedDir = shardDir.resolve(COMPACTED_DIR_NAME);
        final Path compactedFile = compactedDir.resolve(PlanBConstants.DATA_FILE_NAME);

        // Stop all other writes during the compaction process.
        try {
            writeLock.lockInterruptibly();
            try {

                // Ensure the DB is open and won't be closed.
                try {
                    // Perform compaction.
                    LOGGER.info("Running compaction");
                    LOGGER.info(() -> "Size before compaction: " + fileSize(dataFile));
                    FileUtil.deleteDir(compactedDir);
                    Files.createDirectory(compactedDir);
                    db.compact(compactedDir);
                    LOGGER.info(() -> "Size after compaction: " + fileSize(compactedFile));
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                    throw new UncheckedIOException(e);
                }

                // Now we want to switch out the files atomically when nobody is reading.
                exclusiveReadLock.lockInterruptibly();
                try {
                    // Close the DB.
                    close();

                    // Switch files.
                    try {
                        Files.move(
                                compactedFile,
                                dataFile,
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (final IOException e) {
                        // Log any error that happens on move, we will end up reopening the old database.
                        LOGGER.error(e::getMessage, e);
                    }

                    // Cleanup.
                    FileUtil.deleteDir(compactedDir);

                    // Open the new DB.
                    // Note that if the move above fails we will end up reopening the old database.
                    // This is expected recovery behaviour.
                    open();

                    lastWriteTime = Instant.now();
                } finally {
                    exclusiveReadLock.unlock();
                }
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            } finally {
                writeLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    private String fileSize(final Path file) {
        try {
            return ModelStringUtil.formatMetricByteSizeString(Files.size(file));
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public <R> R get(final Function<Db<?, ?>, R> function) {
        try {
            readLock.lockInterruptibly();
            try {
                if (db == null) {
                    // Closed by an idle eviction between lookup and use — ShardManager retries.
                    throw new ShardClosedException();
                }
                lastAccessTime = Instant.now();
                return function.apply(db);
            } finally {
                readLock.unlock();
            }
        } catch (final InterruptedException e2) {
            throw UncheckedInterruptedException.create(e2);
        }
    }

    /**
     * Write-side counterpart to {@link #get(Function)}: hands the open {@link Db} to the caller under the
     * write lock, stamping {@code lastWriteTime} and running {@link #afterMutation()} afterwards.
     *
     * <p>Exists so a mutation that only applies to one store type does not need its own named method
     * here. Store-type-specific callers test the {@code Db} for the capability interface they need — as
     * {@code SnapshotCapable} callers already do — instead of this class growing vocabulary from a single
     * type. Subclasses whose local copy must be refreshed first override it (see
     * {@code SharedFileStoreShard}).
     */
    public <R> R writeWithDb(final Function<Db<?, ?>, R> function) {
        try {
            writeLock.lockInterruptibly();
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
        try {
            if (db == null) {
                // Closed by an idle eviction between lookup and use — ShardManager retries.
                throw new ShardClosedException();
            }
            final R result = function.apply(db);
            lastWriteTime = Instant.now();
            afterMutation();
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Idle reclamation of the local copy: waits for in-flight readers (via {@code exclusiveReadLock}),
     * closes the env and deletes the local shard dir. Safe to block here because a replacement instance
     * uses a fresh generation dir (see the generation ctor) so there is no same-{@code lock.mdb}
     * overlap. The shared/remote store remains the source of truth, so the copy is recreated on next
     * access. Subclasses whose local dir IS the authoritative data (e.g. RestStoreShard) never reach
     * here because their {@link #isIdle()} stays false.
     */
    @Override
    public void evict() {
        try {
            writeLock.lockInterruptibly();
            try {
                exclusiveReadLock.lockInterruptibly();
                try {
                    LOGGER.info(() -> "Evicting idle local shard for: " + doc.asDocRef()
                            + " (shardIndex: " + shardIndex + ")");
                    close();
                    FileUtil.deleteDir(shardDir);
                } finally {
                    exclusiveReadLock.unlock();
                }
            } finally {
                writeLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    /**
     * Must only be called during construction (before the object is published) or while holding
     * {@code exclusiveReadLock}.
     */
    protected void open() {
        if (db == null) {
            if (Files.exists(shardDir)) {
                LOGGER.info(() -> "Opening local shard for '" + doc.asDocRef() + "' (shardIndex: " + shardIndex + ")");
                db = PlanBDb.open(doc, shardDir, byteBuffers, byteBufferFactory,
                        isReadOnly(), isQueryable());
            } else {
                final String message = "Local Plan B shard directory not found for '" + doc.asDocRef() + "'";
                LOGGER.error(() -> message);
                throw new RuntimeException(message);
            }
        }
    }

    /**
     * Must only be called while holding {@code exclusiveReadLock}.
     */
    protected void close() {
        if (db != null) {
            try {
                db.close();
            } finally {
                db = null;
            }
        }
    }

    /**
     * Closes the LMDB environment without deleting any data files.
     * Called by {@link stroom.planb.impl.fs.SharedFileStoreMergeProcessor} when discarding a
     * transient per-cycle shard instance.
     */
    public void dispose() {
        close();
    }

    @Override
    public PlanBDocument getDoc() {
        return doc;
    }

    public Path getShardDir() {
        return shardDir;
    }

    public int getShardIndex() {
        return shardIndex;
    }

    public Usage getUsage() {
        return withOpenDb(Db::getUsage);
    }

    private <R> R withOpenDb(final Function<Db<?, ?>, R> function) {
        try {
            readLock.lockInterruptibly();
            try {
                if (db == null) {
                    throw new ShardClosedException();
                }
                return function.apply(db);
            } finally {
                readLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    @Override
    public String getInfo() {
        try {
            readLock.lockInterruptibly();
            try {
                if (db == null) {
                    throw new ShardClosedException();
                }
                lastAccessTime = Instant.now();
                return db.getInfoString();
            } finally {
                readLock.unlock();
            }
        } catch (final InterruptedException e2) {
            throw UncheckedInterruptedException.create(e2);
        }
    }
}
