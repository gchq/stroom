/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.index;

import stroom.docref.DocRef;
import stroom.index.impl.FieldTypeFactory;
import stroom.index.impl.IndexShardKeyUtil;
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardManager.IndexShardAction;
import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexStore;
import stroom.index.impl.Indexer;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestIndexShardWriterImpl extends AbstractCoreIntegrationTest {
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private IndexShardService indexShardService;
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

    @BeforeEach
    void onBefore() {
        indexShardWriterCache.shutdown();
    }

    @Test
    void testSingle() throws IOException {
        assertThat(indexShardService.find(FindIndexShardCriteria.matchAll()).size()).isZero();

        // Do some work.
        final FieldType fieldType = FieldTypeFactory.createBasic();
        final Field field = new Field("test", "test", fieldType);
        final Document document = new Document();
        document.add(field);

        // Create an index
        final DocRef indexRef1 = commonTestScenarioCreator.createIndex("TEST_2010a");
        final IndexDoc index1 = indexStore.readDocument(indexRef1);
        final IndexShardKey indexShardKey1 = IndexShardKeyUtil.createTestKey(index1);

        // Create a writer in the pool
        final IndexShardWriter writer1 = indexShardWriterCache.getWriterByShardKey(indexShardKey1);

        // Assert that there is 1 writer in the pool.
        assertThat(indexShardService.find(FindIndexShardCriteria.matchAll()).size()).isEqualTo(1);

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
    void testSimple() throws IOException {
        assertThat(indexShardService.find(FindIndexShardCriteria.matchAll()).size()).isZero();

        // Do some work.
        final FieldType fieldType = FieldTypeFactory.createBasic();
        final Field field = new Field("test", "test", fieldType);
        final Document document = new Document();
        document.add(field);

        final DocRef indexRef1 = commonTestScenarioCreator.createIndex("TEST_2010");
        final IndexDoc index1 = indexStore.readDocument(indexRef1);
        final IndexShardKey indexShardKey1 = IndexShardKeyUtil.createTestKey(index1);

        final DocRef indexRef2 = commonTestScenarioCreator.createIndex("TEST_2011");
        final IndexDoc index2 = indexStore.readDocument(indexRef2);
        final IndexShardKey indexShardKey2 = IndexShardKeyUtil.createTestKey(index2);

        // Create 2 writers in the pool.
        final IndexShardWriter writer1 = indexShardWriterCache.getWriterByShardKey(indexShardKey1);
        final IndexShardWriter writer2 = indexShardWriterCache.getWriterByShardKey(indexShardKey2);

        // Assert that there are 2 writers in the pool.
        assertThat(indexShardService.find(FindIndexShardCriteria.matchAll()).size()).isEqualTo(2);

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

    private void checkDocCount(final int expected, final IndexShardWriter indexShardWriter) {
        assertThat(indexShardWriter.getDocumentCount()).isEqualTo(expected);
    }

    private void checkDocCount(final int expected, final long indexShardId) {
        final IndexShard loaded = indexShardService.loadById(indexShardId);
        assertThat(loaded.getDocumentCount()).isEqualTo(expected);
    }

    private boolean compareStatus(final IndexShardStatus expected, final long indexShardId) {
        final IndexShard loaded = indexShardService.loadById(indexShardId);
        return expected.equals(loaded.getStatus());
    }

    @Test
    void testSimpleRoll() throws IOException {
        // Do some work.
        final FieldType fieldType = FieldTypeFactory.createBasic();
        final Field field = new Field("test", "test", fieldType);
        final Document document = new Document();
        document.add(field);

        final DocRef indexRef1 = commonTestScenarioCreator.createIndex("TEST_2010", commonTestScenarioCreator.createIndexFields(), 10);
        final IndexDoc index1 = indexStore.readDocument(indexRef1);
        final IndexShardKey indexShardKey1 = IndexShardKeyUtil.createTestKey(index1);

        final IndexShardWriter writer1 = indexShardWriterCache.getWriterByShardKey(indexShardKey1);

        for (int i = 0; i < 10; i++) {
            assertThat(indexShardWriterCache.getWriterByShardKey(indexShardKey1)).isEqualTo(writer1);
            indexer.addDocument(indexShardKey1, document);
        }

        // Make sure the writer is full.
        assertThatThrownBy(() -> writer1.addDocument(document)).isInstanceOf(IndexException.class);

        // Make sure the writer is still open.
        assertThat(compareStatus(IndexShardStatus.OPEN, writer1.getIndexShardId())).isTrue();

        // Now push the writer over the edge so we get a new writer.
        indexer.addDocument(indexShardKey1, document);

        // Get the new writer.
        final IndexShardWriter writer2 = indexShardWriterCache.getWriterByShardKey(indexShardKey1);

        // Make sure the writers are not the same.
        assertThat(writer2).isNotEqualTo(writer1);

        for (int i = 1; i < 10; i++) {
            assertThat(indexShardWriterCache.getWriterByShardKey(indexShardKey1)).isEqualTo(writer2);
            indexer.addDocument(indexShardKey1, document);
        }

        // Make sure the writer is full.
        assertThatThrownBy(() -> writer2.addDocument(document)).isInstanceOf(IndexException.class);

        // Make sure the writer is still open.
        assertThat(compareStatus(IndexShardStatus.OPEN, writer2.getIndexShardId())).isTrue();
    }
}
