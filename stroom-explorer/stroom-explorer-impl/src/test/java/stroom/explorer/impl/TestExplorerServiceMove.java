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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.DocDependencyService;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.api.ExplorerDecorator;
import stroom.explorer.api.ExplorerFavService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityEventBus;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestExplorerServiceMove {

    private static final DocRef PARENT = folder("parent");
    private static final DocRef CHILD = folder("child");
    private static final DocRef GRANDCHILD = folder("grandchild");
    private static final DocRef OTHER = folder("other");

    @Mock
    private ExplorerNodeService explorerNodeService;
    @Mock
    private ExplorerTreeModel explorerTreeModel;
    @Mock
    private ExplorerActionHandlers explorerActionHandlers;
    @Mock
    private ExplorerActionHandler folderHandler;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private ExplorerEventLog explorerEventLog;
    @Mock
    private Provider<ExplorerDecorator> explorerDecoratorProvider;
    @Mock
    private Provider<ExplorerFavService> explorerFavService;
    @Mock
    private EntityEventBus entityEventBus;
    @Mock
    private DocumentPermissionService documentPermissionService;
    @Mock
    private ContentIndex contentIndex;
    @Mock
    private ExpressionPredicateFactory expressionPredicateFactory;
    @Mock
    private DocDependencyService docDependencyService;

    private ExplorerServiceImpl explorerService;

    @BeforeEach
    void setUp() {
        explorerService = new ExplorerServiceImpl(
                explorerNodeService,
                explorerTreeModel,
                explorerActionHandlers,
                securityContext,
                explorerEventLog,
                explorerDecoratorProvider,
                explorerFavService,
                entityEventBus,
                documentPermissionService,
                contentIndex,
                expressionPredicateFactory,
                docDependencyService);

        when(explorerActionHandlers.getHandler(anyString())).thenReturn(folderHandler);
        when(folderHandler.moveDocument(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityContext.hasDocumentPermission(any(), any())).thenReturn(true);
        when(securityContext.hasDocumentCreatePermission(any(), anyString())).thenReturn(true);

        // parent > child > grandchild. getDescendants includes the folder itself.
        when(explorerNodeService.getDescendants(PARENT)).thenReturn(List.of(
                node(PARENT), node(CHILD), node(GRANDCHILD)));
        when(explorerNodeService.getDescendants(CHILD)).thenReturn(List.of(node(CHILD), node(GRANDCHILD)));
        when(explorerNodeService.getDescendants(OTHER)).thenReturn(List.of(node(OTHER)));
    }

    @Test
    void folderCanBeMovedIntoUnrelatedFolder() {
        final BulkActionResult result = explorerService.move(
                List.of(node(CHILD)), node(OTHER), PermissionInheritance.SOURCE);

        assertThat(result.getMessage()).isEmpty();
        verify(explorerNodeService).moveNode(CHILD, OTHER, PermissionInheritance.SOURCE);
    }

    @Test
    void folderCannotBeMovedIntoItsChild() {
        final BulkActionResult result = explorerService.move(
                List.of(node(PARENT)), node(CHILD), PermissionInheritance.SOURCE);

        assertThat(result.getMessage())
                .contains("Unable to move 'parent' into itself or one of its own sub-folders.");
        verify(folderHandler, never()).moveDocument(any());
        verify(explorerNodeService, never()).moveNode(any(), any(), any());
    }

    @Test
    void folderCannotBeMovedIntoDeeperDescendant() {
        final BulkActionResult result = explorerService.move(
                List.of(node(PARENT)), node(GRANDCHILD), PermissionInheritance.SOURCE);

        assertThat(result.getMessage()).contains("into itself or one of its own sub-folders");
        verify(explorerNodeService, never()).moveNode(any(), any(), any());
    }

    @Test
    void otherItemsStillMoveWhenOneIsRefused() {
        final BulkActionResult result = explorerService.move(
                List.of(node(PARENT), node(OTHER)), node(GRANDCHILD), PermissionInheritance.SOURCE);

        assertThat(result.getMessage()).contains("Unable to move 'parent'");
        assertThat(result.getMessage()).doesNotContain("'other'");
        verify(explorerNodeService, never()).moveNode(PARENT, GRANDCHILD, PermissionInheritance.SOURCE);
        verify(explorerNodeService).moveNode(OTHER, GRANDCHILD, PermissionInheritance.SOURCE);
    }

    @Test
    void documentIsNotCheckedForDescendants() {
        final DocRef pipeline = new DocRef("Pipeline", "pipe", "pipe");

        final BulkActionResult result = explorerService.move(
                List.of(node(pipeline)), node(OTHER), PermissionInheritance.SOURCE);

        assertThat(result.getMessage()).isEmpty();
        verify(explorerNodeService, never()).getDescendants(pipeline);
        verify(explorerNodeService).moveNode(pipeline, OTHER, PermissionInheritance.SOURCE);
    }

    private static DocRef folder(final String name) {
        return new DocRef(ExplorerConstants.FOLDER_TYPE, name, name);
    }

    private static ExplorerNode node(final DocRef docRef) {
        return ExplorerNode.builder()
                .docRef(docRef)
                .build();
    }
}
