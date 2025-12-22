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

package stroom.explorer.impl;

import stroom.cache.impl.CacheManagerImpl;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentActionHandlers;
import stroom.docstore.api.DocumentTypeName;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.shared.Document;
import stroom.util.shared.NullSafe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestDocRefInfoCache {

    public static final String TYPE_FOO = "foo";
    public static final String TYPE_BAR = "bar";
    public static final DocRef DOC_REF_1 = DocRef.builder()
            .randomUuid()
            .type(TYPE_FOO)
            .build();
    public static final DocRef DOC_REF_2 = DocRef.builder()
            .randomUuid()
            .type(TYPE_FOO)
            .build();
    public static final DocRef DOC_REF_3 = DocRef.builder()
            .randomUuid()
            .type(TYPE_BAR)
            .build();
    public static final DocRef DOC_REF_4 = DocRef.builder()
            .randomUuid()
            .type(TYPE_BAR)
            .build();

    public static final DocRef DOC_REF_FOLDER = DocRef.builder()
            .randomUuid()
            .type(ExplorerConstants.FOLDER_TYPE)
            .name("bongo")
            .build();

    final CacheManagerImpl cacheManager = new CacheManagerImpl();
    final SecurityContext securityContext = new MockSecurityContext();
    DocRefInfoCache docRefInfoCache;

    @Mock
    private ExplorerActionHandlers mockExplorerActionHandlers;

    @BeforeEach
    void setUp() {
        final DocumentActionHandlers documentActionHandlers = new DocumentActionHandlers(
                Map.of(
                        new DocumentTypeName(TYPE_FOO), new MyDocumentActionHandler(Set.of(DOC_REF_1, DOC_REF_2)),
                        new DocumentTypeName(TYPE_BAR), new MyDocumentActionHandler(Set.of(DOC_REF_3, DOC_REF_4))));

        docRefInfoCache = new DocRefInfoCache(
                cacheManager,
                ExplorerConfig::new,
                securityContext,
                () -> documentActionHandlers,
                mockExplorerActionHandlers);
    }

    @Test
    void testGet() {
        final DocRef docRef = DOC_REF_1;
        final Optional<DocRefInfo> docRefInfo = docRefInfoCache.get(docRef);

        assertThat(docRefInfo)
                .isNotEmpty();
        assertThat(docRefInfo.get().getDocRef())
                .isEqualTo(docRef);
        assertThat(docRefInfoCache.get(docRef))
                .isEqualTo(docRefInfoCache.get(docRef));
    }

    @Test
    void testGet_folder() {
        final DocRef docRef = DOC_REF_FOLDER;
        Mockito.when(mockExplorerActionHandlers.stream()).thenReturn(Stream.of(new MyFolderExplorerActionHandler()));
        Mockito.when(mockExplorerActionHandlers.getHandler(Mockito.anyString()))
                .thenReturn(new MyFolderExplorerActionHandler());
        final Optional<DocRefInfo> docRefInfo = docRefInfoCache.get(docRef);

        assertThat(docRefInfo)
                .isNotEmpty();
        assertThat(docRefInfo.get().getDocRef())
                .isEqualTo(docRef);
        assertThat(docRefInfoCache.get(docRef))
                .isEqualTo(docRefInfoCache.get(docRef));
    }

    @Test
    void testGet_noName() {
        final DocRef docRef = DOC_REF_1;
        final Optional<DocRefInfo> docRefInfo = docRefInfoCache.get(docRef.withoutName());

        assertThat(docRefInfo)
                .isNotEmpty();
        assertThat(docRefInfo.get().getDocRef())
                .isEqualTo(docRef);
    }

    private static DocRefInfo buildInfo(final DocRef docRef) {
        return NullSafe.get(
                docRef,
                docRef2 -> new DocRefInfo(
                        docRef2,
                        System.currentTimeMillis(),
                        System.currentTimeMillis(),
                        "user1",
                        "user1",
                        null));
    }


    // --------------------------------------------------------------------------------


    private static class MyDocumentActionHandler implements DocumentActionHandler {

        private final Map<DocRef, DocRef> docRefs = new HashMap<>();
        private final String type;

        public MyDocumentActionHandler(final Set<DocRef> docRefs) {
            docRefs.forEach(docRef -> this.docRefs.put(docRef, docRef));
            final Set<String> types = docRefs.stream()
                    .map(DocRef::getType)
                    .collect(Collectors.toSet());
            if (types.size() != 1) {
                throw new IllegalArgumentException("Expecting one type only");
            }
            this.type = types.iterator().next();
        }

        @Override
        public Document readDocument(final DocRef docRef) {
            return null;
        }

        @Override
        public Document writeDocument(final Document document) {
            return null;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public DocRefInfo info(final DocRef docRef) {
            return NullSafe.get(docRefs.get(docRef), TestDocRefInfoCache::buildInfo);
        }
    }


    // --------------------------------------------------------------------------------


    private static class MyFolderExplorerActionHandler implements ExplorerActionHandler {

        @Override
        public Set<DocRef> listDocuments() {
            return null;
        }

        @Override
        public List<DocRef> findByNames(final List<String> names, final boolean allowWildCards) {
            return null;
        }

        @Override
        public DocRef createDocument(final String name) {
            return null;
        }

        @Override
        public DocRef copyDocument(final DocRef docRef,
                                   final String name,
                                   final boolean makeNameUnique,
                                   final Set<String> existingNames) {
            return null;
        }

        @Override
        public DocRef moveDocument(final DocRef docRef) {
            return null;
        }

        @Override
        public DocRef renameDocument(final DocRef docRef, final String name) {
            return null;
        }

        @Override
        public void deleteDocument(final DocRef docRef) {

        }

        @Override
        public DocRefInfo info(final DocRef docRef) {
            return buildInfo(DocRef.builder()
                    .type(ExplorerConstants.FOLDER_TYPE)
                    .uuid(docRef.getUuid())
                    .build());
        }

        @Override
        public Map<DocRef, Set<DocRef>> getDependencies() {
            return null;
        }

        @Override
        public Set<DocRef> getDependencies(final DocRef docRef) {
            return null;
        }

        @Override
        public void remapDependencies(final DocRef docRef, final Map<DocRef, DocRef> remappings) {

        }

        @Override
        public String getType() {
            return ExplorerConstants.FOLDER_TYPE;
        }
    }
}
