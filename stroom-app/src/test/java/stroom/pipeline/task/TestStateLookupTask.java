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

package stroom.pipeline.task;


import stroom.data.shared.StreamTypeNames;
import stroom.data.store.mock.MockStore;
import stroom.docref.DocRef;
import stroom.meta.mock.MockMetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorResult;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.state.impl.ScyllaDbDocStore;
import stroom.state.impl.ScyllaDbUtil;
import stroom.state.impl.StateDocStore;
import stroom.state.shared.ScyllaDbDoc;
import stroom.state.shared.StateDoc;
import stroom.state.shared.StateType;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.CommonTranslationTestHelper;
import stroom.test.StoreCreationTool;
import stroom.test.common.ComparisonHelper;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestStateLookupTask extends AbstractProcessIntegrationTest {

    private static final int N3 = 3;
    private static final int N4 = 4;

    private static final String DIR = "TestStateLookupTask/";

    @Inject
    private MockMetaService metaService;
    @Inject
    private MockStore streamStore;
    @Inject
    private CommonTranslationTestHelper commonTranslationTestHelper;
    @Inject
    private ScyllaDbDocStore scyllaDbDocStore;
    @Inject
    private StateDocStore stateDocStore;
    @Inject
    private ProcessorFilterService processorFilterService;
    @Inject
    private StoreCreationTool storeCreationTool;

    @Test
    void test() throws IOException {
        // Load reference data and create processing pipelines.
        commonTranslationTestHelper.createReferenceFeeds();

        // Process reference data.
        process(3);

        // Alter the scylla db doc to create an isolated instance.
        assertThat(scyllaDbDocStore.list().size()).isOne();
        final ScyllaDbDoc scyllaDbDoc = scyllaDbDocStore.readDocument(scyllaDbDocStore.list().getFirst());

        scyllaDbDoc.setConnection(ScyllaDbUtil.getDefaultConnection());
        scyllaDbDocStore.writeDocument(scyllaDbDoc);

        createStateDoc(scyllaDbDoc, "hostname_to_location_map");
        createStateDoc(scyllaDbDoc, "hostname_to_ip_map");
        createStateDoc(scyllaDbDoc, "id_to_user_map");
        createStateDoc(scyllaDbDoc, "number_to_id");

        // Add reference data to state store.
        // Setup the pipeline.
        final DocRef pipelineRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, "571a3ca1-5f94-4dcb-8289-119a95d60360");
        // Setup the stream processor filter to process all reference data.
        final QueryData findStreamQueryData = QueryData.builder()
                .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                .expression(ExpressionOperator.builder()
                        .addTextTerm(MetaFields.TYPE,
                                ExpressionTerm.Condition.EQUALS,
                                StreamTypeNames.REFERENCE)
                        .build())
                .build();
        processorFilterService.create(
                CreateProcessFilterRequest
                        .builder()
                        .pipeline(pipelineRef)
                        .queryData(findStreamQueryData)
                        .priority(2)
                        .build());

        // Process reference data into state store.
        final List<ProcessorResult> refDataProcessResults = commonTranslationTestHelper.processAll();
        assertThat(refDataProcessResults).hasSize(3);

        // Add event data and processor filters.
        final List<PipelineReference> pipelineReferences = Collections.singletonList(PipelineDataUtil.createReference(
                "translationFilter",
                "pipelineReference",
                new DocRef(StateDoc.DOCUMENT_TYPE, UUID.randomUUID().toString()),
                null,
                null));
        commonTranslationTestHelper.setupStateProcess(
                CommonTranslationTestHelper.FEED_NAME,
                Collections.singletonList(CommonTranslationTestHelper.VALID_RESOURCE_NAME),
                pipelineReferences);

        // Process event data, decorating with state.
        process(1);

        final Path inputDir = StroomPipelineTestFileUtil.getTestResourcesDir().resolve(DIR);
        final Path outputDir = StroomPipelineTestFileUtil.getTestOutputDir().resolve(DIR);

        for (final Entry<Long, Meta> entry : metaService.getMetaMap().entrySet()) {
            final long streamId = entry.getKey();
            final Meta meta = entry.getValue();
            if (StreamTypeNames.EVENTS.equals(meta.getTypeName())) {
                final byte[] data = streamStore.getFileData().get(streamId).get(meta.getTypeName());

                // Write the actual XML out.
                final OutputStream os = StroomPipelineTestFileUtil.getOutputStream(outputDir,
                        "TestStateLookupTask.out");
                os.write(data);
                os.flush();
                os.close();

                ComparisonHelper.compareFiles(inputDir.resolve("TestStateLookupTask.out"),
                        outputDir.resolve("TestStateLookupTask.out"));
            }
        }

//        // Make sure 26 records were written.
//        assertThat(results.get(N3).getWritten())
//                .isEqualTo(26);
    }

    private void process(final int expectedProcessCount) {
        final List<ProcessorResult> results = commonTranslationTestHelper.processAll();
        assertThat(results).hasSize(expectedProcessCount);
        for (final ProcessorResult result : results) {
            assertThat(result.getWritten())
                    .as(result.toString())
                    .isGreaterThan(0);
            assertThat(result.getRead())
                    .as(result.toString())
                    .isLessThanOrEqualTo(result.getWritten());
            assertThat(result.getMarkerCount(Severity.SEVERITIES))
                    .as(result.toString())
                    .isZero();
        }
    }

    private void createStateDoc(final ScyllaDbDoc scyllaDbDoc, final String name) {
        final DocRef docRef = stateDocStore.createDocument(name);
        final StateDoc doc = stateDocStore.readDocument(docRef);
        doc.setScyllaDbRef(scyllaDbDoc.asDocRef());
        doc.setStateType(StateType.TEMPORAL_STATE);
        stateDocStore.writeDocument(doc);
    }
}
