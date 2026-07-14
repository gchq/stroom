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
import stroom.explorer.shared.DeleteConfirmation;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestExplorerServiceGetDeleteConfirmation {

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

        // By default a folder-expansion just returns the node itself, and nothing depends on anything.
        lenient().when(explorerNodeService.getDescendants(any()))
                .thenAnswer(invocation -> List.of(node(invocation.getArgument(0))));
        lenient().when(docDependencyService.getDependantsOf(any())).thenReturn(Set.of());
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertThat(explorerService.getDeleteConfirmation(List.of())).isSameAs(DeleteConfirmation.EMPTY);
    }

    @Test
    void singleLeafWithNothingElseIsEmpty() {
        final DeleteConfirmation info = explorerService.getDeleteConfirmation(List.of(DELETED));

        assertThat(info.isEmpty()).isTrue();
        assertThat(info.hasChildItems()).isFalse();
        assertThat(info.hasDependants()).isFalse();
    }

    // --- dependants ---

    @Test
    void visibleAndHiddenDependantsArePartitioned() {
        when(docDependencyService.getDependantsOf(DELETED))
                .thenReturn(Set.of(VISIBLE_DEPENDANT, HIDDEN_DEPENDANT));
        when(securityContext.hasDocumentPermission(VISIBLE_DEPENDANT, DocumentPermission.VIEW))
                .thenReturn(true);
        when(securityContext.hasDocumentPermission(HIDDEN_DEPENDANT, DocumentPermission.VIEW))
                .thenReturn(false);

        final DeleteConfirmation info = explorerService.getDeleteConfirmation(List.of(DELETED));

        assertThat(info.getVisibleDependants()).containsExactly(VISIBLE_DEPENDANT);
        assertThat(info.isHasHiddenDependants()).isTrue();
        assertThat(info.isEmpty()).isFalse();
    }

    @Test
    void dependantsInsideTheDeleteSetAreExcluded() {
        final DocRef otherDeleted = docRef("XSLT", "AlsoGoing");
        // otherDeleted depends on DELETED, but is itself being deleted, so must not be reported.
        when(docDependencyService.getDependantsOf(DELETED)).thenReturn(Set.of(otherDeleted));

        final DeleteConfirmation info = explorerService.getDeleteConfirmation(List.of(DELETED, otherDeleted));

        assertThat(info.hasDependants()).isFalse();
    }

    @Test
    void permissionCheckFailureFailsOpenAsVisibleDependant() {
        when(docDependencyService.getDependantsOf(DELETED)).thenReturn(Set.of(VISIBLE_DEPENDANT));
        when(securityContext.hasDocumentPermission(VISIBLE_DEPENDANT, DocumentPermission.VIEW))
                .thenThrow(new RuntimeException("not a real document"));

        final DeleteConfirmation info = explorerService.getDeleteConfirmation(List.of(DELETED));

        assertThat(info.getVisibleDependants()).containsExactly(VISIBLE_DEPENDANT);
        assertThat(info.isHasHiddenDependants()).isFalse();
    }

    // --- folder contents ---

    @Test
    void folderContentsAreReportedAndDependantsFound() {
        final DocRef folder = docRef("Folder", "MyFolder");
        final DocRef child = docRef("Feed", "ChildFeed");
        // The folder expands to itself + its child.
        when(explorerNodeService.getDescendants(folder)).thenReturn(List.of(node(folder), node(child)));
        when(securityContext.hasDocumentPermission(child, DocumentPermission.VIEW)).thenReturn(true);
        // Something outside the folder depends on the child.
        when(docDependencyService.getDependantsOf(child)).thenReturn(Set.of(VISIBLE_DEPENDANT));
        when(securityContext.hasDocumentPermission(VISIBLE_DEPENDANT, DocumentPermission.VIEW))
                .thenReturn(true);

        final DeleteConfirmation info = explorerService.getDeleteConfirmation(List.of(folder));

        // The child is a contained item; the folder itself (selected) is not listed as a child.
        assertThat(info.getChildItems()).containsExactly(child);
        assertThat(info.getTotalChildCount()).isEqualTo(1);
        assertThat(info.getChildTypeCounts()).containsExactly(entry("Feed", 1));
        assertThat(info.isHasHiddenChildItems()).isFalse();
        assertThat(info.isChildItemsTruncated()).isFalse();
        assertThat(info.getVisibleDependants()).containsExactly(VISIBLE_DEPENDANT);
    }

    @Test
    void selectedRootsAreNotCountedAsContainedItems() {
        final DocRef folder = docRef("Folder", "MyFolder");
        final DocRef child = docRef("Feed", "ChildFeed");
        // Both the folder and its child are explicitly selected.
        when(explorerNodeService.getDescendants(folder)).thenReturn(List.of(node(folder), node(child)));
        when(explorerNodeService.getDescendants(child)).thenReturn(List.of(node(child)));

        final DeleteConfirmation info = explorerService.getDeleteConfirmation(List.of(folder, child));

        // Everything in the delete set was explicitly selected, so there are no "surprise" contents.
        assertThat(info.hasChildItems()).isFalse();
        assertThat(info.getTotalChildCount()).isZero();
    }

    @Test
    void hiddenContainedItemsAreFlaggedNotListed() {
        final DocRef folder = docRef("Folder", "MyFolder");
        final DocRef visibleChild = docRef("Feed", "VisibleChild");
        final DocRef hiddenChild = docRef("Feed", "HiddenChild");
        when(explorerNodeService.getDescendants(folder))
                .thenReturn(List.of(node(folder), node(visibleChild), node(hiddenChild)));
        when(securityContext.hasDocumentPermission(visibleChild, DocumentPermission.VIEW)).thenReturn(true);
        when(securityContext.hasDocumentPermission(hiddenChild, DocumentPermission.VIEW)).thenReturn(false);

        final DeleteConfirmation info = explorerService.getDeleteConfirmation(List.of(folder));

        assertThat(info.getChildItems()).containsExactly(visibleChild);
        assertThat(info.getTotalChildCount()).isEqualTo(1);   // only the viewable child is counted
        assertThat(info.getChildTypeCounts()).containsExactly(entry("Feed", 1));
        assertThat(info.isHasHiddenChildItems()).isTrue();
    }

    @Test
    void folderWithOnlyHiddenItemsStillReportsContents() {
        final DocRef folder = docRef("Folder", "MyFolder");
        final DocRef hiddenChild = docRef("Feed", "HiddenChild");
        when(explorerNodeService.getDescendants(folder))
                .thenReturn(List.of(node(folder), node(hiddenChild)));
        when(securityContext.hasDocumentPermission(hiddenChild, DocumentPermission.VIEW)).thenReturn(false);

        final DeleteConfirmation info = explorerService.getDeleteConfirmation(List.of(folder));

        // Nothing viewable to list or count, but the folder is not empty so we must still warn.
        assertThat(info.getTotalChildCount()).isZero();
        assertThat(info.getChildItems()).isEmpty();
        assertThat(info.getChildTypeCounts()).isEmpty();
        assertThat(info.isHasHiddenChildItems()).isTrue();
        assertThat(info.hasChildItems()).isTrue();
        assertThat(info.isEmpty()).isFalse();
    }

    @Test
    void childTypeCountsGroupByType() {
        final DocRef folder = docRef("Folder", "MyFolder");
        final DocRef feed1 = docRef("Feed", "Feed1");
        final DocRef feed2 = docRef("Feed", "Feed2");
        final DocRef pipeline = docRef("Pipeline", "Pipe1");
        when(explorerNodeService.getDescendants(folder))
                .thenReturn(List.of(node(folder), node(feed1), node(feed2), node(pipeline)));
        List.of(feed1, feed2, pipeline).forEach(d ->
                when(securityContext.hasDocumentPermission(d, DocumentPermission.VIEW)).thenReturn(true));

        final DeleteConfirmation info = explorerService.getDeleteConfirmation(List.of(folder));

        assertThat(info.getTotalChildCount()).isEqualTo(3);
        assertThat(info.getChildTypeCounts()).containsOnly(entry("Feed", 2), entry("Pipeline", 1));
    }

    @Test
    void largeFolderTruncatesListButReportsTrueTotal() {
        final DocRef folder = docRef("Folder", "BigFolder");
        final List<ExplorerNode> nodes = new ArrayList<>();
        nodes.add(node(folder));
        final List<DocRef> children = IntStream.range(0, 150)
                .mapToObj(i -> docRef("Feed", "Feed" + i))
                .collect(Collectors.toList());
        children.forEach(c -> {
            nodes.add(node(c));
            when(securityContext.hasDocumentPermission(c, DocumentPermission.VIEW)).thenReturn(true);
        });
        when(explorerNodeService.getDescendants(folder)).thenReturn(nodes);

        final DeleteConfirmation info = explorerService.getDeleteConfirmation(List.of(folder));

        assertThat(info.getTotalChildCount()).isEqualTo(150);
        assertThat(info.getChildItems()).hasSize(100);   // capped
        assertThat(info.isChildItemsTruncated()).isTrue();
        assertThat(info.isHasHiddenChildItems()).isFalse();
    }

    private static DocRef docRef(final String type, final String name) {
        return DocRef.builder().randomUuid().type(type).name(name).build();
    }

    private static ExplorerNode node(final DocRef docRef) {
        return ExplorerNode.builder().docRef(docRef).build();
    }
}
