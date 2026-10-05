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

package stroom.gwt.workbench.client;

import stroom.gwt.workbench.framework.client.story.Story;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAllStories {

    @Test
    void testCreate() {
        // Registering the stories doesn't create any widgets, so this runs outside a browser.
        // The registry would throw if two stories had the same id.
        final StoryRegistry registry = AllStories.create();

        assertThat(registry.getStories()).isNotEmpty();
        assertThat(registry.getStories())
                .extracting(Story::getId)
                .allMatch(id -> id.matches("[a-z0-9]+(-[a-z0-9]+)*--[a-z0-9]+(-[a-z0-9]+)*"));
    }

    @Test
    void testIdsMatchReactStorybook() {
        final StoryRegistry registry = AllStories.create();

        // Ids of stories in the React Storybook, so the two can be compared at the same URL
        assertThat(registry.getStories())
                .extracting(Story::getId)
                .contains(
                        "widgets-buttons-button--default",
                        "widgets-buttons-button--with-icons",
                        "widgets-inputs-tickbox--basic",
                        "widgets-inputs-tickbox--no-label");
    }
}
