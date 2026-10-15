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
import stroom.gwt.workbench.framework.client.tree.StoryTreeNode.Type;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TestStoryTreeBuilder {

    private static final StoryRenderer NO_WIDGET = context -> null;

    @Test
    void testBuild() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Buttons/Button")
                .story("Default", NO_WIDGET)
                .story("Primary", NO_WIDGET);
        registry.component("Widgets/ActionMenuCell")
                .story("Actions", NO_WIDGET);
        registry.component("App/Main/AboutDialog")
                .story("Default", NO_WIDGET);

        final List<StoryTreeNode> topLevel = StoryTreeBuilder.build(registry.getStories());

        // Roots in the order first seen
        assertThat(topLevel)
                .extracting(StoryTreeNode::getType, StoryTreeNode::getId, StoryTreeNode::getName)
                .containsExactly(
                        tuple(Type.ROOT, "widgets", "Widgets"),
                        tuple(Type.ROOT, "app", "App"));

        final StoryTreeNode widgets = topLevel.get(0);
        assertThat(widgets.getChildren())
                .extracting(StoryTreeNode::getType, StoryTreeNode::getId)
                .containsExactly(
                        tuple(Type.GROUP, "widgets-buttons"),
                        tuple(Type.COMPONENT, "widgets-actionmenucell"));

        final StoryTreeNode buttons = widgets.getChildren().get(0);
        final StoryTreeNode button = buttons.getChildren().get(0);
        assertThat(button.getType()).isEqualTo(Type.COMPONENT);
        assertThat(button.getId()).isEqualTo("widgets-buttons-button");
        assertThat(button.getParent()).isSameAs(buttons);
        assertThat(button.getChildren())
                .extracting(StoryTreeNode::getType, StoryTreeNode::getId)
                .containsExactly(
                        tuple(Type.STORY, "widgets-buttons-button--default"),
                        tuple(Type.STORY, "widgets-buttons-button--primary"));
        assertThat(button.getChildren().get(0).getStory().getName()).isEqualTo("Default");

        // Depths as used for indentation - children of roots are at zero
        assertThat(widgets.getDepth()).isZero();
        assertThat(buttons.getDepth()).isZero();
        assertThat(button.getDepth()).isEqualTo(1);
        assertThat(button.getChildren().get(0).getDepth()).isEqualTo(2);
    }

    @Test
    void testSingleSegmentTitle() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Button")
                .story("Default", NO_WIDGET);

        final List<StoryTreeNode> topLevel = StoryTreeBuilder.build(registry.getStories());

        // No root, just a top level component
        assertThat(topLevel).hasSize(1);
        assertThat(topLevel.get(0).getType()).isEqualTo(Type.COMPONENT);
        assertThat(topLevel.get(0).getDepth()).isZero();
        assertThat(topLevel.get(0).getChildren().get(0).getDepth()).isEqualTo(1);
    }

    @Test
    void testSingleStoryComponentIsHoisted() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Display/Logo")
                .story("Logo", NO_WIDGET);
        registry.component("Widgets/Display/InfinityLogo")
                .story("Default", NO_WIDGET);
        registry.component("Hoisted")
                .story("Hoisted", NO_WIDGET);

        final List<StoryTreeNode> topLevel = StoryTreeBuilder.build(registry.getStories());

        final StoryTreeNode display = topLevel.get(0).getChildren().get(0);
        // Same name as its component so the story replaces the component
        final StoryTreeNode logo = display.getChildren().get(0);
        assertThat(logo.getType()).isEqualTo(Type.STORY);
        assertThat(logo.getId()).isEqualTo("widgets-display-logo--logo");
        assertThat(logo.getParent()).isSameAs(display);
        assertThat(logo.getDepth()).isEqualTo(1);
        // Different name so not hoisted
        assertThat(display.getChildren().get(1).getType()).isEqualTo(Type.COMPONENT);
        // Top level hoisting
        assertThat(topLevel.get(1).getType()).isEqualTo(Type.STORY);
        assertThat(topLevel.get(1).getId()).isEqualTo("hoisted--hoisted");
    }

    @Test
    void testEmpty() {
        assertThat(StoryTreeBuilder.build(List.of())).isEmpty();
    }

    @Test
    void testBuild_rootAndComponentWithSameName() {
        // Regression test: 'Widgets' as both a root and a top level component used to share the
        // id 'widgets', so the component's stories were put directly under the root
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Button")
                .story("Default", NO_WIDGET);
        registry.component("Widgets")
                .story("Overview", NO_WIDGET)
                .story("Other", NO_WIDGET);

        final List<StoryTreeNode> topLevel = StoryTreeBuilder.build(registry.getStories());

        assertThat(topLevel)
                .extracting(StoryTreeNode::getType, StoryTreeNode::getId, StoryTreeNode::getName)
                .containsExactly(
                        tuple(Type.ROOT, "widgets", "Widgets"),
                        tuple(Type.COMPONENT, "widgets-component", "Widgets"));
        assertThat(topLevel.get(0).getChildren())
                .extracting(StoryTreeNode::getType, StoryTreeNode::getId)
                .containsExactly(tuple(Type.COMPONENT, "widgets-button"));
        assertThat(topLevel.get(1).getChildren())
                .extracting(StoryTreeNode::getType, StoryTreeNode::getId)
                .containsExactly(
                        tuple(Type.STORY, "widgets--overview"),
                        tuple(Type.STORY, "widgets--other"));
        assertIdsUnique(topLevel);
    }

    @Test
    void testBuild_componentBeforeRootWithSameName() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets")
                .story("Overview", NO_WIDGET)
                .story("Other", NO_WIDGET);
        registry.component("Widgets/Button")
                .story("Default", NO_WIDGET);

        final List<StoryTreeNode> topLevel = StoryTreeBuilder.build(registry.getStories());

        // The first one keeps the plain id
        assertThat(topLevel)
                .extracting(StoryTreeNode::getType, StoryTreeNode::getId)
                .containsExactly(
                        tuple(Type.COMPONENT, "widgets"),
                        tuple(Type.ROOT, "widgets-root"));
        assertIdsUnique(topLevel);
    }

    @Test
    void testBuild_groupAndComponentWithSameName() {
        // 'Buttons' is a component in one title and a group in another
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Buttons")
                .story("Overview", NO_WIDGET)
                .story("Other", NO_WIDGET);
        registry.component("Widgets/Buttons/Button")
                .story("Default", NO_WIDGET);

        final List<StoryTreeNode> topLevel = StoryTreeBuilder.build(registry.getStories());

        assertThat(topLevel.get(0).getChildren())
                .extracting(StoryTreeNode::getType, StoryTreeNode::getId)
                .containsExactly(
                        tuple(Type.COMPONENT, "widgets-buttons"),
                        tuple(Type.GROUP, "widgets-buttons-group"));
        assertThat(topLevel.get(0).getChildren().get(1).getChildren())
                .extracting(StoryTreeNode::getType, StoryTreeNode::getId)
                .containsExactly(tuple(Type.COMPONENT, "widgets-buttons-button"));
        assertIdsUnique(topLevel);
    }

    private static void assertIdsUnique(final List<StoryTreeNode> topLevel) {
        final List<String> ids = new ArrayList<>();
        topLevel.forEach(node -> collectIds(node, ids));
        assertThat(ids).doesNotHaveDuplicates();
    }

    private static void collectIds(final StoryTreeNode node, final List<String> ids) {
        ids.add(node.getId());
        node.getChildren().forEach(child -> collectIds(child, ids));
    }
}
