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
import stroom.planb.impl.data.archive.ArchiveShardRef;
import stroom.planb.impl.data.shard.AbstractStoreShard;
import stroom.planb.shared.PlanBDocument;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A read-only, idle-evictable local cache of ONE version of ONE archive bucket on the shared store
 * (<sharedPath>/archive/<uuid>/<idx>/<dateLabel>/). The constructor copies the bucket's
 * {@code data.mdb} down into a per-instance generation dir
 * ({@code archive_cache/<uuid>_<idx>_<dateLabel>/<generation>}) and mmaps the LOCAL copy read-only, so
 * repeat archive reads reuse it instead of re-copying the (large) bucket every query.
 *
 * <p>The copy is never replaced in place. Later records keep merging into the same date bucket, and
 * {@link #isStale()} reports when that has happened; {@code ShardManager} answers by building a
 * replacement instance on a fresh generation dir and retiring this one. Replacing rather than
 * re-opening is what keeps reads lock-free: closing an env to swap its data file underneath needs
 * {@code exclusiveReadLock}, so doing it on the read path made a query wait for every in-flight
 * reader, including the minutes-long pathway build.
 *
 * <p>Bucket deletion (retention) needs no handling here — the locator stops returning a deleted
 * bucket, so this copy is simply never served again and idle-evicts.
 *
 * <p>The fresh generation dir per instance means a closing instance (idle-evicted or retired) and its
 * replacement never share a {@code lock.mdb} directory, avoiding the robust-mutex SIGSEGV hazard.
 */
public class ArchiveStoreShard extends AbstractStoreShard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ArchiveStoreShard.class);

    private static final long STALE_CHECK_INTERVAL_MS = 1000;

    private final Path archiveBucketDir;
    private final String copiedVersion;
    private volatile long lastStaleCheckTimeMs = 0;

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
        // Copy-down + read-only open (throws if the bucket has no data.mdb — caller treats a
        // failed archive read as a miss, so no broken cache entry is left).
        this.copiedVersion = copyDownAndOpen();
    }

    private static String newGeneration() {
        return System.currentTimeMillis() + "_" + UUID.randomUUID();
    }

    @Override
    protected boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isIdle() {
        final Duration timeout = configProvider.get().getMinTimeToKeepStoreShardEnv().getDuration();
        return lastAccessTime.plus(timeout).isBefore(Instant.now());
    }

    /**
     * True when the bucket has been republished since this copy was taken, i.e. this instance should be
     * replaced rather than served again. Reads the bucket's {@code .version} at most once per
     * {@link #STALE_CHECK_INTERVAL_MS} and takes no lock, so a reader never waits on it — a lost race
     * costs one redundant check or a replacement that lands a moment later.
     *
     * <p>False when the version cannot be read at all: retention can delete the bucket at any point,
     * and until the locator stops offering it the copy already held is the best answer available.
     */
    public boolean isStale() {
        final long now = System.currentTimeMillis();
        if (now - lastStaleCheckTimeMs < STALE_CHECK_INTERVAL_MS) {
            return false;
        }
        lastStaleCheckTimeMs = now;

        try {
            final String bucketVersion = readVersionIfPresent(
                    archiveBucketDir.resolve(PlanBConstants.VERSION_FILE_NAME));
            return bucketVersion != null && !bucketVersion.equals(copiedVersion);
        } catch (final IOException e) {
            LOGGER.debug(() -> "Could not read version of " + archiveBucketDir
                    + ", keeping current copy: " + e.getMessage());
            return false;
        }
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
     * Copies the bucket's {@code data.mdb} into this instance's generation dir and opens it read-only,
     * returning the bucket version copied. Runs from the constructor only: the dir is private to an
     * instance nothing can reach yet, so the copy needs no temp file and the open needs no lock.
     */
    private String copyDownAndOpen() {
        final Path sharedDataFile = archiveBucketDir.resolve(PlanBConstants.DATA_FILE_NAME);
        final Path sharedVersionFile = archiveBucketDir.resolve(PlanBConstants.VERSION_FILE_NAME);

        try {
            if (!Files.exists(sharedDataFile)) {
                // Bucket vanished (retention) or is incomplete, so this shard cannot be used.
                throw new RuntimeException("Archive bucket has no data.mdb: " + archiveBucketDir);
            }

            // No .version file at all: either retention deleted the bucket since the locator listed it,
            // or a crash left its first push unversioned (see publishBucketData). A republish does NOT
            // cause this — it rewrites .version in place. Copy on the data file alone and record the
            // empty version, so the first bucket version to appear reads as a change.
            final String readVersion = readVersionIfPresent(sharedVersionFile);
            final String bucketVersion = readVersion == null ? "" : readVersion;

            LOGGER.debug(() -> "Copying archive bucket " + archiveBucketDir + " to local copy " + shardDir);
            Files.copy(sharedDataFile, shardDir.resolve(PlanBConstants.DATA_FILE_NAME));
            Files.writeString(shardDir.resolve(PlanBConstants.VERSION_FILE_NAME), bucketVersion);
            open();
            return bucketVersion;

        } catch (final NoSuchFileException e) {
            // A file we were part way through reading has gone: retention can delete the whole bucket
            // dir at any point, and on a store with no atomic move data.mdb is briefly absent while
            // publishBucketData replaces it. The caller treats a failed archive read as a miss.
            throw new UncheckedIOException(e);
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
