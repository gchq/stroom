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

package stroom.index;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.impl.IndexDocument;
import stroom.index.impl.IndexShardCreator;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardManager.IndexShardAction;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexStore;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.Indexer;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.node.api.NodeInfo;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValString;
import stroom.search.extraction.FieldValue;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.io.FileUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestIndexShardWriterImpl extends AbstractCoreIntegrationTest {

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private IndexShardDao indexShardDao;
    @Inject
    private IndexShardCreator indexShardCreator;
    @Inject
    private IndexShardManager indexShardManager;
    @Inject
    private IndexShardWriterCache indexShardWriterCache;
    @Inject
    private Indexer indexer;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private IndexStore indexStore;
    @Inject
    private IndexVolumeDao indexVolumeDao;
    @Inject
    private NodeInfo nodeInfo;

    @BeforeEach
    void onBefore() {
        indexShardWriterCache.shutdown();
    }

    @Test
    void testSingle() {
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isZero();

        // Do some work.
        final IndexDocument document = new IndexDocument();
        document.add(new FieldValue(LuceneIndexField.createField("test"), ValString.create("test")));

        // Create an index
        final DocRef indexRef1 = commonTestScenarioCreator.createIndex("TEST_2010a");
        final LuceneIndexDoc index1 = indexStore.readDocument(indexRef1);
        final IndexShardKey indexShardKey1 = IndexShardKey.createKey(index1);
        final IndexShard indexShard = indexShardCreator.createIndexShard(indexShardKey1, nodeInfo.getThisNodeName());

        // Create a writer in the pool
        final IndexShardWriter writer1 = indexShardWriterCache.getOrOpenWriter(indexShard.getId());

        // Assert that there is 1 writer in the pool.
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isEqualTo(1);

        final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
        checkDocCount(0, writer1);
        checkDocCount(0, writer1.getIndexShardId());
        writer1.addDocument(document);
        checkDocCount(1, writer1);
        checkDocCount(0, writer1.getIndexShardId());
        indexShardManager.performAction(criteria, IndexShardAction.FLUSH);
        checkDocCount(1, writer1);
        checkDocCount(1, writer1.getIndexShardId());

        // Close writer1 by removing the writer from the cache.
        indexShardWriterCache.close(writer1);
        // Close indexes again.
        indexShardWriterCache.shutdown();
        // Make sure that writer1 was closed.
        assertThat(compareStatus(IndexShardStatus.OPEN, writer1.getIndexShardId())).isFalse();

        // Make sure that adding to writer1 reopens the index.
        indexer.addDocument(indexShardKey1, document);
        assertThat(compareStatus(IndexShardStatus.OPEN, writer1.getIndexShardId())).isTrue();

        // Close indexes again.
        indexShardWriterCache.shutdown();

        // Make sure that writer1 was closed.
        assertThat(compareStatus(IndexShardStatus.OPEN, writer1.getIndexShardId())).isFalse();
    }

    @Test
    void testSimple() {
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isZero();

        // Do some work.
        final IndexDocument document = new IndexDocument();
        document.add(new FieldValue(LuceneIndexField.createField("test"), ValString.create("test")));

        final DocRef indexRef1 = commonTestScenarioCreator.createIndex("TEST_2010");
        final LuceneIndexDoc index1 = indexStore.readDocument(indexRef1);
        final IndexShardKey indexShardKey1 = IndexShardKey.createKey(index1);
        final IndexShard indexShard1 = indexShardCreator.createIndexShard(indexShardKey1, nodeInfo.getThisNodeName());

        final DocRef indexRef2 = commonTestScenarioCreator.createIndex("TEST_2011");
        final LuceneIndexDoc index2 = indexStore.readDocument(indexRef2);
        final IndexShardKey indexShardKey2 = IndexShardKey.createKey(index2);
        final IndexShard indexShard2 = indexShardCreator.createIndexShard(indexShardKey2, nodeInfo.getThisNodeName());

        // Create 2 writers in the pool.
        final IndexShardWriter writer1 = indexShardWriterCache.getOrOpenWriter(indexShard1.getId());
        final IndexShardWriter writer2 = indexShardWriterCache.getOrOpenWriter(indexShard2.getId());

        // Assert that there are 2 writers in the pool.
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isEqualTo(2);

        final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();

        checkDocCount(0, writer1);
        checkDocCount(0, writer1.getIndexShardId());
        writer1.addDocument(document);
        checkDocCount(1, writer1);
        checkDocCount(0, writer1.getIndexShardId());
        indexShardManager.performAction(criteria, IndexShardAction.FLUSH);
        checkDocCount(1, writer1);
        checkDocCount(1, writer1.getIndexShardId());

        writer1.addDocument(document);
        checkDocCount(2, writer1);
        checkDocCount(1, writer1.getIndexShardId());
        indexShardManager.performAction(criteria, IndexShardAction.FLUSH);
        checkDocCount(2, writer1);
        checkDocCount(2, writer1.getIndexShardId());

        checkDocCount(0, writer2);
        checkDocCount(0, writer2.getIndexShardId());
        writer2.addDocument(document);
        checkDocCount(1, writer2);
        checkDocCount(0, writer2.getIndexShardId());
        indexShardManager.performAction(criteria, IndexShardAction.FLUSH);
        checkDocCount(1, writer2);
        checkDocCount(1, writer2.getIndexShardId());

        // Close writer1 by removing the writer from the cache.
        indexShardWriterCache.close(writer1);
        // Close indexes again.
        indexShardWriterCache.shutdown();
        // Make sure that writer1 was closed.
        assertThat(compareStatus(IndexShardStatus.OPEN, writer1.getIndexShardId())).isFalse();

        // Make sure that adding to writer1 reopens the index.
        indexer.addDocument(indexShardKey1, document);
        assertThat(compareStatus(IndexShardStatus.OPEN, writer1.getIndexShardId())).isTrue();

        // Close indexes again.
        indexShardWriterCache.shutdown();

        // Make sure that writer1 was closed.
        assertThat(compareStatus(IndexShardStatus.OPEN, writer1.getIndexShardId())).isFalse();
        // Make sure that writer2 was closed.
        assertThat(compareStatus(IndexShardStatus.OPEN, writer2.getIndexShardId())).isFalse();
    }

    @Test
    void testSimpleRoll() {
        // Do some work.
        final IndexDocument document = new IndexDocument();
        document.add(new FieldValue(LuceneIndexField.createField("test"), ValString.create("test")));

        final DocRef indexRef1 = commonTestScenarioCreator.createIndex("TEST_2010",
                commonTestScenarioCreator.createIndexFields(),
                10);
        final LuceneIndexDoc index1 = indexStore.readDocument(indexRef1);
        final IndexShardKey indexShardKey1 = IndexShardKey.createKey(index1);

        for (int i = 0; i < 10; i++) {
            indexer.addDocument(indexShardKey1, document);
        }

        final ResultPage<IndexShard> indexShardResultPage = indexShardDao.find(FindIndexShardCriteria.matchAll());
        assertThat(indexShardResultPage.size()).isOne();
        final IndexShard indexShard1 = indexShardResultPage.getFirst();
        final IndexShardWriter writer1 = indexShardWriterCache.getOrOpenWriter(indexShard1.getId());

        // Make sure the writer is full.
        assertThatThrownBy(() -> writer1.addDocument(document)).isInstanceOf(IndexException.class);

        // Make sure the writer is still open.
        assertThat(compareStatus(IndexShardStatus.OPEN, writer1.getIndexShardId())).isTrue();

        // Now push the writer over the edge so we get a new writer.
        indexer.addDocument(indexShardKey1, document);

        // Get the new writer.
        final IndexShardWriter writer2 = indexShardWriterCache.getOrOpenWriter(indexShard1.getId());

        // Make sure the writers are not the same.
        assertThat(writer2).isNotEqualTo(writer1);

        for (int i = 1; i < 10; i++) {
            assertThat(indexShardWriterCache.getOrOpenWriter(indexShard1.getId())).isEqualTo(writer2);
            indexer.addDocument(indexShardKey1, document);
        }

        // Make sure the writer is full.
        assertThatThrownBy(() -> writer2.addDocument(document)).isInstanceOf(IndexException.class);
    }

    @Test
    void testFileSystemError() throws IOException {
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isZero();

        // Make index volume paths that we cannot write to.
        final Path tempDir = Files.createTempDirectory("stroom");
        Files.setPosixFilePermissions(tempDir, Set.of(PosixFilePermission.OWNER_READ));
        final ResultPage<IndexVolume> resultPage = indexVolumeDao.find(new ExpressionCriteria());
        resultPage.forEach(indexVolume -> {
            indexVolume.setPath(tempDir + indexVolume.getPath());
            indexVolumeDao.update(indexVolume);
        });

        // Do some work.
        final IndexDocument document = new IndexDocument();
        document.add(new FieldValue(LuceneIndexField.createField("test"), ValString.create("test")));

        final DocRef indexRef1 = commonTestScenarioCreator.createIndex("TEST_2010",
                commonTestScenarioCreator.createIndexFields(),
                10);
        final LuceneIndexDoc index1 = indexStore.readDocument(indexRef1);
        final IndexShardKey indexShardKey1 = IndexShardKey.createKey(index1);

        assertThatThrownBy(() -> indexer.addDocument(indexShardKey1, document))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Index volume path not found");
    }

    @Test
    void testChangeFieldType() {
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isZero();

        final DocRef indexRef1 = commonTestScenarioCreator.createIndex("TEST_2010",
                commonTestScenarioCreator.createIndexFields(),
                10);
        final LuceneIndexDoc index1 = indexStore.readDocument(indexRef1);
        final IndexShardKey indexShardKey1 = IndexShardKey.createKey(index1);

        final Selection<IndexShardStatus> statusSelection = Selection.selectNone();
        statusSelection.addAll(Set.of(IndexShardStatus.CLOSED, IndexShardStatus.OPEN));
        final FindIndexShardCriteria criteria = FindIndexShardCriteria
                .builder()
                .indexShardStatusSet(statusSelection)
                .build();

        final IndexDocument document1 = new IndexDocument();
        document1.add(new FieldValue(LuceneIndexField
                .builder()
                .fldName("SourcePort")
                .fldType(FieldType.TEXT)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .termPositions(false)
                .indexed(true)
                .stored(false)
                .build(), ValString.create("12345")));
        indexer.addDocument(indexShardKey1, document1);
        indexer.addDocument(indexShardKey1, document1);
        assertThat(indexShardDao.find(criteria).size()).isOne();

        final IndexDocument document2 = new IndexDocument();
        document2.add(new FieldValue(LuceneIndexField
                .builder()
                .fldName("SourcePort")
                .fldType(FieldType.INTEGER)
                .analyzerType(AnalyzerType.KEYWORD)
                .termPositions(false)
                .indexed(true)
                .stored(false)
                .build(), ValInteger.create(12345)));
        indexer.addDocument(indexShardKey1, document2);
        indexer.addDocument(indexShardKey1, document2);
        assertThat(indexShardDao.find(criteria).size()).isEqualTo(2);

        final IndexDocument document3 = new IndexDocument();
        document3.add(new FieldValue(LuceneIndexField
                .builder()
                .fldName("SourcePort")
                .fldType(FieldType.TEXT)
                .analyzerType(AnalyzerType.KEYWORD)
                .termPositions(true)
                .indexed(true)
                .stored(false)
                .build(), ValString.create("12345")));
        indexer.addDocument(indexShardKey1, document3);
        indexer.addDocument(indexShardKey1, document3);
        assertThat(indexShardDao.find(criteria).size()).isEqualTo(3);
    }

    @Disabled
    @Test
    void testPerformance() {
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isZero();

        final DocRef indexRef1 = commonTestScenarioCreator.createIndex("TEST_2010",
                commonTestScenarioCreator.createIndexFields(),
                1000000);
        final LuceneIndexDoc index1 = indexStore.readDocument(indexRef1);
        final IndexShardKey indexShardKey1 = IndexShardKey.createKey(index1);

        final IndexDocument document1 = new IndexDocument();
        document1.add(new FieldValue(LuceneIndexField
                .builder()
                .fldName("SourcePort")
                .fldType(FieldType.TEXT)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .termPositions(false)
                .indexed(true)
                .stored(false)
                .build(), ValString.create("12345")));
        for (int i = 0; i < 1000000; i++) {
            indexer.addDocument(indexShardKey1, document1);
        }
    }

    @Test
    void testDelete() {
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isZero();

        final DocRef indexRef1 = commonTestScenarioCreator.createIndex("TEST_2010",
                commonTestScenarioCreator.createIndexFields(),
                1000000);
        final LuceneIndexDoc index1 = indexStore.readDocument(indexRef1);
        final IndexShardKey indexShardKey1 = IndexShardKey.createKey(index1);

        final IndexDocument document1 = new IndexDocument();
        document1.add(new FieldValue(LuceneIndexField
                .builder()
                .fldName("SourcePort")
                .fldType(FieldType.TEXT)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .termPositions(false)
                .indexed(true)
                .stored(false)
                .build(), ValString.create("12345")));

        final Selection<IndexShardStatus> deleted = Selection.selectNone();
        deleted.add(IndexShardStatus.DELETED);
        final FindIndexShardCriteria findDeleted = FindIndexShardCriteria.builder()
                .indexShardStatusSet(deleted).build();

        for (int j = 0; j < 10; j++) {
            // Delete every shard.
            indexShardManager.performAction(FindIndexShardCriteria.matchAll(), IndexShardAction.DELETE);
            assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isEqualTo(j);
            assertThat(indexShardDao.find(findDeleted).size()).isEqualTo(j);

            // Now add more data.
            for (int i = 0; i < 10; i++) {
                indexer.addDocument(indexShardKey1, document1);
            }
            assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isEqualTo(j + 1);
            assertThat(indexShardDao.find(findDeleted).size()).isEqualTo(j);
        }
    }

    @Test
    void testCorruption() {
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isZero();

        final DocRef indexRef1 = commonTestScenarioCreator.createIndex("TEST_2010",
                commonTestScenarioCreator.createIndexFields(),
                1000000);
        final LuceneIndexDoc index1 = indexStore.readDocument(indexRef1);
        final IndexShardKey indexShardKey1 = IndexShardKey.createKey(index1);

        final IndexDocument document1 = new IndexDocument();
        document1.add(new FieldValue(LuceneIndexField
                .builder()
                .fldName("SourcePort")
                .fldType(FieldType.TEXT)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .termPositions(false)
                .indexed(true)
                .stored(false)
                .build(), ValString.create("12345")));

        final List<IndexVolume> volumeList = indexVolumeDao.getAll();
        for (int j = 0; j < 10; j++) {
            // Flush.
            indexShardManager.performAction(FindIndexShardCriteria.matchAll(), IndexShardAction.FLUSH);

            // Delete every shard file.
            for (final IndexVolume indexVolume : volumeList) {
                FileUtil.deleteContents(Paths.get(indexVolume.getPath()));
            }
            assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isEqualTo(j);

            // Now add more data.
            for (int i = 0; i < 10; i++) {
                indexer.addDocument(indexShardKey1, document1);
            }
            assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isEqualTo(j + 1);
        }
    }

    private void checkDocCount(final int expected, final IndexShardWriter indexShardWriter) {
        assertThat(indexShardWriter.getDocumentCount()).isEqualTo(expected);
    }

    private void checkDocCount(final int expected, final long indexShardId) {
        final IndexShard loaded = indexShardDao.fetch(indexShardId).orElseThrow();
        assertThat(loaded.getDocumentCount()).isEqualTo(expected);
    }

    private boolean compareStatus(final IndexShardStatus expected, final long indexShardId) {
        final IndexShard loaded = indexShardDao.fetch(indexShardId).orElseThrow();
        return expected.equals(loaded.getStatus());
    }
}
