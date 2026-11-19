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

package stroom.gwt.workbench.client.widgets.buttons;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.FabButton;

/// Stories for [FabButton], the round floating button (e.g. a Text component's Enter Stepping Mode).
public final class FabButtonStories {

    private FabButtonStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Buttons/FabButton", FabButtonStories.class)
                .layout(StoryLayout.CENTERED)
                // Enabled and disabled
                .story("States", context -> StoryPanels.row(16,
                        fab("Enter Stepping Mode", true),
                        fab("Select a row to step through its source", false)))
                .withPlay(play -> play.expect(play.getByRole("button", "Enter Stepping Mode")).toBeInTheDocument());
    }

    private static FabButton fab(final String name, final boolean enabled) {
        final FabButton button = new FabButton();
        button.setIcon(SvgImage.STEPPING);
        button.setTitle(name);
        button.setEnabled(enabled);
        return button;
    }
}
