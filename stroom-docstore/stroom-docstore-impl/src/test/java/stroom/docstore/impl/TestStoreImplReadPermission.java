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

import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.Embeddable;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestStoreImplReadPermission {

    private static final DocRef DOC_REF = new DocRef("TestType", "doc-uuid", "doc");

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);

    @Test
    void nonEmbeddableDocIsAuthorisedOnItsOwnDocRef() {
        // The core of TB-01: a non-embeddable document must be authorised by VIEW on its own DocRef, not
        // returned unchecked.
        final Object nonEmbeddable = new Object();

        Mockito.when(securityContext.hasDocumentPermission(DOC_REF, DocumentPermission.VIEW)).thenReturn(false);
        assertThat(StoreImpl.findUnauthorisedReadDocRef(securityContext, nonEmbeddable, DOC_REF))
                .contains(DOC_REF);

        Mockito.when(securityContext.hasDocumentPermission(DOC_REF, DocumentPermission.VIEW)).thenReturn(true);
        assertThat(StoreImpl.findUnauthorisedReadDocRef(securityContext, nonEmbeddable, DOC_REF))
                .isEmpty();
    }

    @Test
    void embeddedDocIsAuthorisedViaItsParent() {
        final DocRef parent = new DocRef("Pipeline", "parent-uuid", "pipeline");
        final Embeddable embedded = embeddedIn(parent);

        // The parent, not the embedded doc itself, governs authorisation.
        Mockito.when(securityContext.hasDocumentPermission(parent, DocumentPermission.VIEW)).thenReturn(false);
        assertThat(StoreImpl.findUnauthorisedReadDocRef(securityContext, embedded, DOC_REF))
                .contains(parent);

        Mockito.when(securityContext.hasDocumentPermission(parent, DocumentPermission.VIEW)).thenReturn(true);
        assertThat(StoreImpl.findUnauthorisedReadDocRef(securityContext, embedded, DOC_REF))
                .isEmpty();
    }

    @Test
    void embeddableWithNoParentIsAuthorisedOnItsOwnDocRef() {
        // An Embeddable that is not actually embedded (no parent) falls back to a VIEW check on itself.
        final Embeddable notEmbedded = embeddedIn(null);

        Mockito.when(securityContext.hasDocumentPermission(DOC_REF, DocumentPermission.VIEW)).thenReturn(false);
        assertThat(StoreImpl.findUnauthorisedReadDocRef(securityContext, notEmbedded, DOC_REF))
                .contains(DOC_REF);
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
