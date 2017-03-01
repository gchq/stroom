/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.server;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableFieldType;
import org.junit.Assert;
import org.junit.Test;
import org.xml.sax.SAXException;
import stroom.AbstractProcessIntegrationTest;
import stroom.feed.shared.Feed;
import stroom.index.server.IndexMarshaller;
import stroom.index.server.IndexShardKeyUtil;
import stroom.index.server.IndexShardWriter;
import stroom.index.server.MockIndexShardWriter;
import stroom.index.server.MockIndexShardWriterCache;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexFieldType;
import stroom.index.shared.IndexFields;
import stroom.index.shared.IndexService;
import stroom.index.shared.IndexShardKey;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.server.factory.Pipeline;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.FeedHolder;
import stroom.test.PipelineTestUtil;
import stroom.test.StroomProcessTestFileUtil;
import stroom.util.date.DateUtil;

import javax.annotation.Resource;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class TestIndexingFilter extends AbstractProcessIntegrationTest {
    private static final String PIPELINE = "TestIndexingFilter/TestIndexingFilter.Pipeline.data.xml";

    @Resource
    private PipelineFactory pipelineFactory;
    @Resource
    private ErrorReceiverProxy errorReceiver;
    @Resource
    private FeedHolder feedHolder;
    @Resource
    private MockIndexShardWriterCache indexShardPool;
    @Resource
    private IndexService indexService;
    @Resource
    private PipelineEntityService pipelineEntityService;
    @Resource
    private PipelineMarshaller pipelineMarshaller;
    @Resource
    private PipelineDataCache pipelineDataCache;

    @Test
    public void testSimpleDocuments() throws SAXException, ParserConfigurationException, IOException {
        final IndexFields indexFields = IndexFields.createStreamIndexFields();
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
    public void testDuplicateFields() throws SAXException, ParserConfigurationException, IOException {
        final IndexFields indexFields = IndexFields.createStreamIndexFields();
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
    public void testBlankDocuments() throws SAXException, ParserConfigurationException, IOException {
        final IndexFields indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("sid"));

        final List<Document> documents = doTest("TestIndexDocumentFilter/BlankDocuments.xml", indexFields);
        Assert.assertNull(documents);
    }

    @Test
    public void testInvalidContent1() throws SAXException, ParserConfigurationException, IOException {
        final IndexFields indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("sid"));

        final List<Document> documents = doTest("TestIndexDocumentFilter/InvalidContent1.xml", indexFields);
        Assert.assertNull(documents);
    }

    @Test
    public void testInvalidContent2() throws SAXException, ParserConfigurationException, IOException {
        final IndexFields indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("sid"));

        final List<Document> documents = doTest("TestIndexDocumentFilter/InvalidContent2.xml", indexFields);
        Assert.assertEquals(1, documents.size());
    }

    @Test
    public void testComplexContent() throws SAXException, ParserConfigurationException, IOException {
        final IndexFields indexFields = IndexFields.createStreamIndexFields();
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

    public List<Document> doTest(final String resourceName, final IndexFields indexFields)
            throws SAXException, ParserConfigurationException, IOException {
        // Setup the index.
        Index index = indexService.create(null, "Test index");
        index.setIndexFieldsObject(indexFields);
        index = new IndexMarshaller().marshal(index);
        index = indexService.save(index);

        final File tempDir = getCurrentTestDir();

        // Make sure the config dir is set.
        System.setProperty("stroom.temp", tempDir.getCanonicalPath());

        // Setup the error handler.
        final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
        errorReceiver.setErrorReceiver(loggingErrorReceiver);

        // Create the pipeline.
        final String data = StroomProcessTestFileUtil.getString(PIPELINE);
        PipelineEntity pipelineEntity = PipelineTestUtil.createTestPipeline(pipelineEntityService, pipelineMarshaller, data);
        pipelineEntity.getPipelineData().addProperty(PipelineDataUtil.createProperty("indexingFilter", "index", index));
        pipelineEntity = pipelineEntityService.save(pipelineEntity);

        // Create the parser.
        final PipelineData pipelineData = pipelineDataCache.get(pipelineEntity);
        final Pipeline pipeline = pipelineFactory.create(pipelineData);

        feedHolder.setFeed(new Feed());

        // Set the input.
        final InputStream input = StroomProcessTestFileUtil.getInputStream(resourceName);
        try {
            pipeline.process(input);
        } catch (final Exception e) {
            // Ignore as some tests expect errors. These tests will get see that
            // nothing was written.
        }

        // Wrote anything ?
        final IndexShardKey indexShardKey = IndexShardKeyUtil.createTestKey(index);
        if (indexShardPool.getWriters().size() > 0) {
            Assert.assertEquals(1, indexShardPool.getWriters().size());
            Assert.assertTrue(indexShardPool.getWriters().containsKey(indexShardKey));

            // Get a writer from the pool.
            for (final IndexShardWriter writer : indexShardPool.getWriters().values()) {
                return ((MockIndexShardWriter) writer).getDocuments();
            }
        }

        return null;
    }
}
