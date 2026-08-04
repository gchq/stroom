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

package stroom.planb.impl.data;

import stroom.util.date.DateUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class TestShardManager {

    /**
     * A node that stores shards publishes its snapshot as a file directly in the doc dir. Deleting that on
     * startup leaves it with nothing to serve, and as it is only recreated when a new one is required, a shard
     * that receives no further writes may never publish one again. See gh-5689.
     */
    @Test
    void deleteFetchedSnapshotsKeepsPublishedSnapshot(@TempDir final Path tempDir) throws IOException {
        final Path snapshotDir = tempDir.resolve("snapshots");
        final Path docDir = snapshotDir.resolve("2fd7f1a1-0e1e-4b1e-9f0a-2b0d5f6c7a8b");
        Files.createDirectories(docDir);

        // A snapshot this node publishes for other nodes to fetch.
        final Path publishedZip = docDir.resolve("snapshot.zip");
        Files.writeString(publishedZip, "published");

        // A snapshot previously fetched from the node that stores the shard.
        final Path fetchedDir = docDir.resolve(DateUtil.createFileDateTimeString(Instant.now()));
        Files.createDirectories(fetchedDir);
        final Path fetchedData = fetchedDir.resolve("data.mdb");
        Files.writeString(fetchedData, "fetched");

        ShardManager.deleteFetchedSnapshots(snapshotDir);

        assertThat(publishedZip).exists();
        assertThat(fetchedDir).doesNotExist();
        assertThat(docDir).exists();
    }

    @Test
    void deleteFetchedSnapshotsRemovesFetchesForAllDocs(@TempDir final Path tempDir) throws IOException {
        final Path snapshotDir = tempDir.resolve("snapshots");
        final Path fetchedOne = snapshotDir.resolve("uuid-1").resolve("2026-08-04T10-23-22-413Z");
        final Path fetchedTwo = snapshotDir.resolve("uuid-2").resolve("2026-08-04T10-33-22-413Z");
        Files.createDirectories(fetchedOne);
        Files.createDirectories(fetchedTwo);

        ShardManager.deleteFetchedSnapshots(snapshotDir);

        assertThat(fetchedOne).doesNotExist();
        assertThat(fetchedTwo).doesNotExist();
    }

    @Test
    void deleteFetchedSnapshotsToleratesMissingDir(@TempDir final Path tempDir) {
        assertThatNoException()
                .isThrownBy(() -> ShardManager.deleteFetchedSnapshots(tempDir.resolve("does-not-exist")));
    }
}
