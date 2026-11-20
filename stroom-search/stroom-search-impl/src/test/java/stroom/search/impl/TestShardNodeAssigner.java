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

package stroom.search.impl;

import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.search.impl.ShardNodeAssigner.Reassignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestShardNodeAssigner {

    private static final String OWNER = "node1";
    private static final Set<String> ALL_NODES = Set.of("node1", "node2", "node3");
    private static final Set<String> OWNER_DOWN = Set.of("node2", "node3");

    @ParameterizedTest
    @EnumSource(value = IndexShardStatus.class, names = {"NEW", "OPEN", "OPENING", "CLOSING", "CLOSED"})
    void testLocalVolume_alwaysTheOwner(final IndexShardStatus status) {
        // Only the owner can see a local volume, so nothing else can be chosen even when the owner is down.
        for (long id = 1; id <= 50; id++) {
            final IndexShard shard = shard(id, VolumeType.LOCAL, status);
            assertThat(ShardNodeAssigner.chooseNode(shard, ALL_NODES)).contains(OWNER);
            assertThat(ShardNodeAssigner.chooseNode(shard, OWNER_DOWN)).contains(OWNER);
            assertThat(ShardNodeAssigner.chooseNode(shard, Set.of())).contains(OWNER);
        }
    }

    @Test
    void testShardWithNoVolume_treatedAsLocal() {
        final IndexShard shard = IndexShard.builder()
                .id(1L)
                .nodeName(OWNER)
                .status(IndexShardStatus.CLOSED)
                .build();

        assertThat(ShardNodeAssigner.chooseNode(shard, OWNER_DOWN)).contains(OWNER);
    }

    @Test
    void testSharedVolume_closedShardsSpreadOverRunningNodes() {
        final Map<String, Integer> counts = new HashMap<>();
        for (long id = 1; id <= 3000; id++) {
            final String node = ShardNodeAssigner.chooseNode(shard(id, VolumeType.SHARED, IndexShardStatus.CLOSED),
                    ALL_NODES).orElseThrow();
            counts.merge(node, 1, Integer::sum);
        }

        // Not tied to the owner, and roughly a third each.
        assertThat(counts).containsOnlyKeys(ALL_NODES);
        assertThat(counts.values()).allSatisfy(count -> assertThat(count).isBetween(850, 1150));
    }

    @Test
    void testSharedVolume_closedShardNeverGoesToANodeThatIsDown() {
        for (long id = 1; id <= 500; id++) {
            assertThat(ShardNodeAssigner.chooseNode(shard(id, VolumeType.SHARED, IndexShardStatus.CLOSED),
                    OWNER_DOWN).orElseThrow())
                    .isIn(OWNER_DOWN);
        }
    }

    @Test
    void testSharedVolume_sameNodeEachTimeWhateverTheOrder() {
        final List<String> nodes = new ArrayList<>(List.of("node1", "node2", "node3", "node4", "node5"));
        final Random random = new Random(1);
        for (long id = 1; id <= 200; id++) {
            final String expected = ShardNodeAssigner.highestScoringNode(id, nodes);
            Collections.shuffle(nodes, random);
            assertThat(ShardNodeAssigner.highestScoringNode(id, nodes)).isEqualTo(expected);
        }
    }

    @Test
    void testSharedVolume_onlyTheLostNodesShardsMoveWhenANodeLeaves() {
        final Set<String> before = Set.of("node1", "node2", "node3", "node4");
        final Set<String> after = Set.of("node1", "node2", "node4");
        for (long id = 1; id <= 1000; id++) {
            final String nodeBefore = ShardNodeAssigner.highestScoringNode(id, before);
            final String nodeAfter = ShardNodeAssigner.highestScoringNode(id, after);
            if (!"node3".equals(nodeBefore)) {
                // Keeps each remaining node's file cache warm.
                assertThat(nodeAfter).isEqualTo(nodeBefore);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = IndexShardStatus.class, names = {"NEW", "OPEN", "OPENING", "CLOSING"})
    void testSharedVolume_openShardGoesToItsOwnerWhileRunning(final IndexShardStatus status) {
        // Only the writer sees documents that are not yet committed.
        for (long id = 1; id <= 50; id++) {
            assertThat(ShardNodeAssigner.chooseNode(shard(id, VolumeType.SHARED, status), ALL_NODES))
                    .contains(OWNER);
        }
    }

    @ParameterizedTest
    @EnumSource(value = IndexShardStatus.class, names = {"OPEN", "OPENING", "CLOSING"})
    void testSharedVolume_openShardGoesToAnotherNodeWhenOwnerIsDown(final IndexShardStatus status) {
        for (long id = 1; id <= 50; id++) {
            assertThat(ShardNodeAssigner.chooseNode(shard(id, VolumeType.SHARED, status), OWNER_DOWN).orElseThrow())
                    .isIn(OWNER_DOWN);
        }
    }

    @ParameterizedTest
    @EnumSource(value = IndexShardStatus.class, names = {"OPEN", "OPENING", "CLOSING"})
    void testSharedVolume_shardWithNothingCommittedIsNotSearchedWhenOwnerIsDown(final IndexShardStatus status) {
        // Everything in it was in its writer's memory, and another node would find no index to open.
        final IndexShard uncommitted = uncommittedShard(1, status);

        assertThat(ShardNodeAssigner.chooseNode(uncommitted, OWNER_DOWN)).isEmpty();
        // While its owner is running the owner searches it through its writer.
        assertThat(ShardNodeAssigner.chooseNode(uncommitted, ALL_NODES)).contains(OWNER);
    }

    @Test
    void testSharedVolume_noRunningNodesKnown_staysWithOwner() {
        assertThat(ShardNodeAssigner.chooseNode(shard(1, VolumeType.SHARED, IndexShardStatus.CLOSED), Set.of()))
                .contains(OWNER);
    }

    @Test
    void testReassign_sharedShardGoesToARemainingNode() {
        for (final IndexShardStatus status : List.of(IndexShardStatus.CLOSED, IndexShardStatus.OPEN)) {
            for (long id = 1; id <= 50; id++) {
                final Reassignment reassignment = ShardNodeAssigner.reassign(
                        shard(id, VolumeType.SHARED, status), OWNER_DOWN);
                assertThat(reassignment.nodeName()).isIn(OWNER_DOWN);
                assertThat(reassignment.notReadable()).isFalse();
            }
        }
    }

    @Test
    void testReassign_localShardCannotBeReadElsewhere() {
        final Reassignment reassignment = ShardNodeAssigner.reassign(
                shard(1, VolumeType.LOCAL, IndexShardStatus.CLOSED), OWNER_DOWN);

        assertThat(reassignment.nodeName()).isNull();
        assertThat(reassignment.notReadable()).isTrue();
    }

    @Test
    void testReassign_noNodesLeft() {
        final Reassignment reassignment = ShardNodeAssigner.reassign(
                shard(1, VolumeType.SHARED, IndexShardStatus.CLOSED), Set.of());

        assertThat(reassignment.nodeName()).isNull();
        assertThat(reassignment.notReadable()).isTrue();
    }

    @Test
    void testReassign_sharedShardWithNothingCommittedHoldsNothingToRead() {
        final Reassignment reassignment = ShardNodeAssigner.reassign(
                uncommittedShard(1, IndexShardStatus.OPEN), OWNER_DOWN);

        assertThat(reassignment.nodeName()).isNull();
        assertThat(reassignment.notReadable()).isFalse();
    }

    @Test
    void testReassign_corruptShardIsNeverGivenToAnotherNode() {
        // It may have been marked corrupt after the search began. The first assignment reports corrupt
        // shards and leaves them out, and so must this.
        final Reassignment reassignment = ShardNodeAssigner.reassign(
                shard(1, VolumeType.SHARED, IndexShardStatus.CORRUPT), OWNER_DOWN);

        assertThat(reassignment.nodeName()).isNull();
        assertThat(reassignment.notReadable()).isTrue();
    }

    @Test
    void testHighestScoringNode_needsANode() {
        assertThatThrownBy(() -> ShardNodeAssigner.highestScoringNode(1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // A shard on a shared volume whose writer has never committed.
    private IndexShard uncommittedShard(final long id, final IndexShardStatus status) {
        return shard(id, VolumeType.SHARED, status)
                .copy()
                .commitMs(null)
                .documentCount(0)
                .build();
    }

    private IndexShard shard(final long id, final VolumeType volumeType, final IndexShardStatus status) {
        return IndexShard.builder()
                .id(id)
                .nodeName(OWNER)
                .status(status)
                .commitMs(1_000L)
                .documentCount(10)
                .volume(IndexVolume.builder()
                        .path("/volume")
                        .volumeType(volumeType)
                        .build())
                .build();
    }
}
