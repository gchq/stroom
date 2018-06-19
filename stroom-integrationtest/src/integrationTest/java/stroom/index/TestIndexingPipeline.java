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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import stroom.docref.DocRef;
import stroom.guice.PipelineScopeRunnable;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexFields;
import stroom.index.shared.IndexShardKey;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.XsltStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.StreamHolder;
import stroom.data.meta.api.Stream;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestIndexingPipeline extends AbstractProcessIntegrationTest {
    private static final String PIPELINE = "TestIndexingPipeline/TestIndexingPipeline.Pipeline.data.xml";
    private static final String SAMPLE_INDEX_INPUT = "TestIndexingPipeline/TestIndexes.out";

    private static final String SAMPLE_INDEX_XSLT = "TestIndexingPipeline/Indexes.xsl";

    @Inject
    private XsltStore xsltStore;
    @Inject
    private IndexStore indexStore;
    @Inject
    private Provider<PipelineFactory> pipelineFactoryProvider;
    @Inject
    private Provider<ErrorReceiverProxy> errorReceiverProvider;
    @Inject
    private MockIndexShardWriterCache indexShardWriterCache;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private Provider<StreamHolder> streamHolderProvider;
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
    public void testSimple() {
        pipelineScopeRunnable.scopeRunnable(() -> {
            // Setup the XSLT.
            final DocRef xsltRef = xsltStore.createDocument("Indexing XSLT");
            final XsltDoc xsltDoc = xsltStore.readDocument(xsltRef);
            xsltDoc.setData(StreamUtil.streamToString(StroomPipelineTestFileUtil.getInputStream(SAMPLE_INDEX_XSLT)));
            xsltStore.writeDocument(xsltDoc);

            final List<IndexField> indexFields = IndexFields.createStreamIndexFields();
            // indexFields.add(IndexField.createIdField(IndexConstants.STREAM_ID));
            // indexFields.add(IndexField.createIdField(IndexConstants.EVENT_ID));
            indexFields.add(IndexField.createDateField("EventTime"));
            indexFields.add(IndexField.createField("UserId", AnalyzerType.KEYWORD));
            indexFields.add(IndexField.createField("Action"));
            indexFields.add(IndexField.createField("Generator"));
            indexFields.add(IndexField.createNumericField("DeviceLocationFloor"));
            indexFields.add(IndexField.createField("DeviceHostName"));
            indexFields.add(IndexField.createField("ProcessCommand"));

            // Setup the target index
            final DocRef indexRef = indexStore.createDocument("Test index");
            IndexDoc index = indexStore.readDocument(indexRef);
            index.setIndexFields(indexFields);
            index = indexStore.writeDocument(index);

            errorReceiverProvider.get().setErrorReceiver(new FatalErrorReceiver());

            // Set the stream for decoration purposes.
            final long id = (long) (Math.random() * 1000);

            final Stream stream = mock(Stream.class);
            when(stream.getId()).thenReturn(id);
            streamHolderProvider.get().setStream(stream);

            // Create the pipeline.
            final DocRef pipelineRef = PipelineTestUtil.createTestPipeline(pipelineStore, StroomPipelineTestFileUtil.getString(PIPELINE));
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
            pipelineDoc.getPipelineData().addProperty(PipelineDataUtil.createProperty("xsltFilter", "xslt", xsltRef));
            pipelineDoc.getPipelineData().addProperty(PipelineDataUtil.createProperty("indexingFilter", "index", indexRef));
            pipelineStore.writeDocument(pipelineDoc);

            // Create the parser.
            final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
            final Pipeline pipeline = pipelineFactoryProvider.get().create(pipelineData);

            final InputStream inputStream = StroomPipelineTestFileUtil.getInputStream(SAMPLE_INDEX_INPUT);
            pipeline.process(inputStream);

            // Make sure we only used one writer.
            Assert.assertEquals(1, indexShardWriterCache.getWriters().size());

            // Get the writer from the pool.
            final Map<IndexShardKey, IndexShardWriter> writers = indexShardWriterCache.getWriters();
            final MockIndexShardWriter writer = (MockIndexShardWriter) writers.values().iterator().next();

            // Check that we indexed 4 documents.
            Assert.assertEquals(4, writer.getDocuments().size());
            Assert.assertEquals("Authenticate", writer.getDocuments().get(0).getField("Action").stringValue());
            Assert.assertEquals("Process", writer.getDocuments().get(1).getField("Action").stringValue());
            Assert.assertEquals("Process", writer.getDocuments().get(2).getField("Action").stringValue());
            Assert.assertEquals("Process", writer.getDocuments().get(3).getField("Action").stringValue());

            for (int i = 0; i < 4; i++) {
                final String streamId = writer.getDocuments().get(i).getField("StreamId").stringValue();
                final String eventId = writer.getDocuments().get(i).getField("EventId").stringValue();
                final String userId = writer.getDocuments().get(i).getField("UserId").stringValue();

                System.out.println(streamId + ":" + eventId);

                Assert.assertEquals(String.valueOf(id), streamId);
                Assert.assertEquals(Integer.toString(i + 1), eventId);
                Assert.assertEquals("user" + (i + 1), userId);
            }

            // // Return the writer to the pool.
            // indexShardManager.returnObject(poolItem, true);
        });
    }
}
