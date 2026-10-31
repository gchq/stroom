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
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentDocumentationTool {

    private static final DocRef DICTIONARY = new DocRef(DictionaryDoc.TYPE, "dict-uuid", "Malicious IPs");

    private final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    @SuppressWarnings("rawtypes")
    private final Map<DocumentTypeName, DocumentActionHandler> handlers = new HashMap<>();
    private final DocumentActionHandler<?> handler = Mockito.mock(DocumentActionHandler.class);
    private DocumentDocumentationTool tool;

    @BeforeEach
    void setUp() {
        tool = new DocumentDocumentationTool(workspace, new DocumentAccess(() -> explorerNodeService,
                securityContext, () -> handlers, auditLog));
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(explorerNodeService.getNodeByUuid(DICTIONARY.getUuid()))
                .thenReturn(Optional.of(ExplorerNode.builder().docRef(DICTIONARY).build()));
        handlers.put(new DocumentTypeName(DictionaryDoc.TYPE), handler);
    }

    @Test
    void readsTheDocumentationTab() {
        canView(true);
        documentation("# Malicious IPs\nKept up to date by the SOC.");

        assertThat(run()).isEqualTo("Documentation of Dictionary 'Malicious IPs':\n\n"
                                    + "# Malicious IPs\nKept up to date by the SOC.");
        Mockito.verify(auditLog).documentViewed(Mockito.eq(ToolSession.chat(1)), Mockito.eq(DICTIONARY),
                Mockito.anyString());
    }

    @Test
    void theMappingKeptHiddenInTheDocumentationIsLeftOut() {
        // Found reviewing the tools: read_document left it out, this tool gave the model the hidden JSON.
        canView(true);
        documentation(KeptMapping.withMapping("# Malicious IPs\nKept up to date by the SOC.",
                KeptMapping.TRANSLATION, Map.of("mapping", Map.of("input", "data_splitter"))));

        assertThat(run()).isEqualTo("Documentation of Dictionary 'Malicious IPs':\n\n"
                                    + "# Malicious IPs\nKept up to date by the SOC.");
    }

    @Test
    void noDocumentationIsNone() {
        canView(true);
        documentation(" ");

        assertThat(run()).isEqualTo("none");
    }

    @Test
    void aDocumentTheUserCantViewIsRefusedWithoutSayingIfItExists() {
        canView(false);
        documentation("secret");

        assertThat(run()).isEqualTo(DocumentAccess.NO_PERMISSION);
        Mockito.verifyNoInteractions(auditLog, handler);
    }

    @Test
    void largeDocumentationIsSavedToTheWorkspace() {
        canView(true);
        final String text = "Line of documentation.\n".repeat(2_000);
        documentation(text);

        final String result = run();

        Mockito.verify(workspace).writeDocument(1, WorkspacePath.parse("/documents/dict-uuid/documentation.md"),
                text, DICTIONARY.getUuid());
        assertThat(result)
                .contains("too large to show in full")
                .contains("/documents/dict-uuid/documentation.md");
    }

    @Test
    void aDocumentationDocumentOfTheSameNameBesideItIsNamed() {
        // Seen: an indexing pipeline's tab, holding only the tools' hidden block, read as its documentation lost,
        // and its Documentation document rewritten three times over.
        canView(true);
        documentation("<!-- stroom-mcp agreed index template ... -->");
        final DocRef folder = new DocRef("Folder", "folder-uuid", "Indexing");
        final DocRef beside = new DocRef("Documentation", "doc-uuid", DICTIONARY.getName());
        Mockito.when(explorerNodeService.getParent(DICTIONARY))
                .thenReturn(Optional.of(ExplorerNode.builder().docRef(folder).build()));
        Mockito.when(explorerNodeService.getChildren(folder)).thenReturn(List.of(
                ExplorerNode.builder().docRef(DICTIONARY).build(), ExplorerNode.builder().docRef(beside).build()));
        Mockito.when(securityContext.hasDocumentPermission(beside, DocumentPermission.VIEW)).thenReturn(true);

        assertThat(run()).startsWith("Documentation of Dictionary 'Malicious IPs': Its documentation is the "
                                     + "Documentation document 'Malicious IPs' beside it (uuid doc-uuid): read it "
                                     + "with read_document.");
        documentation(" ");
        assertThat(run()).startsWith("none on its Documentation tab. Its documentation is the Documentation "
                                     + "document 'Malicious IPs'");
    }

    @Test
    void theDescriptionIsTakenFromTheDocumentsJson() {
        assertThat(DocumentDocumentationTool.description(new DictionaryDoc("u", "n", null, null, null, null, null,
                "Some *markdown*", null, List.of()))).contains("Some *markdown*");
        assertThat(DocumentDocumentationTool.description(Map.of("other", "x"))).isEmpty();
    }

    private void canView(final boolean view) {
        Mockito.when(securityContext.hasDocumentPermission(DICTIONARY, DocumentPermission.VIEW)).thenReturn(view);
    }

    private void documentation(final String description) {
        Mockito.doReturn(new DictionaryDoc(DICTIONARY.getUuid(), DICTIONARY.getName(), null, null, null, null, null,
                description, "1.2.3.4", List.of())).when(handler).readDocument(DICTIONARY);
    }

    private String run() {
        return tool.execute(new ToolCall(null, DocumentDocumentationTool.NAME, "{\"uuid\":\"dict-uuid\"}"),
                ToolCalls.inChat(1, workspace)).text();
    }
}
