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

package stroom.index.impl.db;

import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefDecorator;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.index.api.IndexVolumeGroupService;
import stroom.index.impl.IndexStore;
import stroom.index.mock.MockIndexVolumeGroupService;
import stroom.security.api.SecurityContext;
import stroom.test.common.util.db.DbTestModule;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestModule extends AbstractModule {

    static final String TEST_USER = "testUser";

    @Override
    protected void configure() {
        install(new DbTestModule());
        install(new MockDocRefInfoModule());

        // Create a test security context
        final SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getUserIdentityForAudit())
                .thenReturn(TEST_USER);
        bind(SecurityContext.class).toInstance(securityContext);
        bind(IndexVolumeGroupService.class).toInstance(new MockIndexVolumeGroupService());
        bind(IndexStore.class).toInstance(mock(IndexStore.class));
    }

    @Provides
    CollectionService collectionService() {
        return new CollectionService() {
            @Override
            public Set<DocRef> getChildren(final DocRef folder, final String type) {
                return null;
            }

            @Override
            public Set<DocRef> getDescendants(final DocRef folder, final String type) {
                return null;
            }
        };
    }

    @Provides
    WordListProvider wordListProvider() {
        return new WordListProvider() {

            @Override
            public List<DocRef> findByName(final String name) {
                return List.of();
            }

            @Override
            public Optional<DocRef> findByUuid(final String uuid) {
                return Optional.empty();
            }

            @Override
            public String getCombinedData(final DocRef dictionaryRef) {
                return null;
            }

            @Override
            public String[] getWords(final DocRef dictionaryRef) {
                return null;
            }

            @Override
            public WordList getCombinedWordList(final DocRef dictionaryRef,
                                                final DocRefDecorator docRefDecorator) {
                return null;
            }
        };
    }
}
