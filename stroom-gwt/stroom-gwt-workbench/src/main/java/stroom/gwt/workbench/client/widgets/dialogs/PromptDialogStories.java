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


package stroom.gwt.workbench.client.widgets.dialogs;

import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// Stories for Stroom's prompt, matching `Widgets/Dialogs/PromptDialog` in the React Storybook.
///
/// The same prompt as `Widgets/Dialogs/AlertDialog`'s `Prompt` story: a `PromptEvent` handled by
/// Stroom's `PromptPresenter`.
public final class PromptDialogStories {

    private PromptDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // The React meta's `alerts` arg is a stub that only satisfies the component's types
        registry.component("Widgets/Dialogs/PromptDialog", PromptDialogStories.class)
                .layout(StoryLayout.CENTERED)
                // Prompt dialog (text input with OK / Cancel)
                .story("Prompt", AlertDialogStories::prompt);
    }
}
