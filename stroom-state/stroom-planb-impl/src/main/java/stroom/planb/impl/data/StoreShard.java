package stroom.planb.impl.data;


import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.DurationSetting;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RangeStateSettings;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.SessionSettings;
import stroom.planb.shared.SnapshotSettings;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.TemporalRangeStateSettings;
import stroom.planb.shared.TemporalStateSettings;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.SimpleDurationUtil;
import stroom.util.time.StroomDuration;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Provider;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.lmdbjava.LmdbException;

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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

class StoreShard implements Shard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoreShard.class);

    private static final String DATA_FILE_NAME = "data.mdb";
    private static final String COMPACTED_DIR_NAME = "compacted";

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
    private volatile boolean open;
    private volatile Instant lastWriteTime;
    private volatile Instant lastSnapshotTime;

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
        RetentionSettings retention = null;
        if (doc.getSettings() instanceof final StateSettings stateSettings) {
            retention = stateSettings.getRetention();
        } else if (doc.getSettings() instanceof final TemporalStateSettings temporalStateSettings) {
            retention = temporalStateSettings.getRetention();
        } else if (doc.getSettings() instanceof final RangeStateSettings rangeStateSettings) {
            retention = rangeStateSettings.getRetention();
        } else if (doc.getSettings() instanceof final TemporalRangeStateSettings temporalRangeStateSettings) {
            retention = temporalRangeStateSettings.getRetention();
        } else if (doc.getSettings() instanceof final SessionSettings sessionSettings) {
            retention = sessionSettings.getRetention();
        }
        return retention;
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
        DurationSetting condense = null;
        if (doc.getSettings() instanceof final TemporalStateSettings temporalStateSettings) {
            condense = temporalStateSettings.getCondense();
        } else if (doc.getSettings() instanceof final TemporalRangeStateSettings temporalRangeStateSettings) {
            condense = temporalRangeStateSettings.getCondense();
        } else if (doc.getSettings() instanceof final SessionSettings sessionSettings) {
            condense = sessionSettings.getCondense();
        }
        return condense;
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
                        LOGGER.error(e::getMessage, e);
                    }

                    // Cleanup.
                    FileUtil.deleteDir(compactedDir);

                    // Open the new DB.
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
        if (!Files.exists(getSnapshotZip())) {
            throw new RuntimeException("Snapshot not found");
        }
    }

    @Override
    public void createSnapshot() {
        if (isNewSnapshotRequired()) {
            try {
                writeLock.lockInterruptibly();
                try {
                    // TODO : Possibly create windowed snapshots.
                    final Instant lastWriteTime = this.lastWriteTime;
                    try {
                        // Get the snapshot file.
                        Files.createDirectories(snapshotDir);
                        final Path tmpFile = getSnapshotTmp();
                        final Path zipFile = getSnapshotZip();
                        createZip(tmpFile, lastWriteTime);
                        Files.move(tmpFile, zipFile, StandardCopyOption.ATOMIC_MOVE);
                    } finally {
                        this.lastSnapshotTime = lastWriteTime;
                    }
                } catch (final Exception e) {
                    LOGGER.error(e::getMessage, e);
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

        final Instant lastWriteTime = this.lastWriteTime;
        final Instant lastSnapshotTime = this.lastSnapshotTime;

        return lastSnapshotTime == null ||
               (lastSnapshotTime.isBefore(lastWriteTime) &&
                lastSnapshotTime.plus(getSnapshotLifespan()).isBefore(Instant.now()));
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
            final Db<?, ?> db = this.db;
            if (db != null) {
                return function.apply(db);
            }
        } catch (final LmdbException e) {
            LOGGER.debug(e::getMessage, e);
        }

        // Try again under lock.
        try {
            readLock.lockInterruptibly();
            try {
                if (!open) {
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

    @Override
    public void cleanup() {

    }

    private void open() {
        if (!open) {
            if (Files.exists(shardDir)) {
                LOGGER.info(() -> "Found local shard for '" + doc.asDocRef() + "'");
                db = PlanBDb.open(doc, shardDir, byteBuffers, byteBufferFactory, false);
                open = true;

            } else {
                // If this node is supposed to be a node that stores shards, but it doesn't have it, then error.
                final String message = "Local Plan B shard not found for '" + doc.asDocRef() + "'";
                LOGGER.error(() -> message);
                throw new RuntimeException(message);
            }
        }
    }

    private void close() {
        if (open) {
            db.close();
            db = null;
            open = false;
        }
    }

    @Override
    public PlanBDoc getDoc() {
        return doc;
    }

    @Override
    public String getInfo() {
        try {
            final Db<?, ?> db = this.db;
            if (db != null) {
                return db.getInfoString();
            }
        } catch (final LmdbException e) {
            LOGGER.debug(e::getMessage, e);
        }

        // Try again under lock.
        try {
            readLock.lockInterruptibly();
            try {
                if (!open) {
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
