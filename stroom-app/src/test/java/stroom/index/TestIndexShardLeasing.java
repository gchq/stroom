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

package stroom.index;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.impl.IndexDocument;
import stroom.index.impl.IndexShardCreator;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardLeases;
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardUtil;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexStore;
import stroom.index.impl.IndexVolumeService;
import stroom.index.impl.Indexer;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.node.api.NodeInfo;
import stroom.query.language.functions.ValString;
import stroom.search.extraction.FieldValue;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests that writing to an index shard on a shared volume takes and gives up the shard's lease, against a
/// real database and real index files.
class TestIndexShardLeasing extends AbstractCoreIntegrationTest {

    private static final long LEASE_MS = 60_000;
    private static final String OTHER_NODE = "otherNode";
    private static final String OTHER_PROCESS = "other-process";

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private IndexShardDao indexShardDao;
    @Inject
    private IndexShardCreator indexShardCreator;
    @Inject
    private IndexShardWriterCache indexShardWriterCache;
    @Inject
    private IndexShardLeases indexShardLeases;
    @Inject
    private IndexVolumeService indexVolumeService;
    @Inject
    private Indexer indexer;
    @Inject
    private IndexStore indexStore;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private PathCreator pathCreator;
    @Inject
    private IndexShardManager indexShardManager;

    @BeforeEach
    void onBefore() {
        indexShardWriterCache.shutdown();
    }

    @Test
    void testSharedVolume_writingHoldsTheLeaseAndClosingGivesItUp() {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("LEASE_HELD");
        final IndexShard indexShard = indexShardCreator.createIndexShard(indexShardKey, nodeInfo.getThisNodeName());
        final long shardId = indexShard.getId();

        final IndexShardWriter writer = indexShardWriterCache.getOrOpenWriter(shardId);

        // While this node writes to the shard no other process can have it.
        assertThat(getStatus(shardId)).isEqualTo(IndexShardStatus.OPEN);
        assertThat(indexShardLeases.isHeld(shardId)).isTrue();
        assertThat(indexShardDao.findLeasedShardIds(indexShardLeases.getInstanceId())).containsExactly(shardId);
        assertThat(indexShardDao.claimLease(shardId, OTHER_NODE, OTHER_PROCESS, LEASE_MS)).isFalse();

        writer.addDocument(buildDocument());
        closeAllWriters();

        // The document was committed and the shard is free for any node, as after a clean shutdown.
        assertThat(getStatus(shardId)).isEqualTo(IndexShardStatus.CLOSED);
        assertThat(indexShardDao.fetch(shardId).orElseThrow().getDocumentCount()).isEqualTo(1);
        assertThat(indexShardLeases.isHeld(shardId)).isFalse();
        assertThat(indexShardDao.findLeasedShardIds(indexShardLeases.getInstanceId())).isEmpty();
        assertThat(indexShardDao.claimLease(shardId, OTHER_NODE, OTHER_PROCESS, LEASE_MS)).isTrue();
    }

    @Test
    void testSharedVolume_shardHeldByAnotherProcessCannotBeOpened() {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("LEASE_ELSEWHERE");
        final IndexShard indexShard = indexShardCreator.createIndexShard(indexShardKey, nodeInfo.getThisNodeName());
        final long shardId = indexShard.getId();
        assertThat(indexShardDao.claimLease(shardId, OTHER_NODE, OTHER_PROCESS, LEASE_MS)).isTrue();

        assertThatThrownBy(() -> indexShardWriterCache.getOrOpenWriter(shardId))
                .isInstanceOf(IndexException.class)
                .hasMessageContaining("lease");

        // The other process's shard is left exactly as it was, neither reset nor deleted.
        final IndexShard unchanged = indexShardDao.fetch(shardId).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(IndexShardStatus.OPENING);
        assertThat(unchanged.getNodeName()).isEqualTo(OTHER_NODE);
        assertThat(indexShardDao.findLeasedShardIds(OTHER_PROCESS)).containsExactly(shardId);
    }

    @Test
    void testSharedVolume_lostLeaseDiscardsWhatWasNotCommitted() {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("LEASE_LOST");
        final IndexShard indexShard = indexShardCreator.createIndexShard(indexShardKey, nodeInfo.getThisNodeName());
        final long shardId = indexShard.getId();
        final IndexShardWriter writer = indexShardWriterCache.getOrOpenWriter(shardId);
        writer.addDocument(buildDocument());
        writer.flush();
        writer.addDocument(buildDocument());
        writer.addDocument(buildDocument());

        // The lease goes from the database, as it would if this process had been unable to renew it in
        // time, and the next renewal finds out.
        assertThat(indexShardDao.releaseLease(shardId, indexShardLeases.getInstanceId(), null)).isTrue();
        indexShardLeases.renew();
        closeAllWriters();

        // Only the document committed while the lease was held is in the shard.
        assertThat(indexShardLeases.isHeld(shardId)).isFalse();
        assertThat(indexShardDao.fetch(shardId).orElseThrow().getDocumentCount()).isEqualTo(1);
        assertThatThrownBy(() -> writer.addDocument(buildDocument()))
                .isInstanceOf(RuntimeException.class);

        // The shard is intact, and this node can claim it again and carry on.
        final IndexShardWriter nextWriter = indexShardWriterCache.getOrOpenWriter(shardId);
        assertThat(nextWriter.getDocumentCount()).isEqualTo(1);
        nextWriter.addDocument(buildDocument());
        closeAllWriters();
        assertThat(indexShardDao.fetch(shardId).orElseThrow().getDocumentCount()).isEqualTo(2);
    }

    @Test
    void testSharedVolume_closedShardOfAnotherNodeIsUsedBeforeANewOneIsCreated() {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("LEASE_REUSE");
        // A shard written by a node that has since stopped cleanly.
        final IndexShard indexShard = indexShardCreator.createIndexShard(indexShardKey, OTHER_NODE);
        final long shardId = indexShard.getId();
        assertThat(indexShardDao.claimLease(shardId, OTHER_NODE, OTHER_PROCESS, LEASE_MS)).isTrue();
        assertThat(indexShardDao.releaseLease(shardId, OTHER_PROCESS, null)).isTrue();

        indexer.addDocument(indexShardKey, buildDocument());

        // This node took the shard over, and wrote to it, in place of adding one of its own.
        final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
        criteria.getIndexUuidSet().add(indexShardKey.getIndexUuid());
        assertThat(indexShardDao.find(criteria).getValues())
                .extracting(IndexShard::getId)
                .contains(shardId);
        final IndexShard claimed = indexShardDao.fetch(shardId).orElseThrow();
        assertThat(claimed.getNodeName()).isEqualTo(nodeInfo.getThisNodeName());
        assertThat(claimed.getStatus()).isEqualTo(IndexShardStatus.OPEN);
        assertThat(indexShardLeases.isHeld(shardId)).isTrue();

        closeAllWriters();
        assertThat(indexShardDao.fetch(shardId).orElseThrow().getDocumentCount()).isEqualTo(1);
    }

    @Test
    void testSharedVolume_replacedCommitsLeftInAClosedShardArePruned() throws IOException {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("PRUNE");
        final IndexShard indexShard = indexShardCreator.createIndexShard(indexShardKey, nodeInfo.getThisNodeName());
        final long shardId = indexShard.getId();
        final IndexShardWriter writer = indexShardWriterCache.getOrOpenWriter(shardId);
        writer.addDocument(buildDocument());
        writer.flush();
        writer.addDocument(buildDocument());
        closeAllWriters();

        // The writer closed while keeping the commit it had replaced, for readers on other nodes. The
        // shard is marked to have it removed, but not until the retention period has passed.
        assertThat(countCommitPoints(indexShard)).isEqualTo(2);
        indexShardWriterCache.sweep();
        assertThat(countCommitPoints(indexShard)).isEqualTo(2);
        assertThat(indexShardDao.findShardsWithCommitsToPrune(10)).isEmpty();

        // Bring the time forward, as if the retention period had passed with the shard still closed.
        assertThat(indexShardDao.claimLease(shardId, OTHER_NODE, OTHER_PROCESS, LEASE_MS)).isTrue();
        assertThat(indexShardDao.releaseLease(shardId, OTHER_PROCESS, -1_000L)).isTrue();
        assertThat(indexShardDao.findShardsWithCommitsToPrune(10)).extracting(IndexShard::getId).contains(shardId);

        indexShardWriterCache.sweep();

        // The replaced commit has gone, and the shard is closed, unleased and complete.
        assertThat(countCommitPoints(indexShard)).isEqualTo(1);
        assertThat(indexShardDao.findShardsWithCommitsToPrune(10)).isEmpty();
        assertThat(getStatus(shardId)).isEqualTo(IndexShardStatus.CLOSED);
        assertThat(indexShardDao.findLeasedShardIds(indexShardLeases.getInstanceId())).isEmpty();
        assertThat(indexShardWriterCache.getOrOpenWriter(shardId).getDocumentCount()).isEqualTo(2);
        closeAllWriters();
    }

    // Lucene writes one segments_N file for each commit it is keeping.
    private long countCommitPoints(final IndexShard indexShard) throws IOException {
        final Path dir = IndexShardUtil.getIndexPath(indexShard, pathCreator);
        try (final Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(file -> file.getFileName().toString().startsWith("segments_"))
                    .count();
        }
    }

    @Test
    void testSharedVolume_deletedShardKeepsItsFilesWhileAnotherNodeHasItOpen() {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("LEASE_DELETE");
        final IndexShard indexShard = indexShardCreator.createIndexShard(indexShardKey, nodeInfo.getThisNodeName());
        final long shardId = indexShard.getId();
        indexShardWriterCache.getOrOpenWriter(shardId).addDocument(buildDocument());
        closeAllWriters();
        final Path dir = IndexShardUtil.getIndexPath(indexShard, pathCreator);
        assertThat(dir).isDirectory();

        // Another node opens the shard, and then it is deleted.
        assertThat(indexShardDao.claimLease(shardId, OTHER_NODE, OTHER_PROCESS, LEASE_MS)).isTrue();
        indexShardDao.logicalDelete(shardId);

        // This node is the only one running, so deleting the files falls to it, but it must wait.
        indexShardManager.deleteFromDisk();
        ThreadUtil.sleep(2_000);
        assertThat(dir).isDirectory();
        assertThat(indexShardDao.fetch(shardId)).isPresent();

        // The other node closes the shard, which stays deleted, and now its files can go.
        assertThat(indexShardDao.releaseLease(shardId, OTHER_PROCESS, null)).isTrue();
        assertThat(getStatus(shardId)).isEqualTo(IndexShardStatus.DELETED);
        for (int i = 0; i < 100 && indexShardDao.fetch(shardId).isPresent(); i++) {
            indexShardManager.deleteFromDisk();
            ThreadUtil.sleep(200);
        }
        assertThat(indexShardDao.fetch(shardId)).isEmpty();
        assertThat(dir).doesNotExist();
    }

    @Test
    void testLocalVolume_hasNoLease() {
        // Index volumes are local unless marked otherwise, and their shards behave as they always have.
        final DocRef indexRef = commonTestScenarioCreator.createIndex("NO_LEASE");
        final IndexShardKey indexShardKey = IndexShardKey.createKey(indexStore.readDocument(indexRef));
        final IndexShard indexShard = indexShardCreator.createIndexShard(indexShardKey, nodeInfo.getThisNodeName());
        final long shardId = indexShard.getId();
        assertThat(indexShard.getVolume().getVolumeType()).isEqualTo(VolumeType.LOCAL);

        final IndexShardWriter writer = indexShardWriterCache.getOrOpenWriter(shardId);
        writer.addDocument(buildDocument());

        assertThat(getStatus(shardId)).isEqualTo(IndexShardStatus.OPEN);
        assertThat(indexShardLeases.isHeld(shardId)).isFalse();
        assertThat(indexShardDao.findLeasedShardIds(indexShardLeases.getInstanceId())).isEmpty();

        // Renewing leases has no bearing on it, however long it stays open.
        indexShardLeases.renew();
        writer.addDocument(buildDocument());
        closeAllWriters();

        assertThat(getStatus(shardId)).isEqualTo(IndexShardStatus.CLOSED);
        assertThat(indexShardDao.fetch(shardId).orElseThrow().getDocumentCount()).isEqualTo(2);
        // Another node cannot reach its files, so it is not offered to one.
        assertThat(indexShardDao.findUnleasedShardsOnSharedVolumes(
                indexShardKey.getIndexUuid(), indexShardKey.getPartition().getLabel())).isEmpty();
    }

    // Creates an index and marks every index volume as shared, so that its shards are created on one.
    private IndexShardKey createIndexOnSharedVolumes(final String indexName) {
        final DocRef indexRef = commonTestScenarioCreator.createIndex(indexName);
        final LuceneIndexDoc index = indexStore.readDocument(indexRef);
        indexVolumeService.find(new ExpressionCriteria())
                .getValues()
                .stream()
                .filter(volume -> !volume.isShared())
                .forEach(volume -> indexVolumeService.update(volume.copy()
                        .volumeType(VolumeType.SHARED)
                        .build()));
        return IndexShardKey.createKey(index);
    }

    // Returns once every writer has closed and given up its lease.
    private void closeAllWriters() {
        indexShardWriterCache.shutdown();
    }

    private IndexShardStatus getStatus(final long shardId) {
        return indexShardDao.fetch(shardId).orElseThrow().getStatus();
    }

    private IndexDocument buildDocument() {
        final IndexDocument document = new IndexDocument();
        document.add(new FieldValue(LuceneIndexField.createField("test"), ValString.create("test")));
        return document;
    }
}
