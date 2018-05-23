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

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableFieldType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import stroom.feed.shared.Feed;
import stroom.guice.PipelineScopeRunnable;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexFieldType;
import stroom.index.shared.IndexFields;
import stroom.index.shared.IndexShardKey;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.FeedHolder;
import stroom.docref.DocRef;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public class TestIndexingFilter extends AbstractProcessIntegrationTest {
    private static final String PIPELINE = "TestIndexingFilter/TestIndexingFilter.Pipeline.data.xml";

    @Inject
    private Provider<PipelineFactory> pipelineFactoryProvider;
    @Inject
    private Provider<ErrorReceiverProxy> errorReceiverProvider;
    @Inject
    private Provider<FeedHolder> feedHolderProvider;
    @Inject
    private MockIndexShardWriterCache indexShardWriterCache;
    @Inject
    private IndexStore indexStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private PipelineDataCache pipelineDataCache;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;

    @Before
    @After
    public void clear() {
        indexShardWriterCache.shutdown();
    }

    @Test
    public void testSimpleDocuments() {
        final List<IndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("sid"));
        indexFields.add(IndexField.createField("sid2", AnalyzerType.ALPHA_NUMERIC, false, true, true, false));
        indexFields.add(IndexField.create(IndexFieldType.NUMERIC_FIELD, "size", AnalyzerType.KEYWORD, false, false,
                false, false));
        indexFields.add(IndexField.createDateField("eventTime"));

        final List<Document> documents = doTest("TestIndexDocumentFilter/SimpleDocuments.xml", indexFields);

        Assert.assertEquals(3, documents.size());
        final Document doc = documents.get(0);
        Assert.assertTrue(doc.getField("sid2").fieldType().stored());
        Assert.assertTrue(doc.getField("sid2").fieldType().indexOptions().equals(IndexOptions.DOCS));
        Assert.assertTrue(doc.getField("sid2").fieldType().omitNorms());
        Assert.assertFalse(doc.getField("sid2").fieldType().storeTermVectors());
        Assert.assertFalse(doc.getField("sid2").fieldType().storeTermVectorPositions());
        Assert.assertFalse(doc.getField("sid2").fieldType().storeTermVectorOffsets());
        Assert.assertFalse(doc.getField("sid2").fieldType().storeTermVectorPayloads());

        Assert.assertNull(doc.getField("size"));

        Assert.assertEquals("someuser", documents.get(1).getField("sid").stringValue());
        Assert.assertEquals("someuser", documents.get(2).getField("sid").stringValue());
    }

    @Test
    public void testDuplicateFields() {
        final List<IndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("sid"));
        indexFields.add(IndexField.createDateField("eventTime"));

        final List<Document> documents = doTest("TestIndexDocumentFilter/DuplicateFields.xml", indexFields);

        Assert.assertEquals(4, documents.size());
        Assert.assertEquals(2, documents.get(0).getFields("sid").length);
        Assert.assertEquals(1, documents.get(1).getFields("sid").length);
        Assert.assertEquals(1, documents.get(2).getFields("sid").length);
        Assert.assertEquals(0, documents.get(3).getFields("sid").length);
    }

    @Test
    public void testBlankDocuments() {
        final List<IndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("sid"));

        final List<Document> documents = doTest("TestIndexDocumentFilter/BlankDocuments.xml", indexFields);
        Assert.assertNull(documents);
    }

    @Test
    public void testInvalidContent1() {
        final List<IndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("sid"));

        final List<Document> documents = doTest("TestIndexDocumentFilter/InvalidContent1.xml", indexFields);
        Assert.assertNull(documents);
    }

    @Test
    public void testInvalidContent2() {
        final List<IndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("sid"));

        final List<Document> documents = doTest("TestIndexDocumentFilter/InvalidContent2.xml", indexFields);
        Assert.assertEquals(1, documents.size());
    }

    @Test
    public void testComplexContent() {
        final List<IndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("f1", AnalyzerType.ALPHA_NUMERIC, false, true, true, true));
        indexFields.add(IndexField.createField("f2", AnalyzerType.ALPHA_NUMERIC, false, false, true, false));
        indexFields.add(IndexField.createDateField("d1"));
        indexFields.add(IndexField.createNumericField("n1"));
        indexFields.add(IndexField.createNumericField("n2"));

        final List<Document> documents = doTest("TestIndexDocumentFilter/ComplexContent.xml", indexFields);

        Assert.assertEquals(1, documents.size());
        final IndexableFieldType fieldType = documents.get(0).getField("f1").fieldType();
        Assert.assertTrue(fieldType.stored());
        Assert.assertTrue(fieldType.indexOptions().equals(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS));
        Assert.assertTrue(fieldType.tokenized());

        Assert.assertFalse(documents.get(0).getField("f2").fieldType().stored());

        Assert.assertEquals(DateUtil.parseUnknownString("2010-01-01T12:00:00.000Z"),
                ((documents.get(0).getField("d1")).numericValue().longValue()));

    }

    private List<Document> doTest(final String resourceName, final List<IndexField> indexFields) {
        return pipelineScopeRunnable.scopeResult(() -> {
            // Setup the index.
            final DocRef indexRef = indexStore.createDocument("Test index");
            final IndexDoc index = indexStore.readDocument(indexRef);
            index.setIndexFields(indexFields);
            indexStore.writeDocument(index);

            final Path tempDir = getCurrentTestDir();

            // Make sure the config dir is set.
            System.setProperty("stroom.temp", FileUtil.getCanonicalPath(tempDir));

            // Setup the error handler.
            final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
            errorReceiverProvider.get().setErrorReceiver(loggingErrorReceiver);

            // Create the pipeline.
            final String data = StroomPipelineTestFileUtil.getString(PIPELINE);
            final DocRef pipelineRef = PipelineTestUtil.createTestPipeline(pipelineStore, data);
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
            pipelineDoc.getPipelineData().addProperty(PipelineDataUtil.createProperty("indexingFilter", "index", indexRef));
            pipelineStore.writeDocument(pipelineDoc);

            // Create the parser.
            final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
            final Pipeline pipeline = pipelineFactoryProvider.get().create(pipelineData);

//            feedHolderProvider.get().setFeed(new Feed());

//            // Setup the meta data holder.
//            metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(streamHolder, streamProcessorService, pipelineStore));

            // Set the input.
            final InputStream input = StroomPipelineTestFileUtil.getInputStream(resourceName);
            try {
                pipeline.process(input);
            } catch (final RuntimeException e) {
                // Ignore as some tests expect errors. These tests will get see that
                // nothing was written.
            }

            // Wrote anything ?
            final IndexShardKey indexShardKey = IndexShardKeyUtil.createTestKey(index);
            if (indexShardWriterCache.getWriters().size() > 0) {
                Assert.assertEquals(1, indexShardWriterCache.getWriters().size());
                Assert.assertTrue(indexShardWriterCache.getWriters().containsKey(indexShardKey));

                // Get a writer from the pool.
                for (final IndexShardWriter writer : indexShardWriterCache.getWriters().values()) {
                    return ((MockIndexShardWriter) writer).getDocuments();
                }
            }

            return null;
        });
    }
}
