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

package stroom.sqlstore.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link SqlTemporalStoreDocStoreImpl} no longer imposes its own name rules.
 *
 * <p>It used to override create, copy and rename to reject any name already used by another
 * {@code SqlTemporalStoreDoc}, because the name was the storage key. That guard was removed for
 * two reasons, and these tests pin both:</p>
 *
 * <ul>
 *   <li><strong>It leaked.</strong> The check called {@link Store#list()}, which applies no
 *       permission filtering, and then named the clash in the error - letting any user with
 *       create rights probe for stores anywhere in the tree they could not see.
 *       {@link #testNoOperationEnumeratesEveryDocument()} is the regression test.</li>
 *   <li><strong>It is unnecessary.</strong> Storage is keyed on the document UUID, so two
 *       documents may share a name without sharing data and a rename keeps its data.</li>
 * </ul>
 *
 * <p>Name handling now falls through to {@code AbstractDocumentStore}, which uses the explorer's
 * permission-filtered, folder-scoped candidate names and the same {@code UniqueNameUtil}
 * convention as every other document type.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestSqlTemporalStoreDocStoreImpl {

    @Mock
    private SqlTemporalStoreSerialiser mockSerialiser;
    @Mock
    private Store<SqlTemporalStoreDoc> mockStore;
    @Mock
    private StoreFactory mockStoreFactory;
    @Mock
    private SecurityContext mockSecurityContext;

    /**
     * The point of the whole change: no operation may enumerate every document in the system.
     * That listing is what disclosed the existence of stores the caller had no right to see.
     */
    @Test
    void testNoOperationEnumeratesEveryDocument() {
        final SqlTemporalStoreDocStoreImpl store = getStore();
        when(mockSecurityContext.hasDocumentPermission(any(), any())).thenReturn(true);

        store.createDocument("aName");
        store.copyDocument(docRef("uuid-src", "source"), "aName", false, Set.of());
        store.renameDocument(docRef("uuid-self", "before"), "aName");

        verify(mockStore, never()).list();
    }

    /**
     * A name already used by another store is no longer refused - names need not be unique now
     * that rows are keyed on the document UUID.
     */
    @Test
    void testCreateDocumentWithAnAlreadyUsedNameIsAllowed() {
        final DocRef created = docRef("uuid-new", "dupName");
        when(mockStore.createDocument("dupName")).thenReturn(created);

        assertThat(getStore().createDocument("dupName")).isEqualTo(created);
    }

    @Test
    void testRenameToANameUsedByAnotherStoreIsAllowed() {
        final DocRef self = docRef("uuid-self", "before");
        when(mockSecurityContext.hasDocumentPermission(any(), any())).thenReturn(true);
        when(mockStore.renameDocument(self, "nameUsedElsewhere")).thenReturn(self);

        assertThat(getStore().renameDocument(self, "nameUsedElsewhere")).isEqualTo(self);
    }

    /**
     * Copy is authorised by VIEW on the source. The removed override called the backing store
     * directly and so skipped this check altogether; delegating to the base class restores it.
     */
    @Test
    void testCopyDocumentChecksViewPermissionOnTheSource() {
        final DocRef source = docRef("uuid-src", "source");
        when(mockSecurityContext.hasDocumentPermission(any(), any())).thenReturn(true);
        when(mockStore.copyDocument(eq("uuid-src"), any())).thenReturn(docRef("uuid-copy", "copy"));

        getStore().copyDocument(source, "copy", false, Set.of());

        verify(mockSecurityContext).hasDocumentPermission(source, DocumentPermission.VIEW);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SqlTemporalStoreDocStoreImpl getStore() {
        when(mockStoreFactory.<SqlTemporalStoreDoc, SqlTemporalStoreDoc.Builder>createStore(
                any(), eq(SqlTemporalStoreDoc.TYPE), any(), any(), any()))
                .thenReturn(mockStore);
        return new SqlTemporalStoreDocStoreImpl(
                mockStoreFactory, mockSecurityContext, mockSerialiser);
    }

    private static DocRef docRef(final String uuid, final String name) {
        return DocRef.builder()
                .type(SqlTemporalStoreDoc.TYPE)
                .uuid(uuid)
                .name(name)
                .build();
    }
}
