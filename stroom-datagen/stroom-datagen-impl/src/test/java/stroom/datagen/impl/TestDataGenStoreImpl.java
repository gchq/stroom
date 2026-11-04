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

package stroom.datagen.impl;

import stroom.datagen.shared.DataGenDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.StoreFactory;
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
class TestDataGenStoreImpl {

    private static final DocRef FEED = new DocRef("Feed", "feed-uuid", "FEED");
    private static final DocRef NEW_FEED = new DocRef("Feed", "new-feed-uuid", "NEW_FEED");

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private DataGenSerialiser serialiser;
    @Mock
    private Provider<DataGenProcessors> dataGenProcessorsProvider;

    private DataGenStoreImpl dataGenStore;

    @BeforeEach
    void setUp() {
        dataGenStore = new DataGenStoreImpl(storeFactory, securityContext, serialiser, dataGenProcessorsProvider);
    }

    @Test
    void testDependencyRemapFunction_recordsFeed() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();

        dataGenStore.getDependencyRemapFunction().remap(createDoc(FEED), dependencyRemapper);

        assertThat(dependencyRemapper.getDependencies())
                .containsExactly(FEED);
    }

    @Test
    void testDependencyRemapFunction_remapsFeed() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(Map.of(FEED, NEW_FEED));

        final DataGenDoc remapped = dataGenStore.getDependencyRemapFunction()
                .remap(createDoc(FEED), dependencyRemapper);

        assertThat(remapped.getFeed())
                .isEqualTo(NEW_FEED);
        assertThat(dependencyRemapper.isChanged())
                .isTrue();
    }

    @Test
    void testDependencyRemapFunction_noFeed() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(Map.of(FEED, NEW_FEED));

        final DataGenDoc remapped = dataGenStore.getDependencyRemapFunction()
                .remap(createDoc(null), dependencyRemapper);

        assertThat(remapped.getFeed())
                .isNull();
        assertThat(dependencyRemapper.getDependencies())
                .isEmpty();
    }

    private DataGenDoc createDoc(final DocRef feed) {
        return DataGenDoc.builder()
                .uuid("datagen-uuid")
                .name("Data generator")
                .feed(feed)
                .build();
    }
}
