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
import stroom.planb.impl.data.shard.AbstractStoreShard;
import stroom.planb.impl.db.Db;
import stroom.planb.shared.ArchivalSettings;
import stroom.planb.shared.HasSharedFileStore;
import stroom.planb.shared.PlanBDocument;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.SimpleDurationUtil;

import jakarta.inject.Provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class SharedFileStoreShard extends AbstractStoreShard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedFileStoreShard.class);

    private static final long SYNC_CHECK_INTERVAL_MS = 1000;
    private final ReentrantLock syncLock = new ReentrantLock();
    private volatile long lastSyncCheckTimeMs = 0;

    public SharedFileStoreShard(final ByteBuffers byteBuffers,
                                final ByteBufferFactory byteBufferFactory,
                                final Provider<PlanBConfig> configProvider,
                                final PlanBPaths planBPaths,
                                final PlanBDocument doc,
                                final int shardIndex) {
        // Read instances live in a per-instance generation subdir (shards/<uuid>_<idx>/<generation>)
        // so an idle-evicted (closing) instance and its replacement never share a lock.mdb dir.
        super(byteBuffers, byteBufferFactory, configProvider, planBPaths, doc, shardIndex,
                planBPaths.getShardDir(), newGeneration());
        syncFromSharedStoreIfRequired();
    }

    private static String newGeneration() {
        return System.currentTimeMillis() + "_" + UUID.randomUUID();
    }

    /**
     * Idle when neither read nor write has touched this shard within
     * {@code minTimeToKeepStoreShardEnv}. The local copy is then evicted (deleted)
     * and re-synced from the shared store on next access.
     */
    @Override
    public boolean isIdle() {
        final Duration timeout = configProvider.get().getMinTimeToKeepStoreShardEnv().getDuration();
        final Instant idleSince = lastAccessTime.isAfter(lastWriteTime) ? lastAccessTime : lastWriteTime;
        return idleSince.plus(timeout).isBefore(Instant.now());
    }

    public SharedFileStoreShard(final ByteBuffers byteBuffers,
                                final ByteBufferFactory byteBufferFactory,
                                final Provider<PlanBConfig> configProvider,
                                final PlanBPaths planBPaths,
                                final PlanBDocument doc,
                                final int shardIndex,
                                final Path shardBaseDir) {
        super(byteBuffers, byteBufferFactory, configProvider, planBPaths, doc, shardIndex, shardBaseDir);
        syncFromSharedStoreIfRequired();
    }

    /**
     * A shared-file-store shard is a holding area, not a query target — queries read the archive buckets.
     * So it carries none of the query-only structures a store can have (for traces, the secondary sort
     * indexes), which would otherwise be maintained for every span merged and never read.
     */
    @Override
    protected boolean isQueryable() {
        return false;
    }

    @Override
    public void merge(final Path sourceDir) {
        syncFromSharedStoreIfRequired();
        super.merge(sourceDir);
    }

    @Override
    public long runRetention(final PlanBDocument doc) {
        syncFromSharedStoreIfRequired();
        return super.runRetention(doc);
    }

    @Override
    public long condense(final PlanBDocument doc) {
        syncFromSharedStoreIfRequired();
        return super.condense(doc);
    }

    @Override
    public <R> R get(final Function<Db<?, ?>, R> function) {
        syncFromSharedStoreIfRequired();
        return super.get(function);
    }

    @Override
    public String getInfo() {
        syncFromSharedStoreIfRequired();
        return super.getInfo();
    }

    /**
     * Archives entries older than the configured archival lead time to local
     * temporary directories grouped by ArchivalGranularity, then deletes them
     * from the main shard.
     *
     * Must be called inside the shard cluster lock.
     * The caller (ArchiveOperation) pushes the returned dirs to the shared store
     * and deletes them afterwards.
     *
     * @param doc            PlanBDoc carrying ArchivalSettings
     * @param archiveBaseDir local base dir; dated subdirs are created underneath
     * @return count of archived entries (0 if nothing to archive)
     */
    public long runArchival(final PlanBDocument doc,
                               final Path archiveBaseDir) throws IOException {
        syncFromSharedStoreIfRequired();

        // Present for any doc with a shared file store, which this shard by definition has. Throw rather
        // than return 0: silently not archiving would leave data only in the holding area, which queries
        // never read.
        final ArchivalSettings archival = HasSharedFileStore.archivalSettings(doc.getSettings())
                .orElseThrow(() -> new IllegalStateException(
                        "No shared file store settings for " + doc.getName()));

        final Instant archiveBefore =
                SimpleDurationUtil.minus(Instant.now(), archival.getDuration());

        Files.createDirectories(archiveBaseDir);

        final long count;
        try {
            writeLock.lockInterruptibly();
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
        try {
            count = db.runArchival(archiveBefore, archival.getGranularity(), archiveBaseDir);
            if (count > 0) {
                lastWriteTime = Instant.now();
            }
        } finally {
            writeLock.unlock();
        }

        return count;
    }

    private void syncFromSharedStoreIfRequired() {
        if (doc.getSharedPath() == null || shardIndex < 0) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - lastSyncCheckTimeMs < SYNC_CHECK_INTERVAL_MS) {
            return;
        }

        syncLock.lock();
        try {
            syncFromSharedStoreUnderLock();
        } finally {
            syncLock.unlock();
        }
    }

    private void syncFromSharedStoreUnderLock() {
        // Re-check under the lock: another thread may have synced while we waited.
        final long now = System.currentTimeMillis();
        if (now - lastSyncCheckTimeMs < SYNC_CHECK_INTERVAL_MS) {
            return;
        }

        try {
            final Path sharedShardDir = Path.of(doc.getSharedPath())
                    .resolve(PlanBConstants.SHARDS_DIR_NAME)
                    .resolve(doc.getUuid())
                    .resolve(String.format("%04d", shardIndex));
            final Path sharedVersionFile = sharedShardDir.resolve(PlanBConstants.VERSION_FILE_NAME);
            final Path localVersionFile = shardDir.resolve(PlanBConstants.VERSION_FILE_NAME);

            final String v = readVersionIfPresent(sharedVersionFile);
            if (v == null) {
                // No readable shared version right now: either no merged shard exists yet, or the
                // shared shard is mid-republish and its .version is momentarily absent (we hold no
                // cluster lock, so we can race the writer's rename-swap). Keep the current local
                // copy and re-check next interval.
                lastSyncCheckTimeMs = now;
                return;
            }
            final String sharedVersion = v;
            final String localVersion = Files.exists(localVersionFile)
                    ? Files.readString(localVersionFile).trim()
                    : "";

            if (localVersion.equals(sharedVersion)) {
                lastSyncCheckTimeMs = now;
                return;
            }

            // Stale or missing local copy, perform copy-then-validate.
            LOGGER.info(() -> "Local shard version (" + localVersion + ") for " + doc.getName()
                    + " (shard " + shardIndex + ") is stale. Syncing from shared store version ("
                    + sharedVersion + ")");

            int attempts = 0;
            boolean success = false;
            while (attempts < 3 && !success) {
                attempts++;
                final Path syncTmpDir = shardDir.resolve("sync_tmp");
                try {
                    final String v1 = readVersionIfPresent(sharedVersionFile);
                    if (v1 == null) {
                        LOGGER.warn("Shared version vanished during sync. Retrying. Attempt " + attempts);
                        continue;
                    }
                    FileUtil.deleteDir(syncTmpDir);
                    Files.createDirectories(syncTmpDir);

                    final Path sharedDataFile = sharedShardDir.resolve(PlanBConstants.DATA_FILE_NAME);
                    if (Files.exists(sharedDataFile)) {
                        interruptibleCopy(sharedDataFile, syncTmpDir.resolve(PlanBConstants.DATA_FILE_NAME));
                    }

                    final String v2 = readVersionIfPresent(sharedVersionFile);
                    if (v2 != null && v1.equals(v2)) {
                        // Success! Swap the files under exclusive lock
                        writeLock.lockInterruptibly();
                        try {
                            exclusiveReadLock.lockInterruptibly();
                            try {
                                close();
                                // Move files
                                final Path localDataFile = shardDir.resolve(PlanBConstants.DATA_FILE_NAME);
                                final Path tmpDataFile = syncTmpDir.resolve(PlanBConstants.DATA_FILE_NAME);
                                if (Files.exists(tmpDataFile)) {
                                    Files.move(tmpDataFile, localDataFile,
                                            StandardCopyOption.REPLACE_EXISTING);
                                }

                                // Delete any existing lock.mdb so LMDB creates a fresh one
                                // with clean mutex state on the next mdb_env_open.
                                final Path localLockFile = shardDir.resolve(PlanBConstants.LOCK_FILE_NAME);
                                Files.deleteIfExists(localLockFile);

                                Files.writeString(localVersionFile, v2);
                                open();
                                success = true;
                                lastSyncCheckTimeMs = System.currentTimeMillis();
                            } finally {
                                exclusiveReadLock.unlock();
                            }
                        } finally {
                            writeLock.unlock();
                        }
                    } else {
                        LOGGER.warn("Version changed during sync copy. Retrying. Attempt " + attempts);
                    }
                } catch (final NoSuchFileException e) {
                    // The shared shard was republished mid-copy (a file briefly absent during the
                    // writer's rename-swap). Treat as a concurrent change and retry.
                    LOGGER.warn("Shared shard changed during sync copy. Retrying. Attempt " + attempts);
                } finally {
                    FileUtil.deleteDir(syncTmpDir);
                }
            }
            if (!success) {
                throw new RuntimeException(
                        "Failed to sync shard from shared store due to concurrent modifications");
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    // Copies a (potentially large) file in chunks, checking the calling thread's interrupt flag between
    // chunks and aborting with an InterruptedException if it is set. Lets a terminated query TaskContext
    // stop a long shard copy-down promptly instead of waiting for the whole file to copy.
    private static void interruptibleCopy(final Path source, final Path target)
            throws IOException, InterruptedException {
        try (final InputStream in = Files.newInputStream(source);
                final OutputStream out = Files.newOutputStream(target,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
            final byte[] buffer = new byte[1 << 20];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Interrupted while copying " + source);
                }
                out.write(buffer, 0, read);
            }
        }
    }

    // Reads the shared version file, tolerating a concurrent republish. Readers hold no cluster lock, so a
    // writer's rename-swap can move/replace the shared .version between our checks; returns null if the file
    // is absent or vanishes mid-read (treated as "no readable version right now").
    private static String readVersionIfPresent(final Path versionFile) throws IOException {
        try {
            return Files.readString(versionFile).trim();
        } catch (final NoSuchFileException e) {
            return null;
        }
    }
}
