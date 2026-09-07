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

package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.pathways.shared.TracesDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PermissionException;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TestTracesDocLoader {

    private static final String DOC_UUID = "trace-doc-uuid";
    private static final String DOC_NAME = "My Traces";

    @Mock
    private SecurityContext securityContext;
    @Mock
    private DocumentActionHandler<TracesDoc> handler;

    private Map<DocumentTypeName, DocumentActionHandler> handlers;
    private TracesDocLoader loader;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handlers = new HashMap<>();
        handlers.put(new DocumentTypeName(TracesDoc.TYPE), handler);
        final Provider<Map<DocumentTypeName, DocumentActionHandler>> handlersProvider = () -> handlers;
        loader = new TracesDocLoader(handlersProvider, securityContext);
    }

    private static TracesDoc tracesDoc() {
        return TracesDoc.tracesBuilder()
                .uuid(DOC_UUID)
                .name(DOC_NAME)
                .build();
    }

    // The ref a caller supplies. Deliberately carries a different name to the stored document, so a
    // test can tell which of the two the permission check is applied to.
    private static DocRef requestedDocRef() {
        return DocRef.builder()
                .type(TracesDoc.TYPE)
                .uuid(DOC_UUID)
                .name("name supplied by the caller")
                .build();
    }

    @Test
    void nullDocRefReturnsNull() {
        assertThat(loader.getPlanBDoc(null)).isNull();
        verifyNoInteractions(securityContext, handler);
    }

    @Test
    void returnsDocumentWhenUserHasUsePermission() {
        final TracesDoc doc = tracesDoc();
        when(handler.readDocument(any())).thenReturn(doc);
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(true);

        assertThat(loader.getPlanBDoc(requestedDocRef())).isSameAs(doc);
    }

    @Test
    void checksUsePermissionAgainstTheLoadedDocument() {
        final TracesDoc doc = tracesDoc();
        when(handler.readDocument(any())).thenReturn(doc);
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(true);

        loader.getPlanBDoc(requestedDocRef());

        final ArgumentCaptor<DocRef> docRefCaptor = ArgumentCaptor.forClass(DocRef.class);
        final ArgumentCaptor<DocumentPermission> permissionCaptor =
                ArgumentCaptor.forClass(DocumentPermission.class);
        verify(securityContext).hasDocumentPermission(docRefCaptor.capture(), permissionCaptor.capture());

        assertThat(permissionCaptor.getValue()).isEqualTo(DocumentPermission.USE);
        // The stored document's ref, not the caller's — so a caller cannot name the document the
        // check is made against.
        assertThat(docRefCaptor.getValue()).isEqualTo(doc.asDocRef());
        assertThat(docRefCaptor.getValue().getName()).isEqualTo(DOC_NAME);
    }

    @Test
    void refusesWhenUserLacksUsePermission() {
        when(handler.readDocument(any())).thenReturn(tracesDoc());
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> loader.getPlanBDoc(requestedDocRef()))
                .isInstanceOf(PermissionException.class);
    }

    /**
     * The read is wrapped in a catch-all that reports failures as "Failed to read". A refusal must not
     * be caught by it, or an unauthorised caller is told the store is broken rather than forbidden.
     */
    @Test
    void refusalIsNotReportedAsAReadFailure() {
        when(handler.readDocument(any())).thenReturn(tracesDoc());
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> loader.getPlanBDoc(requestedDocRef()))
                .isInstanceOf(PermissionException.class)
                .hasMessageNotContaining("Failed to read");
    }

    @Test
    void missingDocumentReturnsNullWithoutCheckingPermission() {
        when(handler.readDocument(any())).thenReturn(null);

        assertThat(loader.getPlanBDoc(requestedDocRef())).isNull();
        verify(securityContext, never()).hasDocumentPermission(any(), any());
    }

    @Test
    void missingHandlerFailsWithoutCheckingPermission() {
        handlers.clear();

        assertThatThrownBy(() -> loader.getPlanBDoc(requestedDocRef()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to read TracesDoc");
        verify(securityContext, never()).hasDocumentPermission(any(), any());
    }

    @Test
    void readFailureIsWrappedWithoutCheckingPermission() {
        when(handler.readDocument(any())).thenThrow(new IllegalStateException("store is down"));

        assertThatThrownBy(() -> loader.getPlanBDoc(requestedDocRef()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to read TracesDoc")
                .hasRootCauseMessage("store is down");
        verify(securityContext, never()).hasDocumentPermission(any(), any());
    }

    /**
     * The type travels in the request body, so a caller can set it to anything. It must not be able to
     * pick a document of another type, nor reach a different lookup by naming one.
     */
    @Test
    void anotherDocumentTypeIsRejected() {
        final DocRef stateDocRef = DocRef.builder()
                .type("StateStore")
                .uuid("state-uuid")
                .name("Some State Store")
                .build();

        assertThatThrownBy(() -> loader.getPlanBDoc(stateDocRef))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(TracesDoc.TYPE);
        verifyNoInteractions(handler, securityContext);
    }

}
