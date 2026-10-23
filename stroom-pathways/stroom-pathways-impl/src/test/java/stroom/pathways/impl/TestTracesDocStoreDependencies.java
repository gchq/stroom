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

package stroom.pathways.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracesDoc;
import stroom.security.api.SecurityContext;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * The function {@link TracesDocStoreImpl} hands to the docstore is what puts its reference to a
 * Pathways document into {@code doc_dependency}, so the Dependencies screen, rename propagation,
 * import remapping and the broken-dependency report all rest on it. It is captured from the
 * {@link StoreFactory} call rather than reached through the store, because the store itself is a
 * mock here.
 */
class TestTracesDocStoreDependencies {

    private static final DocRef OLD_PATHWAYS = DocRef.builder()
            .type(PathwaysDoc.TYPE)
            .uuid("11111111-1111-1111-1111-111111111111")
            .name("Old Pathways")
            .build();
    private static final DocRef NEW_PATHWAYS = DocRef.builder()
            .type(PathwaysDoc.TYPE)
            .uuid("22222222-2222-2222-2222-222222222222")
            .name("New Pathways")
            .build();

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private Store<TracesDoc> store;
    @Mock
    private TracesDocSerialiser serialiser;
    @Mock
    private ClusterLockService clusterLockService;
    @Mock
    private SecurityContext securityContext;

    private DependencyRemapFunction<TracesDoc> remapFunction;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MockitoAnnotations.openMocks(this);
        doReturn(store).when(storeFactory).createStore(any(), any(), any(), any(), any());
        final Provider<ClusterLockService> lockServiceProvider = () -> clusterLockService;
        new TracesDocStoreImpl(storeFactory, securityContext, serialiser, lockServiceProvider);

        final ArgumentCaptor<Supplier<DependencyRemapFunction<TracesDoc>>> captor =
                ArgumentCaptor.forClass(Supplier.class);
        verify(storeFactory).createStore(any(), any(), any(), any(), captor.capture());
        remapFunction = captor.getValue().get();
    }

    @Test
    void thePathwaysReferenceIsRecordedAsADependency() {
        final DependencyRemapper remapper = new DependencyRemapper();

        remapFunction.remap(doc(OLD_PATHWAYS), remapper);

        assertThat(remapper.getDependencies()).containsExactly(OLD_PATHWAYS);
    }

    @Test
    void thePathwaysReferenceIsRewrittenOnImport() {
        final DependencyRemapper remapper = new DependencyRemapper(Map.of(OLD_PATHWAYS, NEW_PATHWAYS));

        final TracesDoc result = remapFunction.remap(doc(OLD_PATHWAYS), remapper);

        assertThat(result.getPathwaysDocRef()).isEqualTo(NEW_PATHWAYS);
        assertThat(remapper.isChanged()).isTrue();
    }

    @Test
    void aDocumentWithNoPathwaysReferenceHasNoDependency() {
        final DependencyRemapper remapper = new DependencyRemapper();

        final TracesDoc result = remapFunction.remap(doc(null), remapper);

        assertThat(result.getPathwaysDocRef()).isNull();
        assertThat(remapper.getDependencies()).isEmpty();
    }

    private static TracesDoc doc(final DocRef pathwaysDocRef) {
        return TracesDoc.tracesBuilder()
                .uuid("33333333-3333-3333-3333-333333333333")
                .name("test_traces")
                .pathwaysDocRef(pathwaysDocRef)
                .build();
    }
}
