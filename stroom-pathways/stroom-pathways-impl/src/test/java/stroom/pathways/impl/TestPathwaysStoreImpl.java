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
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.StoreFactory;
import stroom.pathways.shared.PathwaysDoc;
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
class TestPathwaysStoreImpl {

    private static final DocRef TRACES = new DocRef("PlanB", "traces-uuid", "Traces");
    private static final DocRef NEW_TRACES = new DocRef("PlanB", "new-traces-uuid", "New traces");
    private static final DocRef INFO_FEED = new DocRef("Feed", "feed-uuid", "INFO_FEED");

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private PathwaysSerialiser serialiser;
    @Mock
    private Provider<ClusterLockService> clusterLockServiceProvider;
    @Mock
    private Provider<TracesDocStore> tracesDocStoreProvider;

    private PathwaysStoreImpl pathwaysStore;

    @BeforeEach
    void setUp() {
        pathwaysStore = new PathwaysStoreImpl(
                storeFactory,
                securityContext,
                serialiser,
                clusterLockServiceProvider,
                tracesDocStoreProvider);
    }

    @Test
    void testDependencyRemapFunction_recordsTracesAndInfoFeed() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();

        pathwaysStore.getDependencyRemapFunction().remap(createDoc(INFO_FEED), dependencyRemapper);

        assertThat(dependencyRemapper.getDependencies())
                .containsExactlyInAnyOrder(INFO_FEED);
    }

    @Test
    void testDependencyRemapFunction_noReferences() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(Map.of(TRACES, NEW_TRACES));

        final PathwaysDoc remapped = pathwaysStore.getDependencyRemapFunction()
                .remap(createDoc(null), dependencyRemapper);

        assertThat(remapped.getInfoFeed())
                .isNull();
        assertThat(dependencyRemapper.getDependencies())
                .isEmpty();
    }

    private PathwaysDoc createDoc(final DocRef infoFeed) {
        return PathwaysDoc.builder()
                .uuid("pathways-uuid")
                .name("Pathways")
                .infoFeed(infoFeed)
                .build();
    }
}
