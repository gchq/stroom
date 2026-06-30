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

package stroom.planb.impl.data;


import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.impl.rest.NotModifiedException;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.SnapshotSettings;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
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

public class RestStoreShard extends AbstractStoreShard implements SnapshotCapable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RestStoreShard.class);

    private final Path snapshotDir;
    private volatile Instant lastSnapshotTime;

    public RestStoreShard(final ByteBuffers byteBuffers,
                          final ByteBufferFactory byteBufferFactory,
                          final Provider<PlanBConfig> configProvider,
                          final StatePaths statePaths,
                          final PlanBDocument doc) {
        this(byteBuffers, byteBufferFactory, configProvider, statePaths, doc, -1);
    }

    public RestStoreShard(final ByteBuffers byteBuffers,
                          final ByteBufferFactory byteBufferFactory,
                          final Provider<PlanBConfig> configProvider,
                          final StatePaths statePaths,
                          final PlanBDocument doc,
                          final int shardIndex) {
        this(byteBuffers, byteBufferFactory, configProvider, statePaths, doc, shardIndex,
                statePaths.getShardDir());
    }

    RestStoreShard(final ByteBuffers byteBuffers,
                   final ByteBufferFactory byteBufferFactory,
                   final Provider<PlanBConfig> configProvider,
                   final StatePaths statePaths,
                   final PlanBDocument doc,
                   final int shardIndex,
                   final Path shardBaseDir) {
        super(byteBuffers, byteBufferFactory, configProvider, statePaths, doc, shardIndex, shardBaseDir);
        final String dirSuffix = shardIndex >= 0 ? doc.getUuid() + "_" + shardIndex : doc.getUuid();
        this.snapshotDir = statePaths.getSnapshotDir().resolve(dirSuffix);
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
        // Check if a new snapshot is required and create one if it is.
        if (isNewSnapshotRequired()) {
            try {
                writeLock.lockInterruptibly();
                try {
                    // Check again that a new snapshot is required new we are under lock.
                    if (isNewSnapshotRequired()) {
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
                PlanBDocument::getSettings,
                AbstractPlanBSettings::getSnapshotSettings,
                new SnapshotSettings());

        if (!snapshotSettings.isUseSnapshotsForLookup() &&
            !snapshotSettings.isUseSnapshotsForGet() &&
            !snapshotSettings.isUseSnapshotsForQuery()) {
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
        return snapshotDir.resolve(PlanBConstants.SNAPSHOT_TMP_DIR_NAME);
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
