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
import stroom.planb.impl.data.AbstractStoreShard;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.StatePaths;
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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.function.Function;

public class SharedFileStoreShard extends AbstractStoreShard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedFileStoreShard.class);

    private static final long SYNC_CHECK_INTERVAL_MS = 1000;
    private volatile long lastSyncCheckTimeMs = 0;

    public SharedFileStoreShard(final ByteBuffers byteBuffers,
                                final ByteBufferFactory byteBufferFactory,
                                final Provider<PlanBConfig> configProvider,
                                final StatePaths statePaths,
                                final PlanBDocument doc,
                                final int shardIndex) {
        super(byteBuffers, byteBufferFactory, configProvider, statePaths, doc, shardIndex);
        syncFromSharedStoreIfRequired();
    }

    public SharedFileStoreShard(final ByteBuffers byteBuffers,
                                final ByteBufferFactory byteBufferFactory,
                                final Provider<PlanBConfig> configProvider,
                                final StatePaths statePaths,
                                final PlanBDocument doc,
                                final int shardIndex,
                                final Path shardBaseDir) {
        super(byteBuffers, byteBufferFactory, configProvider, statePaths, doc, shardIndex, shardBaseDir);
        syncFromSharedStoreIfRequired();
    }

    @Override
    public void merge(final Path sourceDir) {
        syncFromSharedStoreIfRequired();
        super.merge(sourceDir);
    }

    @Override
    public long deleteOldData(final PlanBDocument doc) {
        syncFromSharedStoreIfRequired();
        return super.deleteOldData(doc);
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
    public long archiveOldData(final PlanBDocument doc,
                               final Path archiveBaseDir) throws IOException {
        syncFromSharedStoreIfRequired();

        final ArchivalSettings archival = doc.getSettings() instanceof final HasSharedFileStore s
                && s.getSharedFileStore() != null
                ? s.getSharedFileStore().getArchival() : null;
        if (archival == null || !archival.isEnabled()) {
            return 0;
        }

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
            count = db.archiveOldData(archiveBefore, archival.getGranularity(), archiveBaseDir);
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

        try {
            final Path sharedShardDir = Path.of(doc.getSharedPath())
                    .resolve(PlanBConstants.SHARDS_DIR_NAME)
                    .resolve(doc.getUuid())
                    .resolve(String.format("%04d", shardIndex));
            final Path sharedVersionFile = sharedShardDir.resolve(PlanBConstants.VERSION_FILE_NAME);

            if (!Files.exists(sharedVersionFile)) {
                // No shared version yet, meaning no merged shard exists in shared store yet.
                lastSyncCheckTimeMs = now;
                return;
            }

            final Path localVersionFile = shardDir.resolve(PlanBConstants.VERSION_FILE_NAME);
            final String localVersion = Files.exists(localVersionFile) ? Files.readString(localVersionFile).trim() : "";
            final String sharedVersion = Files.readString(sharedVersionFile).trim();

            if (localVersion.equals(sharedVersion)) {
                lastSyncCheckTimeMs = now;
                return;
            }

            // Stale or missing local copy, perform copy-then-validate
            LOGGER.info(() -> "Local shard version (" + localVersion + ") for " + doc.getName()
                    + " (shard " + shardIndex + ") is stale. Syncing from shared store version ("
                    + sharedVersion + ")");

            int attempts = 0;
            boolean success = false;
            while (attempts < 3 && !success) {
                attempts++;
                final String v1 = Files.readString(sharedVersionFile).trim();
                final Path syncTmpDir = shardDir.resolve("sync_tmp");
                FileUtil.deleteDir(syncTmpDir);
                Files.createDirectories(syncTmpDir);

                // Copy data.mdb
                final Path sharedDataFile = sharedShardDir.resolve(PlanBConstants.DATA_FILE_NAME);
                if (Files.exists(sharedDataFile)) {
                    Files.copy(sharedDataFile, syncTmpDir.resolve(PlanBConstants.DATA_FILE_NAME),
                            StandardCopyOption.REPLACE_EXISTING);
                }

                final String v2 = Files.readString(sharedVersionFile).trim();
                if (v1.equals(v2)) {
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
                                Files.move(tmpDataFile, localDataFile, StandardCopyOption.REPLACE_EXISTING);
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
                FileUtil.deleteDir(syncTmpDir);
            }
            if (!success) {
                throw new RuntimeException("Failed to sync shard from shared store due to concurrent modifications");
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }
}
