/*
 * Copyright 2026 Crown Copyright
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

import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.dao.trace.QueueItem;
import stroom.planb.shared.SharedFileStoreSettings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules of one shard's queue folder, exercised against directories rather than real items.
 *
 * <p>Nothing here writes an LMDB environment: what makes a directory an item is its name, and that is
 * all this needs to know. Both ends of the hand-over address the folder through this class, so these
 * are also what stops a producer writing where a consumer does not read.
 */
class TestShardQueue {

    private static final String DOC_UUID = "c37f9502-0614-4bb3-a237-cc5a6b60f500";
    private static final int SHARD = 3;

    @TempDir
    Path shared;

    private ShardQueue queue;

    @BeforeEach
    void setUp() {
        queue = ShardQueue.of(new SharedFileStoreSettings(8, shared.toString()), DOC_UUID, SHARD);
    }

    @Test
    void theFolderIsWhereBothEndsLookForIt() {
        assertThat(queue.dir()).isEqualTo(shared
                .resolve(PlanBConstants.QUEUE_DIR_NAME)
                .resolve(DOC_UUID)
                .resolve(PlanBConstants.formatShardIndex(SHARD)));
    }

    @Test
    void askingWhereItIsDoesNotCreateIt() {
        // The writer brings the folder into being with the item's own temporary directory, so a reader
        // asking where to look must not leave an empty one behind for the cleaner to wonder about.
        assertThat(queue.dir()).doesNotExist();
    }

    @Test
    void onlyThingsNamedLikeAnItemCountTowardsTheDepth() throws IOException {
        addItem(1_000L);
        Files.createDirectories(queue.dir().resolve(QueueItem.tmpName(QueueItem.newName(2_000L))));
        Files.createFile(queue.dir().resolve("notes.txt"));

        assertThat(queue.isDeeperThan(1)).as("one item, whatever else is in the folder").isFalse();
        assertThat(queue.isDeeperThan(0)).isTrue();
    }

    @Test
    void depthIsMeasuredAgainstTheLimitTheCallerGives() throws IOException {
        for (int i = 0; i < 3; i++) {
            addItem(1_000L + i);
        }

        assertThat(queue.isDeeperThan(3)).as("three items is not deeper than three").isFalse();
        assertThat(queue.isDeeperThan(2)).isTrue();
    }

    @Test
    void anAbsentFolderIsNotDeep() throws IOException {
        assertThat(queue.isDeeperThan(0)).isFalse();
    }

    @Test
    void shardCountIsClampedSoNothingRoutesToAMissingShard() {
        assertThat(ShardQueue.shardCount(new SharedFileStoreSettings(0, shared.toString()))).isEqualTo(1);
        assertThat(ShardQueue.shardCount(new SharedFileStoreSettings(-1, shared.toString()))).isEqualTo(1);
        assertThat(ShardQueue.shardCount(new SharedFileStoreSettings(8, shared.toString()))).isEqualTo(8);
    }

    private Path addItem(final long orderKey) throws IOException {
        return Files.createDirectories(queue.dir().resolve(QueueItem.newName(orderKey)));
    }
}
