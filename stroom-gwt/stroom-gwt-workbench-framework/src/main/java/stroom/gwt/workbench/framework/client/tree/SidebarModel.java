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

package stroom.gwt.workbench.framework.client.tree;

import stroom.gwt.workbench.framework.client.tree.StoryTreeNode.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The state of the sidebar tree, i.e. which nodes are expanded and which story is selected,
/// and the rows that result from that state.
public class SidebarModel {

    private List<StoryTreeNode> topLevelNodes;
    private final Map<String, StoryTreeNode> nodesById = new HashMap<>();
    private final Set<String> expandedIds = new HashSet<>();
    private final Set<String> collapsedRootIds = new HashSet<>();
    private String selectedId;

    /// @param topLevelNodes The top level nodes, as created by [StoryTreeBuilder].
    public SidebarModel(final List<StoryTreeNode> topLevelNodes) {
        this.topLevelNodes = topLevelNodes;
        topLevelNodes.forEach(this::index);
    }

    /// Replaces the tree, e.g. when the tag filter changes, keeping the expanded state of nodes
    /// that are in both trees.
    ///
    /// @param topLevelNodes The new top level nodes.
    public void setTopLevelNodes(final List<StoryTreeNode> topLevelNodes) {
        this.topLevelNodes = topLevelNodes;
        nodesById.clear();
        topLevelNodes.forEach(this::index);
    }

    private void index(final StoryTreeNode node) {
        nodesById.put(node.getId(), node);
        node.getChildren().forEach(this::index);
    }

    /// @param id The id of a node.
    /// @return The node with the id, or null if there isn't one.
    public StoryTreeNode getNode(final String id) {
        if (id == null) {
            return null;
        }
        return nodesById.get(id);
    }

    /// @return The id of the selected story, or null if nothing is selected.
    public String getSelectedId() {
        return selectedId;
    }

    /// Selects a story and expands all of its ancestors so it is visible.
    ///
    /// @param storyId The id of the story to select.
    public void select(final String storyId) {
        selectedId = storyId;
        final StoryTreeNode node = nodesById.get(storyId);
        if (node != null) {
            StoryTreeNode ancestor = node.getParent();
            while (ancestor != null) {
                if (ancestor.getType() == Type.ROOT) {
                    collapsedRootIds.remove(ancestor.getId());
                } else {
                    expandedIds.add(ancestor.getId());
                }
                ancestor = ancestor.getParent();
            }
        }
    }

    /// @param id The id of a node.
    /// @return True if the node is expanded. Roots are expanded unless collapsed.
    public boolean isExpanded(final String id) {
        final StoryTreeNode node = nodesById.get(id);
        if (node == null) {
            return false;
        }
        if (node.getType() == Type.ROOT) {
            return !collapsedRootIds.contains(id);
        }
        return expandedIds.contains(id);
    }

    /// Expands or collapses a node.
    ///
    /// @param id       The id of the node.
    /// @param expanded True to expand the node, false to collapse it.
    public void setExpanded(final String id, final boolean expanded) {
        final StoryTreeNode node = nodesById.get(id);
        if (node == null) {
            return;
        }
        if (node.getType() == Type.ROOT) {
            if (expanded) {
                collapsedRootIds.remove(id);
            } else {
                collapsedRootIds.add(id);
            }
        } else if (node.isExpandable()) {
            if (expanded) {
                expandedIds.add(id);
            } else {
                expandedIds.remove(id);
            }
        }
    }

    /// Toggles whether a node is expanded.
    ///
    /// @param id The id of the node.
    public void toggleExpanded(final String id) {
        setExpanded(id, !isExpanded(id));
    }

    /// @param rootId The id of a root.
    /// @return True if every expandable node below the root is expanded.
    public boolean isAllExpanded(final String rootId) {
        final StoryTreeNode root = nodesById.get(rootId);
        if (root == null) {
            return false;
        }
        final List<StoryTreeNode> expandable = new ArrayList<>();
        collectExpandable(root, expandable);
        return !expandable.isEmpty()
               && expandable.stream().allMatch(node -> expandedIds.contains(node.getId()));
    }

    /// Expands or collapses every node below a root.
    ///
    /// @param rootId   The id of the root.
    /// @param expanded True to expand all, false to collapse all.
    public void setAllExpanded(final String rootId, final boolean expanded) {
        final StoryTreeNode root = nodesById.get(rootId);
        if (root == null) {
            return;
        }
        final List<StoryTreeNode> expandable = new ArrayList<>();
        collectExpandable(root, expandable);
        expandable.forEach(node -> setExpanded(node.getId(), expanded));
    }

    private void collectExpandable(final StoryTreeNode node, final List<StoryTreeNode> list) {
        for (final StoryTreeNode child : node.getChildren()) {
            if (child.isExpandable()) {
                list.add(child);
            }
            collectExpandable(child, list);
        }
    }

    /// Finds where keyboard navigation should continue from when the highlighted node may have
    /// been hidden, e.g. by collapsing one of its ancestors.
    ///
    /// @param id The id of a node.
    /// @return The id of the node if it is visible, otherwise of its nearest visible ancestor
    /// that isn't a root, or null if there isn't one (or the node doesn't exist).
    public String getNearestVisibleId(final String id) {
        StoryTreeNode node = getNode(id);
        final List<StoryTreeNode> visible = getVisibleNodes();
        while (node != null) {
            if (node.getType() != Type.ROOT && visible.contains(node)) {
                return node.getId();
            }
            node = node.getParent();
        }
        return null;
    }

    /// @return The nodes currently visible in the sidebar, in display order.
    public List<StoryTreeNode> getVisibleNodes() {
        final List<StoryTreeNode> visible = new ArrayList<>();
        topLevelNodes.forEach(node -> addVisible(node, visible));
        return visible;
    }

    private void addVisible(final StoryTreeNode node, final List<StoryTreeNode> visible) {
        visible.add(node);
        if (isExpanded(node.getId())) {
            node.getChildren().forEach(child -> addVisible(child, visible));
        }
    }
}
