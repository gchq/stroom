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
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.ResultPage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import event.logging.EventAction;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// A small world of documents for the document, content and stream tool tests: documents by UUID, which of them
/// the user can view, what each holds, a workspace that keeps what is written to it in memory, and a mocked audit
/// log. No database is needed.
final class ToolTestFixture {

    final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    final StroomEventLoggingService eventLoggingService = Mockito.mock(StroomEventLoggingService.class);
    final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    /// What has been written to the workspace, by path.
    final Map<String, String> written = new HashMap<>();
    /// The documents, by UUID.
    final Map<String, DocRef> docRefs = new HashMap<>();
    /// What each document holds, by UUID.
    final Map<String, Object> documents = new HashMap<>();
    /// The UUIDs of the documents the user can view.
    final Set<String> visible = new HashSet<>();
    /// The UUIDs of the documents whose permission check fails.
    final Set<String> failing = new HashSet<>();
    /// The dependency graph's edges.
    final List<Dependency> edges = new ArrayList<>();
    /// The dependency graph, answering the quick filter queries the tools make.
    final DocDependencyService dependencyService = Mockito.mock(DocDependencyService.class);
    @SuppressWarnings("rawtypes")
    private final Map<DocumentTypeName, DocumentActionHandler> handlers = new HashMap<>();
    private AiWorkspaceConfig limits = new AiWorkspaceConfig();

    ToolTestFixture(final Path tempDir) {
        Mockito.when(securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(true);
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(DocRef.class),
                Mockito.eq(DocumentPermission.VIEW))).thenAnswer(invocation -> {
                    final DocRef docRef = invocation.getArgument(0);
                    if (failing.contains(docRef.getUuid())) {
                        throw new IllegalStateException("Unknown type");
                    }
                    return visible.contains(docRef.getUuid());
                });
        Mockito.when(explorerNodeService.getNodeByUuid(Mockito.anyString())).thenAnswer(invocation ->
                Optional.ofNullable(docRefs.get(invocation.<String>getArgument(0)))
                        .map(docRef -> ExplorerNode.builder().docRef(docRef).build()));
        Mockito.when(explorerNodeService.getPath(Mockito.any())).thenReturn(List.of(
                ExplorerNode.builder().docRef(new DocRef("Folder", "folder-uuid", "Lookups")).build()));
        Mockito.when(workspace.limits()).thenAnswer(invocation -> limits);
        Mockito.when(dependencyService.fetchDependencies(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            final DependencyCriteria criteria = invocation.getArgument(0);
            final List<Dependency> found = new ArrayList<>();
            for (final String term : criteria.getPartialName().split(" or ")) {
                final String[] parts = term.split(":=");
                final boolean to = DependencyCriteria.FIELD_DEF_TO_UUID.getFilterQualifier().equals(parts[0]);
                edges.stream()
                        .filter(edge -> (to
                                ? edge.getTo()
                                : edge.getFrom()).getUuid().equals(parts[1]))
                        .forEach(found::add);
            }
            return new ResultPage<>(found);
        });
        Mockito.when(workspace.tempFile()).thenAnswer(invocation -> Files.createTempFile(tempDir, "tool", ".tmp"));
        Mockito.when(workspace.write(Mockito.anyInt(), Mockito.any(WorkspacePath.class), Mockito.any(Path.class),
                Mockito.any())).thenAnswer(invocation -> {
                    final WorkspacePath path = invocation.getArgument(1);
                    written.put(path.path(), Files.readString(invocation.getArgument(2), StandardCharsets.UTF_8));
                    return 1;
                });
        Mockito.when(workspace.write(Mockito.anyInt(), Mockito.any(WorkspacePath.class), Mockito.anyString(),
                Mockito.any())).thenAnswer(invocation -> {
                    final WorkspacePath path = invocation.getArgument(1);
                    written.put(path.path(), invocation.getArgument(2));
                    return 1;
                });
    }

    /// @return The tools' access checks, over this world, logging to its audit log.
    DocumentAccess documentAccess() {
        return new DocumentAccess(() -> explorerNodeService, securityContext, () -> handlers, auditLog());
    }

    /// @return The audit log, logging to this world's mocked event logging.
    AiAuditLog auditLog() {
        return new AiAuditLog(() -> eventLoggingService);
    }

    /// Adds a document.
    ///
    /// @param docRef   The document.
    /// @param document What it holds, as its handler reads it, or null.
    /// @param canView  True if the user can view it.
    /// @return The document.
    @SuppressWarnings("unchecked")
    DocRef add(final DocRef docRef, final Object document, final boolean canView) {
        docRefs.put(docRef.getUuid(), docRef);
        if (document != null) {
            documents.put(docRef.getUuid(), document);
        }
        if (canView) {
            visible.add(docRef.getUuid());
        }
        handlers.computeIfAbsent(new DocumentTypeName(docRef.getType()), type -> {
            final DocumentActionHandler handler = Mockito.mock(DocumentActionHandler.class);
            Mockito.when(handler.readDocument(Mockito.any())).thenAnswer(invocation ->
                    documents.get(invocation.<DocRef>getArgument(0).getUuid()));
            return handler;
        });
        return docRef;
    }

    /// Adds an edge to the dependency graph.
    void link(final DocRef from, final DocRef to) {
        edges.add(new Dependency(from, to, true));
    }

    void setLimits(final AiWorkspaceConfig limits) {
        this.limits = limits;
    }

    /// @return The events logged, of their type.
    <T extends EventAction> List<T> logged(final Class<T> type) {
        final ArgumentCaptor<EventAction> captor = ArgumentCaptor.forClass(EventAction.class);
        Mockito.verify(eventLoggingService, Mockito.atLeast(0)).log(Mockito.anyString(), Mockito.anyString(),
                captor.capture());
        return captor.getAllValues().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    static ToolExecutionRequest request(final String name, final String arguments) {
        return ToolExecutionRequest.builder().id("1").name(name).arguments(arguments).build();
    }

    static String read(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
