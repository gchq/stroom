/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.docstore.impl;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.Store;
import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Behavioural tests asserting that {@link StoreImpl} keeps the dependency store current by extracting
 * a document's dependencies <b>directly from the in-hand object</b> (via the dependency remap function
 * it is given at construction) and calling {@link DocDependencyService#setDependencies} with what it
 * extracted, rather than re-reading the document back out of storage via the handler registry.
 * <p>
 * Uses an in-memory persistence and a real document type ({@link DictionaryDoc}) with a mocked
 * dependency service and a stub remap function that reports a single, fixed dependency, so we can
 * verify exactly which dependency operations each write path triggers and with what edges.
 */
class TestStoreImplDocDependencies {

    // The stub remap function reports this as the (only) dependency of every document, so we can
    // assert the store passes exactly what it extracted from the in-hand object.
    private static final DocRef DEP = new DocRef("Dictionary", "dep-uuid", "DepDict");

    private DocDependencyService docDependencyService;
    private Store<DictionaryDoc> store;

    @BeforeEach
    void setUp() {
        final SecurityContext securityContext = new MockSecurityContext();
        docDependencyService = mock(DocDependencyService.class);

        // The store is told how to extract deps at construction (like its serialiser).
        final Supplier<DependencyRemapFunction<DictionaryDoc>> mapperSupplier =
                () -> (doc, remapper) -> {
                    remapper.remap(DEP);
                    return doc;
                };

        // entityEventBus and docFinderProvider are not needed for these paths and are nullable
        // (StoreImpl tolerates a null event bus), matching how other lightweight store tests wire it.
        final StoreFactoryImpl storeFactory = new StoreFactoryImpl(
                new MemoryPersistence(),
                null,
                securityContext,
                null,
                () -> docDependencyService);

        store = storeFactory.createStore(
                new JsonSerialiser2<>(DictionaryDoc.class),
                DictionaryDoc.TYPE,
                DictionaryDoc::builder,
                DictionaryDoc::copy,
                mapperSupplier);
    }

    @Test
    void create_setsDependenciesOnce() {
        final DocRef docRef = store.createDocument("dict1");

        // Deps came straight from the extracted document, not a re-read.
        verify(docDependencyService, times(1)).setDependencies(docRefWithUuid(docRef.getUuid()), eq(Set.of(DEP)));
        // Nothing can reference a brand-new doc yet, so no name propagation and no removal.
        verify(docDependencyService, never()).propagateName(any());
        verify(docDependencyService, never()).removeDependencies(any());
        verifyNoMoreInteractions(docDependencyService);
    }

    @Test
    void write_setsDependenciesAndPropagatesNameOnce() {
        final DocRef docRef = store.createDocument("dict1");
        // Ignore the create's dependency call; we only care about the subsequent write.
        clearInvocations(docDependencyService);

        final DictionaryDoc doc = store.readDocument(docRef);
        store.writeDocument(doc);

        verify(docDependencyService, times(1)).setDependencies(docRefWithUuid(docRef.getUuid()), eq(Set.of(DEP)));
        verify(docDependencyService, times(1)).propagateName(docRefWithUuid(docRef.getUuid()));
        verify(docDependencyService, never()).removeDependencies(any());
        verifyNoMoreInteractions(docDependencyService);
    }

    @Test
    void rename_setsDependenciesAndPropagatesNameOnce() {
        final DocRef docRef = store.createDocument("dict1");
        clearInvocations(docDependencyService);

        store.renameDocument(docRef, "dict1-renamed");

        verify(docDependencyService, times(1)).setDependencies(docRefWithUuid(docRef.getUuid()), eq(Set.of(DEP)));
        verify(docDependencyService, times(1)).propagateName(docRefWithUuid(docRef.getUuid()));
        verify(docDependencyService, never()).removeDependencies(any());
        verifyNoMoreInteractions(docDependencyService);
    }

    @Test
    void delete_removesDependenciesOnce() {
        final DocRef docRef = store.createDocument("dict1");
        clearInvocations(docDependencyService);

        store.deleteDocument(docRef);

        verify(docDependencyService, times(1)).removeDependencies(docRefWithUuid(docRef.getUuid()));
        verify(docDependencyService, never()).setDependencies(any(), any());
        verify(docDependencyService, never()).propagateName(any());
        verifyNoMoreInteractions(docDependencyService);
    }

    @Test
    void import_setsDependenciesAndPropagatesNameOnce() throws java.io.IOException {
        final String uuid = UUID.randomUUID().toString();
        final DocRef docRef = new DocRef(DictionaryDoc.TYPE, uuid, "imported");
        final DictionaryDoc doc = DictionaryDoc.builder()
                .uuid(uuid)
                .name("imported")
                .version(UUID.randomUUID().toString())
                .stampAudit("user1")
                .data("some data")
                .build();
        final ImportExportDocument importData = new JsonSerialiser2<>(DictionaryDoc.class).write(doc);

        final ImportState importState = new ImportState(docRef, "imported");
        store.importDocument(docRef, importData, importState, ImportSettings.auto());

        verify(docDependencyService, times(1)).setDependencies(docRefWithUuid(uuid), eq(Set.of(DEP)));
        verify(docDependencyService, times(1)).propagateName(docRefWithUuid(uuid));
        verify(docDependencyService, never()).removeDependencies(any());
        verifyNoMoreInteractions(docDependencyService);
    }

    private static DocRef docRefWithUuid(final String uuid) {
        return org.mockito.ArgumentMatchers.argThat(ref -> ref != null && uuid.equals(ref.getUuid()));
    }
}
