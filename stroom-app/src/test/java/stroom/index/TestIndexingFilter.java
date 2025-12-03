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
import stroom.pipeline.PipelineStore;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.FeedHolder;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;
import stroom.search.extraction.FieldValue;
import stroom.task.api.SimpleTaskContext;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexingFilter extends AbstractProcessIntegrationTest {

    private static final String PIPELINE = "TestIndexingFilter/TestIndexingFilter.Pipeline.json";

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

    @BeforeEach
    @AfterEach
    void clear() {
        indexShardWriterCache.shutdown();
    }

    @Test
    void testSimpleDocuments() {
        final List<LuceneIndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(LuceneIndexField.createField("sid"));
        indexFields.add(LuceneIndexField.createField("sid2", AnalyzerType.ALPHA_NUMERIC, false, true, true, false));
        indexFields.add(LuceneIndexField
                .builder()
                .fldType(FieldType.LONG)
                .fldName("size")
                .analyzerType(AnalyzerType.KEYWORD)
                .indexed(false)
                .build());
        indexFields.add(LuceneIndexField.createDateField("eventTime"));

        final List<IndexDocument> documents = doTest("TestIndexDocumentFilter/SimpleDocuments.xml", indexFields);

        assertThat(documents.size()).isEqualTo(3);
        final IndexDocument doc = documents.getFirst();
        final List<FieldValue> list = getFields(doc, "sid2");
        assertThat(list.size()).isOne();
        final FieldValue fieldValue = list.getFirst();
        final IndexField field = fieldValue.field();


        // FIXME : BROKEN BY LUCENE553 SEGREGATION
//        assertThat(field.isStored()).isTrue();
//        assertThat(field.st.indexOptions().equals(IndexOptions.DOCS)).isTrue();
//        assertThat(doc.getField("sid2").fieldType().omitNorms()).isTrue();
//        assertThat(getField(doc, "sid2").fieldType().storeTermVectors()).isFalse();
//        assertThat(getField(doc, "sid2").fieldType().storeTermVectorPositions()).isFalse();
//        assertThat(getField(doc, "sid2").fieldType().storeTermVectorOffsets()).isFalse();
//        assertThat(getField(doc, "sid2").fieldType().storeTermVectorPayloads()).isFalse();

        assertThat(getFields(doc, "size").size()).isZero();

        assertThat(getValue(documents.get(1), "sid")).isEqualTo("someuser");
        assertThat(getValue(documents.get(2), "sid")).isEqualTo("someuser");
    }

    private List<FieldValue> getFields(final IndexDocument document, final String fieldName) {
        return document
                .getValues()
                .stream()
                .filter(fv -> fv.field().getFldName().equals(fieldName))
                .collect(Collectors.toList());
    }

    private String getValue(final IndexDocument document, final String fieldName) {
        final List<FieldValue> list = getFields(document, fieldName);
        assertThat(list.size()).isOne();
        return list.get(0).value().toString();
    }

    @Test
    void testDuplicateFields() {
        final List<LuceneIndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(LuceneIndexField.createField("sid"));
        indexFields.add(LuceneIndexField.createDateField("eventTime"));

        final List<IndexDocument> documents = doTest("TestIndexDocumentFilter/DuplicateFields.xml", indexFields);

        assertThat(documents.size()).isEqualTo(4);
        assertThat(getFields(documents.get(0), "sid").size()).isEqualTo(2);
        assertThat(getFields(documents.get(1), "sid").size()).isEqualTo(1);
        assertThat(getFields(documents.get(2), "sid").size()).isEqualTo(1);
        assertThat(getFields(documents.get(3), "sid").size()).isEqualTo(0);
    }

    @Test
    void testBlankDocuments() {
        final List<LuceneIndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(LuceneIndexField.createField("sid"));

        final List<IndexDocument> documents = doTest("TestIndexDocumentFilter/BlankDocuments.xml", indexFields);
        assertThat(documents).isNull();
    }

    @Test
    void testInvalidContent1() {
        final List<LuceneIndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(LuceneIndexField.createField("sid"));

        final List<IndexDocument> documents = doTest("TestIndexDocumentFilter/InvalidContent1.xml", indexFields);
        Assertions.assertThat(documents).isNull();
    }

    @Test
    void testInvalidContent2() {
        final List<LuceneIndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(LuceneIndexField.createField("sid"));

        final List<IndexDocument> documents = doTest("TestIndexDocumentFilter/InvalidContent2.xml", indexFields);
        assertThat(documents.size()).isEqualTo(1);
    }

    @Test
    void testComplexContent() {
        final List<LuceneIndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(LuceneIndexField.createField("f1", AnalyzerType.ALPHA_NUMERIC, false, true, true, true));
        indexFields.add(LuceneIndexField.createField("f2", AnalyzerType.ALPHA_NUMERIC, false, false, true, false));
        indexFields.add(LuceneIndexField.createDateField("d1"));
        indexFields.add(LuceneIndexField.createNumericField("n1"));
        indexFields.add(LuceneIndexField.createNumericField("n2"));

        final List<IndexDocument> documents = doTest("TestIndexDocumentFilter/ComplexContent.xml", indexFields);

        // FIXME : BROKEN BY LUCENE553 SEGREGATION
//        assertThat(documents.size()).isEqualTo(1);
//        final IndexableFieldType fieldType = documents.get(0).getField("f1").fieldType();
//        Assertions.assertThat(fieldType.stored()).isTrue();
//        Assertions.assertThat(fieldType.indexOptions().equals(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)).isTrue();
//        Assertions.assertThat(fieldType.tokenized()).isTrue();
//
//        assertThat(documents.get(0).getField("f2").fieldType().stored()).isFalse();
//
//        assertThat(((documents.get(0).getField("d1")).numericValue().longValue()))
//                .isEqualTo(DateUtil.parseUnknownString("2010-01-01T12:00:00.000Z"));

    }

    private List<IndexDocument> doTest(final String resourceName, final List<LuceneIndexField> indexFields) {
        return pipelineScopeRunnable.scopeResult(() -> {
            // Setup the index.
            final DocRef indexRef = indexStore.createDocument("Test index");
            LuceneIndexDoc index = indexStore.readDocument(indexRef);
            index.setFields(indexFields);
            index = indexStore.writeDocument(index);

            // Setup the error handler.
            final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
            errorReceiverProvider.get().setErrorReceiver(loggingErrorReceiver);

            // Create the pipeline.
            final String data = StroomPipelineTestFileUtil.getString(PIPELINE);
            final DocRef pipelineRef = PipelineTestUtil.createTestPipeline(pipelineStore, data);
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
            final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineDoc.getPipelineData());
            builder.addProperty(PipelineDataUtil.createProperty("indexingFilter",
                    "index",
                    indexRef));
            pipelineDoc.setPipelineData(builder.build());
            pipelineStore.writeDocument(pipelineDoc);

            // Create the parser.
            final PipelineData pipelineData = pipelineDataCache.get(pipelineDoc);
            final Pipeline pipeline = pipelineFactoryProvider.get().create(pipelineData, new SimpleTaskContext());

//            feedHolderProvider.get().setFeed(new Feed());

//            // Setup the meta data holder.
//            metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(
//            metaHolder, streamProcessorService, pipelineStore));

            // Set the input.
            final InputStream input = StroomPipelineTestFileUtil.getInputStream(resourceName);
            try {
                pipeline.process(input);
            } catch (final RuntimeException e) {
                // Ignore as some tests expect errors. These tests will get see that
                // nothing was written.
            }

            // Wrote anything ?
            if (!indexShardWriterCache.getWriters().isEmpty()) {
                assertThat(indexShardWriterCache.getWriters().size()).isEqualTo(1);

                // Get a writer from the pool.
                for (final IndexShardWriter writer : indexShardWriterCache.getWriters().values()) {
                    return ((MockIndexShardWriter) writer).getDocuments();
                }
            }

            return null;
        });
    }
}
