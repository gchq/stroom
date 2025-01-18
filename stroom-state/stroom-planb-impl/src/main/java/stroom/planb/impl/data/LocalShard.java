package stroom.planb.impl.data;


import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.db.AbstractLmdb;
import stroom.planb.impl.db.RangedStateDb;
import stroom.planb.impl.db.SessionDb;
import stroom.planb.impl.db.StateDb;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.impl.db.TemporalRangedStateDb;
import stroom.planb.impl.db.TemporalStateDb;
import stroom.planb.shared.PlanBDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Provider;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

class LocalShard implements Shard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LocalShard.class);

    private static final String DATA_FILE_NAME = "data.mdb";
    private static final String LOCK_FILE_NAME = "lock.mdb";

    private final ByteBufferFactory byteBufferFactory;
    private final PlanBDocCache planBDocCache;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
    private final String mapName;
    private final Path shardDir;

    private final ReentrantLock lock = new ReentrantLock();

    private volatile PlanBDoc doc;
    private final AtomicInteger useCount = new AtomicInteger();
    private volatile AbstractLmdb<?, ?> db;
    private volatile boolean open;
    private volatile Instant lastAccessTime;
    private volatile Instant lastWriteTime;

    public LocalShard(final ByteBufferFactory byteBufferFactory,
                      final PlanBDocCache planBDocCache,
                      final Provider<PlanBConfig> configProvider,
                      final StatePaths statePaths,
                      final String mapName) {
        this.byteBufferFactory = byteBufferFactory;
        this.planBDocCache = planBDocCache;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.mapName = mapName;
        lastWriteTime = Instant.now();
        this.shardDir = statePaths.getShardDir().resolve(mapName);
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
    public void merge(final Path sourceDir) {
        boolean success = false;

        // See if we can just merge by moving the file.
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
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
    }

    @Override
    public void condense() {
        try {
            final PlanBDoc doc = getDoc();
            if (doc != null && doc.isCondense()) {
                final SimpleDuration duration = SimpleDuration
                        .builder()
                        .time(doc.getCondenseAge())
                        .timeUnit(doc.getCondenseTimeUnit())
                        .build();
                final Instant maxAge = SimpleDurationUtil.minus(Instant.now(), duration);
                incrementUseCount();
                try {
                    db.condense(maxAge);
                } finally {
                    decrementUseCount();
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    @Override
    public void checkSnapshotStatus(final SnapshotRequest request) {
        // If we already have a snapshot for the current write time then don't create a snapshot and just return an
        // error.
        final Instant lastWriteTime = this.lastWriteTime;
        if (request.getCurrentSnapshotTime() != null &&
            Objects.equals(lastWriteTime.toEpochMilli(), request.getCurrentSnapshotTime())) {
            throw new NotModifiedException();
        }

        // Get shard dir.
        if (!Files.exists(shardDir)) {
            throw new RuntimeException("Shard not found");
        }
        final Path lmdbDataFile = shardDir.resolve(DATA_FILE_NAME);
        if (!Files.exists(lmdbDataFile)) {
            throw new RuntimeException("LMDB data file not found");
        }
    }

    @Override
    public void createSnapshot(final SnapshotRequest request,
                               final OutputStream outputStream) {
        // TODO : Possibly create windowed snapshots.

        boolean success = false;

        // If the DB is not open then we can just create the zip from the dir.
        lock.lock();
        try {
            if (!open) {
                success = true;
                createZip(outputStream, lastWriteTime);
            }
        } finally {
            lock.unlock();
        }

        // If the DB was open then we will need to lock the DB and zip the dir.
        if (!success) {
            incrementUseCount();
            try {
                db.lock(() -> createZip(outputStream, lastWriteTime));
            } finally {
                decrementUseCount();
            }
        }
    }

    private void createZip(final OutputStream outputStream,
                           final Instant lastWriteTime) {
        try (final ZipArchiveOutputStream zipOutputStream =
                ZipUtil.createOutputStream(new BufferedOutputStream(outputStream))) {
            ZipUtil.zip(shardDir, zipOutputStream);
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry(SNAPSHOT_INFO_FILE_NAME));
            zipOutputStream.write(lastWriteTime.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public <R> R get(final Function<AbstractLmdb<?, ?>, R> function) {
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

    private PlanBDoc getDoc() {
        if (doc == null) {
            doc = planBDocCache.get(mapName);
            if (doc == null) {
                LOGGER.warn(() -> "No PlanB doc found for '" + mapName + "'");
                throw new RuntimeException("No PlanB doc found for '" + mapName + "'");
            }
        }
        return doc;
    }

    private void open() {
        final PlanBDoc doc = getDoc();
        final String mapName = doc.getName();

        final Path shardDir = statePaths.getShardDir().resolve(mapName);
        if (Files.exists(shardDir)) {
            LOGGER.info(() -> "Found local shard for '" + mapName + "'");
            db = openDb(doc, shardDir);


        } else {
            // If this node is supposed to be a node that stores shards, but it doesn't have it, then error.
            final String message = "Local Plan B shard not found for '" +
                                   mapName +
                                   "'";
            LOGGER.error(() -> message);
            throw new RuntimeException(message);
        }
    }

    private AbstractLmdb<?, ?> openDb(final PlanBDoc doc,
                                      final Path targetPath) {
        switch (doc.getStateType()) {
            case STATE -> {
                return new StateDb(targetPath, byteBufferFactory, true, false);
            }
            case TEMPORAL_STATE -> {
                return new TemporalStateDb(targetPath, byteBufferFactory, true, false);
            }
            case RANGED_STATE -> {
                return new RangedStateDb(targetPath, byteBufferFactory, true, false);
            }
            case TEMPORAL_RANGED_STATE -> {
                return new TemporalRangedStateDb(targetPath, byteBufferFactory, true, false);
            }
            case SESSION -> {
                return new SessionDb(targetPath, byteBufferFactory, true, false);
            }
            default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
        }
    }
}
