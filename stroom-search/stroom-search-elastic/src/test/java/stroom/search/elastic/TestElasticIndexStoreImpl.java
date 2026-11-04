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

package stroom.search.elastic;

import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.StoreFactory;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestElasticIndexStoreImpl {

    private static final DocRef CLUSTER = new DocRef("ElasticCluster", "cluster-uuid", "Cluster");
    private static final DocRef VECTOR_MODEL = new DocRef("OpenAIModel", "vector-model-uuid", "Vector model");
    private static final DocRef RERANK_MODEL = new DocRef("OpenAIModel", "rerank-model-uuid", "Rerank model");
    private static final DocRef PIPELINE = new DocRef("Pipeline", "pipeline-uuid", "Pipeline");
    private static final DocRef NEW_PIPELINE = new DocRef("Pipeline", "new-pipeline-uuid", "New pipeline");
    private static final DocRef NEW_CLUSTER = new DocRef("ElasticCluster", "new-cluster-uuid", "New cluster");

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private ElasticIndexService elasticIndexService;
    @Mock
    private ElasticIndexSerialiser serialiser;

    private ElasticIndexStoreImpl elasticIndexStore;

    @BeforeEach
    void setUp() {
        elasticIndexStore = new ElasticIndexStoreImpl(storeFactory, securityContext, elasticIndexService, serialiser);
    }

    @Test
    void testDependencyRemapFunction_recordsAllReferences() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();

        elasticIndexStore.getDependencyRemapFunction().remap(createDoc(CLUSTER, PIPELINE), dependencyRemapper);

        assertThat(dependencyRemapper.getDependencies())
                .containsExactlyInAnyOrder(CLUSTER, VECTOR_MODEL, RERANK_MODEL, PIPELINE);
    }

    @Test
    void testDependencyRemapFunction_remapsReferences() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(Map.of(
                CLUSTER, NEW_CLUSTER,
                PIPELINE, NEW_PIPELINE));

        final ElasticIndexDoc remapped = elasticIndexStore.getDependencyRemapFunction()
                .remap(createDoc(CLUSTER, PIPELINE), dependencyRemapper);

        assertThat(remapped.getClusterRef())
                .isEqualTo(NEW_CLUSTER);
        assertThat(remapped.getDefaultExtractionPipeline())
                .isEqualTo(NEW_PIPELINE);
        assertThat(remapped.getVectorGenerationModelRef())
                .isEqualTo(VECTOR_MODEL);
        assertThat(remapped.getRerankModelRef())
                .isEqualTo(RERANK_MODEL);
        assertThat(dependencyRemapper.isChanged())
                .isTrue();
    }

    @Test
    void testDependencyRemapFunction_noDefaultExtractionPipeline() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(Map.of(PIPELINE, NEW_PIPELINE));

        final ElasticIndexDoc remapped = elasticIndexStore.getDependencyRemapFunction()
                .remap(createDoc(CLUSTER, null), dependencyRemapper);

        assertThat(remapped.getDefaultExtractionPipeline())
                .isNull();
        assertThat(dependencyRemapper.getDependencies())
                .containsExactlyInAnyOrder(CLUSTER, VECTOR_MODEL, RERANK_MODEL);
    }

    private ElasticIndexDoc createDoc(final DocRef clusterRef, final DocRef defaultExtractionPipeline) {
        return ElasticIndexDoc.builder()
                .uuid("index-uuid")
                .name("Index")
                .clusterRef(clusterRef)
                .vectorGenerationModelRef(VECTOR_MODEL)
                .rerankModelRef(RERANK_MODEL)
                .defaultExtractionPipeline(defaultExtractionPipeline)
                .build();
    }
}
