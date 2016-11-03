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

package stroom.index;

import java.io.IOException;

import javax.annotation.Resource;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.junit.Assert;
import org.junit.Test;

import stroom.index.server.FieldTypeFactory;
import stroom.index.server.IndexShardKeyUtil;
import stroom.index.server.IndexShardWriter;
import stroom.index.server.IndexShardWriterCache;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestControl;
import stroom.CommonTestScenarioCreator;

public class TestIndexShardWriterImpl extends AbstractCoreIntegrationTest {
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private IndexShardWriterCache indexShardWriterCache;
    @Resource
    private CommonTestControl commonTestControl;

    @Override
    public void onBefore() {
        indexShardWriterCache.shutdown();
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

        final Index index2 = commonTestScenarioCreator.createIndex("TEST_2011");
        final IndexShardKey indexShardKey2 = IndexShardKeyUtil.createTestKey(index2);

        // Create 2 writers in the pool.
        final IndexShardWriter writer1 = indexShardWriterCache.get(indexShardKey1);
        final IndexShardWriter writer2 = indexShardWriterCache.get(indexShardKey2);

        // Assert that there are 2 writers in the pool.
        Assert.assertEquals(2, commonTestControl.countEntity(IndexShard.class));

        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getIndexIdSet().setMatchAll(true);

        Assert.assertEquals(0, writer1.getDocumentCount());
        Assert.assertEquals(0, writer1.getIndexShard().getDocumentCount());
        writer1.addDocument(document);
        Assert.assertEquals(1, writer1.getDocumentCount());
        Assert.assertEquals(0, writer1.getIndexShard().getDocumentCount());
        indexShardWriterCache.findFlush(criteria);
        Assert.assertEquals(1, writer1.getDocumentCount());
        Assert.assertEquals(1, writer1.getIndexShard().getDocumentCount());

        writer1.addDocument(document);
        Assert.assertEquals(2, writer1.getDocumentCount());
        Assert.assertEquals(1, writer1.getIndexShard().getDocumentCount());
        indexShardWriterCache.findFlush(criteria);
        Assert.assertEquals(2, writer1.getDocumentCount());
        Assert.assertEquals(2, writer1.getIndexShard().getDocumentCount());

        Assert.assertEquals(0, writer2.getDocumentCount());
        Assert.assertEquals(0, writer2.getIndexShard().getDocumentCount());
        writer2.addDocument(document);
        Assert.assertEquals(1, writer2.getDocumentCount());
        Assert.assertEquals(0, writer2.getIndexShard().getDocumentCount());
        indexShardWriterCache.findClose(criteria);
        Assert.assertEquals(1, writer2.getDocumentCount());
        Assert.assertEquals(1, writer2.getIndexShard().getDocumentCount());

        // Make sure that writer1 was closed.
        Assert.assertFalse(writer1.isOpen());

        // Make sure that adding to writer1 reopens the index.
        final boolean added = writer1.addDocument(document);
        Assert.assertTrue(added);
        Assert.assertTrue(writer1.isOpen());

        // Close indexes again.
        indexShardWriterCache.findClose(criteria);

        // Make sure that writer1 was closed.
        Assert.assertFalse(writer1.isOpen());
    }

    @Test
    public void testSimpleRoll() throws IOException {
        // Do some work.
        final FieldType fieldType = FieldTypeFactory.createBasic();
        final Field field = new Field("test", "test", fieldType);
        final Document document = new Document();
        document.add(field);

        // final Folder folder = commonTestScenarioCreator
        // .getGlobalGroup();
        //
        final Index index1 = commonTestScenarioCreator.createIndex("TEST_2010",
                commonTestScenarioCreator.createIndexFields(), 10);
        // index1.setMaxDocsPerShard(10);
        // index1.setName("TEST/2010");
        // index1.setFolder(folder);
        // index1 = indexService.save(index1);

        final IndexShardKey indexShardKey1 = IndexShardKeyUtil.createTestKey(index1);

        final IndexShardWriter toFillWriter = indexShardWriterCache.get(indexShardKey1);

        for (int i = 0; i < 10; i++) {
            Assert.assertFalse(toFillWriter.isFull());
            Assert.assertTrue(toFillWriter.addDocument(document));
        }

        // Make sure the writer is full.
        Assert.assertTrue(toFillWriter.isFull());
        // Try and add a document and make sure that it returns false as the
        // writer is full.
        final boolean added = toFillWriter.addDocument(document);
        Assert.assertFalse(added);
        // Make sure the writer is still open.
        Assert.assertTrue(toFillWriter.isOpen());
        // Remove the item from the pool.
        indexShardWriterCache.remove(indexShardKey1);
        // Make sure the writer is closed when the pool destroys it.
        Assert.assertFalse(toFillWriter.isOpen());
        // Make sure the pool doesn't destroy items more than once.
        indexShardWriterCache.remove(indexShardKey1);
        Assert.assertFalse(toFillWriter.isOpen());

        final IndexShardWriter newWriter = indexShardWriterCache.get(indexShardKey1);

        Assert.assertTrue(newWriter.addDocument(document));

        // Force the pool to load up a load which should close off the full
        // writer
        for (int i = 0; i < 10; i++) {
            final IndexShardWriter poolItem = indexShardWriterCache.get(indexShardKey1);
            // poolItems.add(poolItem);
        }

        Assert.assertFalse(toFillWriter.isOpen());

        // // Return all poolItems to the pool.
        // for (final PoolItem<IndexShardKey, IndexShardWriter> poolItem :
        // poolItems) {
        // indexShardWriterCache.returnObject(poolItem, true);
        // }
    }
}
