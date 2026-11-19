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

package stroom.gwt.workbench.framework.client.story;

import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.args.ControlType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStoryRegistry {

    private static final StoryRenderer NO_WIDGET = context -> null;

    @Test
    void testComponentStories() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Buttons/Button")
                .story("Default", NO_WIDGET)
                .layout(StoryLayout.CENTERED)
                .story("WithIcons", NO_WIDGET)
                .story("DialogClose", "Dialog — Close button", NO_WIDGET);

        assertThat(registry.getStories())
                .extracting(Story::getId)
                .containsExactly(
                        "widgets-buttons-button--default",
                        "widgets-buttons-button--with-icons",
                        "widgets-buttons-button--dialog-close");
        assertThat(registry.getStories())
                .extracting(Story::getName)
                .containsExactly("Default", "With Icons", "Dialog — Close button");
        assertThat(registry.getStories())
                .extracting(Story::getLayout)
                .containsExactly(StoryLayout.PADDED, StoryLayout.CENTERED, StoryLayout.CENTERED);
        assertThat(registry.getStories())
                .extracting(Story::getTitle)
                .containsOnly("Widgets/Buttons/Button");
    }

    @Test
    void testArgsPlayTagsAndSource() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Buttons/Button", TestStoryRegistry.class)
                .tags("autodocs")
                .argType(ArgType.text("text"))
                .argType(ArgType.bool("loading"))
                // Replaces the earlier arg type of the same name
                .argType(ArgType.text("loading"))
                .args(Args.of("text", "Close"))
                .story("Default", NO_WIDGET)
                .story("Loading", NO_WIDGET)
                .withArgs(Args.of("loading", "yes", "text", "Wait"))
                .withPlay(play -> play.tab())
                .withTags("slow");

        final Story defaultStory = registry.getStory("widgets-buttons-button--default");
        final Story loadingStory = registry.getStory("widgets-buttons-button--loading");

        assertThat(defaultStory.getArgTypes()).extracting(ArgType::getName).containsExactly("text", "loading");
        assertThat(defaultStory.getArgType("loading").getControl()).isEqualTo(ControlType.TEXT);
        assertThat(defaultStory.getArgType("missing")).isNull();
        assertThat(defaultStory.getInitialArgs()).isEqualTo(Args.of("text", "Close"));
        assertThat(defaultStory.getPlay()).isNull();
        assertThat(defaultStory.getTags()).containsExactly("autodocs");
        assertThat(defaultStory.getSourceClassName()).isEqualTo(TestStoryRegistry.class.getName());

        assertThat(loadingStory.getInitialArgs()).isEqualTo(Args.of("text", "Wait", "loading", "yes"));
        assertThat(loadingStory.getPlay()).isNotNull();
        assertThat(loadingStory.getTags()).containsExactly("autodocs", Story.PLAY_TAG, "slow");
    }

    @Test
    void testWithBeforeStory() {
        final StoryRegistry registry = new StoryRegistry();
        assertThatThrownBy(() -> registry.component("A/B").withArgs(Args.empty()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testGetStory() {
        final StoryRegistry registry = new StoryRegistry();
        registry.component("Widgets/Inputs/TickBox")
                .story("Basic", NO_WIDGET)
                .story("NoLabel", NO_WIDGET);

        assertThat(registry.getStory("widgets-inputs-tickbox--no-label").getName())
                .isEqualTo("No Label");
        assertThat(registry.getStory("widgets-inputs-tickbox--missing")).isNull();
        assertThat(registry.getStory(null)).isNull();
        assertThat(registry.getFirstStory().getId()).isEqualTo("widgets-inputs-tickbox--basic");
    }

    @Test
    void testEmpty() {
        final StoryRegistry registry = new StoryRegistry();
        assertThat(registry.getStories()).isEmpty();
        assertThat(registry.getFirstStory()).isNull();
    }

    @Test
    void testDuplicateId() {
        final StoryRegistry registry = new StoryRegistry();
        final ComponentStories component = registry.component("Widgets/Buttons/Button")
                .story("WithIcons", NO_WIDGET);

        // Different export name but the same sanitised id
        assertThatThrownBy(() -> component.story("With_Icons", NO_WIDGET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("widgets-buttons-button--with-icons");
    }

    @Test
    void testBlankTitle() {
        final StoryRegistry registry = new StoryRegistry();
        assertThatThrownBy(() -> registry.component(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.component(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testExportNameWithoutLettersOrDigits() {
        final StoryRegistry registry = new StoryRegistry();
        assertThatThrownBy(() -> registry.component("A/B").story("$", NO_WIDGET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alphanumeric");
    }

    @Test
    void testNullRenderer() {
        final StoryRegistry registry = new StoryRegistry();
        assertThatThrownBy(() -> registry.component("A/B").story("Default", null))
                .isInstanceOf(NullPointerException.class);
    }
}
