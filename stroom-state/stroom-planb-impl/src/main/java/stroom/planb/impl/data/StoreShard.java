package stroom.planb.impl.data;


import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.DurationSetting;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RangedStateSettings;
import stroom.planb.shared.SessionSettings;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.TemporalRangedStateSettings;
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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

class StoreShard implements Shard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoreShard.class);

    private static final String DATA_FILE_NAME = "data.mdb";
    private static final String COMPACTED_DIR_NAME = "compacted";

    private final ByteBuffers byteBuffers;
    private final Provider<PlanBConfig> configProvider;
    private final Path shardDir;
    private final Path snapshotDir;

    private final ReentrantLock readLock = new ReentrantLock();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final Condition compactingCondition = readLock.newCondition();
    private final Condition useCountCondition = readLock.newCondition();

    private final PlanBDoc doc;
    private final AtomicInteger useCount = new AtomicInteger();
    private volatile Db<?, ?> db;
    private volatile boolean open;
    private volatile Instant lastAccessTime;
    private volatile Instant lastWriteTime;
    private volatile Instant lastSnapshotTime;
    private volatile boolean compacting;

    public StoreShard(final ByteBuffers byteBuffers,
                      final Provider<PlanBConfig> configProvider,
                      final StatePaths statePaths,
                      final PlanBDoc doc) {
        this.byteBuffers = byteBuffers;
        this.configProvider = configProvider;
        this.doc = doc;
        lastWriteTime = Instant.now();
        this.shardDir = statePaths.getShardDir().resolve(doc.getUuid());
        this.snapshotDir = statePaths.getSnapshotDir().resolve(doc.getUuid());
    }

    private void incrementReadCount() {
        try {
            readLock.lockInterruptibly();
            try {
                // Don't allow a new read until we have finished compacting.
                while (compacting) {
                    compactingCondition.await();
                }

                // Open if needed.
                if (!open) {
                    open();
                    open = true;
                }

                final int count = useCount.incrementAndGet();
                if (count <= 0) {
                    throw new RuntimeException("Unexpected count");
                }

                lastAccessTime = Instant.now();
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            } finally {
                readLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    private void decrementReadCount() {
        try {
            readLock.lockInterruptibly();
            try {
                final int count = useCount.decrementAndGet();
                if (count < 0) {
                    throw new RuntimeException("Unexpected count");
                }
                cleanup();

                // Let anything waiting on this condition know that the use count has changed.
                useCountCondition.signalAll();
            } finally {
                readLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    @Override
    public boolean delete() {
        try {
            writeLock.lockInterruptibly();
            try {
                readLock.lockInterruptibly();
                try {
                    if (useCount.get() == 0) {
                        LOGGER.info(() -> "Deleting data for: " + doc);
                        cleanup();
                        FileUtil.deleteDir(shardDir);
                        return true;
                    }
                    return false;
                } finally {
                    readLock.unlock();
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

    @Override
    public void merge(final Path sourceDir) {
        try {
            writeLock.lockInterruptibly();
            try {
                // See if we can just merge by moving the file.
                boolean success = false;

                // If we don't already have the shard dir then just move the source to the target.
                if (!Files.isDirectory(shardDir)) {
                    readLock.lockInterruptibly();
                    try {
                        success = true;
                        Files.createDirectories(shardDir.getParent());
                        Files.move(sourceDir, shardDir);
                        lastWriteTime = Instant.now();
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    } finally {
                        readLock.unlock();
                    }
                }

                // If the file already existed then we must open the DB and merge with LMDB.
                if (!success) {
                    // Ensure the DB is open and won't be closed.
                    incrementReadCount();
                    try {
                        db.merge(sourceDir);
                        lastWriteTime = Instant.now();
                    } finally {
                        decrementReadCount();
                    }
                }

                // Create a new snapshot periodically.
                createSnapshot();
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
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
        boolean useStateTime = false;
        DurationSetting retention = null;
        if (doc.getSettings() instanceof final StateSettings stateSettings) {
            retention = stateSettings.getRetention();
        } else if (doc.getSettings() instanceof final TemporalStateSettings temporalStateSettings) {
            retention = temporalStateSettings.getRetention();
            useStateTime = temporalStateSettings.getUseStateTimeForRetention() != null &&
                           temporalStateSettings.getUseStateTimeForRetention();
        } else if (doc.getSettings() instanceof final RangedStateSettings rangedStateSettings) {
            retention = rangedStateSettings.getRetention();
        } else if (doc.getSettings() instanceof final TemporalRangedStateSettings temporalRangedStateSettings) {
            retention = temporalRangedStateSettings.getRetention();
            useStateTime = temporalRangedStateSettings.getUseStateTimeForRetention() != null &&
                           temporalRangedStateSettings.getUseStateTimeForRetention();
        } else if (doc.getSettings() instanceof final SessionSettings sessionSettings) {
            retention = sessionSettings.getRetention();
            useStateTime = sessionSettings.getUseStateTimeForRetention() != null &&
                           sessionSettings.getUseStateTimeForRetention();
        }

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
                    // Ensure the DB is open and won't be closed.
                    incrementReadCount();
                    try {
                        result = db.deleteOldData(deleteBefore, useStateTime);
                        lastWriteTime = Instant.now();
                    } finally {
                        decrementReadCount();
                    }
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
                    // Ensure the DB is open and won't be closed.
                    incrementReadCount();
                    try {
                        result = db.condense(condenseBefore);
                        lastWriteTime = Instant.now();
                    } finally {
                        decrementReadCount();
                    }
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
        } else if (doc.getSettings() instanceof final TemporalRangedStateSettings temporalRangedStateSettings) {
            condense = temporalRangedStateSettings.getCondense();
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
                incrementReadCount();
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
                } finally {
                    decrementReadCount();
                }

                // Now we want to switch out the files atomically when nobody is reading.
                readLock.lockInterruptibly();
                try {
                    // Let readers know we are performing compaction and don't allow usage to be incremented.
                    compacting = true;
                    try {
                        // Wait for all readers to stop reading.
                        while (useCount.get() > 0) {
                            useCountCondition.await();
                        }

                        // Now we have no readers we can switch the files.

                        // Close the DB if open.
                        if (open) {
                            db.close();
                            db = null;
                            open = false;
                        }

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

                        lastWriteTime = Instant.now();
                    } finally {
                        compacting = false;
                        // Wake up waiting threads.
                        compactingCondition.signalAll();
                    }
                } catch (final InterruptedException e) {
                    throw UncheckedInterruptedException.create(e);
                } finally {
                    readLock.unlock();
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
            incrementReadCount();
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
                decrementReadCount();
            }
        }
    }

    private boolean isNewSnapshotRequired() {
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
        incrementReadCount();
        try {
            return function.apply(db);
        } finally {
            decrementReadCount();
        }
    }

    @Override
    public void cleanup() {
        try {
            readLock.lockInterruptibly();
            try {
                if (useCount.get() == 0) {
                    if (open && isIdle()) {
                        db.close();
                        db = null;
                        open = false;
                    }
                }
            } finally {
                readLock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    private boolean isIdle() {
        return lastAccessTime.isBefore(Instant.now().minus(
                configProvider.get().getMinTimeToKeepEnvOpen().getDuration()));
    }

    private void open() {
        if (Files.exists(shardDir)) {
            LOGGER.info(() -> "Found local shard for '" + doc + "'");
            db = PlanBDb.open(doc, shardDir, byteBuffers, false);

        } else {
            // If this node is supposed to be a node that stores shards, but it doesn't have it, then error.
            final String message = "Local Plan B shard not found for '" +
                                   doc +
                                   "'";
            LOGGER.error(() -> message);
            throw new RuntimeException(message);
        }
    }

    @Override
    public PlanBDoc getDoc() {
        return doc;
    }

    @Override
    public String getInfo() {
        incrementReadCount();
        try {
            return db.getInfoString();
        } finally {
            decrementReadCount();
        }
    }
}
