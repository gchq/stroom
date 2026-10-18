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
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.ResultPage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentInfoTool {

    private static final DocRef DICTIONARY = new DocRef(DictionaryDoc.TYPE, "dict-uuid", "Malicious IPs");
    private static final DocRef PIPELINE = new DocRef("Pipeline", "pipe-uuid", "Firewall");
    private static final DocRef SECRET = new DocRef("Pipeline", "secret-uuid", "Secret");
    private static final DocRef BASE = new DocRef(DictionaryDoc.TYPE, "base-uuid", "Base IPs");

    private final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final DocDependencyService docDependencyService = Mockito.mock(DocDependencyService.class);
    @SuppressWarnings("rawtypes")
    private final Map<DocumentTypeName, DocumentActionHandler> handlers = new HashMap<>();
    private final Set<DocRef> viewable = new HashSet<>();
    private final List<Dependency> dependencies = new ArrayList<>();
    private DocumentInfoTool tool;

    @BeforeEach
    void setUp() {
        final DocumentAccess documentAccess = new DocumentAccess(() -> explorerNodeService, securityContext,
                () -> handlers, auditLog);
        tool = new DocumentInfoTool(documentAccess, () -> docDependencyService);
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.eq(DocumentPermission.VIEW)))
                .thenAnswer(invocation -> viewable.stream()
                        .anyMatch(docRef -> docRef.getUuid().equals(((DocRef) invocation.getArgument(0)).getUuid())));
        Mockito.when(explorerNodeService.getNodeByUuid(DICTIONARY.getUuid()))
                .thenReturn(Optional.of(ExplorerNode.builder().docRef(DICTIONARY).addTag("security").build()));
        Mockito.when(explorerNodeService.getPath(DICTIONARY)).thenReturn(List.of(ExplorerConstants.SYSTEM_NODE,
                ExplorerNode.builder().type(ExplorerConstants.FOLDER_TYPE).uuid("f").name("Lookups").build()));
        Mockito.when(docDependencyService.fetchDependencies(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    // The service filters by the quick filter, as the dependencies screen's does.
                    final String filter = ((DependencyCriteria) invocation.getArgument(0)).getPartialName();
                    final boolean from = filter.startsWith(
                            DependencyCriteria.FIELD_DEF_FROM_UUID.getFilterQualifier() + ":=");
                    final String uuid = filter.substring(filter.indexOf(":=") + 2);
                    return ResultPage.createUnboundedList(dependencies.stream()
                            .filter(dependency -> uuid.equals(from
                                    ? dependency.getFrom().getUuid()
                                    : dependency.getTo().getUuid()))
                            .toList());
                });
        final DocumentActionHandler<?> handler = Mockito.mock(DocumentActionHandler.class);
        Mockito.doReturn(new DictionaryDoc(DICTIONARY.getUuid(), DICTIONARY.getName(), "v1", 0L, 86_400_000L,
                "admin", "jbloggs", null, "1.2.3.4", List.of(BASE))).when(handler).readDocument(DICTIONARY);
        handlers.put(new DocumentTypeName(DictionaryDoc.TYPE), handler);
    }

    @Test
    void describesADocumentAndItsLinks() {
        viewable.addAll(List.of(DICTIONARY, PIPELINE, BASE));
        dependencies.add(new Dependency(DICTIONARY, BASE, true));
        dependencies.add(new Dependency(PIPELINE, DICTIONARY, true));

        final String result = run();

        assertThat(result)
                .contains("Dictionary 'Malicious IPs'")
                .contains("UUID: dict-uuid")
                .contains("Path: /Lookups")
                .contains("Tags: security")
                .contains("Created: 1970-01-01T00:00:00.000Z by admin")
                .contains("Updated: 1970-01-02T00:00:00.000Z by jbloggs")
                .contains("Version: v1")
                .contains("Dependencies (documents it uses): 1\n- [Base IPs](?action=open-doc&docType=Dictionary"
                          + "&docUuid=base-uuid) (Dictionary)")
                .contains("Dependants (documents that use it): 1\n- [Firewall](?action=open-doc&docType=Pipeline"
                          + "&docUuid=pipe-uuid) (Pipeline)");
        Mockito.verify(auditLog).documentViewed(Mockito.eq(1), Mockito.eq(DICTIONARY), Mockito.anyString());
        final ArgumentCaptor<DependencyCriteria> criteria = ArgumentCaptor.forClass(DependencyCriteria.class);
        Mockito.verify(docDependencyService, Mockito.times(2)).fetchDependencies(criteria.capture(),
                Mockito.any());
        assertThat(criteria.getAllValues()).extracting(DependencyCriteria::getPartialName)
                .containsExactly("fromuuid:=dict-uuid", "touuid:=dict-uuid");
    }

    @Test
    void aDocumentTheUserCantViewIsRefusedWithoutSayingIfItExists() {
        // It exists, but the user can't view it.
        assertThat(run()).isEqualTo(DocumentAccess.NO_PERMISSION);
        // It doesn't exist.
        assertThat(tool.execute(request("{\"uuid\":\"no-such-uuid\"}"), 1)).isEqualTo(DocumentAccess.NO_PERMISSION);
        Mockito.verifyNoInteractions(auditLog, docDependencyService);
    }

    @Test
    void linksToDocumentsTheUserCantViewAreLeftOut() {
        viewable.addAll(List.of(DICTIONARY, PIPELINE));
        dependencies.add(new Dependency(PIPELINE, DICTIONARY, true));
        dependencies.add(new Dependency(SECRET, DICTIONARY, true));
        dependencies.add(new Dependency(DICTIONARY, BASE, true));

        final String result = run();

        assertThat(result)
                .contains("Firewall")
                .doesNotContain("Secret")
                .doesNotContain("secret-uuid")
                .doesNotContain("Base IPs")
                .contains("Dependencies (documents it uses): none you can see");
    }

    @Test
    void aLinkWhoseCheckFailsIsLeftOut() {
        viewable.add(DICTIONARY);
        dependencies.add(new Dependency(PIPELINE, DICTIONARY, true));
        Mockito.when(securityContext.hasDocumentPermission(PIPELINE, DocumentPermission.VIEW))
                .thenThrow(new RuntimeException("check failed"));

        assertThat(run()).doesNotContain("Firewall");
    }

    @Test
    void manyLinksAreCapped() {
        viewable.add(DICTIONARY);
        for (int i = 0; i < DocumentInfoTool.MAX_LINKS + 5; i++) {
            final DocRef user = new DocRef("Pipeline", "p" + i, String.format("P%03d", i));
            viewable.add(user);
            dependencies.add(new Dependency(user, DICTIONARY, true));
        }

        final String result = run();

        assertThat(result)
                .contains("Dependants (documents that use it): " + (DocumentInfoTool.MAX_LINKS + 5))
                .contains("P049")
                .doesNotContain("P050")
                .contains("… and 5 more");
    }

    @Test
    void linksThatCantBeListedAreSaidSo() {
        viewable.add(DICTIONARY);
        Mockito.doThrow(new RuntimeException("db down")).when(docDependencyService)
                .fetchDependencies(Mockito.any(), Mockito.any());

        assertThat(run()).contains("Dependencies (documents it uses): couldn't be listed.");
    }

    private String run() {
        return tool.execute(request("{\"uuid\":\"dict-uuid\"}"), 1);
    }

    private static ToolExecutionRequest request(final String arguments) {
        return ToolExecutionRequest.builder().name(DocumentInfoTool.NAME).arguments(arguments).build();
    }
}
