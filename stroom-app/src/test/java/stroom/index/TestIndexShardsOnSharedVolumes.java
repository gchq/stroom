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
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardUtil;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexStore;
import stroom.index.impl.IndexVolumeService;
import stroom.index.impl.Indexer;
import stroom.index.impl.db.IndexDbConnProvider;
import stroom.index.shared.FindIndexShardCriteria;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests that an index shard on a shared volume is written to and looked after only by the node that created
/// it, against a real database and real index files.
class TestIndexShardsOnSharedVolumes extends AbstractCoreIntegrationTest {

    private static final String OTHER_NODE = "otherNode";

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private IndexShardDao indexShardDao;
    @Inject
    private IndexShardCreator indexShardCreator;
    @Inject
    private IndexShardWriterCache indexShardWriterCache;
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
    @Inject
    private IndexDbConnProvider indexDbConnProvider;

    @BeforeEach
    void onBefore() {
        closeAllWriters();
    }

    @Test
    void testShardOfAnotherNodeIsNotWrittenTo() {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("OWNER_ONLY");
        // A shard with room in it that another node wrote and closed cleanly.
        final long otherShardId = indexShardCreator.createIndexShard(indexShardKey, OTHER_NODE).getId();
        close(otherShardId);

        indexer.addDocument(indexShardKey, buildDocument());
        closeAllWriters();

        final IndexShard otherShard = indexShardDao.fetch(otherShardId).orElseThrow();
        assertThat(otherShard.getNodeName()).isEqualTo(OTHER_NODE);
        assertThat(otherShard.getStatus()).isEqualTo(IndexShardStatus.CLOSED);
        assertThat(otherShard.getDocumentCount()).isZero();

        // The document went to a shard of this node's own.
        final List<IndexShard> ownShards = findShards(indexShardKey)
                .stream()
                .filter(shard -> nodeInfo.getThisNodeName().equals(shard.getNodeName()))
                .toList();
        assertThat(ownShards).isNotEmpty();
        assertThat(ownShards.stream().mapToInt(IndexShard::getDocumentCount).sum()).isOne();
    }

    @Test
    void testOwnClosedShardIsWrittenToAgain() {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("OWNER_AGAIN");
        indexer.addDocument(indexShardKey, buildDocument());
        closeAllWriters();
        final int shardCount = findShards(indexShardKey).size();

        indexer.addDocument(indexShardKey, buildDocument());
        closeAllWriters();

        final List<IndexShard> shards = findShards(indexShardKey);
        assertThat(shards).hasSize(shardCount);
        assertThat(shards.stream().mapToInt(IndexShard::getDocumentCount).sum()).isEqualTo(2);
    }

    @Test
    void testUncommittedDocumentsSurviveTheDatabaseRowChangingMeanwhile() throws SQLException {
        // Nothing about the shard's row, such as a lease that has lapsed, makes a writer discard documents.
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("NO_DISCARD");
        final long shardId = createShard(indexShardKey).getId();
        final IndexShardWriter writer = indexShardWriterCache.getOrOpenWriter(shardId);
        writer.addDocument(buildDocument());
        writer.flush();
        writer.addDocument(buildDocument());

        leaveLeased(shardId);
        writer.addDocument(buildDocument());
        closeAllWriters();

        assertThat(indexShardDao.fetch(shardId).orElseThrow().getDocumentCount()).isEqualTo(3);
        assertThat(indexShardWriterCache.getOrOpenWriter(shardId).getDocumentCount()).isEqualTo(3);
        closeAllWriters();
    }

    @Test
    void testShardThatBeta19LeftLeasedAndMarkedForRecoveryCanBeWrittenTo() throws SQLException {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("BETA_19");
        final long shardId = createShard(indexShardKey).getId();
        indexShardWriterCache.getOrOpenWriter(shardId).addDocument(buildDocument());
        closeAllWriters();
        leaveLeased(shardId);

        indexShardWriterCache.getOrOpenWriter(shardId).addDocument(buildDocument());
        closeAllWriters();

        final IndexShard indexShard = indexShardDao.fetch(shardId).orElseThrow();
        assertThat(indexShard.getStatus()).isEqualTo(IndexShardStatus.CLOSED);
        assertThat(indexShard.getDocumentCount()).isEqualTo(2);
    }

    @Test
    void testReplacedCommitsLeftInAClosedShardArePruned() throws IOException, SQLException {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("PRUNE");
        final IndexShard indexShard = createShard(indexShardKey);
        final long shardId = indexShard.getId();
        leaveReplacedCommit(shardId);

        // The writer closed while keeping the commit it had replaced, for readers on other nodes. The
        // shard is marked to have it removed, but not until the retention period has passed.
        assertThat(countCommitPoints(indexShard)).isEqualTo(2);
        assertThat(getPruneCommitsAfterMs(shardId)).isGreaterThan(System.currentTimeMillis());
        indexShardWriterCache.sweep();
        assertThat(countCommitPoints(indexShard)).isEqualTo(2);
        assertThat(findShardsToPrune(nodeInfo.getThisNodeName())).isEmpty();

        bringPruneTimeForward(shardId);
        assertThat(findShardsToPrune(nodeInfo.getThisNodeName())).containsExactly(shardId);
        indexShardWriterCache.sweep();

        // The replaced commit has gone, and the shard is closed and complete.
        assertThat(countCommitPoints(indexShard)).isOne();
        assertThat(getPruneCommitsAfterMs(shardId)).isZero();
        assertThat(findShardsToPrune(nodeInfo.getThisNodeName())).isEmpty();
        assertThat(indexShardDao.fetch(shardId).orElseThrow().getStatus()).isEqualTo(IndexShardStatus.CLOSED);
        assertThat(indexShardWriterCache.getOrOpenWriter(shardId).getDocumentCount()).isEqualTo(2);
        closeAllWriters();
    }

    @Test
    void testReplacedCommitsAreNotPrunedWhileTheShardIsOpen() throws IOException {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("PRUNE_OPEN");
        final IndexShard indexShard = createShard(indexShardKey);
        final long shardId = indexShard.getId();
        leaveReplacedCommit(shardId);
        bringPruneTimeForward(shardId);
        indexShardWriterCache.getOrOpenWriter(shardId);

        indexShardWriterCache.sweep();

        assertThat(countCommitPoints(indexShard)).isEqualTo(2);
        assertThat(indexShardDao.fetch(shardId).orElseThrow().getStatus()).isEqualTo(IndexShardStatus.OPEN);
        closeAllWriters();
    }

    @Test
    void testShardOfAnotherNodeIsNotDeletedFromDisk() throws IOException {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("OWNER_DELETES");
        final IndexShard otherShard = indexShardCreator.createIndexShard(indexShardKey, OTHER_NODE);
        final IndexShard ownShard = createShard(indexShardKey);
        final Path otherDir = IndexShardUtil.getIndexPath(otherShard, pathCreator);
        final Path ownDir = IndexShardUtil.getIndexPath(ownShard, pathCreator);
        Files.createDirectories(otherDir);
        Files.createDirectories(ownDir);
        indexShardDao.logicalDelete(otherShard.getId());
        indexShardDao.logicalDelete(ownShard.getId());

        for (int i = 0; i < 100 && indexShardDao.fetch(ownShard.getId()).isPresent(); i++) {
            indexShardManager.deleteFromDisk();
            ThreadUtil.sleep(200);
        }

        assertThat(indexShardDao.fetch(ownShard.getId())).isEmpty();
        assertThat(ownDir).doesNotExist();
        // Only the other node knows whether it still has the shard open.
        assertThat(indexShardDao.fetch(otherShard.getId())).isPresent();
        assertThat(otherDir).isDirectory();
    }

    private void leaveReplacedCommit(final long shardId) {
        final IndexShardWriter writer = indexShardWriterCache.getOrOpenWriter(shardId);
        writer.addDocument(buildDocument());
        writer.flush();
        writer.addDocument(buildDocument());
        closeAllWriters();
    }

    // As if the retention period had passed with the shard still closed.
    private void bringPruneTimeForward(final long shardId) {
        assertThat(indexShardDao.setStatus(shardId, IndexShardStatus.OPENING)).isTrue();
        indexShardDao.reset(shardId, -1_000L);
    }

    private void close(final long shardId) {
        assertThat(indexShardDao.setStatus(shardId, IndexShardStatus.OPENING)).isTrue();
        indexShardDao.reset(shardId);
    }

    // The columns that v7.14-beta.19 set, with a lease that has long since lapsed.
    private void leaveLeased(final long shardId) throws SQLException {
        try (final Connection connection = indexDbConnProvider.getConnection();
                final PreparedStatement statement = connection.prepareStatement(
                        "UPDATE index_shard SET lease_instance_id = 'gone', lease_expiry_ms = 1, "
                        + "recover_pending_streams = 1 WHERE id = ?")) {
            statement.setLong(1, shardId);
            assertThat(statement.executeUpdate()).isOne();
        }
    }

    // Zero if there is nothing to prune.
    private long getPruneCommitsAfterMs(final long shardId) throws SQLException {
        try (final Connection connection = indexDbConnProvider.getConnection();
                final PreparedStatement statement = connection.prepareStatement(
                        "SELECT prune_commits_after_ms FROM index_shard WHERE id = ?")) {
            statement.setLong(1, shardId);
            try (final ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private List<Long> findShardsToPrune(final String nodeName) {
        return indexShardDao.findShardsWithCommitsToPrune(nodeName, 10)
                .stream()
                .map(IndexShard::getId)
                .toList();
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

    private List<IndexShard> findShards(final IndexShardKey indexShardKey) {
        final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
        criteria.getIndexUuidSet().add(indexShardKey.getIndexUuid());
        return indexShardDao.find(criteria).getValues();
    }

    private IndexShard createShard(final IndexShardKey indexShardKey) {
        return indexShardCreator.createIndexShard(indexShardKey, nodeInfo.getThisNodeName());
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

    // Returns once every writer has closed.
    private void closeAllWriters() {
        indexShardWriterCache.shutdown();
    }

    private IndexDocument buildDocument() {
        final IndexDocument document = new IndexDocument();
        document.add(new FieldValue(LuceneIndexField.createField("test"), ValString.create("test")));
        return document;
    }
}
