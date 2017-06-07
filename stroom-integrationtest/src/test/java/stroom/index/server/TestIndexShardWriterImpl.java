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
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestControl;
import stroom.CommonTestScenarioCreator;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;

import javax.annotation.Resource;
import java.io.IOException;

public class TestIndexShardWriterImpl extends AbstractCoreIntegrationTest {
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private IndexShardManagerImpl indexShardManager;
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

        final Index index2 = commonTestScenarioCreator.createIndex("TEST_2011");
        final IndexShardKey indexShardKey2 = IndexShardKeyUtil.createTestKey(index2);

        // Create 2 writers in the pool.
        final IndexShardWriter writer1 = indexShardManager.get(indexShardKey1);
        final IndexShardWriter writer2 = indexShardManager.get(indexShardKey2);

        // Assert that there are 2 writers in the pool.
        Assert.assertEquals(2, commonTestControl.countEntity(IndexShard.class));

        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getIndexIdSet().setMatchAll(true);

        Assert.assertEquals(0, writer1.getDocumentCount());
        Assert.assertEquals(0, writer1.getIndexShard().getDocumentCount());
        writer1.addDocument(document);
        Assert.assertEquals(1, writer1.getDocumentCount());
        Assert.assertEquals(0, writer1.getIndexShard().getDocumentCount());
        indexShardManager.findFlush(criteria);
        Assert.assertEquals(1, writer1.getDocumentCount());
        Assert.assertEquals(1, writer1.getIndexShard().getDocumentCount());

        writer1.addDocument(document);
        Assert.assertEquals(2, writer1.getDocumentCount());
        Assert.assertEquals(1, writer1.getIndexShard().getDocumentCount());
        indexShardManager.findFlush(criteria);
        Assert.assertEquals(2, writer1.getDocumentCount());
        Assert.assertEquals(2, writer1.getIndexShard().getDocumentCount());

        Assert.assertEquals(0, writer2.getDocumentCount());
        Assert.assertEquals(0, writer2.getIndexShard().getDocumentCount());
        writer2.addDocument(document);
        Assert.assertEquals(1, writer2.getDocumentCount());
        Assert.assertEquals(0, writer2.getIndexShard().getDocumentCount());
        indexShardManager.findClose(criteria);
        Assert.assertEquals(1, writer2.getDocumentCount());
        Assert.assertEquals(1, writer2.getIndexShard().getDocumentCount());

        // Make sure that writer1 was closed.
        Assert.assertFalse(IndexShardStatus.OPEN.equals(writer1.getStatus()));

        // Make sure that adding to writer1 reopens the index.
        indexShardManager.addDocument(indexShardKey1, document);
        Assert.assertTrue(IndexShardStatus.OPEN.equals(writer1.getStatus()));

        // Close indexes again.
        indexShardManager.findClose(criteria);

        // Make sure that writer1 was closed.
        Assert.assertFalse(IndexShardStatus.OPEN.equals(writer1.getStatus()));
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

        final IndexShardWriter writer1 = indexShardManager.get(indexShardKey1);

        for (int i = 0; i < 10; i++) {
            Assert.assertFalse(writer1.isFull());
            Assert.assertEquals(writer1, indexShardManager.get(indexShardKey1));
            indexShardManager.addDocument(indexShardKey1, document);
        }

        // Make sure the writer is full.
        Assert.assertTrue(writer1.isFull());
        // Make sure the writer is still open.
        Assert.assertTrue(IndexShardStatus.OPEN.equals(writer1.getStatus()));

        // Now push the writer over the edge so we get a new writer.
        indexShardManager.addDocument(indexShardKey1, document);

        // Get the new writer.
        final IndexShardWriter writer2 = indexShardManager.get(indexShardKey1);

        // Make sure the writers are not the same.
        Assert.assertNotEquals(writer1, writer2);

        for (int i = 1; i < 10; i++) {
            Assert.assertFalse(writer2.isFull());
            Assert.assertEquals(writer2, indexShardManager.get(indexShardKey1));
            indexShardManager.addDocument(indexShardKey1, document);
        }

        // Make sure the writer is full.
        Assert.assertTrue(writer2.isFull());
        // Make sure the writer is still open.
        Assert.assertTrue(IndexShardStatus.OPEN.equals(writer2.getStatus()));

    }
}
