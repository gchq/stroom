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

package stroom.explorer.client.presenter;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodePermissions;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermission;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestExplorerDragDropRules {

    private static final String SYSTEM_ROOT = ExplorerConstants.SYSTEM_DOC_REF.getUuid();
    private static final String FAVOURITES_ROOT = ExplorerConstants.FAVOURITES_DOC_REF.getUuid();

    private static final DocRef FOLDER_A = new DocRef(ExplorerConstants.FOLDER_TYPE, "a", "A");
    private static final DocRef FOLDER_A1 = new DocRef(ExplorerConstants.FOLDER_TYPE, "a1", "A1");
    private static final DocRef PIPE_A2 = new DocRef("Pipeline", "a2", "A2");
    private static final DocRef FOLDER_B = new DocRef(ExplorerConstants.FOLDER_TYPE, "b", "B");
    private static final DocRef FEED_B1 = new DocRef(FeedDoc.TYPE, "b1", "B1");

    // Favourites
    //   A1 (favourite copy)
    // System
    //   A
    //     A1
    //     A2
    //   B
    //     B1 (feed)
    private static final ExplorerNode FAVOURITES = node(ExplorerConstants.FAVOURITES_DOC_REF, 0, FAVOURITES_ROOT);
    private static final ExplorerNode FAV_A1 = node(FOLDER_A1, 1, FAVOURITES_ROOT);
    private static final ExplorerNode SYSTEM = node(ExplorerConstants.SYSTEM_DOC_REF, 0, SYSTEM_ROOT);
    private static final ExplorerNode A = node(FOLDER_A, 1, SYSTEM_ROOT);
    private static final ExplorerNode A1 = node(FOLDER_A1, 2, SYSTEM_ROOT);
    private static final ExplorerNode A2 = node(PIPE_A2, 2, SYSTEM_ROOT);
    private static final ExplorerNode B = node(FOLDER_B, 1, SYSTEM_ROOT);
    private static final ExplorerNode B1 = node(FEED_B1, 2, SYSTEM_ROOT);

    private static final List<ExplorerNode> ROWS = List.of(FAVOURITES, FAV_A1, SYSTEM, A, A1, A2, B, B1);

    @Test
    void rootNodesAreNotDraggable() {
        assertThat(ExplorerDragDropRules.isDraggable(SYSTEM)).isFalse();
        assertThat(ExplorerDragDropRules.isDraggable(FAVOURITES)).isFalse();
        assertThat(ExplorerDragDropRules.isDraggable(null)).isFalse();
    }

    @Test
    void nodeWithoutUuidIsNotDraggable() {
        final ExplorerNode nullSelection = node(new DocRef("", "", "None"), 0, null);

        assertThat(ExplorerDragDropRules.isDraggable(nullSelection)).isFalse();
    }

    @Test
    void ordinaryAndFavouriteNodesAreDraggable() {
        assertThat(ExplorerDragDropRules.isDraggable(A2)).isTrue();
        assertThat(ExplorerDragDropRules.isDraggable(FAV_A1)).isTrue();
    }

    @Test
    void dragOfSelectedNodeCarriesWholeSelection() {
        assertThat(ExplorerDragDropRules.getDraggedNodes(A2, List.of(A1, A2)))
                .containsExactly(A1, A2);
    }

    @Test
    void dragOfUnselectedNodeCarriesOnlyThatNode() {
        assertThat(ExplorerDragDropRules.getDraggedNodes(B1, List.of(A1, A2)))
                .containsExactly(B1);
        assertThat(ExplorerDragDropRules.getDraggedNodes(B1, null))
                .containsExactly(B1);
    }

    @Test
    void dragExcludesRootsAndDuplicateFavourites() {
        assertThat(ExplorerDragDropRules.getDraggedNodes(A2, List.of(SYSTEM, FAV_A1, A1, A2)))
                .containsExactly(FAV_A1, A2);
    }

    @Test
    void dragOfRootOnlyCarriesNothing() {
        assertThat(ExplorerDragDropRules.getDraggedNodes(SYSTEM, List.of(SYSTEM))).isEmpty();
        assertThat(ExplorerDragDropRules.getDraggedNodes(FAVOURITES, null)).isEmpty();
    }

    @Test
    void canMoveIntoAnotherFolder() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A2), B, false)).isTrue();
    }

    @Test
    void canMoveIntoSystemRoot() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A2), SYSTEM, false)).isTrue();
    }

    @Test
    void cannotDropOntoFavouritesRoot() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A2), FAVOURITES, false)).isFalse();
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A2), FAVOURITES, true)).isFalse();
    }

    @Test
    void canDropOntoFavouriteFolder() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(B1), FAV_A1, false)).isTrue();
    }

    @Test
    void cannotDropOntoDocument() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(B1), A2, false)).isFalse();
    }

    @Test
    void cannotDropOntoNothing() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(B1), null, false)).isFalse();
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(), B, false)).isFalse();
    }

    @Test
    void dropIsJudgedOnTargetAloneWhenNoRowsDisplayed() {
        assertThat(ExplorerDragDropRules.isValidDrop(null, List.of(A2), B, false)).isTrue();
        assertThat(ExplorerDragDropRules.isValidDrop(null, List.of(A2), A2, false)).isFalse();
    }

    @Test
    void rootNodesFromAnyRootAreNotDraggable() {
        // The tree's own root node instances may not carry the same root node UUID as the constants.
        assertThat(ExplorerDragDropRules.isDraggable(node(ExplorerConstants.SYSTEM_DOC_REF, 0, null))).isFalse();
        assertThat(ExplorerDragDropRules.isDraggable(node(ExplorerConstants.FAVOURITES_DOC_REF, 0, null)))
                .isFalse();
    }

    @Test
    void cannotDropFolderOntoItself() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A), A, false)).isFalse();
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A), A, true)).isFalse();
    }

    @Test
    void cannotDropFolderOntoItsFavouriteCopy() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A1), FAV_A1, false)).isFalse();
    }

    @Test
    void cannotDropFolderOntoItsDescendant() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A), A1, false)).isFalse();
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A), A1, true)).isFalse();
    }

    @Test
    void cannotDropFolderOntoFavouriteCopyOfItsDescendant() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A), FAV_A1, false)).isFalse();
    }

    @Test
    void moveIntoCurrentParentIsRefused() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A1, A2), A, false)).isFalse();
    }

    @Test
    void moveIsAllowedIfAnyNodeIsOutsideTarget() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A2, B1), A, false)).isTrue();
    }

    @Test
    void copyIntoCurrentParentIsAllowed() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A2), A, true)).isTrue();
    }

    @Test
    void feedsCanBeMovedButNotCopied() {
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(B1), A, false)).isTrue();
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(B1), A, true)).isFalse();
        assertThat(ExplorerDragDropRules.isValidDrop(ROWS, List.of(A2, B1), B, true)).isFalse();
    }

    @Test
    void displayedParentIsNearestShallowerRow() {
        assertThat(ExplorerDragDropRules.getDisplayedParent(ROWS, A2)).isEqualTo(A);
        assertThat(ExplorerDragDropRules.getDisplayedParent(ROWS, B)).isEqualTo(SYSTEM);
        assertThat(ExplorerDragDropRules.getDisplayedParent(ROWS, SYSTEM)).isNull();
        assertThat(ExplorerDragDropRules.getDisplayedParent(ROWS, node(FOLDER_B, 1, "other"))).isNull();
    }

    @Test
    void displayedDescendantsStopAtSiblingDepth() {
        assertThat(ExplorerDragDropRules.getDisplayedDescendants(ROWS, A))
                .containsExactlyInAnyOrder(FOLDER_A1, PIPE_A2);
        assertThat(ExplorerDragDropRules.getDisplayedDescendants(ROWS, A2)).isEmpty();
    }

    @Test
    void moveNeedsEditOnEveryNodeAndCreateInTarget() {
        final Map<DocRef, ExplorerNodePermissions> permissions = Map.of(
                PIPE_A2, permissions(PIPE_A2, Set.of(), DocumentPermission.VIEW, DocumentPermission.EDIT),
                FEED_B1, permissions(FEED_B1, Set.of(), DocumentPermission.VIEW),
                FOLDER_B, permissions(FOLDER_B, Set.of("Pipeline", FeedDoc.TYPE)));

        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(A2), B, permissions, false)).isTrue();
        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(B1), B, permissions, false)).isFalse();
        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(A2, B1), B, permissions, false)).isFalse();
    }

    @Test
    void copyNeedsOnlyView() {
        final Map<DocRef, ExplorerNodePermissions> permissions = Map.of(
                PIPE_A2, permissions(PIPE_A2, Set.of(), DocumentPermission.VIEW),
                FOLDER_B, permissions(FOLDER_B, Set.of("Pipeline")));

        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(A2), B, permissions, true)).isTrue();
        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(A2), B, permissions, false)).isFalse();
    }

    @Test
    void targetMustAllowEveryDraggedType() {
        final Map<DocRef, ExplorerNodePermissions> permissions = Map.of(
                PIPE_A2, permissions(PIPE_A2, Set.of(), DocumentPermission.EDIT),
                FOLDER_A1, permissions(FOLDER_A1, Set.of(), DocumentPermission.EDIT),
                FOLDER_B, permissions(FOLDER_B, Set.of("Pipeline")));

        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(A2), B, permissions, false)).isTrue();
        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(A2, A1), B, permissions, false)).isFalse();
    }

    @Test
    void unknownPermissionsAreNotPermitted() {
        final Map<DocRef, ExplorerNodePermissions> targetOnly = Map.of(
                FOLDER_B, permissions(FOLDER_B, Set.of("Pipeline")));
        final Map<DocRef, ExplorerNodePermissions> nodeOnly = Map.of(
                PIPE_A2, permissions(PIPE_A2, Set.of(), DocumentPermission.EDIT));

        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(A2), B, targetOnly, false)).isFalse();
        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(A2), B, nodeOnly, false)).isFalse();
        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(A2), B, null, false)).isFalse();
        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(A2), null, nodeOnly, false)).isFalse();
        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(), B, targetOnly, false)).isFalse();
    }

    @Test
    void adminIsPermittedEverything() {
        final Map<DocRef, ExplorerNodePermissions> permissions = Map.of(
                PIPE_A2, new ExplorerNodePermissions(A2, null, null, true),
                FOLDER_B, new ExplorerNodePermissions(B, null, null, true));

        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(A2), B, permissions, false)).isTrue();
    }

    @Test
    void favouriteTargetUsesSamePermissionsAsSystemCopy() {
        final Map<DocRef, ExplorerNodePermissions> permissions = Map.of(
                PIPE_A2, permissions(PIPE_A2, Set.of(), DocumentPermission.EDIT),
                FOLDER_A1, permissions(FOLDER_A1, Set.of("Pipeline")));

        assertThat(ExplorerDragDropRules.isPermittedDrop(List.of(A2), FAV_A1, permissions, false)).isTrue();
    }

    private static ExplorerNodePermissions permissions(final DocRef docRef,
                                                       final Set<String> createTypes,
                                                       final DocumentPermission... documentPermissions) {
        return new ExplorerNodePermissions(
                node(docRef, 0, SYSTEM_ROOT),
                createTypes,
                Set.of(documentPermissions),
                false);
    }

    private static ExplorerNode node(final DocRef docRef, final int depth, final String rootNodeUuid) {
        return ExplorerNode.builder()
                .docRef(docRef)
                .depth(depth)
                .rootNodeUuid(rootNodeUuid)
                .build();
    }
}
