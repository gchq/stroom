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

package stroom.search.solr;

import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.StoreFactory;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestSolrIndexStoreImpl {

    private static final DocRef PIPELINE = new DocRef("Pipeline", "pipeline-uuid", "Pipeline");
    private static final DocRef NEW_PIPELINE = new DocRef("Pipeline", "new-pipeline-uuid", "New pipeline");

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private SolrIndexClientCache solrIndexClientCache;
    @Mock
    private SolrIndexSerialiser serialiser;

    private SolrIndexStoreImpl solrIndexStore;

    @BeforeEach
    void setUp() {
        solrIndexStore = new SolrIndexStoreImpl(storeFactory, securityContext, solrIndexClientCache, serialiser);
    }

    @Test
    void testDependencyRemapFunction_recordsDefaultExtractionPipeline() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();

        solrIndexStore.getDependencyRemapFunction().remap(createDoc(PIPELINE), dependencyRemapper);

        assertThat(dependencyRemapper.getDependencies())
                .containsExactly(PIPELINE);
    }

    @Test
    void testDependencyRemapFunction_remapsDefaultExtractionPipeline() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(Map.of(PIPELINE, NEW_PIPELINE));

        final SolrIndexDoc remapped = solrIndexStore.getDependencyRemapFunction()
                .remap(createDoc(PIPELINE), dependencyRemapper);

        assertThat(remapped.getDefaultExtractionPipeline())
                .isEqualTo(NEW_PIPELINE);
        assertThat(dependencyRemapper.isChanged())
                .isTrue();
    }

    @Test
    void testDependencyRemapFunction_noDefaultExtractionPipeline() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(Map.of(PIPELINE, NEW_PIPELINE));

        final SolrIndexDoc remapped = solrIndexStore.getDependencyRemapFunction()
                .remap(createDoc(null), dependencyRemapper);

        assertThat(remapped.getDefaultExtractionPipeline())
                .isNull();
        assertThat(dependencyRemapper.getDependencies())
                .isEmpty();
    }

    private SolrIndexDoc createDoc(final DocRef defaultExtractionPipeline) {
        return SolrIndexDoc.builder()
                .uuid("index-uuid")
                .name("Index")
                .defaultExtractionPipeline(defaultExtractionPipeline)
                .build();
    }
}
