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

package stroom.planb.impl.db;

import stroom.planb.impl.PlanBConfig;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.file.Path;

@Singleton
public class StatePaths {

    // The root directory for the whole state store.
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

    @Inject
    public StatePaths(final Provider<PlanBConfig> configProvider,
                      final PathCreator pathCreator) {
        this(pathCreator.toAppPath(configProvider.get().getPath()));
    }

    public StatePaths(final Path rootDir) {
        this.rootDir = rootDir;
        writerDir = rootDir.resolve("writer");
        receiveDir = rootDir.resolve("receive");
        stagingDir = rootDir.resolve("staging");
        unzipDir = rootDir.resolve("unzip");
        mergingDir = rootDir.resolve("merging");
        shardDir = rootDir.resolve("shards");
        snapshotDir = rootDir.resolve("snapshots");
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
}
