/*
 * Copyright 2016 Crown Copyright
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

package stroom.index.server;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.junit.Assert;
import org.junit.Test;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexShardService;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;

import javax.annotation.Resource;
import java.io.IOException;

public class TestIndexShardWriterImpl extends AbstractCoreIntegrationTest {
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private IndexShardService indexShardService;
    @Resource
    private IndexShardManager indexShardManager;
    @Resource
    private IndexShardKeyCache indexShardKeyCache;
    @Resource
    private IndexShardWriterCache indexShardWriterCache;
    @Resource
    private Indexer indexer;
    @Resource
    private CommonTestControl commonTestControl;

    @Override
    public void onBefore() {
        indexShardManager.shutdown();
    }

    @Test
    public void testSimple() throws IOException {
        Assert.assertEquals(0, commonTestControl.countEntity(IndexShard.class));

        // Do some work.
        final FieldType fieldType = FieldTypeFactory.createBasic();
        final Field field = new Field("test", "test", fieldType);
        final Document document = new Document();
        document.add(field);

        final Index index1 = commonTestScenarioCreator.createIndex("TEST_2010");
        final IndexShardKey indexShardKey1 = IndexShardKeyUtil.createTestKey(index1);
        final IndexShard indexShard1 = indexShardKeyCache.getOrCreate(indexShardKey1);

        final Index index2 = commonTestScenarioCreator.createIndex("TEST_2011");
        final IndexShardKey indexShardKey2 = IndexShardKeyUtil.createTestKey(index2);
        final IndexShard indexShard2 = indexShardKeyCache.getOrCreate(indexShardKey2);

        // Create 2 writers in the pool.
        final IndexShardWriter writer1 = indexShardWriterCache.getOrCreate(indexShard1.getId());
        final IndexShardWriter writer2 = indexShardWriterCache.getOrCreate(indexShard2.getId());

        // Assert that there are 2 writers in the pool.
        Assert.assertEquals(2, commonTestControl.countEntity(IndexShard.class));

        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getIndexIdSet().setMatchAll(true);

        checkDocCount(0, writer1);
        checkDocCount(0, indexShard1);
        writer1.addDocument(document);
        checkDocCount(1, writer1);
        checkDocCount(0, indexShard1);
        indexShardManager.findFlush(criteria);
        checkDocCount(1, writer1);
        checkDocCount(1, indexShard1);

        writer1.addDocument(document);
        checkDocCount(2, writer1);
        checkDocCount(1, indexShard1);
        indexShardManager.findFlush(criteria);
        checkDocCount(2, writer1);
        checkDocCount(2, indexShard1);

        checkDocCount(0, writer2);
        checkDocCount(0, indexShard2);
        writer2.addDocument(document);
        checkDocCount(1, writer2);
        checkDocCount(0, indexShard2);
        indexShardManager.findFlush(criteria);
        checkDocCount(1, writer2);
        checkDocCount(1, indexShard2);

        // Close writer1 by removing the writer from the cache.
        indexShardWriterCache.remove(indexShard1.getId());
        // Make sure that writer1 was closed.
        Assert.assertFalse(compareStatus(IndexShardStatus.OPEN, indexShard1));

        // Make sure that adding to writer1 reopens the index.
        final IndexShard test = indexShardService.load(indexShard1);
        indexer.addDocument(indexShardKey1, document);
        Assert.assertTrue(compareStatus(IndexShardStatus.OPEN, indexShard1));

        // Close indexes again.
        indexShardWriterCache.clear();

        // Make sure that writer1 was closed.
        Assert.assertFalse(compareStatus(IndexShardStatus.OPEN, indexShard1));
        // Make sure that writer2 was closed.
        Assert.assertFalse(compareStatus(IndexShardStatus.OPEN, indexShard2));
    }

    private void checkDocCount(final int expected, final IndexShardWriter indexShardWriter) {
        Assert.assertEquals(expected, indexShardWriter.getDocumentCount());
    }

    private void checkDocCount(final int expected, final IndexShard indexShard) {
        final IndexShard loaded = indexShardService.load(indexShard);
        Assert.assertEquals(expected, loaded.getDocumentCount());
    }

    private boolean compareStatus(final IndexShardStatus expected, final IndexShard indexShard) {
        final IndexShard loaded = indexShardService.load(indexShard);
        return expected.equals(loaded.getStatus());
    }

    @Test
    public void testSimpleRoll() throws IOException {
        // Do some work.
        final FieldType fieldType = FieldTypeFactory.createBasic();
        final Field field = new Field("test", "test", fieldType);
        final Document document = new Document();
        document.add(field);

        final Index index1 = commonTestScenarioCreator.createIndex("TEST_2010",
                commonTestScenarioCreator.createIndexFields(), 10);

        final IndexShardKey indexShardKey1 = IndexShardKeyUtil.createTestKey(index1);
        IndexShard indexShard1 = indexShardKeyCache.getOrCreate(indexShardKey1);

        final IndexShardWriter writer1 = indexShardWriterCache.getOrCreate(indexShard1.getId());

        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(writer1, indexShardWriterCache.getOrCreate(indexShard1.getId()));
            indexer.addDocument(indexShardKey1, document);
        }

        // Make sure the writer is full.
        try {
            writer1.addDocument(document);
            Assert.fail();
        } catch (final IndexException e) {
            // Expected.
        }

        // Make sure the writer is still open.
        indexShard1 = indexShardService.load(indexShard1);
        Assert.assertTrue(IndexShardStatus.OPEN.equals(indexShard1.getStatus()));

        // Now push the writer over the edge so we get a new writer.
        indexer.addDocument(indexShardKey1, document);

        // Get the new writer.
        IndexShard indexShard2 = indexShardKeyCache.getOrCreate(indexShardKey1);
        Assert.assertNotEquals(indexShard1, indexShard2);
        final IndexShardWriter writer2 = indexShardWriterCache.getOrCreate(indexShard2.getId());

        // Make sure the writers are not the same.
        Assert.assertNotEquals(writer1, writer2);

        for (int i = 1; i < 10; i++) {
            Assert.assertEquals(writer2, indexShardWriterCache.getOrCreate(indexShard2.getId()));
            indexer.addDocument(indexShardKey1, document);
        }

        // Make sure the writer is full.
        try {
            writer2.addDocument(document);
            Assert.fail();
        } catch (final IndexException e) {
            // Expected.
        }

        // Make sure the writer is still open.
        indexShard2 = indexShardService.load(indexShard2);
        Assert.assertTrue(IndexShardStatus.OPEN.equals(indexShard2.getStatus()));
    }
}
