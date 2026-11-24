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

package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.StoreFactory;
import stroom.index.api.IndexVolumeGroupService;
import stroom.index.shared.LuceneIndexDoc;
import stroom.security.api.SecurityContext;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestIndexStoreImpl {

    private static final DocRef PIPELINE = new DocRef("Pipeline", "pipeline-uuid", "Pipeline");
    private static final DocRef NEW_PIPELINE = new DocRef("Pipeline", "new-pipeline-uuid", "New pipeline");

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private IndexSerialiser serialiser;
    @Mock
    private Provider<IndexFieldService> indexFieldServiceProvider;
    @Mock
    private Provider<IndexVolumeGroupService> indexVolumeGroupServiceProvider;

    private IndexStoreImpl indexStore;

    @BeforeEach
    void setUp() {
        indexStore = new IndexStoreImpl(
                storeFactory,
                securityContext,
                serialiser,
                indexFieldServiceProvider,
                indexVolumeGroupServiceProvider);
    }

    @Test
    void testDependencyRemapFunction_recordsDefaultExtractionPipeline() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();

        indexStore.getDependencyRemapFunction().remap(createDoc(PIPELINE), dependencyRemapper);

        assertThat(dependencyRemapper.getDependencies())
                .containsExactly(PIPELINE);
    }

    @Test
    void testDependencyRemapFunction_remapsDefaultExtractionPipeline() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(Map.of(PIPELINE, NEW_PIPELINE));

        final LuceneIndexDoc remapped = indexStore.getDependencyRemapFunction()
                .remap(createDoc(PIPELINE), dependencyRemapper);

        assertThat(remapped.getDefaultExtractionPipeline())
                .isEqualTo(NEW_PIPELINE);
        assertThat(dependencyRemapper.isChanged())
                .isTrue();
    }

    @Test
    void testDependencyRemapFunction_noDefaultExtractionPipeline() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(Map.of(PIPELINE, NEW_PIPELINE));

        final LuceneIndexDoc remapped = indexStore.getDependencyRemapFunction()
                .remap(createDoc(null), dependencyRemapper);

        assertThat(remapped.getDefaultExtractionPipeline())
                .isNull();
        assertThat(dependencyRemapper.getDependencies())
                .isEmpty();
    }

    private LuceneIndexDoc createDoc(final DocRef defaultExtractionPipeline) {
        return LuceneIndexDoc.builder()
                .uuid("index-uuid")
                .name("Index")
                .defaultExtractionPipeline(defaultExtractionPipeline)
                .build();
    }
}
