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

import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.docref.DocRef;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexStore;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.ResultStore;
import stroom.task.api.SimpleTaskContext;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class TestNodeSearchTaskCreator {

    private static final DateTimeSettings SETTINGS = DateTimeSettings.builder().build();
    private static final String MID_MARCH = "2024-03-15T10:00:00.000Z";
    private static final String END_MARCH = "2024-03-31T23:00:00.000Z";

    @Test
    void testPartitionTimeRange_isTheQueryTimesNotRoundedToPartitions() {
        final Range<Long> range = NodeSearchTaskCreator.partitionTimeRange(
                new TimeRange("March", MID_MARCH, END_MARCH), SETTINGS);

        assertThat(range.getFrom()).isEqualTo(Instant.parse(MID_MARCH).toEpochMilli());
        assertThat(range.getTo()).isEqualTo(Instant.parse(END_MARCH).toEpochMilli());
    }

    @Test
    void testPartitionTimeRange_fromOnly() {
        final Range<Long> range = NodeSearchTaskCreator.partitionTimeRange(
                new TimeRange("From", MID_MARCH, null), SETTINGS);

        assertThat(range.getFrom()).isEqualTo(Instant.parse(MID_MARCH).toEpochMilli());
        assertThat(range.getTo()).isNull();
    }

    @Test
    void testPartitionTimeRange_toOnly() {
        final Range<Long> range = NodeSearchTaskCreator.partitionTimeRange(
                new TimeRange("To", " ", END_MARCH), SETTINGS);

        assertThat(range.getFrom()).isNull();
        assertThat(range.getTo()).isEqualTo(Instant.parse(END_MARCH).toEpochMilli());
    }

    @Test
    void testPartitionTimeRange_noTimeRange() {
        final Range<Long> range = NodeSearchTaskCreator.partitionTimeRange(null, SETTINGS);

        assertThat(range.getFrom()).isNull();
        assertThat(range.getTo()).isNull();
    }

    @Test
    void testCreateNodeSearchTasks_localShardsGoToTheirOwners() throws Exception {
        final Fixture fixture = new Fixture(Set.of("node1", "node2", "node3"));
        fixture.addShard(1, "node1", VolumeType.LOCAL, IndexShardStatus.CLOSED);
        fixture.addShard(2, "node1", VolumeType.LOCAL, IndexShardStatus.OPEN);
        fixture.addShard(3, "node2", VolumeType.LOCAL, IndexShardStatus.CLOSED);
        // The owner of a local shard is still asked for it when it is down, which reports the gap.
        fixture.addShard(4, "node9", VolumeType.LOCAL, IndexShardStatus.CLOSED);

        final Map<String, NodeSearchTask> tasks = fixture.createTasks();

        assertThat(tasks).containsOnlyKeys("node1", "node2", "node9");
        assertThat(tasks.get("node1").getShards()).containsExactly(1L, 2L);
        assertThat(tasks.get("node2").getShards()).containsExactly(3L);
        assertThat(tasks.get("node9").getShards()).containsExactly(4L);
    }

    @Test
    void testCreateNodeSearchTasks_sharedShardsDoNotNeedTheirOwner() throws Exception {
        // Every shard was written by a node that is no longer running.
        final Fixture fixture = new Fixture(Set.of("node1", "node2"));
        for (long id = 1; id <= 100; id++) {
            fixture.addShard(id, "node9", VolumeType.SHARED, IndexShardStatus.CLOSED);
        }
        // Left open by the node when it stopped, so readable up to its last commit.
        fixture.addShard(101, "node9", VolumeType.SHARED, IndexShardStatus.OPEN);
        // Nothing was committed to this one, so there is nothing for another node to read.
        fixture.addUncommittedShard(102, "node9");

        final Map<String, NodeSearchTask> tasks = fixture.createTasks();

        assertThat(tasks).containsOnlyKeys("node1", "node2");
        final List<Long> allShards = new ArrayList<>(tasks.get("node1").getShards());
        allShards.addAll(tasks.get("node2").getShards());
        assertThat(allShards).hasSize(101).doesNotHaveDuplicates().contains(101L).doesNotContain(102L);
        Mockito.verify(fixture.resultStore, Mockito.never()).onFailure(any(), any());
    }

    @Test
    void testCreateNodeSearchTasks_openSharedShardGoesToItsRunningOwner() throws Exception {
        final Fixture fixture = new Fixture(Set.of("node1", "node2", "node3"));
        for (long id = 1; id <= 30; id++) {
            fixture.addShard(id, "node2", VolumeType.SHARED, IndexShardStatus.OPEN);
        }

        final Map<String, NodeSearchTask> tasks = fixture.createTasks();

        assertThat(tasks).containsOnlyKeys("node2");
        assertThat(tasks.get("node2").getShards()).hasSize(30);
    }

    @Test
    void testCreateNodeSearchTasks_eachTaskHasItsOwnDispatchId() throws Exception {
        final Fixture fixture = new Fixture(Set.of("node1", "node2"));
        fixture.addShard(1, "node1", VolumeType.LOCAL, IndexShardStatus.CLOSED);
        fixture.addShard(2, "node2", VolumeType.LOCAL, IndexShardStatus.CLOSED);

        final Map<String, NodeSearchTask> tasks = fixture.createTasks();

        assertThat(tasks.get("node1").getDispatchId()).isNotBlank();
        assertThat(tasks.get("node2").getDispatchId()).isNotBlank().isNotEqualTo(tasks.get("node1").getDispatchId());
    }

    @Test
    void testCreateNodeSearchTasks_corruptShardIsReportedNotSearched() throws Exception {
        final Fixture fixture = new Fixture(Set.of("node1"));
        fixture.addShard(1, "node1", VolumeType.SHARED, IndexShardStatus.CORRUPT);

        assertThat(fixture.createTasks()).isEmpty();
        Mockito.verify(fixture.resultStore).onFailure(Mockito.eq("node1"), any(SearchException.class));
    }

    @Test
    void testCreateNodeSearchTasks_clusterStateUnknown_everyShardStaysWithItsOwner() throws Exception {
        final Fixture fixture = new Fixture(Set.of());
        Mockito.when(fixture.targetNodeSetFactory.getEnabledActiveTargetNodeSet())
                .thenThrow(new NullClusterStateException());
        fixture.addShard(1, "node1", VolumeType.SHARED, IndexShardStatus.CLOSED);
        fixture.addShard(2, "node2", VolumeType.SHARED, IndexShardStatus.CLOSED);

        final Map<String, NodeSearchTask> tasks = fixture.createTasks();

        assertThat(tasks.get("node1").getShards()).containsExactly(1L);
        assertThat(tasks.get("node2").getShards()).containsExactly(2L);
    }

    @Test
    void testReassign_sharedShardsGoToTheRemainingNodes() throws Exception {
        final Fixture fixture = new Fixture(Set.of("node1", "node2", "node3"));
        for (long id = 1; id <= 60; id++) {
            fixture.addShard(id, "node2", VolumeType.SHARED, id % 2 == 0
                    ? IndexShardStatus.CLOSED
                    : IndexShardStatus.OPEN);
        }
        final NodeSearchTask failedTask = fixture.task(fixture.shardIds(), "first");

        final NodeSearchReassignment reassignment = fixture.creator.reassign(failedTask, Set.of("node2"));

        assertThat(reassignment.workLost()).isFalse();
        assertThat(reassignment.tasks()).containsOnlyKeys("node1", "node3");
        final List<Long> allShards = new ArrayList<>();
        final Set<String> dispatchIds = new HashSet<>();
        reassignment.tasks().values().forEach(nodeSearchTask -> {
            allShards.addAll(nodeSearchTask.getShards());
            dispatchIds.add(nodeSearchTask.getDispatchId());
            // Still the same search.
            assertThat(nodeSearchTask.getKey()).isEqualTo(failedTask.getKey());
        });
        assertThat(allShards).containsExactlyInAnyOrderElementsOf(fixture.shardIds());
        // A node that is already searching for this search must be able to tell the new task from its first.
        assertThat(dispatchIds).hasSize(2).doesNotContain("first");

        // Only the shards of the failed task are looked up.
        final ArgumentCaptor<FindIndexShardCriteria> criteriaCaptor =
                ArgumentCaptor.forClass(FindIndexShardCriteria.class);
        Mockito.verify(fixture.indexShardDao).find(criteriaCaptor.capture());
        assertThat(criteriaCaptor.getValue().getIndexShardIdSet().getSet())
                .containsExactlyInAnyOrderElementsOf(fixture.shardIds());
    }

    @Test
    void testReassign_localShardsAreLost() throws Exception {
        final Fixture fixture = new Fixture(Set.of("node1", "node2", "node3"));
        fixture.addShard(1, "node2", VolumeType.LOCAL, IndexShardStatus.CLOSED);
        fixture.addShard(2, "node2", VolumeType.SHARED, IndexShardStatus.CLOSED);

        final NodeSearchReassignment reassignment = fixture.creator.reassign(
                fixture.task(fixture.shardIds(), "first"), Set.of("node2"));

        assertThat(reassignment.workLost()).isTrue();
        assertThat(reassignment.tasks().values())
                .singleElement()
                .satisfies(nodeSearchTask -> assertThat(nodeSearchTask.getShards()).containsExactly(2L));
    }

    @Test
    void testReassign_noNodesLeft() throws Exception {
        final Fixture fixture = new Fixture(Set.of("node1", "node2"));
        fixture.addShard(1, "node2", VolumeType.SHARED, IndexShardStatus.CLOSED);

        final NodeSearchReassignment reassignment = fixture.creator.reassign(
                fixture.task(fixture.shardIds(), "first"), Set.of("node1", "node2"));

        assertThat(reassignment.workLost()).isTrue();
        assertThat(reassignment.tasks()).isEmpty();
    }

    @Test
    void testReassign_deletedAndUncommittedShardsAreNotLostWork() throws Exception {
        final Fixture fixture = new Fixture(Set.of("node1", "node2"));
        // Deleted since the search began, and never committed to, so neither holds anything to search.
        fixture.addShard(1, "node2", VolumeType.SHARED, IndexShardStatus.DELETED);
        fixture.addUncommittedShard(2, "node2");

        final NodeSearchReassignment reassignment = fixture.creator.reassign(
                fixture.task(fixture.shardIds(), "first"), Set.of("node2"));

        assertThat(reassignment.workLost()).isFalse();
        assertThat(reassignment.tasks()).isEmpty();
    }

    @Test
    void testReassign_shardMarkedCorruptSinceTheSearchBeganIsLostWork() throws Exception {
        final Fixture fixture = new Fixture(Set.of("node1", "node2"));
        fixture.addShard(1, "node2", VolumeType.SHARED, IndexShardStatus.CORRUPT);

        final NodeSearchReassignment reassignment = fixture.creator.reassign(
                fixture.task(fixture.shardIds(), "first"), Set.of("node2"));

        assertThat(reassignment.workLost()).isTrue();
        assertThat(reassignment.tasks()).isEmpty();
    }

    @Test
    void testReassign_taskWithNoShards() throws Exception {
        final Fixture fixture = new Fixture(Set.of("node1", "node2"));

        final NodeSearchReassignment reassignment = fixture.creator.reassign(
                fixture.task(List.of(), "first"), Set.of("node2"));

        assertThat(reassignment.workLost()).isFalse();
        assertThat(reassignment.tasks()).isEmpty();
        Mockito.verify(fixture.indexShardDao, Mockito.never()).find(any());
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static final class Fixture {

        private static final DocRef INDEX = new DocRef(LuceneIndexDoc.TYPE, "index-uuid");

        private final List<IndexShard> shards = new ArrayList<>();
        private final IndexShardDao indexShardDao = Mockito.mock(IndexShardDao.class);
        private final TargetNodeSetFactory targetNodeSetFactory = Mockito.mock(TargetNodeSetFactory.class);
        private final ResultStore resultStore = Mockito.mock(ResultStore.class);
        private final NodeSearchTaskCreator creator;

        private Fixture(final Set<String> runningNodes) throws NullClusterStateException, NodeNotFoundException {
            final IndexStore indexStore = Mockito.mock(IndexStore.class);
            Mockito.when(indexStore.readDocument(INDEX))
                    .thenReturn(LuceneIndexDoc.builder().uuid("index-uuid").build());
            Mockito.when(indexShardDao.find(any())).thenAnswer(invocation -> ResultPage.createUnboundedList(shards));
            Mockito.when(targetNodeSetFactory.getEnabledActiveTargetNodeSet()).thenReturn(runningNodes);
            creator = new NodeSearchTaskCreator(indexStore, indexShardDao, targetNodeSetFactory);
        }

        private void addShard(final long id,
                              final String ownerNode,
                              final VolumeType volumeType,
                              final IndexShardStatus status) {
            shards.add(IndexShard.builder()
                    .id(id)
                    .nodeName(ownerNode)
                    .status(status)
                    .commitMs(1_000L)
                    .documentCount(10)
                    .volume(IndexVolume.builder().path("/volume").volumeType(volumeType).build())
                    .build());
        }

        // An open shard on a shared volume whose writer has never committed.
        private void addUncommittedShard(final long id, final String ownerNode) {
            shards.add(IndexShard.builder()
                    .id(id)
                    .nodeName(ownerNode)
                    .status(IndexShardStatus.OPEN)
                    .volume(IndexVolume.builder().path("/volume").volumeType(VolumeType.SHARED).build())
                    .build());
        }

        private List<Long> shardIds() {
            return shards.stream().map(IndexShard::getId).toList();
        }

        private Map<String, NodeSearchTask> createTasks() {
            final Query query = Query.builder()
                    .dataSource(INDEX)
                    .expression(ExpressionOperator.builder().build())
                    .build();
            final FederatedSearchTask task = new FederatedSearchTask(
                    null, new QueryKey("search"), "Test search", query, List.of(), SETTINGS);
            task.setResultStore(resultStore);
            return creator.createNodeSearchTasks(task, query, new SimpleTaskContext());
        }

        private NodeSearchTask task(final List<Long> shardIds, final String dispatchId) {
            return new NodeSearchTask(
                    NodeSearchTaskType.LUCENE,
                    null,
                    "Cluster Search",
                    null,
                    new QueryKey("search"),
                    null,
                    List.of(),
                    SETTINGS,
                    shardIds)
                    .withShards(shardIds, dispatchId);
        }
    }
}
