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

package stroom.planb.impl.data;


import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.impl.db.StatePaths;
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

    protected AbstractStoreShard(final ByteBuffers byteBuffers,
                       final ByteBufferFactory byteBufferFactory,
                       final Provider<PlanBConfig> configProvider,
                       final StatePaths statePaths,
                       final PlanBDocument doc) {
        this(byteBuffers, byteBufferFactory, configProvider, statePaths, doc, -1);
    }

    protected AbstractStoreShard(final ByteBuffers byteBuffers,
                       final ByteBufferFactory byteBufferFactory,
                       final Provider<PlanBConfig> configProvider,
                       final StatePaths statePaths,
                       final PlanBDocument doc,
                       final int shardIndex) {
        this(byteBuffers, byteBufferFactory, configProvider, statePaths, doc, shardIndex,
                statePaths.getShardDir());
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
                       final StatePaths statePaths,
                       final PlanBDocument doc,
                       final int shardIndex,
                       final Path shardBaseDir) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.configProvider = configProvider;
        this.doc = doc;
        this.shardIndex = shardIndex;
        lastWriteTime = Instant.now();
        final String dirSuffix = shardIndex >= 0 ? doc.getUuid() + "_" + shardIndex : doc.getUuid();
        this.shardDir = shardBaseDir.resolve(dirSuffix);

        // Just open the DB.
        try {
            Files.createDirectories(shardDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        open();
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

    @Override
    public void merge(final Path sourceDir) {
        try {
            writeLock.lockInterruptibly();
            try {
                db.merge(sourceDir);
                lastWriteTime = Instant.now();
                if (this instanceof final SnapshotCapable snapshotCapable) {
                    snapshotCapable.createSnapshot();
                }
            } finally {
                writeLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    @Override
    public long deleteOldData(final PlanBDocument doc) {
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
                    result = db.deleteOldData(deleteBefore, useStateTime);
                    lastWriteTime = Instant.now();
                } finally {
                    writeLock.unlock();
                }
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            }
        }

        if (result > 0) {
            // Create a new snapshot periodically.
            if (this instanceof final SnapshotCapable snapshotCapable) {
                snapshotCapable.createSnapshot();
            }
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
            // Create a new snapshot periodically.
            if (this instanceof final SnapshotCapable snapshotCapable) {
                snapshotCapable.createSnapshot();
            }
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
                    throw new RuntimeException("Database is closed");
                }
                return function.apply(db);
            } finally {
                readLock.unlock();
            }
        } catch (final InterruptedException e2) {
            throw UncheckedInterruptedException.create(e2);
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
                db = PlanBDb.open(doc, shardDir, byteBuffers, byteBufferFactory, false);
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
     * Called by {@link SharedFileStoreMergeProcessor} when discarding a
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

    @Override
    public String getInfo() {
        try {
            readLock.lockInterruptibly();
            try {
                if (db == null) {
                    throw new RuntimeException("Database is closed");
                }
                return db.getInfoString();
            } finally {
                readLock.unlock();
            }
        } catch (final InterruptedException e2) {
            throw UncheckedInterruptedException.create(e2);
        }
    }
}
