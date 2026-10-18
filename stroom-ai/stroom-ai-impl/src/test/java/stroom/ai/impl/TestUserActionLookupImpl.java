/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.explorer.api.ExplorerNodeService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.processor.api.ProcessorFilterService;
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserActionLookupImpl {

    private static final String PARENT_UUID = "11111111-0000-0000-0000-000000000001";
    private static final String CHILD_UUID = "11111111-0000-0000-0000-000000000002";

    private final Map<String, PipelineDoc> pipelines = new HashMap<>();
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final UserActionLookupImpl lookup;

    @SuppressWarnings({"unchecked", "rawtypes"})
    TestUserActionLookupImpl() {
        final DocumentActionHandler handler = Mockito.mock(DocumentActionHandler.class);
        Mockito.when(handler.readDocument(Mockito.any(DocRef.class)))
                .thenAnswer(invocation -> pipelines.get(invocation.<DocRef>getArgument(0).getUuid()));
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.any())).thenReturn(true);
        lookup = new UserActionLookupImpl(Mockito.mock(ProcessorFilterService.class),
                Mockito.mock(ExplorerNodeService.class), securityContext,
                Map.of(new DocumentTypeName(PipelineDoc.TYPE), handler));
    }

    @Test
    void theIndexIsTheElasticIndexingFiltersIndexNameAsThePipelineSetsIt() {
        // As an "Events to Elasticsearch" template gives it: the filter is the parent's, its index the child's.
        pipelines.put(PARENT_UUID, PipelineDoc.builder()
                .uuid(PARENT_UUID)
                .name("Events to Elasticsearch")
                .pipelineData(new PipelineDataBuilder()
                        .addElement(PipelineDataUtil.createElement("elasticIndexingFilter", "ElasticIndexingFilter",
                                null, null))
                        .addElement(PipelineDataUtil.createElement("xsltFilter", "XSLTFilter", null, null))
                        .addProperty(PipelineDataUtil.createProperty("elasticIndexingFilter", "indexName",
                                "template-index"))
                        // Not an Elastic indexing filter's, so not an index.
                        .addProperty(PipelineDataUtil.createProperty("xsltFilter", "indexName", "not-an-index"))
                        .build())
                .build());
        pipelines.put(CHILD_UUID, PipelineDoc.builder()
                .uuid(CHILD_UUID)
                .name("TWITTER-Indexing-V1.0")
                .parentPipeline(new DocRef(PipelineDoc.TYPE, PARENT_UUID))
                .pipelineData(new PipelineDataBuilder()
                        .addProperty(PipelineDataUtil.createProperty("elasticIndexingFilter", "indexName",
                                " stroom-ecs-twitter-v1 "))
                        .build())
                .build());

        assertThat(lookup.elasticIndexNames(new DocRef(PipelineDoc.TYPE, CHILD_UUID)))
                .containsExactly("stroom-ecs-twitter-v1");
        assertThat(lookup.elasticIndexNames(new DocRef(PipelineDoc.TYPE, PARENT_UUID)))
                .containsExactly("template-index");
    }

    @Test
    void aPipelineTheUserMayNotSeeHasNoIndexes() {
        pipelines.put(CHILD_UUID, PipelineDoc.builder()
                .uuid(CHILD_UUID)
                .name("Hidden")
                .pipelineData(new PipelineDataBuilder()
                        .addElement(PipelineDataUtil.createElement("elasticIndexingFilter", "ElasticIndexingFilter",
                                null, null))
                        .addProperty(PipelineDataUtil.createProperty("elasticIndexingFilter", "indexName", "secret"))
                        .build())
                .build());
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.any())).thenReturn(false);

        assertThat(lookup.elasticIndexNames(new DocRef(PipelineDoc.TYPE, CHILD_UUID))).isEmpty();
        assertThat(lookup.elasticIndexNames(new DocRef(PipelineDoc.TYPE, "unknown"))).isEmpty();
    }
}
