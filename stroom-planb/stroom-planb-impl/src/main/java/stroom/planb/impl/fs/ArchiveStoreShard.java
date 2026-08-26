/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.fs;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.dao.Db;
import stroom.planb.impl.data.archive.ArchiveShardRef;
import stroom.planb.impl.data.shard.AbstractStoreShard;
import stroom.planb.shared.PlanBDocument;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * A read-only, idle-evictable local cache of ONE archive bucket on the shared store
 * (<sharedPath>/archive/<uuid>/<idx>/<dateLabel>/). It copies the
 * bucket's {@code data.mdb} down into a per-instance generation dir
 * ({@code archive_cache/<uuid>_<idx>_<dateLabel>/<generation>}) and mmaps the LOCAL copy read-only, so
 * repeat archive reads reuse it instead of re-copying the (large) bucket every query. Version-checks the
 * bucket's {@code .version} on access and re-syncs when it changes (later records keep merging into
 * the same date bucket). Bucket deletion (retention) needs no handling here — the locator stops
 * returning a deleted bucket, so this copy is simply never served again and idle-evicts.
 *
 * <p>The fresh generation dir per instance means an idle-evicted (closing) instance and its replacement
 * never share a {@code lock.mdb} directory, avoiding the robust-mutex SIGSEGV hazard.
 */
public class ArchiveStoreShard extends AbstractStoreShard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ArchiveStoreShard.class);

    private static final long SYNC_CHECK_INTERVAL_MS = 1000;

    private final Path archiveBucketDir;
    private final ReentrantLock syncLock = new ReentrantLock();
    private volatile long lastSyncCheckTimeMs = 0;

    public ArchiveStoreShard(final ByteBuffers byteBuffers,
                             final ByteBufferFactory byteBufferFactory,
                             final Provider<PlanBConfig> configProvider,
                             final PlanBPaths planBPaths,
                             final PlanBDocument doc,
                             final int shardIndex,
                             final ArchiveShardRef ref) {
        super(byteBuffers, byteBufferFactory, configProvider, doc, shardIndex,
                planBPaths.getArchiveCacheDir()
                        .resolve(doc.getUuid() + "_" + shardIndex + "_" + ref.dateLabel())
                        .resolve(newGeneration()),
                false); // deferred open — read-only env can't open until data.mdb is copied in
        this.archiveBucketDir = ref.dir();
        // Initial copy-down + read-only open (throws if the bucket has no data.mdb — caller treats a
        // failed archive read as a miss, so no broken cache entry is left).
        syncFromArchiveIfRequired();
    }

    private static String newGeneration() {
        return System.currentTimeMillis() + "_" + UUID.randomUUID();
    }

    @Override
    protected boolean isReadOnly() {
        return true;
    }

    @Override
    public <R> R get(final Function<Db<?, ?>, R> function) {
        syncFromArchiveIfRequired();
        return super.get(function);
    }

    @Override
    public String getInfo() {
        syncFromArchiveIfRequired();
        return super.getInfo();
    }

    @Override
    public boolean isIdle() {
        final Duration timeout = configProvider.get().getMinTimeToKeepStoreShardEnv().getDuration();
        return lastAccessTime.plus(timeout).isBefore(Instant.now());
    }

    // Read-only — these are never invoked (archive shards are held in a separate map that the merge /
    // maintenance loops do not touch) but guard against accidental mutation.
    @Override
    public void merge(final Path sourceDir) {
        throw new UnsupportedOperationException("Archive shard is read-only");
    }

    @Override
    public long runRetention(final PlanBDocument doc) {
        throw new UnsupportedOperationException("Archive shard is read-only");
    }

    @Override
    public long condense(final PlanBDocument doc) {
        throw new UnsupportedOperationException("Archive shard is read-only");
    }

    @Override
    public void compact() {
        throw new UnsupportedOperationException("Archive shard is read-only");
    }

    /**
     * Copies the bucket's {@code data.mdb} down and opens it read-only when the local copy is absent or
     * its {@code .version} differs from the bucket's. Throttled to at most once per
     * {@link #SYNC_CHECK_INTERVAL_MS}. The swap runs under {@code exclusiveReadLock} so no in-flight
     * reader sees a half-open env.
     */
    private void syncFromArchiveIfRequired() {
        final long now = System.currentTimeMillis();
        if (db != null && now - lastSyncCheckTimeMs < SYNC_CHECK_INTERVAL_MS) {
            return;
        }

        syncLock.lock();
        try {
            syncFromArchiveUnderLock();
        } finally {
            syncLock.unlock();
        }
    }

    private void syncFromArchiveUnderLock() {
        // Re-check under the lock: another thread may have synced while we waited.
        final long now = System.currentTimeMillis();
        if (db != null && now - lastSyncCheckTimeMs < SYNC_CHECK_INTERVAL_MS) {
            return;
        }
        lastSyncCheckTimeMs = now;

        final Path sharedDataFile = archiveBucketDir.resolve(PlanBConstants.DATA_FILE_NAME);
        final Path sharedVersionFile = archiveBucketDir.resolve(PlanBConstants.VERSION_FILE_NAME);
        final Path localVersionFile = shardDir.resolve(PlanBConstants.VERSION_FILE_NAME);

        try {
            if (!Files.exists(sharedDataFile)) {
                // Bucket vanished (retention) or is incomplete. If we already have a local copy keep
                // serving it (it will idle-evict); otherwise this shard cannot be used.
                if (db == null) {
                    throw new RuntimeException("Archive bucket has no data.mdb: " + archiveBucketDir);
                }
                return;
            }

            String sharedVersion = readVersionIfPresent(sharedVersionFile);
            if (sharedVersion == null) {
                // No .version file at all: either retention deleted the bucket since the locator listed
                // it, or a crash left its first push unversioned (see publishBucketData). A republish
                // does NOT cause this — it rewrites .version in place. If we already have a local copy,
                // keep serving it and re-check next interval; otherwise sync on the data file alone.
                if (db != null) {
                    return;
                }
                sharedVersion = "";
            }
            final String localVersion = Files.exists(localVersionFile)
                    ? Files.readString(localVersionFile).trim()
                    : "";

            if (db != null && localVersion.equals(sharedVersion)) {
                return; // local copy is current
            }

            LOGGER.info(() -> "Syncing archive bucket " + archiveBucketDir + " to local copy " + shardDir);

            final Path syncTmpDir = shardDir.resolve("sync_tmp");
            FileUtil.deleteDir(syncTmpDir);
            Files.createDirectories(syncTmpDir);
            Files.copy(sharedDataFile, syncTmpDir.resolve(PlanBConstants.DATA_FILE_NAME),
                    StandardCopyOption.REPLACE_EXISTING);

            writeLock.lockInterruptibly();
            try {
                exclusiveReadLock.lockInterruptibly();
                try {
                    closeDb();
                    Files.move(syncTmpDir.resolve(PlanBConstants.DATA_FILE_NAME),
                            shardDir.resolve(PlanBConstants.DATA_FILE_NAME),
                            StandardCopyOption.REPLACE_EXISTING);
                    // Drop any lock.mdb so the read-only open starts with clean mutex state.
                    Files.deleteIfExists(shardDir.resolve(PlanBConstants.LOCK_FILE_NAME));
                    Files.writeString(localVersionFile, sharedVersion);
                    open();
                } finally {
                    exclusiveReadLock.unlock();
                }
            } finally {
                writeLock.unlock();
            }
            FileUtil.deleteDir(syncTmpDir);

        } catch (final NoSuchFileException e) {
            // A file we were part way through reading has gone: retention can delete the whole bucket
            // dir at any point, and on a store with no atomic move data.mdb is briefly absent while
            // publishBucketData replaces it. Keep serving any current local copy; only surface a
            // failure if we have no copy yet (the caller treats a failed archive read as a miss).
            if (db == null) {
                throw new UncheckedIOException(e);
            }
            LOGGER.debug(() -> "Archive bucket changed during sync, keeping current copy: "
                    + archiveBucketDir);
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Reads the bucket version file. Returns null if it is absent or vanishes mid-read, i.e. the bucket has
    // been deleted (treated as "no readable version right now"). A republish rewrites .version in place, so
    // it shows up here as a short or empty read rather than an absent file.
    private static String readVersionIfPresent(final Path versionFile) throws IOException {
        try {
            return Files.readString(versionFile).trim();
        } catch (final NoSuchFileException e) {
            return null;
        }
    }
}
