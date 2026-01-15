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
import stroom.index.impl.IndexDocument;
import stroom.index.impl.IndexFields;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexStore;
import stroom.index.mock.MockIndexShardWriter;
import stroom.index.mock.MockIndexShardWriterCache;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.meta.shared.Meta;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.xslt.XsltStore;
import stroom.query.api.datasource.AnalyzerType;
import stroom.search.extraction.FieldValue;
import stroom.task.api.SimpleTaskContext;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestIndexingPipeline extends AbstractProcessIntegrationTest {

    private static final String PIPELINE = "TestIndexingPipeline/TestIndexingPipeline.Pipeline.json";
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
    private Provider<MetaHolder> metaHolderProvider;
    @Inject
    private PipelineDataCache pipelineDataCache;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;

    @BeforeEach
    @AfterEach
    void clear() {
        indexShardWriterCache.shutdown();
    }

    @Test
    void testSimple() {
        pipelineScopeRunnable.scopeRunnable(() -> {
            // Setup the XSLT.
            final DocRef xsltRef = xsltStore.createDocument("Indexing XSLT");
            final XsltDoc xsltDoc = xsltStore.readDocument(xsltRef);
            xsltDoc.setData(StreamUtil.streamToString(StroomPipelineTestFileUtil.getInputStream(SAMPLE_INDEX_XSLT)));
            xsltStore.writeDocument(xsltDoc);

            final List<LuceneIndexField> indexFields = IndexFields.createStreamIndexFields();
            // indexFields.add(IndexField.createIdField(IndexConstants.ID));
            // indexFields.add(IndexField.createIdField(IndexConstants.EVENT_ID));
            indexFields.add(LuceneIndexField.createDateField("EventTime"));
            indexFields.add(LuceneIndexField.createField("UserId", AnalyzerType.KEYWORD));
            indexFields.add(LuceneIndexField.createField("Action"));
            indexFields.add(LuceneIndexField.createField("Generator"));
            indexFields.add(LuceneIndexField.createNumericField("DeviceLocationFloor"));
            indexFields.add(LuceneIndexField.createField("DeviceHostName"));
            indexFields.add(LuceneIndexField.createField("ProcessCommand"));

            // Setup the target index
            final DocRef indexRef = indexStore.createDocument("Test index");
            LuceneIndexDoc index = indexStore.readDocument(indexRef);
            index.setFields(indexFields);
            index = indexStore.writeDocument(index);

            errorReceiverProvider.get().setErrorReceiver(new FatalErrorReceiver());

            // Set the stream for decoration purposes.
            final long id = (long) (Math.random() * 1000);

            final Meta meta = mock(Meta.class);
            when(meta.getId()).thenReturn(id);
            metaHolderProvider.get().setMeta(meta);

            // Create the pipeline.
            final DocRef pipelineRef = PipelineTestUtil.createTestPipeline(pipelineStore,
                    StroomPipelineTestFileUtil.getString(PIPELINE));
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
            PipelineData pipelineData = pipelineDoc.getPipelineData();
            final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineData);
            builder.addProperty(PipelineDataUtil.createProperty("xsltFilter", "xslt", xsltRef));
            builder.addProperty(PipelineDataUtil.createProperty("indexingFilter", "index", indexRef));
            pipelineData = builder.build();
            pipelineDoc.setPipelineData(pipelineData);
            pipelineStore.writeDocument(pipelineDoc);

            // Create the parser.
            pipelineData = pipelineDataCache.get(pipelineDoc);
            final Pipeline pipeline = pipelineFactoryProvider.get().create(pipelineData, new SimpleTaskContext());

            final InputStream inputStream = StroomPipelineTestFileUtil.getInputStream(SAMPLE_INDEX_INPUT);
            pipeline.process(inputStream);

            // Make sure we only used one writer.
            assertThat(indexShardWriterCache.getWriters().size()).isEqualTo(1);

            // Get the writer from the pool.
            final Map<Long, IndexShardWriter> writers = indexShardWriterCache.getWriters();
            final MockIndexShardWriter writer = (MockIndexShardWriter) writers.values().iterator().next();

            // Check that we indexed 4 documents.
            assertThat(writer.getDocuments().size()).isEqualTo(4);
            checkField(writer.getDocuments().get(0), "Action", "Authenticate");
            checkField(writer.getDocuments().get(1), "Action", "Process");
            checkField(writer.getDocuments().get(2), "Action", "Process");
            checkField(writer.getDocuments().get(3), "Action", "Process");

            for (int i = 0; i < 4; i++) {
                final String streamId = getValue(writer.getDocuments().get(i), "StreamId");
                final String eventId = getValue(writer.getDocuments().get(i), "EventId");
                final String userId = getValue(writer.getDocuments().get(i), "UserId");

                System.out.println(streamId + ":" + eventId);

                assertThat(streamId).isEqualTo(String.valueOf(id));
                assertThat(eventId).isEqualTo(Integer.toString(i + 1));
                assertThat(userId).isEqualTo("user" + (i + 1));
            }

            // // Return the writer to the pool.
            // indexShardManager.returnObject(poolItem, true);
        });
    }

    private String getValue(final IndexDocument document, final String fieldName) {
        final Optional<FieldValue> opt = document
                .getValues()
                .stream()
                .filter(fv -> fv.field().getFldName().equals(fieldName))
                .findFirst();
        assertThat(opt).isPresent();
        return opt.get().value().toString();
    }

    private void checkField(final IndexDocument document, final String fieldName, final String expectedValue) {
        assertThat(getValue(document, fieldName)).isEqualTo(expectedValue);
    }
}
