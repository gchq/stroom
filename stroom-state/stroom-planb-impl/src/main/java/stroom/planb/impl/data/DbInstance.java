package stroom.planb.impl.data;


import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.shared.PlanBDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Provider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

class DbInstance {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DbInstance.class);

    private final ByteBuffers byteBuffers;
    private final Provider<PlanBConfig> configProvider;
    private final Path dbDir;

    private final ReentrantLock readLock = new ReentrantLock();
    private final PlanBDoc doc;
    private final AtomicInteger activeReaders = new AtomicInteger();
    private volatile Db<?, ?> db;
    private volatile boolean open;
    private volatile Instant lastAccessTime;
    private volatile Instant lastWriteTime;
    private volatile Instant lastSnapshotTime;

    public DbInstance(final ByteBuffers byteBuffers,
                      final Provider<PlanBConfig> configProvider,
                      final Path dbDir,
                      final PlanBDoc doc) {
        this.byteBuffers = byteBuffers;
        this.configProvider = configProvider;
        this.doc = doc;
        lastWriteTime = Instant.now();
        this.dbDir = dbDir;
    }

    private void incrementUseCount() {
        readLock.lock();
        try {
            // Open if needed.
            if (!open) {
                open();
                open = true;
            }

            final int count = activeReaders.incrementAndGet();
            if (count <= 0) {
                throw new RuntimeException("Unexpected count");
            }

            lastAccessTime = Instant.now();

        } finally {
            readLock.unlock();
        }
    }

    private void decrementUseCount() {
        readLock.lock();
        try {
            final int count = activeReaders.decrementAndGet();
            if (count < 0) {
                throw new RuntimeException("Unexpected count");
            }
            cleanup();
        } finally {
            readLock.unlock();
        }
    }

    public <R> R get(final Function<Db<?, ?>, R> function) {
        incrementUseCount();
        try {
            return function.apply(db);
        } finally {
            decrementUseCount();
        }
    }

    public void cleanup() {
        readLock.lock();
        try {
            if (activeReaders.get() == 0) {
                if (open && isIdle()) {
                    db.close();
                    db = null;
                    open = false;
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    private boolean isIdle() {
        return lastAccessTime.isBefore(Instant.now().minus(
                configProvider.get().getMinTimeToKeepEnvOpen().getDuration()));
    }

    private void open() {
        if (Files.exists(dbDir)) {
            LOGGER.info(() -> "Found local shard for '" + doc + "'");
            db = PlanBDb.open(doc, dbDir, byteBuffers, false);

        } else {
            // If this node is supposed to be a node that stores shards, but it doesn't have it, then error.
            final String message = "Local Plan B shard not found for '" +
                                   doc +
                                   "'";
            LOGGER.error(() -> message);
            throw new RuntimeException(message);
        }
    }

    public String getInfo() {
        incrementUseCount();
        try {
            return db.getInfoString();
        } finally {
            decrementUseCount();
        }
    }
}
