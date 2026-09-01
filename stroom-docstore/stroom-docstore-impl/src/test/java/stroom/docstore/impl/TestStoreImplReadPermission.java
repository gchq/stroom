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

package stroom.docstore.impl;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.Embeddable;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which document a read is authorised against.
 *
 * <p>An embedded document — an XSLT or TextConverter that declares a parent pipeline via
 * {@link Embeddable#getEmbeddedIn()} — is authorised by VIEW on that <b>parent</b>; everything else by
 * VIEW on itself.
 *
 * <p>The rule lives in {@code AbstractDocumentStore}, alongside the check it belongs to, and this
 * exercises it there rather than through a copy of its own: a test asserting a copy that production
 * does not call is worse than no test, because it goes on passing after the live one changes.
 */
class TestStoreImplReadPermission {

    private static final DocRef DOC_REF = new DocRef("TestType", "doc-uuid", "doc");

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);

    /** Re-exposes the protected rule so it can be exercised directly. */
    private static class TestDocumentStore extends AbstractDocumentStore<DictionaryDoc> {

        TestDocumentStore(final StoreFactory storeFactory, final SecurityContext securityContext) {
            super(storeFactory,
                    securityContext,
                    new JsonSerialiser2<>(DictionaryDoc.class),
                    DictionaryDoc.TYPE,
                    DictionaryDoc::builder,
                    DictionaryDoc::copy);
        }

        void check(final Object document, final DocRef docRef) {
            checkReadPermission(document, docRef);
        }
    }

    private TestDocumentStore store() {
        return new TestDocumentStore(
                new StoreFactoryImpl(new MemoryPersistence(), null, securityContext, null, () -> null),
                securityContext);
    }

    @Test
    void nonEmbeddableDocIsAuthorisedOnItsOwnDocRef() {
        // A non-embeddable document must be authorised by VIEW on its own DocRef, not returned unchecked.
        final Object nonEmbeddable = new Object();

        Mockito.when(securityContext.hasDocumentPermission(DOC_REF, DocumentPermission.VIEW)).thenReturn(false);
        assertThatThrownBy(() -> store().check(nonEmbeddable, DOC_REF))
                .isInstanceOf(PermissionException.class);

        Mockito.when(securityContext.hasDocumentPermission(DOC_REF, DocumentPermission.VIEW)).thenReturn(true);
        assertThatCode(() -> store().check(nonEmbeddable, DOC_REF)).doesNotThrowAnyException();
    }

    @Test
    void embeddedDocIsAuthorisedViaItsParent() {
        final DocRef parent = new DocRef("Pipeline", "parent-uuid", "pipeline");
        final Embeddable embedded = embeddedIn(parent);

        // The parent, not the embedded doc itself, governs authorisation — including when the embedded
        // doc's own DocRef would have passed.
        Mockito.when(securityContext.hasDocumentPermission(DOC_REF, DocumentPermission.VIEW)).thenReturn(true);
        Mockito.when(securityContext.hasDocumentPermission(parent, DocumentPermission.VIEW)).thenReturn(false);
        assertThatThrownBy(() -> store().check(embedded, DOC_REF))
                .isInstanceOf(PermissionException.class);

        Mockito.when(securityContext.hasDocumentPermission(parent, DocumentPermission.VIEW)).thenReturn(true);
        assertThatCode(() -> store().check(embedded, DOC_REF)).doesNotThrowAnyException();
    }

    @Test
    void embeddableWithNoParentIsAuthorisedOnItsOwnDocRef() {
        // An Embeddable that is not actually embedded (no parent) falls back to a VIEW check on itself.
        final Embeddable notEmbedded = embeddedIn(null);

        Mockito.when(securityContext.hasDocumentPermission(DOC_REF, DocumentPermission.VIEW)).thenReturn(false);
        assertThatThrownBy(() -> store().check(notEmbedded, DOC_REF))
                .isInstanceOf(PermissionException.class);
    }

    private static Embeddable embeddedIn(final DocRef parent) {
        return new Embeddable() {
            private DocRef ref = parent;

            @Override
            public void setEmbeddedIn(final DocRef embeddedIn) {
                this.ref = embeddedIn;
            }

            @Override
            public DocRef getEmbeddedIn() {
                return ref;
            }
        };
    }
}
