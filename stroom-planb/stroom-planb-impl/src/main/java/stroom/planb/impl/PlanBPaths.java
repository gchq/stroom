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

package stroom.planb.impl;

import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.file.Path;

@Singleton
public class PlanBPaths {

    // The root directory for the whole local Plan B store.
    private final Path rootDir;
    // Each node writes a shard for each stream it processes to the writer dir.
    private final Path writerDir;
    // Once written a shard is posted to one or more store nodes that keep the shard in the receive dir.
    private final Path receiveDir;
    // Once received the data is moved to the staging dir awaiting merge.
    private final Path stagingDir;
    // After staging decompress the zip files and queue the individual parts for merging.
    private final Path unzipDir;
    // During the merging process shards are decompressed to the merging dir.
    private final Path mergingDir;
    // Active shards end up in the shard directory.
    private final Path shardDir;
    // Local snapshots allow for faster lookups.
    private final Path snapshotDir;
    // Local read-only cached copies of shared-store archive buckets.
    private final Path archiveCacheDir;
    // Local working area for publish writes, so no LMDB env is ever opened on the shared mount.
    private final Path archiveLocalDir;

    @Inject
    public PlanBPaths(final Provider<PlanBConfig> configProvider,
                      final PathCreator pathCreator) {
        this(pathCreator.toAppPath(configProvider.get().getPath()));
    }

    public PlanBPaths(final Path rootDir) {
        this.rootDir = rootDir;
        writerDir = rootDir.resolve(PlanBConstants.WRITER_DIR_NAME);
        receiveDir = rootDir.resolve(PlanBConstants.RECEIVE_DIR_NAME);
        stagingDir = rootDir.resolve(PlanBConstants.STAGING_DIR_NAME);
        unzipDir = rootDir.resolve(PlanBConstants.UNZIP_DIR_NAME);
        mergingDir = rootDir.resolve(PlanBConstants.MERGING_DIR_NAME);
        shardDir = rootDir.resolve(PlanBConstants.SHARDS_DIR_NAME);
        snapshotDir = rootDir.resolve(PlanBConstants.SNAPSHOTS_DIR_NAME);
        archiveCacheDir = rootDir.resolve(PlanBConstants.ARCHIVE_CACHE_DIR_NAME);
        archiveLocalDir = rootDir.resolve(PlanBConstants.ARCHIVE_LOCAL_DIR_NAME);
    }

    public Path getRootDir() {
        return rootDir;
    }

    public Path getWriterDir() {
        return writerDir;
    }

    public Path getReceiveDir() {
        return receiveDir;
    }

    public Path getUnzipDir() {
        return unzipDir;
    }

    public Path getStagingDir() {
        return stagingDir;
    }

    public Path getMergingDir() {
        return mergingDir;
    }

    public Path getShardDir() {
        return shardDir;
    }

    public Path getSnapshotDir() {
        return snapshotDir;
    }

    public Path getArchiveCacheDir() {
        return archiveCacheDir;
    }

    public Path getArchiveLocalDir() {
        return archiveLocalDir;
    }
}
