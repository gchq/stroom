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

import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.gwt.workbench.framework.client.story.StoryRenderer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSidebarModel {

    private static final StoryRenderer NO_WIDGET = context -> null;

    private SidebarModel model;

    @BeforeEach
    void setUp() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Buttons/Button")
                .story("Default", NO_WIDGET)
                .story("Primary", NO_WIDGET);
        registry.component("Widgets/Inputs/TickBox")
                .story("Basic", NO_WIDGET);
        model = new SidebarModel(StoryTreeBuilder.build(registry.getStories()));
    }

    @Test
    void testInitialState() {
        // Roots are expanded, everything else is collapsed
        assertThat(model.getVisibleNodes())
                .extracting(StoryTreeNode::getId)
                .containsExactly("widgets", "widgets-buttons", "widgets-inputs");
        assertThat(model.getSelectedId()).isNull();
    }

    @Test
    void testSelectExpandsAncestors() {
        model.select("widgets-buttons-button--primary");

        assertThat(model.getSelectedId()).isEqualTo("widgets-buttons-button--primary");
        assertThat(model.getVisibleNodes())
                .extracting(StoryTreeNode::getId)
                .containsExactly(
                        "widgets",
                        "widgets-buttons",
                        "widgets-buttons-button",
                        "widgets-buttons-button--default",
                        "widgets-buttons-button--primary",
                        "widgets-inputs");
    }

    @Test
    void testSelectExpandsCollapsedRoot() {
        model.toggleExpanded("widgets");
        assertThat(model.getVisibleNodes())
                .extracting(StoryTreeNode::getId)
                .containsExactly("widgets");

        model.select("widgets-inputs-tickbox--basic");
        assertThat(model.isExpanded("widgets")).isTrue();
    }

    @Test
    void testToggleExpanded() {
        model.toggleExpanded("widgets-inputs");
        assertThat(model.isExpanded("widgets-inputs")).isTrue();
        assertThat(model.getVisibleNodes())
                .extracting(StoryTreeNode::getId)
                .contains("widgets-inputs-tickbox");

        model.toggleExpanded("widgets-inputs");
        assertThat(model.isExpanded("widgets-inputs")).isFalse();
    }

    @Test
    void testStoriesCannotBeExpanded() {
        model.setExpanded("widgets-inputs-tickbox--basic", true);
        assertThat(model.isExpanded("widgets-inputs-tickbox--basic")).isFalse();
    }

    @Test
    void testUnknownIds() {
        model.toggleExpanded("unknown");
        model.setAllExpanded("unknown", true);
        assertThat(model.isExpanded("unknown")).isFalse();
        assertThat(model.isAllExpanded("unknown")).isFalse();
        assertThat(model.getNode("unknown")).isNull();
        assertThat(model.getNode(null)).isNull();

        // Selecting an unknown story doesn't expand anything
        model.select("unknown");
        assertThat(model.getVisibleNodes()).hasSize(3);
    }

    @Test
    void testExpandAll() {
        assertThat(model.isAllExpanded("widgets")).isFalse();

        model.setAllExpanded("widgets", true);
        assertThat(model.isAllExpanded("widgets")).isTrue();
        assertThat(model.getVisibleNodes()).hasSize(8);

        model.setAllExpanded("widgets", false);
        assertThat(model.isAllExpanded("widgets")).isFalse();
        assertThat(model.getVisibleNodes()).hasSize(3);
    }

    @Test
    void testSetTopLevelNodes() {
        model.select("widgets-buttons-button--primary");
        model.toggleExpanded("widgets-inputs");

        // A filtered tree with only the tick box
        final StoryRegistry filtered = new StoryRegistry();
        filtered.component("Widgets/Inputs/TickBox")
                .story("Basic", NO_WIDGET);
        model.setTopLevelNodes(StoryTreeBuilder.build(filtered.getStories()));

        // The expanded state is kept for nodes in both trees
        assertThat(model.isExpanded("widgets-inputs")).isTrue();
        assertThat(model.getVisibleNodes())
                .extracting(StoryTreeNode::getId)
                .containsExactly("widgets", "widgets-inputs", "widgets-inputs-tickbox");
        // Nodes not in the new tree can't be found
        assertThat(model.getNode("widgets-buttons-button--primary")).isNull();
        assertThat(model.getNode("widgets-inputs-tickbox--basic")).isNotNull();
    }

    @Test
    void testSetTopLevelNodes_selectedStoryFilteredOut() {
        model.select("widgets-buttons-button--primary");

        final StoryRegistry filtered = new StoryRegistry();
        filtered.component("Widgets/Inputs/TickBox")
                .story("Basic", NO_WIDGET);
        model.setTopLevelNodes(StoryTreeBuilder.build(filtered.getStories()));

        // The story stays selected (it is still shown in the preview) though not in the tree
        assertThat(model.getSelectedId()).isEqualTo("widgets-buttons-button--primary");
        assertThat(model.getNode(model.getSelectedId())).isNull();
        assertThat(model.getVisibleNodes())
                .extracting(StoryTreeNode::getId)
                .containsExactly("widgets", "widgets-inputs");

        // Going back to the full tree shows it again
        final StoryRegistry all = new StoryRegistry();
        all.component("Widgets/Buttons/Button")
                .story("Default", NO_WIDGET)
                .story("Primary", NO_WIDGET);
        model.setTopLevelNodes(StoryTreeBuilder.build(all.getStories()));
        assertThat(model.getNode("widgets-buttons-button--primary")).isNotNull();
        assertThat(model.getVisibleNodes())
                .extracting(StoryTreeNode::getId)
                .contains("widgets-buttons-button--primary");
    }

    @Test
    void testSetTopLevelNodes_empty() {
        model.select("widgets-buttons-button--primary");
        model.setTopLevelNodes(List.of());
        assertThat(model.getVisibleNodes()).isEmpty();
        assertThat(model.getNode("widgets")).isNull();
        assertThat(model.getNearestVisibleId("widgets-buttons-button--primary")).isNull();
    }

    @Test
    void testGetNearestVisibleId() {
        model.select("widgets-buttons-button--primary");
        assertThat(model.getNearestVisibleId("widgets-buttons-button--primary"))
                .isEqualTo("widgets-buttons-button--primary");

        // Collapsing the component hides the story, so its component is the nearest visible
        model.setExpanded("widgets-buttons-button", false);
        assertThat(model.getNearestVisibleId("widgets-buttons-button--primary"))
                .isEqualTo("widgets-buttons-button");

        // Collapsing the group hides the component too
        model.setExpanded("widgets-buttons", false);
        assertThat(model.getNearestVisibleId("widgets-buttons-button--primary"))
                .isEqualTo("widgets-buttons");

        // Roots aren't highlighted, so collapsing the root leaves nothing
        model.setExpanded("widgets", false);
        assertThat(model.getNearestVisibleId("widgets-buttons-button--primary")).isNull();

        assertThat(model.getNearestVisibleId("unknown")).isNull();
        assertThat(model.getNearestVisibleId(null)).isNull();
    }
}
