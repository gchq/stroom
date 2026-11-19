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

import stroom.gwt.workbench.framework.client.story.Story;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// A node in the workbench sidebar tree.
public class StoryTreeNode {

    private final Type type;
    private final String id;
    private final String name;
    private final StoryTreeNode parent;
    private final Story story;
    private final List<StoryTreeNode> children = new ArrayList<>();

    /// Creates a node, see [StoryTreeBuilder].
    ///
    /// @param type   The type of the node.
    /// @param id     The unique id of the node, which for a story is the story id.
    /// @param name   The name shown in the sidebar.
    /// @param parent The parent node, or null for a top level node.
    /// @param story  The story for a [Type#STORY] node, otherwise null.
    StoryTreeNode(final Type type,
                  final String id,
                  final String name,
                  final StoryTreeNode parent,
                  final Story story) {
        this.type = type;
        this.id = id;
        this.name = name;
        this.parent = parent;
        this.story = story;
    }

    /// @return The type of the node.
    public Type getType() {
        return type;
    }

    /// @return The unique id of the node, which for a story is the story id.
    public String getId() {
        return id;
    }

    /// @return The name shown in the sidebar.
    public String getName() {
        return name;
    }

    /// @return The parent node, or null for a top level node.
    public StoryTreeNode getParent() {
        return parent;
    }

    /// @return The story if this is a [Type#STORY] node, otherwise null.
    public Story getStory() {
        return story;
    }

    /// @return The child nodes in display order.
    public List<StoryTreeNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /// Adds a child after any existing children.
    ///
    /// @param child The child node, whose parent must be this node.
    void addChild(final StoryTreeNode child) {
        children.add(child);
    }

    /// Replaces a child, keeping its position.
    ///
    /// @param oldChild The existing child.
    /// @param newChild The node to put in its place.
    /// @throws IllegalArgumentException If the old node isn't a child of this node.
    void replaceChild(final StoryTreeNode oldChild, final StoryTreeNode newChild) {
        final int index = children.indexOf(oldChild);
        if (index < 0) {
            throw new IllegalArgumentException("Not a child: " + oldChild);
        }
        children.set(index, newChild);
    }

    /// @return True if the node can be expanded to show children.
    public boolean isExpandable() {
        return type == Type.GROUP || type == Type.COMPONENT;
    }

    /// The depth used to indent the node, where children of a root (or top level nodes when
    /// there is no root) have a depth of zero. Roots are not indented so are also zero.
    ///
    /// @return The depth.
    public int getDepth() {
        int depth = 0;
        StoryTreeNode ancestor = parent;
        while (ancestor != null && ancestor.type != Type.ROOT) {
            depth++;
            ancestor = ancestor.parent;
        }
        return depth;
    }

    @Override
    public String toString() {
        return type + ":" + id;
    }


    // --------------------------------------------------------------------------------


    /// The types of node, matching the `data-nodetype` values used by Storybook.
    public enum Type {
        /// A top level heading, e.g. `WIDGETS`.
        ROOT("root"),
        /// A folder, e.g. `Buttons`.
        GROUP("group"),
        /// A component, e.g. `Button`.
        COMPONENT("component"),
        /// A story, e.g. `Default`.
        STORY("story");

        private final String nodeType;

        Type(final String nodeType) {
            this.nodeType = nodeType;
        }

        /// @return The name Storybook uses for this type of node.
        public String getNodeType() {
            return nodeType;
        }
    }
}
