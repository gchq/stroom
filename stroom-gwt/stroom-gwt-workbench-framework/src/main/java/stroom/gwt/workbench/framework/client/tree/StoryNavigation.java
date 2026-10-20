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
import java.util.List;

/// Works out the previous/next story or component in sidebar order, for Storybook's
/// 'Previous story', 'Next story', 'Previous component' and 'Next component' shortcuts.
public class StoryNavigation {

    private final List<String> storyIds = new ArrayList<>();
    private final List<String> componentIdOfStory = new ArrayList<>();

    /// @param topLevelNodes The top level nodes of the sidebar tree.
    public StoryNavigation(final List<StoryTreeNode> topLevelNodes) {
        topLevelNodes.forEach(this::collect);
    }

    private void collect(final StoryTreeNode node) {
        if (node.getType() == Type.STORY) {
            storyIds.add(node.getId());
            // A hoisted story is its own component
            final StoryTreeNode parent = node.getParent();
            componentIdOfStory.add(parent != null && parent.getType() == Type.COMPONENT
                    ? parent.getId()
                    : node.getId());
        } else {
            node.getChildren().forEach(this::collect);
        }
    }

    /// @param currentStoryId The id of the current story.
    /// @param forwards       True for the next story, false for the previous one.
    /// @return The id of the adjacent story, or null if there isn't one.
    public String adjacentStory(final String currentStoryId, final boolean forwards) {
        final int index = storyIds.indexOf(currentStoryId);
        if (index < 0) {
            return null;
        }
        final int newIndex = forwards
                ? index + 1
                : index - 1;
        if (newIndex < 0 || newIndex >= storyIds.size()) {
            return null;
        }
        return storyIds.get(newIndex);
    }

    /// @param currentStoryId The id of the current story.
    /// @param forwards       True for the next component, false for the previous one.
    /// @return The id of the first story of the adjacent component, or null if there isn't one.
    public String adjacentComponent(final String currentStoryId, final boolean forwards) {
        final int index = storyIds.indexOf(currentStoryId);
        if (index < 0) {
            return null;
        }
        final String currentComponent = componentIdOfStory.get(index);
        if (forwards) {
            for (int i = index + 1; i < storyIds.size(); i++) {
                if (!componentIdOfStory.get(i).equals(currentComponent)) {
                    return storyIds.get(i);
                }
            }
            return null;
        }

        // Find the start of the previous component
        int i = index - 1;
        while (i >= 0 && componentIdOfStory.get(i).equals(currentComponent)) {
            i--;
        }
        if (i < 0) {
            return null;
        }
        final String previousComponent = componentIdOfStory.get(i);
        while (i > 0 && componentIdOfStory.get(i - 1).equals(previousComponent)) {
            i--;
        }
        return storyIds.get(i);
    }
}
