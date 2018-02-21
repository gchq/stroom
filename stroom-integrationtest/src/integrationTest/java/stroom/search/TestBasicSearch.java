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
 *
 */

package stroom.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.junit.Assert;
import org.junit.Test;
import stroom.entity.shared.DocRefUtil;
import stroom.index.FieldFactory;
import stroom.index.IndexShardKeyUtil;
import stroom.index.IndexShardService;
import stroom.index.IndexShardWriterCache;
import stroom.index.Indexer;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexFields;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.search.MaxHitCollector;
import stroom.search.shard.IndexShardSearcher;
import stroom.search.shard.IndexShardSearcherImpl;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;

public class TestBasicSearch extends AbstractCoreIntegrationTest {
    @Resource
    private Indexer indexer;
    @Resource
    private IndexShardWriterCache indexShardWriterCache;
    @Resource
    private IndexShardService indexShardService;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;

    @Test
    public void testSimple() throws IOException {
        final IndexFields indexFields = IndexFields.createStreamIndexFields();
        final IndexField idField = IndexField.createField("IdTreeNode", AnalyzerType.ALPHA_NUMERIC, false, true, true, false);
        final IndexField testField = IndexField.createField("test", AnalyzerType.ALPHA_NUMERIC, false, true, true,
                false);
        final IndexField nonStoreField = IndexField.createField("nonstore", AnalyzerType.ALPHA_NUMERIC, false, false,
                true, false);
        indexFields.add(idField);
        indexFields.add(testField);
        indexFields.add(nonStoreField);
        final int indexTestSize = 10;

        final String indexName = "TEST";
        final Index index = commonTestScenarioCreator.createIndex(indexName, indexFields);

        final IndexShardKey indexShardKey = IndexShardKeyUtil.createTestKey(index);

        // Do some work.
        for (int i = 1; i <= indexTestSize; i++) {
            final Field idFld = FieldFactory.create(idField, i + ":" + i);
            final Field testFld = FieldFactory.create(testField, "test");
            final Field nonStoredFld = FieldFactory.create(nonStoreField, "test");

            final Document document = new Document();
            document.add(idFld);
            document.add(testFld);
            document.add(nonStoredFld);

            indexer.addDocument(indexShardKey, document);
        }

        indexShardWriterCache.flushAll();

        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getIndexSet().add(DocRefUtil.create(index));
        final List<IndexShard> shards = indexShardService.find(criteria);

        // Open readers and add reader searcher to the multi searcher.
        final IndexShardSearcher[] indexShardSearchers = new IndexShardSearcherImpl[shards.size()];
        int i = 0;
        for (final IndexShard indexShard : shards) {
            final IndexShardSearcher indexShardSearcher = new IndexShardSearcherImpl(indexShard);
            indexShardSearchers[i++] = indexShardSearcher;
        }

        final IndexSearcher[] indexSearchers = new IndexSearcher[indexShardSearchers.length];
        for (i = 0; i < indexShardSearchers.length; i++) {
            indexSearchers[i] = indexShardSearchers[i].getSearcherManager().acquire();
        }

        final IndexReader[] indexReaders = new IndexReader[indexSearchers.length];
        for (i = 0; i < indexSearchers.length; i++) {
            indexReaders[i] = indexSearchers[i].getIndexReader();
        }

        final MultiReader multiReader = new MultiReader(indexReaders);
        final IndexSearcher indexSearcher = new IndexSearcher(multiReader);

        final TermQuery termQuery = new TermQuery(new Term("test", "test"));
        final MaxHitCollector maxHitCollector = new MaxHitCollector(3000);
        indexSearcher.search(termQuery, maxHitCollector);
        Assert.assertEquals(indexTestSize, maxHitCollector.getDocIdList().size());

        for (final Integer id : maxHitCollector.getDocIdList()) {
            final Document doc = indexSearcher.doc(id);
            final IndexableField testFld = doc.getField("test");
            Assert.assertNotNull(testFld);
            Assert.assertEquals("test", testFld.stringValue());
        }

        final TermQuery termQuery2 = new TermQuery(new Term("nonstore", "test"));
        final MaxHitCollector maxHitCollector2 = new MaxHitCollector(3000);
        indexSearcher.search(termQuery2, maxHitCollector2);
        Assert.assertEquals(indexTestSize, maxHitCollector2.getDocIdList().size());

        for (final Integer id : maxHitCollector2.getDocIdList()) {
            final Document doc = indexSearcher.doc(id);
            final IndexableField testFld = doc.getField("test");
            Assert.assertNotNull(testFld);
            Assert.assertEquals("test", testFld.stringValue());
            final IndexableField nonstoreField = doc.getField("nonstore");
            Assert.assertNull(nonstoreField);
        }

        // Release searchers
        for (i = 0; i < indexShardSearchers.length; i++) {
            final IndexSearcher searcher = indexSearchers[i];
            indexShardSearchers[i].getSearcherManager().release(searcher);
        }

        // Close readers.
        for (final IndexShardSearcher indexShardSearcher : indexShardSearchers) {
            indexShardSearcher.destroy();
        }
    }
}
