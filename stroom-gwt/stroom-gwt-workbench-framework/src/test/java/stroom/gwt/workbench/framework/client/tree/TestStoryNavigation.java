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

class TestStoryNavigation {

    private static final StoryRenderer NO_WIDGET = context -> null;

    private StoryNavigation navigation;

    @BeforeEach
    void setUp() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Buttons/Button")
                .story("Default", NO_WIDGET)
                .story("Primary", NO_WIDGET);
        // Hoisted, so a component of its own
        registry.component("Widgets/Display/Logo")
                .story("Logo", NO_WIDGET);
        registry.component("Widgets/Inputs/TickBox")
                .story("Basic", NO_WIDGET)
                .story("Disabled", NO_WIDGET);
        navigation = new StoryNavigation(StoryTreeBuilder.build(registry.getStories()));
    }

    @Test
    void testAdjacentStory() {
        assertThat(navigation.adjacentStory("widgets-buttons-button--default", true))
                .isEqualTo("widgets-buttons-button--primary");
        // Crosses components
        assertThat(navigation.adjacentStory("widgets-buttons-button--primary", true))
                .isEqualTo("widgets-display-logo--logo");
        assertThat(navigation.adjacentStory("widgets-inputs-tickbox--basic", false))
                .isEqualTo("widgets-display-logo--logo");
        // Ends
        assertThat(navigation.adjacentStory("widgets-buttons-button--default", false)).isNull();
        assertThat(navigation.adjacentStory("widgets-inputs-tickbox--disabled", true)).isNull();
        assertThat(navigation.adjacentStory("unknown", true)).isNull();
    }

    @Test
    void testAdjacentComponent() {
        assertThat(navigation.adjacentComponent("widgets-buttons-button--default", true))
                .isEqualTo("widgets-display-logo--logo");
        assertThat(navigation.adjacentComponent("widgets-display-logo--logo", true))
                .isEqualTo("widgets-inputs-tickbox--basic");
        // Previous goes to the first story of the previous component
        assertThat(navigation.adjacentComponent("widgets-inputs-tickbox--disabled", false))
                .isEqualTo("widgets-display-logo--logo");
        assertThat(navigation.adjacentComponent("widgets-display-logo--logo", false))
                .isEqualTo("widgets-buttons-button--default");
        // Ends
        assertThat(navigation.adjacentComponent("widgets-buttons-button--primary", false)).isNull();
        assertThat(navigation.adjacentComponent("widgets-inputs-tickbox--basic", true)).isNull();
        assertThat(navigation.adjacentComponent("unknown", false)).isNull();
    }

    @Test
    void testFilteredTree() {
        // As built from the stories matching a tag filter, without the logo and the disabled
        // tick box
        final StoryRegistry filtered = new StoryRegistry();
        filtered.component("Widgets/Buttons/Button")
                .story("Default", NO_WIDGET)
                .story("Primary", NO_WIDGET);
        filtered.component("Widgets/Inputs/TickBox")
                .story("Basic", NO_WIDGET);
        final StoryNavigation filteredNavigation = new StoryNavigation(
                StoryTreeBuilder.build(filtered.getStories()));

        // Hidden stories are skipped
        assertThat(filteredNavigation.adjacentStory("widgets-buttons-button--primary", true))
                .isEqualTo("widgets-inputs-tickbox--basic");
        assertThat(filteredNavigation.adjacentStory("widgets-inputs-tickbox--basic", true)).isNull();
        assertThat(filteredNavigation.adjacentComponent("widgets-buttons-button--default", true))
                .isEqualTo("widgets-inputs-tickbox--basic");
        assertThat(filteredNavigation.adjacentComponent("widgets-inputs-tickbox--basic", false))
                .isEqualTo("widgets-buttons-button--default");
        // A story that is filtered out has no neighbours
        assertThat(filteredNavigation.adjacentStory("widgets-display-logo--logo", true)).isNull();
        assertThat(filteredNavigation.adjacentComponent("widgets-display-logo--logo", true)).isNull();
    }

    @Test
    void testEmptyTree() {
        final StoryNavigation empty = new StoryNavigation(List.of());
        assertThat(empty.adjacentStory("widgets-buttons-button--default", true)).isNull();
        assertThat(empty.adjacentComponent("widgets-buttons-button--default", false)).isNull();
    }
}
