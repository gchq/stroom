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

package stroom.planb.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.planb.shared.PlanBDocument;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestPlanBDocCacheImpl {

    private static final String DOC_TYPE = "PlanBDoc";
    private static final String OTHER_DOC_TYPE = "OtherDoc";
    private static final String DOC_NAME = "test-doc";

    @Mock
    private CacheManager cacheManager;
    @Mock
    private LoadingStroomCache<String, PlanBDocument> nameCache;
    @Mock
    private LoadingStroomCache<DocRef, PlanBDocument> docRefCache;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private DocFinder docFinder;
    @Mock
    private DocumentActionHandler<PlanBDocument> documentHandler;

    private PlanBDocCacheImpl cache;
    private PlanBDocument document;
    private DocRef docRef;
    private Function<String, PlanBDocument> nameLoader;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        final PlanBConfig config = new PlanBConfig();
        lenient().when(cacheManager.createLoadingCache(anyString(), any(Supplier.class), any(Function.class)))
                .thenAnswer(invocation -> {
                    final String cacheName = invocation.getArgument(0);
                    final Function<?, ?> loader = invocation.getArgument(2);
                    if (cacheName.contains("by name")) {
                        nameLoader = (Function<String, PlanBDocument>) loader;
                        return nameCache;
                    }
                    return docRefCache;
                });
        lenient().when(securityContext.asProcessingUserResult(any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());

        document = org.mockito.Mockito.mock(PlanBDocument.class);
        docRef = new DocRef(DOC_TYPE, "uuid", DOC_NAME);
        lenient().when(document.getType()).thenReturn(DOC_TYPE);
        lenient().when(document.getUuid()).thenReturn("uuid");
        lenient().when(document.getName()).thenReturn(DOC_NAME);
        lenient().when(document.asDocRef()).thenReturn(docRef);
        lenient().when(documentHandler.readDocument(docRef)).thenReturn(document);

        cache = new PlanBDocCacheImpl(
                cacheManager,
                securityContext,
                () -> config,
                docFinder,
                () -> Map.of(new DocumentTypeName(DOC_TYPE), documentHandler),
                Set.of(DOC_TYPE));
    }

    @Test
    void get_loadsDocumentByNameAndChecksUsePermission() {
        when(securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)).thenReturn(true);
        when(nameCache.get(DOC_NAME)).thenAnswer(ignored -> document);

        final PlanBDocument result = cache.get(DOC_NAME);

        assertThat(result).isSameAs(document);
        verify(securityContext).hasDocumentPermission(docRef, DocumentPermission.USE);
    }

    @Test
    void get_rejectsNullName() {
        assertThatThrownBy(() -> cache.get(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Null key supplied");
    }

    @Test
    void get_rejectsDocumentWithoutUsePermission() {
        when(nameCache.get(DOC_NAME)).thenReturn(document);
        when(securityContext.hasDocumentPermission(any(DocRef.class), any(DocumentPermission.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> cache.get(DOC_NAME))
                .isInstanceOf(stroom.util.shared.PermissionException.class);
    }

    @Test
    void nameLoader_returnsDocumentLoadedByItsHandler() {
        when(docFinder.findByName(DOC_TYPE, DOC_NAME)).thenReturn(List.of(docRef));
        when(securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)).thenReturn(true);

        when(nameCache.get(DOC_NAME)).thenAnswer(ignored -> nameLoader.apply(DOC_NAME));

        assertThat(cache.get(DOC_NAME)).isSameAs(document);
        verify(docFinder).findByName(DOC_TYPE, DOC_NAME);
        verify(documentHandler).readDocument(docRef);
    }

    @Test
    void nameLoader_throwsWhenNoDocumentIsFound() {
        when(docFinder.findByName(DOC_TYPE, DOC_NAME)).thenReturn(List.of());
        when(nameCache.get(DOC_NAME)).thenAnswer(ignored -> nameLoader.apply(DOC_NAME));

        assertThatThrownBy(() -> cache.get(DOC_NAME))
                .isInstanceOf(PlanBDocNotFoundException.class);
    }

    @Test
    void getAll_loadsDocumentsByDocRefAndIgnoresNonImportExportHandlers() {
        final DocumentActionHandler<?> handler = Mockito.mock(
                DocumentActionHandler.class,
                Mockito.withSettings().extraInterfaces(ImportExportActionHandler.class));
        final ImportExportActionHandler importExportActionHandler =
                (ImportExportActionHandler) handler;
        when(importExportActionHandler.listDocuments()).thenReturn(Set.of(docRef));
        when(docRefCache.get(docRef)).thenReturn(document);

        final PlanBDocCacheImpl cacheWithImportExportHandler = new PlanBDocCacheImpl(
                cacheManager,
                securityContext,
                PlanBConfig::new,
                docFinder,
                () -> Map.<DocumentTypeName, DocumentActionHandler>of(
                        new DocumentTypeName(DOC_TYPE), handler),
                Set.of(DOC_TYPE));

        assertThat(cacheWithImportExportHandler.getAll()).containsExactly(document);
        verify(importExportActionHandler).listDocuments();
        verify(docRefCache).get(docRef);
    }

    @Test
    void onChange_updateInvalidatesDocumentAndItsOldName() {
        final DocRef updatedDocRef = new DocRef(DOC_TYPE, "uuid", "new-name");
        final DocRef oldDocRef = new DocRef(DOC_TYPE, "uuid", "old-name");

        cache.onChange(new EntityEvent(updatedDocRef, oldDocRef, EntityAction.UPDATE));

        verify(docRefCache).invalidate(updatedDocRef);
        verify(nameCache).invalidate("new-name");
        verify(nameCache).invalidate("old-name");
    }

    @Test
    void onChange_eventForAnotherTypeDoesNothing() {
        cache.onChange(new EntityEvent(new DocRef(OTHER_DOC_TYPE, "uuid", DOC_NAME), EntityAction.DELETE));

        verifyNoInteractions(nameCache, docRefCache);
    }

    @Test
    void clear_clearsBothCaches() {
        cache.clear();

        verify(nameCache).clear();
        verify(docRefCache).clear();
    }
}
