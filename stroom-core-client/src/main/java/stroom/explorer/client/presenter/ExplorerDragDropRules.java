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
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Decides which explorer nodes may be dragged and where they may be dropped, using only the rows
/// currently displayed in the tree.
///
/// The tree shows the same document more than once when it is also a favourite (once under
/// `System` and once under `Favourites`), so all comparisons are made on [DocRef] rather than on
/// [ExplorerNode], whose equality includes the root node.
public final class ExplorerDragDropRules {

    private ExplorerDragDropRules() {
    }

    /// Tests whether a node can be picked up and dragged.
    ///
    /// @param node The node under the pointer.
    /// @return False for the `System` and `Favourites` roots, and for nodes without a UUID such as
    /// [ExplorerTreeModel#NULL_SELECTION].
    public static boolean isDraggable(final ExplorerNode node) {
        return node != null
               && node.getDocRef() != null
               && NullSafe.isNonEmptyString(node.getUuid())
               && !ExplorerConstants.isSystemNode(node.getType(), node.getUuid())
               && !ExplorerConstants.isFavouritesNode(node.getType(), node.getUuid());
    }

    /// Works out which nodes a drag that starts on `draggedNode` carries.
    ///
    /// If the dragged node is part of the current selection the whole selection is carried,
    /// otherwise only the dragged node is. Nodes that cannot be dragged are dropped from the result
    /// and a document that appears twice (e.g. under `System` and under `Favourites`) is carried once.
    ///
    /// @param draggedNode   The node the drag started on.
    /// @param selectedNodes The nodes currently selected in the tree, may be null.
    /// @return The nodes to carry, empty if there is nothing that can be dragged.
    public static List<ExplorerNode> getDraggedNodes(final ExplorerNode draggedNode,
                                                     final List<ExplorerNode> selectedNodes) {
        final List<ExplorerNode> candidates;
        if (NullSafe.hasItems(selectedNodes) && selectedNodes.contains(draggedNode)) {
            candidates = selectedNodes;
        } else {
            candidates = NullSafe.singletonList(draggedNode);
        }

        final Set<DocRef> seen = new HashSet<>();
        final List<ExplorerNode> result = new ArrayList<>();
        for (final ExplorerNode node : candidates) {
            if (isDraggable(node) && seen.add(node.getDocRef())) {
                result.add(node);
            }
        }
        return result;
    }

    /// Tests whether `draggedNodes` may be dropped onto `target`.
    ///
    /// A drop is refused when:
    /// * the target is not a folder (or the `System` root), which also excludes the `Favourites` root;
    /// * the target is one of the dragged nodes, or a displayed descendant of one of them;
    /// * it is a move and the target is already the displayed parent of every dragged node;
    /// * it is a copy and a feed is being dragged, as feeds cannot be copied.
    ///
    /// Descendants that are not displayed (e.g. inside a collapsed folder reached via
    /// `Favourites`) cannot be detected here, so the server must also refuse such moves.
    ///
    /// @param rows         The rows currently displayed in the tree, in display order.
    /// @param draggedNodes The nodes being dragged.
    /// @param target       The node under the pointer.
    /// @param copy         True if the drop will copy, false if it will move.
    /// @return True if the drop is allowed.
    public static boolean isValidDrop(final List<ExplorerNode> rows,
                                      final List<ExplorerNode> draggedNodes,
                                      final ExplorerNode target,
                                      final boolean copy) {
        if (target == null
            || !NullSafe.hasItems(draggedNodes)
            || !ExplorerConstants.isFolderOrSystem(target.getDocRef())) {
            return false;
        }

        final DocRef targetDocRef = target.getDocRef();
        final List<ExplorerNode> safeRows = NullSafe.list(rows);
        boolean allAlreadyInTarget = true;
        for (final ExplorerNode draggedNode : draggedNodes) {
            if (Objects.equals(draggedNode.getDocRef(), targetDocRef)) {
                return false;
            }
            if (copy && FeedDoc.TYPE.equals(draggedNode.getType())) {
                return false;
            }
            if (getDisplayedDescendants(safeRows, draggedNode).contains(targetDocRef)) {
                return false;
            }
            final ExplorerNode parent = getDisplayedParent(safeRows, draggedNode);
            if (parent == null || !Objects.equals(parent.getDocRef(), targetDocRef)) {
                allAlreadyInTarget = false;
            }
        }
        return copy || !allAlreadyInTarget;
    }

    /// Tests whether the user's permissions allow `draggedNodes` to be dropped onto `target`. This
    /// mirrors the context menu: a move needs `Edit` on every dragged node and a copy needs `View`. The
    /// target folder must also allow every dragged type to be created in it.
    ///
    /// Permissions that have not been fetched yet count as not permitted, so the drop is only
    /// offered once they are known.
    ///
    /// @param draggedNodes The nodes being dragged.
    /// @param target       The folder under the pointer.
    /// @param permissions  The permissions fetched so far, keyed by [DocRef].
    /// @param copy         True if the drop will copy, false if it will move.
    /// @return True if the user is permitted to make the drop.
    public static boolean isPermittedDrop(final List<ExplorerNode> draggedNodes,
                                          final ExplorerNode target,
                                          final Map<DocRef, ExplorerNodePermissions> permissions,
                                          final boolean copy) {
        if (target == null || !NullSafe.hasItems(draggedNodes) || permissions == null) {
            return false;
        }
        final ExplorerNodePermissions targetPermissions = permissions.get(target.getDocRef());
        if (targetPermissions == null) {
            return false;
        }
        final DocumentPermission required = copy
                ? DocumentPermission.VIEW
                : DocumentPermission.EDIT;
        for (final ExplorerNode draggedNode : draggedNodes) {
            final ExplorerNodePermissions nodePermissions = permissions.get(draggedNode.getDocRef());
            if (nodePermissions == null
                || !nodePermissions.hasDocumentPermission(required)
                || !targetPermissions.hasCreatePermission(draggedNode.getType())) {
                return false;
            }
        }
        return true;
    }

    /// Finds the [DocRef]s of every displayed descendant of `node`, wherever `node` appears in
    /// the displayed rows (it can appear under both `System` and `Favourites`).
    ///
    /// @param rows The rows currently displayed in the tree, in display order.
    /// @param node The node whose descendants are wanted.
    /// @return The descendants' [DocRef]s, excluding `node` itself.
    static Set<DocRef> getDisplayedDescendants(final List<ExplorerNode> rows,
                                               final ExplorerNode node) {
        final Set<DocRef> descendants = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            final ExplorerNode row = rows.get(i);
            if (Objects.equals(row.getDocRef(), node.getDocRef())) {
                for (int j = i + 1; j < rows.size() && rows.get(j).getDepth() > row.getDepth(); j++) {
                    descendants.add(rows.get(j).getDocRef());
                }
            }
        }
        return descendants;
    }

    /// Finds the displayed parent of `node`, i.e. the nearest earlier row with a smaller depth.
    ///
    /// @param rows The rows currently displayed in the tree, in display order.
    /// @param node The node whose parent is wanted (matched by its root as well as its [DocRef]).
    /// @return The parent row, or null if `node` is not displayed or is at the top level.
    static ExplorerNode getDisplayedParent(final List<ExplorerNode> rows,
                                           final ExplorerNode node) {
        final int index = rows.indexOf(node);
        if (index > 0) {
            final int depth = rows.get(index).getDepth();
            for (int i = index - 1; i >= 0; i--) {
                if (rows.get(i).getDepth() < depth) {
                    return rows.get(i);
                }
            }
        }
        return null;
    }
}
