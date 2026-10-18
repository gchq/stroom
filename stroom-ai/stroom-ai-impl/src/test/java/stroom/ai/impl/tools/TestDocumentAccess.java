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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiAuditLog;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentAccess {

    private static final DocRef XSLT = new DocRef("XSLT", "xslt-uuid", "Event translation");

    private final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    @SuppressWarnings("rawtypes")
    private final Map<DocumentTypeName, DocumentActionHandler> handlers = new HashMap<>();
    private final DocumentAccess documentAccess = new DocumentAccess(() -> explorerNodeService, securityContext,
            () -> handlers, auditLog);

    @Test
    void aDocumentTheUserCanViewIsFound() {
        nodeExists(XSLT);
        Mockito.when(securityContext.hasDocumentPermission(XSLT, DocumentPermission.VIEW)).thenReturn(true);

        assertThat(documentAccess.find(" xslt-uuid ")).map(ExplorerNode::getDocRef).contains(XSLT);
    }

    @Test
    void aDocumentTheUserCantViewIsTreatedAsMissing() {
        nodeExists(XSLT);
        Mockito.when(securityContext.hasDocumentPermission(XSLT, DocumentPermission.VIEW)).thenReturn(false);

        assertThat(documentAccess.find("xslt-uuid")).isEmpty();
        assertThat(documentAccess.find("missing-uuid")).isEmpty();
        assertThat(documentAccess.find(null)).isEmpty();
        assertThat(documentAccess.find(" ")).isEmpty();
    }

    @Test
    void theRootsAreNeverFound() {
        Mockito.when(explorerNodeService.getNodeByUuid(ExplorerConstants.SYSTEM_DOC_REF.getUuid()))
                .thenReturn(Optional.of(ExplorerConstants.SYSTEM_NODE));
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.any())).thenReturn(true);

        assertThat(documentAccess.find(ExplorerConstants.SYSTEM_DOC_REF.getUuid())).isEmpty();
    }

    @Test
    void checksThatFailAreFailedClosed() {
        Mockito.when(explorerNodeService.getNodeByUuid("xslt-uuid")).thenThrow(new RuntimeException("db down"));
        Mockito.when(securityContext.hasDocumentPermission(XSLT, DocumentPermission.VIEW))
                .thenThrow(new RuntimeException("no"));

        assertThat(documentAccess.find("xslt-uuid")).isEmpty();
        assertThat(documentAccess.canView(XSLT)).isFalse();
        assertThat(documentAccess.canView(null)).isFalse();
    }

    @Test
    void thePathLeavesOutTheSystemRoot() {
        assertThat(DocumentAccess.explorerPath(List.of(ExplorerConstants.SYSTEM_NODE,
                folder("Security"), folder("Lookups")))).isEqualTo("/Security/Lookups");
        assertThat(DocumentAccess.explorerPath(List.of(ExplorerConstants.SYSTEM_NODE))).isEqualTo("/");
        assertThat(DocumentAccess.explorerPath(null)).isEqualTo("/");
    }

    @Test
    void aDocumentIsReadThroughItsTypesHandler() {
        @SuppressWarnings("unchecked") final DocumentActionHandler<?> handler =
                Mockito.mock(DocumentActionHandler.class);
        Mockito.when(handler.readDocument(XSLT)).thenReturn(null);
        handlers.put(new DocumentTypeName("XSLT"), handler);

        assertThat(documentAccess.read(XSLT)).isEmpty();
        assertThat(documentAccess.read(new DocRef("Unknown", "u"))).isEmpty();
        Mockito.verify(handler).readDocument(XSLT);
    }

    @Test
    void viewsAreLogged() {
        documentAccess.logView(3, XSLT, "Read");

        Mockito.verify(auditLog).documentViewed(3, XSLT, "Read");
    }

    private void nodeExists(final DocRef docRef) {
        Mockito.when(explorerNodeService.getNodeByUuid(docRef.getUuid()))
                .thenReturn(Optional.of(ExplorerNode.builder().docRef(docRef).build()));
    }

    private static ExplorerNode folder(final String name) {
        return ExplorerNode.builder().type(ExplorerConstants.FOLDER_TYPE).uuid(name + "-uuid").name(name).build();
    }

    @Test
    void aDocumentOfAClassIsReadOnlyIfTheUserCanViewIt() {
        @SuppressWarnings("unchecked") final DocumentActionHandler<XsltDoc> handler =
                Mockito.mock(DocumentActionHandler.class);
        Mockito.when(handler.readDocument(XSLT)).thenReturn(XsltDoc.builder().uuid("xslt-uuid").data("x").build());
        handlers.put(new DocumentTypeName("XSLT"), handler);
        Mockito.when(securityContext.hasDocumentPermission(XSLT, DocumentPermission.VIEW)).thenReturn(true);

        assertThat(documentAccess.read(XSLT, XsltDoc.class)).map(XsltDoc::getData).contains("x");
        // Of another class than asked for.
        assertThat(documentAccess.read(XSLT, PipelineDoc.class)).isEmpty();

        Mockito.when(securityContext.hasDocumentPermission(XSLT, DocumentPermission.VIEW)).thenReturn(false);
        assertThat(documentAccess.read(XSLT, XsltDoc.class)).isEmpty();
    }

    @Test
    void aDocRefIsFoundOnlyIfTheUserCanViewIt() {
        nodeExists(XSLT);
        Mockito.when(securityContext.hasDocumentPermission(XSLT, DocumentPermission.VIEW)).thenReturn(true);
        assertThat(documentAccess.findDocRef("xslt-uuid")).contains(XSLT);

        Mockito.when(securityContext.hasDocumentPermission(XSLT, DocumentPermission.VIEW)).thenReturn(false);
        assertThat(documentAccess.findDocRef("xslt-uuid")).isEmpty();
    }

    @Test
    void viewDataIsCheckedFailingClosed() {
        Mockito.when(securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(true);
        assertThat(documentAccess.canViewData()).isTrue();

        Mockito.when(securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION))
                .thenThrow(new IllegalStateException("No user"));
        assertThat(documentAccess.canViewData()).isFalse();
    }
}
