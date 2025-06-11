package stroom.planb.impl.data;


import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.db.AbstractDb;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.DurationSetting;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SessionSettings;
import stroom.planb.shared.TemporalRangedStateSettings;
import stroom.planb.shared.TemporalStateSettings;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
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
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

class StoreShard implements Shard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoreShard.class);

    private final ByteBufferFactory byteBufferFactory;
    private final Provider<PlanBConfig> configProvider;
    private final Path shardDir;
    private final Path snapshotDir;

    private final ReentrantLock lock = new ReentrantLock();

    private final PlanBDoc doc;
    private final AtomicInteger useCount = new AtomicInteger();
    private volatile AbstractDb<?, ?> db;
    private volatile boolean open;
    private volatile Instant lastAccessTime;
    private volatile Instant lastWriteTime;
    private volatile Instant lastSnapshotTime;

    public StoreShard(final ByteBufferFactory byteBufferFactory,
                      final Provider<PlanBConfig> configProvider,
                      final StatePaths statePaths,
                      final PlanBDoc doc) {
        this.byteBufferFactory = byteBufferFactory;
        this.configProvider = configProvider;
        this.doc = doc;
        lastWriteTime = Instant.now();
        this.shardDir = statePaths.getShardDir().resolve(doc.getUuid());
        this.snapshotDir = statePaths.getSnapshotDir().resolve(doc.getUuid());
    }

    private void incrementUseCount() {
        lock.lock();
        try {
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

        } finally {
            lock.unlock();
        }
    }

    private void decrementUseCount() {
        lock.lock();
        try {
            final int count = useCount.decrementAndGet();
            if (count < 0) {
                throw new RuntimeException("Unexpected count");
            }
            cleanup();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete() {
        lock.lock();
        try {
            if (useCount.get() == 0) {
                LOGGER.info(() -> "Deleting data for: " + doc);
                cleanup();
                FileUtil.deleteDir(shardDir);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void merge(final Path sourceDir) {
        // See if we can just merge by moving the file.
        lock.lock();
        try {
            boolean success = false;

            // If we don't already have the shard dir then just move the source to the target.
            if (!Files.isDirectory(shardDir)) {
                try {
                    success = true;
                    Files.createDirectories(shardDir.getParent());
                    Files.move(sourceDir, shardDir);
                    lastWriteTime = Instant.now();
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            // If the file already existed then we must open the DB and merge with LMDB.
            if (!success) {
                incrementUseCount();
                try {
                    db.merge(sourceDir);
                    lastWriteTime = Instant.now();
                } finally {
                    decrementUseCount();
                }
            }

            // Create a new snapshot periodically.
            createSnapshot();

        } finally {
            lock.unlock();
        }
    }

    @Override
    public void condense(final PlanBDoc doc) {
        lock.lock();
        try {
            // Find out how old data needs to be before we condense it.
            DurationSetting condense = null;
            if (doc.getSettings() instanceof final TemporalStateSettings temporalStateSettings) {
                condense = temporalStateSettings.getCondense();
            } else if (doc.getSettings() instanceof final TemporalRangedStateSettings temporalRangedStateSettings) {
                condense = temporalRangedStateSettings.getCondense();
            } else if (doc.getSettings() instanceof final SessionSettings sessionSettings) {
                condense = sessionSettings.getCondense();
            }

            final long condenseBeforeMs;
            if (condense != null && condense.isEnabled()) {
                condenseBeforeMs = SimpleDurationUtil.minus(Instant.now(), condense.getDuration()).toEpochMilli();
            } else {
                condenseBeforeMs = 0;
            }

            // Find out how old data needs to be before we delete it.
            DurationSetting retention = null;
            if (doc.getSettings() instanceof final TemporalStateSettings temporalStateSettings) {
                retention = temporalStateSettings.getRetention();
            } else if (doc.getSettings() instanceof final TemporalRangedStateSettings temporalRangedStateSettings) {
                retention = temporalRangedStateSettings.getRetention();
            } else if (doc.getSettings() instanceof final SessionSettings sessionSettings) {
                retention = sessionSettings.getRetention();
            }

            final long deleteBeforeMs;
            if (retention != null && retention.isEnabled()) {
                deleteBeforeMs = SimpleDurationUtil.minus(Instant.now(), retention.getDuration()).toEpochMilli();
            } else {
                deleteBeforeMs = 0;
            }

            // If we are condensing or deleting data then do so.
            if (condenseBeforeMs > 0 || deleteBeforeMs > 0) {
                incrementUseCount();
                try {
                    db.condense(condenseBeforeMs, deleteBeforeMs);
                    lastWriteTime = Instant.now();
                } finally {
                    decrementUseCount();
                }
            }

            // Create a new snapshot periodically.
            createSnapshot();

        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        } finally {
            lock.unlock();
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
            lockAndCreateSnapshot();
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

    private void lockAndCreateSnapshot() {
        lock.lock();
        try {
            // TODO : Possibly create windowed snapshots.
            if (isNewSnapshotRequired()) {
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
            }
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        } finally {
            lock.unlock();
        }
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
    public <R> R get(final Function<AbstractDb<?, ?>, R> function) {
        incrementUseCount();
        try {
            return function.apply(db);
        } finally {
            decrementUseCount();
        }
    }

    @Override
    public void cleanup() {
        lock.lock();
        try {
            if (useCount.get() == 0) {
                if (open && isIdle()) {
                    db.close();
                    db = null;
                    open = false;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean isIdle() {
        return lastAccessTime.isBefore(Instant.now().minus(
                configProvider.get().getMinTimeToKeepEnvOpen().getDuration()));
    }

    private void open() {
        if (Files.exists(shardDir)) {
            LOGGER.info(() -> "Found local shard for '" + doc + "'");
            db = PlanBDb.open(doc, shardDir, byteBufferFactory, false);

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
}
