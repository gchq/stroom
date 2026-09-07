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

package stroom.planb.impl.data.shard;


import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.data.SnapshotNotFoundException;
import stroom.planb.impl.rest.NotModifiedException;
import stroom.planb.shared.AbstractHttpStoreSettings;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.SnapshotSettings;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
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

public class RestStoreShard extends AbstractStoreShard implements SnapshotCapable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RestStoreShard.class);

    /**
     * Caps the linear growth of the delay between snapshot creation retries, as a multiple of the snapshot
     * lifespan. At the default 10 minute lifespan this caps retries at one per hour.
     */
    private static final int MAX_SNAPSHOT_RETRY_MULTIPLIER = 6;

    private final Path snapshotDir;
    private volatile Instant lastSnapshotTime;
    // Time of the last failed snapshot creation, and the number of consecutive failures. Both are only mutated
    // while holding writeLock and are reset when a snapshot is successfully created.
    private volatile Instant lastSnapshotFailureTime;
    private final AtomicInteger snapshotFailureCount = new AtomicInteger();

    public RestStoreShard(final ByteBuffers byteBuffers,
                          final ByteBufferFactory byteBufferFactory,
                          final Provider<PlanBConfig> configProvider,
                          final PlanBPaths planBPaths,
                          final PlanBDocument doc) {
        super(byteBuffers, byteBufferFactory, configProvider, planBPaths, doc);
        this.snapshotDir = planBPaths.getSnapshotDir().resolve(doc.getUuid());
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
    protected void afterMutation() {
        createSnapshot();
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

                    // Don't leave a part written snapshot behind. It is about the size of the shard, and a full
                    // disk is the most likely reason for creation to fail repeatedly, so keeping it would hold
                    // on to the space that caused the failure. See gh-5689.
                    deleteSnapshotTmp();
                } finally {
                    writeLock.unlock();
                }
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            }
        }
    }

    private boolean isNewSnapshotRequired() {
        final SnapshotSettings snapshotSettings = AbstractHttpStoreSettings.snapshotSettings(
                NullSafe.get(doc, PlanBDocument::getSettings));

        if (!snapshotSettings.isUseSnapshotsForLookup() &&
            !snapshotSettings.isUseSnapshotsForGet() &&
            !snapshotSettings.isUseSnapshotsForQuery()) {
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

    private Duration getSnapshotLifespan() {
        return NullSafe.getOrElse(
                configProvider.get(),
                PlanBConfig::getMinTimeToKeepSnapshots,
                StroomDuration::getDuration,
                Duration.ofMinutes(10));
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

    public Path getSnapshotTmp() {
        return snapshotDir.resolve(PlanBConstants.SNAPSHOT_TMP_DIR_NAME);
    }

    /**
     * Delete any part written snapshot left behind by a failed attempt. Must not throw, as it is called while
     * handling a failure that we are deliberately swallowing.
     */
    private void deleteSnapshotTmp() {
        final Path tmpFile = getSnapshotTmp();
        try {
            Files.deleteIfExists(tmpFile);
        } catch (final Exception e) {
            LOGGER.error(() -> LogUtil.message("Error deleting part written snapshot '{}': {}",
                    FileUtil.getCanonicalPath(tmpFile), e.getMessage()), e);
        }
    }

    public Path getSnapshotZip() {
        return snapshotDir.resolve(PlanBConstants.SNAPSHOT_ZIP_FILE_NAME);
    }

    private void createZip(final Path zipFile,
                           final Instant lastWriteTime) {
        try (final ZipArchiveOutputStream zipOutputStream =
                ZipUtil.createOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
            ZipUtil.zip(shardDir, zipOutputStream);
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry(PlanBConstants.SNAPSHOT_INFO_FILE_NAME));
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
}
