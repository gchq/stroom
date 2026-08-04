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
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.DurationSetting;
import stroom.planb.shared.HasCondenseSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.SnapshotSettings;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.SimpleDurationUtil;
import stroom.util.time.StroomDuration;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Provider;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

class StoreShard implements Shard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoreShard.class);

    private static final String DATA_FILE_NAME = "data.mdb";
    private static final String COMPACTED_DIR_NAME = "compacted";

    /**
     * Caps the linear growth of the delay between snapshot creation retries, as a multiple of the snapshot
     * lifespan. At the default 10 minute lifespan this caps retries at one per hour.
     */
    private static final int MAX_SNAPSHOT_RETRY_MULTIPLIER = 6;

    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final Provider<PlanBConfig> configProvider;
    private final Path shardDir;
    private final Path snapshotDir;

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock exclusiveReadLock = readWriteLock.writeLock();
    private final Lock writeLock = new ReentrantLock();

    private final PlanBDoc doc;
    private volatile Db<?, ?> db;
    private volatile Instant lastWriteTime;
    private volatile Instant lastSnapshotTime;
    // Time of the last failed snapshot creation, and the number of consecutive failures. Both are only mutated
    // while holding writeLock and are reset when a snapshot is successfully created.
    private volatile Instant lastSnapshotFailureTime;
    private volatile AtomicInteger snapshotFailureCount;

    public StoreShard(final ByteBuffers byteBuffers,
                      final ByteBufferFactory byteBufferFactory,
                      final Provider<PlanBConfig> configProvider,
                      final StatePaths statePaths,
                      final PlanBDoc doc) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.configProvider = configProvider;
        this.doc = doc;
        lastWriteTime = Instant.now();
        this.shardDir = statePaths.getShardDir().resolve(doc.getUuid());
        this.snapshotDir = statePaths.getSnapshotDir().resolve(doc.getUuid());

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
                createSnapshot();
            } finally {
                writeLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    @Override
    public long deleteOldData(final PlanBDoc doc) {
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
            createSnapshot();
        }

        return result;
    }

    private RetentionSettings getRetentionSettings(final PlanBDoc doc) {
        return NullSafe.get(doc, PlanBDoc::getSettings, AbstractPlanBSettings::getRetention);
    }

    @Override
    public long condense(final PlanBDoc doc) {
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
            createSnapshot();
        }

        return result;
    }

    private static DurationSetting getCondenseDuration(final PlanBDoc doc) {
        if (doc.getSettings() instanceof final HasCondenseSettings hasCondenseSettings) {
            return hasCondenseSettings.getCondense();
        }
        return null;
    }

    @Override
    public void compact() {
        final Path dataFile = shardDir.resolve(DATA_FILE_NAME);
        final Path compactedDir = shardDir.resolve(COMPACTED_DIR_NAME);
        final Path compactedFile = compactedDir.resolve(DATA_FILE_NAME);

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
    public void checkSnapshotStatus(final SnapshotRequest request) {
        // If we already have a snapshot for the current write time then don't create a snapshot and just return an
        // error.
        final Instant lastSnapshotTime = this.lastSnapshotTime;
        if (request.getCurrentSnapshotTime() != null &&
            lastSnapshotTime != null &&
            Objects.equals(lastSnapshotTime.toEpochMilli(), request.getCurrentSnapshotTime())) {
            throw new NotModifiedException();
        }

        // Do we have a snapshot
        final Path snapshotZip = getSnapshotZip();
        if (!Files.exists(snapshotZip)) {
            throw new SnapshotNotFoundException(LogUtil.message(
                    "No snapshot has been created yet for {}. Expected '{}'. lastSnapshotTime={}, lastWriteTime={}",
                    doc.asDocRef(),
                    FileUtil.getCanonicalPath(snapshotZip),
                    lastSnapshotTime,
                    lastWriteTime));
        }
    }

    @Override
    public void createSnapshot() {
        // Check if a new snapshot is required and create one if it is.
        if (isNewSnapshotRequired()) {
            try {
                writeLock.lockInterruptibly();
                try {
                    // Check again that a new snapshot is required new we are under lock.
                    if (isNewSnapshotRequired()) {
                        // TODO : Possibly create windowed snapshots.
                        final Instant lastWriteTime = this.lastWriteTime;

                        // Get the snapshot file.
                        Files.createDirectories(snapshotDir);
                        final Path tmpFile = getSnapshotTmp();
                        final Path zipFile = getSnapshotZip();
                        createZip(tmpFile, lastWriteTime);
                        Files.move(tmpFile, zipFile, StandardCopyOption.ATOMIC_MOVE);

                        // Only record the snapshot time once we have actually created a snapshot. If this is
                        // recorded even when creation fails then this shard believes it has a current snapshot
                        // that doesn't exist on disk, and as lastWriteTime only advances when data is written,
                        // a shard that receives no further writes would never retry. See gh-5689.
                        this.lastSnapshotTime = lastWriteTime;
                        this.lastSnapshotFailureTime = null;
                        this.snapshotFailureCount.set(0);
                    }
                } catch (final Exception e) {
                    // Swallowed so one bad shard doesn't stop snapshots being created for the others. Record the
                    // failure so we back off rather than re-zipping the whole shard on every run, as creation is
                    // expensive and holds the write lock. Mutated under writeLock.
                    this.snapshotFailureCount.incrementAndGet();
                    this.lastSnapshotFailureTime = Instant.now();
                    LOGGER.error(() -> LogUtil.message(
                            "Error creating snapshot for {}, consecutive failures: {}, next attempt after {}: {}",
                            doc.asDocRef(),
                            snapshotFailureCount,
                            getSnapshotRetryDelay(),
                            e.getMessage()), e);
                } finally {
                    writeLock.unlock();
                }
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            }
        }
    }

    private boolean isNewSnapshotRequired() {
        final SnapshotSettings snapshotSettings = NullSafe.getOrElse(
                doc,
                PlanBDoc::getSettings,
                AbstractPlanBSettings::getSnapshotSettings,
                new SnapshotSettings());

        if (!snapshotSettings.isUseSnapshotsForLookup() &&
            !snapshotSettings.isUseSnapshotsForGet() &&
            snapshotSettings.isUseSnapshotsForQuery()) {
            return false;
        }

        // Back off after a failed attempt. Without this a shard that always fails to create a snapshot, e.g.
        // because the disk is full, would re-zip the whole shard on every run of the snapshot creation job.
        final Instant lastSnapshotFailureTime = this.lastSnapshotFailureTime;
        if (lastSnapshotFailureTime != null &&
            Instant.now().isBefore(lastSnapshotFailureTime.plus(getSnapshotRetryDelay()))) {
            return false;
        }

        final Instant lastWriteTime = this.lastWriteTime;
        final Instant lastSnapshotTime = this.lastSnapshotTime;

        return lastSnapshotTime == null ||
               (lastSnapshotTime.isBefore(lastWriteTime) &&
                lastSnapshotTime.plus(getSnapshotLifespan()).isBefore(Instant.now()));
    }

    private Duration getSnapshotRetryDelay() {
        return getSnapshotRetryDelay(getSnapshotLifespan(), snapshotFailureCount.get());
    }

    /**
     * How long to wait after a failed snapshot creation before trying again. The delay grows linearly with the
     * number of consecutive failures, up to a cap, so a persistently failing shard is retried occasionally
     * rather than on every run of the snapshot creation job.
     */
    // Package private for testing.
    static Duration getSnapshotRetryDelay(final Duration snapshotLifespan, final int failureCount) {
        final int multiplier = Math.min(Math.max(failureCount, 1), MAX_SNAPSHOT_RETRY_MULTIPLIER);
        return snapshotLifespan.multipliedBy(multiplier);
    }

    private Duration getSnapshotLifespan() {
        return NullSafe.getOrElse(
                configProvider.get(),
                PlanBConfig::getMinTimeToKeepSnapshots,
                StroomDuration::getDuration,
                Duration.ofMinutes(10));
    }

    public Path getSnapshotTmp() {
        return snapshotDir.resolve("snapshot.tmp");
    }

    public Path getSnapshotZip() {
        return snapshotDir.resolve("snapshot.zip");
    }

    private void createZip(final Path zipFile,
                           final Instant lastWriteTime) {
        try (final ZipArchiveOutputStream zipOutputStream =
                ZipUtil.createOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
            ZipUtil.zip(shardDir, zipOutputStream);
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry(SNAPSHOT_INFO_FILE_NAME));
            try {
                zipOutputStream.write(lastWriteTime.toString().getBytes(StandardCharsets.UTF_8));
            } finally {
                zipOutputStream.closeArchiveEntry();
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
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
    private void open() {
        if (db == null) {
            if (Files.exists(shardDir)) {
                LOGGER.info(() -> "Found local shard for '" + doc.asDocRef() + "'");
                db = PlanBDb.open(doc, shardDir, byteBuffers, byteBufferFactory, false);

            } else {
                // If this node is supposed to be a node that stores shards, but it doesn't have it, then error.
                final String message = "Local Plan B shard not found for '" + doc.asDocRef() + "'";
                LOGGER.error(() -> message);
                throw new RuntimeException(message);
            }
        }
    }

    /**
     * Must only be called while holding {@code exclusiveReadLock}.
     */
    private void close() {
        if (db != null) {
            try {
                db.close();
            } finally {
                db = null;
            }
        }
    }

    @Override
    public PlanBDoc getDoc() {
        return doc;
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
