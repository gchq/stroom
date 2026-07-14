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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.DocDependencyService;
import stroom.explorer.api.ExplorerDecorator;
import stroom.explorer.api.ExplorerFavService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.Dependants;
import stroom.explorer.shared.ExplorerNode;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestExplorerServiceGetDependants {

    private static final DocRef DELETED = docRef("Feed", "TheFeed");
    private static final DocRef VISIBLE_DEPENDANT = docRef("Pipeline", "VisiblePipe");
    private static final DocRef HIDDEN_DEPENDANT = docRef("Pipeline", "SecretPipe");

    @Mock
    private ExplorerNodeService explorerNodeService;
    @Mock
    private ExplorerTreeModel explorerTreeModel;
    @Mock
    private ExplorerActionHandlers explorerActionHandlers;
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

        // By default a folder-expansion just returns the node itself.
        lenient().when(explorerNodeService.getDescendants(any()))
                .thenAnswer(invocation -> List.of(node(invocation.getArgument(0))));
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertThat(explorerService.getDependants(List.of())).isSameAs(Dependants.EMPTY);
    }

    @Test
    void noDependantsReturnsEmpty() {
        when(docDependencyService.getDependantsOf(DELETED)).thenReturn(Set.of());

        final Dependants dependants = explorerService.getDependants(List.of(DELETED));

        assertThat(dependants.isEmpty()).isTrue();
        assertThat(dependants.getVisibleDependants()).isEmpty();
        assertThat(dependants.isHasHiddenDependants()).isFalse();
    }

    @Test
    void visibleAndHiddenDependantsArePartitioned() {
        when(docDependencyService.getDependantsOf(DELETED))
                .thenReturn(Set.of(VISIBLE_DEPENDANT, HIDDEN_DEPENDANT));
        when(securityContext.hasDocumentPermission(VISIBLE_DEPENDANT, DocumentPermission.VIEW))
                .thenReturn(true);
        when(securityContext.hasDocumentPermission(HIDDEN_DEPENDANT, DocumentPermission.VIEW))
                .thenReturn(false);

        final Dependants dependants = explorerService.getDependants(List.of(DELETED));

        assertThat(dependants.getVisibleDependants()).containsExactly(VISIBLE_DEPENDANT);
        assertThat(dependants.isHasHiddenDependants()).isTrue();
        assertThat(dependants.isEmpty()).isFalse();
    }

    @Test
    void dependantsInsideTheDeleteSetAreExcluded() {
        final DocRef otherDeleted = docRef("XSLT", "AlsoGoing");
        // otherDeleted depends on DELETED, but is itself being deleted, so must not be reported.
        when(docDependencyService.getDependantsOf(DELETED)).thenReturn(Set.of(otherDeleted));
        when(docDependencyService.getDependantsOf(otherDeleted)).thenReturn(Set.of());

        final Dependants dependants = explorerService.getDependants(List.of(DELETED, otherDeleted));

        assertThat(dependants.isEmpty()).isTrue();
    }

    @Test
    void folderDescendantsAreIncludedInTheLookup() {
        final DocRef folder = docRef("Folder", "MyFolder");
        final DocRef child = docRef("Feed", "ChildFeed");
        // The folder expands to itself + its child.
        when(explorerNodeService.getDescendants(folder))
                .thenReturn(List.of(node(folder), node(child)));
        // Something outside the folder depends on the child.
        when(docDependencyService.getDependantsOf(folder)).thenReturn(Set.of());
        when(docDependencyService.getDependantsOf(child)).thenReturn(Set.of(VISIBLE_DEPENDANT));
        when(securityContext.hasDocumentPermission(VISIBLE_DEPENDANT, DocumentPermission.VIEW))
                .thenReturn(true);

        final Dependants dependants = explorerService.getDependants(List.of(folder));

        assertThat(dependants.getVisibleDependants()).containsExactly(VISIBLE_DEPENDANT);
    }

    @Test
    void permissionCheckFailureFailsOpenAsVisible() {
        when(docDependencyService.getDependantsOf(DELETED)).thenReturn(Set.of(VISIBLE_DEPENDANT));
        when(securityContext.hasDocumentPermission(VISIBLE_DEPENDANT, DocumentPermission.VIEW))
                .thenThrow(new RuntimeException("not a real document"));

        final Dependants dependants = explorerService.getDependants(List.of(DELETED));

        assertThat(dependants.getVisibleDependants()).containsExactly(VISIBLE_DEPENDANT);
        assertThat(dependants.isHasHiddenDependants()).isFalse();
    }

    private static DocRef docRef(final String type, final String name) {
        return DocRef.builder().randomUuid().type(type).name(name).build();
    }

    private static ExplorerNode node(final DocRef docRef) {
        return ExplorerNode.builder().docRef(docRef).build();
    }
}
