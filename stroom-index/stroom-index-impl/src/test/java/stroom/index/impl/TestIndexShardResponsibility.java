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

package stroom.index.impl;

import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.node.api.NodeInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexShardResponsibility {

    private static final Set<String> ALL_NODES = Set.of("node1", "node2", "node3");

    @ParameterizedTest
    @EnumSource(IndexShardStatus.class)
    void testLocalVolume_onlyItsOwnNode(final IndexShardStatus status) {
        final IndexShard shard = shard(1, "node2", VolumeType.LOCAL, status);

        assertThat(responsibleNodes(shard, ALL_NODES)).containsExactly("node2");
        // No other node can reach its files, so nothing changes when its node is down.
        assertThat(responsibleNodes(shard, Set.of("node1", "node3"))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = IndexShardStatus.class, names = {"CLOSED", "DELETED", "CORRUPT", "NEW"})
    void testSharedVolume_exactlyOneRunningNodeWhoeverWroteIt(final IndexShardStatus status) {
        for (long id = 1; id <= 200; id++) {
            // Written by a node that has gone for good.
            final IndexShard shard = shard(id, "node9", VolumeType.SHARED, status);

            assertThat(responsibleNodes(shard, ALL_NODES)).hasSize(1);
        }
    }

    @Test
    void testSharedVolume_spreadOverTheRunningNodes() {
        int node1Count = 0;
        for (long id = 1; id <= 3000; id++) {
            if (IndexShardResponsibility.isResponsible(
                    shard(id, "node9", VolumeType.SHARED, IndexShardStatus.CLOSED), "node1", ALL_NODES)) {
                node1Count++;
            }
        }

        assertThat(node1Count).isBetween(850, 1150);
    }

    @ParameterizedTest
    @EnumSource(value = IndexShardStatus.class, names = {"OPENING", "OPEN", "CLOSING"})
    void testSharedVolume_openShardIsLookedAfterByItsLeaseHolder(final IndexShardStatus status) {
        for (long id = 1; id <= 50; id++) {
            final IndexShard shard = shard(id, "node2", VolumeType.SHARED, status);

            // Its writer is there.
            assertThat(responsibleNodes(shard, ALL_NODES)).containsExactly("node2");
            // Unless the holder has gone, when one of the others takes it on.
            assertThat(responsibleNodes(shard, Set.of("node1", "node3"))).hasSize(1).doesNotContain("node2");
        }
    }

    @Test
    void testSharedVolume_runningNodesNotKnown_staysWithItsNode() {
        final IndexShard shard = shard(1, "node2", VolumeType.SHARED, IndexShardStatus.CLOSED);

        assertThat(IndexShardResponsibility.isResponsible(shard, "node2", Set.of())).isTrue();
        assertThat(IndexShardResponsibility.isResponsible(shard, "node1", Set.of())).isFalse();
    }

    @Test
    void testCreateTest_usesThisNodeAndTheRunningNodes() throws Exception {
        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node1");
        final TargetNodeSetFactory targetNodeSetFactory = Mockito.mock(TargetNodeSetFactory.class);
        // This node is the only one running, so everything on shared volumes falls to it.
        Mockito.when(targetNodeSetFactory.getEnabledActiveTargetNodeSet()).thenReturn(Set.of("node1"));

        final Predicate<IndexShard> test = new IndexShardResponsibility(nodeInfo, targetNodeSetFactory).createTest();

        assertThat(test.test(shard(1, "node9", VolumeType.SHARED, IndexShardStatus.CLOSED))).isTrue();
        assertThat(test.test(shard(2, "node9", VolumeType.LOCAL, IndexShardStatus.CLOSED))).isFalse();
        assertThat(test.test(shard(3, "node1", VolumeType.LOCAL, IndexShardStatus.CLOSED))).isTrue();
    }

    @Test
    void testCreateTest_clusterStateNotKnown() throws Exception {
        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node1");
        final TargetNodeSetFactory targetNodeSetFactory = Mockito.mock(TargetNodeSetFactory.class);
        Mockito.when(targetNodeSetFactory.getEnabledActiveTargetNodeSet()).thenThrow(new NullClusterStateException());

        final Predicate<IndexShard> test = new IndexShardResponsibility(nodeInfo, targetNodeSetFactory).createTest();

        assertThat(test.test(shard(1, "node1", VolumeType.SHARED, IndexShardStatus.CLOSED))).isTrue();
        assertThat(test.test(shard(2, "node2", VolumeType.SHARED, IndexShardStatus.CLOSED))).isFalse();
    }

    @Test
    void testShardWithNoVolume_treatedAsLocal() {
        final IndexShard shard = IndexShard.builder()
                .id(1L)
                .nodeName("node2")
                .status(IndexShardStatus.CLOSED)
                .build();

        assertThat(responsibleNodes(shard, ALL_NODES)).containsExactly("node2");
    }

    private List<String> responsibleNodes(final IndexShard shard, final Set<String> runningNodes) {
        return runningNodes.stream()
                .filter(node -> IndexShardResponsibility.isResponsible(shard, node, runningNodes))
                .toList();
    }

    private IndexShard shard(final long id,
                             final String nodeName,
                             final VolumeType volumeType,
                             final IndexShardStatus status) {
        return IndexShard.builder()
                .id(id)
                .nodeName(nodeName)
                .status(status)
                .volume(IndexVolume.builder().path("/volume").volumeType(volumeType).build())
                .build();
    }
}
