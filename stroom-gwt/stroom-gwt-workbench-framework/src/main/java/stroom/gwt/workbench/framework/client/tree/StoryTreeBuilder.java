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
import stroom.gwt.workbench.framework.client.story.StoryIds;
import stroom.gwt.workbench.framework.client.tree.StoryTreeNode.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Builds the sidebar tree from a list of stories using the same rules as Storybook:
///
/// * The first part of a multi part title is a root, e.g. `Widgets` in `Widgets/Buttons/Button`.
/// * The last part of a title is the component, e.g. `Button`.
/// * Any parts in between are groups (folders), e.g. `Buttons`.
/// * A component with a single story of the same name is shown as just the story.
///
/// Nodes keep the order in which they were first encountered. Node ids are unique: if two
/// different kinds of node would get the same id, e.g. `Widgets` as both a root (from
/// `Widgets/Button`) and a component (from a story titled `Widgets`), the later one's id gets
/// its type as a suffix, e.g. `widgets-component`.
public final class StoryTreeBuilder {

    private static final String TITLE_SEPARATOR = "/";

    private StoryTreeBuilder() {
        // Static utility
    }

    /// @param stories The stories in display order.
    /// @return The top level nodes of the tree.
    public static List<StoryTreeNode> build(final List<Story> stories) {
        final List<StoryTreeNode> topLevel = new ArrayList<>();
        // Keyed by type and path id, so different kinds of node with the same path stay apart
        final Map<String, StoryTreeNode> nodesByKey = new HashMap<>();
        final Set<String> usedIds = new HashSet<>();
        for (final Story story : stories) {
            usedIds.add(story.getId());
        }

        for (final Story story : stories) {
            final String[] parts = story.getTitle().split(TITLE_SEPARATOR);
            StoryTreeNode parent = null;
            final StringBuilder path = new StringBuilder();

            for (int i = 0; i < parts.length; i++) {
                final String part = parts[i].trim();
                if (path.length() > 0) {
                    path.append(TITLE_SEPARATOR);
                }
                path.append(part);

                final Type type;
                if (i == parts.length - 1) {
                    type = Type.COMPONENT;
                } else if (i == 0) {
                    type = Type.ROOT;
                } else {
                    type = Type.GROUP;
                }

                final String pathId = StoryIds.sanitise(path.toString());
                final String key = type.getNodeType() + ":" + pathId;
                StoryTreeNode node = nodesByKey.get(key);
                if (node == null) {
                    node = new StoryTreeNode(type, uniqueId(pathId, type, usedIds), part, parent, null);
                    nodesByKey.put(key, node);
                    if (parent == null) {
                        topLevel.add(node);
                    } else {
                        parent.addChild(node);
                    }
                }
                parent = node;
            }

            if (parent != null) {
                parent.addChild(new StoryTreeNode(
                        Type.STORY, story.getId(), story.getName(), parent, story));
            }
        }

        topLevel.replaceAll(StoryTreeBuilder::hoistSingleStoryComponents);
        return topLevel;
    }

    private static String uniqueId(final String pathId, final Type type, final Set<String> usedIds) {
        String id = pathId;
        int suffix = 1;
        if (usedIds.contains(id)) {
            id = pathId + "-" + type.getNodeType();
        }
        while (usedIds.contains(id)) {
            suffix++;
            id = pathId + "-" + type.getNodeType() + suffix;
        }
        usedIds.add(id);
        return id;
    }

    /// Replaces components that have one story with the same name as the component with the
    /// story itself, as Storybook does.
    ///
    /// @return The node to use in place of the supplied node, which may be the same node.
    private static StoryTreeNode hoistSingleStoryComponents(final StoryTreeNode node) {
        if (isSingleStoryComponent(node)) {
            final StoryTreeNode storyNode = node.getChildren().get(0);
            return new StoryTreeNode(
                    Type.STORY,
                    storyNode.getId(),
                    storyNode.getName(),
                    node.getParent(),
                    storyNode.getStory());
        }

        for (final StoryTreeNode child : new ArrayList<>(node.getChildren())) {
            final StoryTreeNode replacement = hoistSingleStoryComponents(child);
            if (replacement != child) {
                node.replaceChild(child, replacement);
            }
        }
        return node;
    }

    private static boolean isSingleStoryComponent(final StoryTreeNode node) {
        return node.getType() == Type.COMPONENT
               && node.getChildren().size() == 1
               && node.getChildren().get(0).getName().equals(node.getName());
    }
}
