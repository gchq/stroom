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


package stroom.gwt.workbench.client.widgets.overlays;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.help.client.HelpButton;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [HelpButton], matching `Widgets/Overlays/HelpButton` in the React
/// Storybook. Its help popup is shown by Stroom's `HelpManager`.
public final class HelpButtonStories {

    private HelpButtonStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Overlays/HelpButton", HelpButtonStories.class)
                .layout(StoryLayout.CENTERED)
                // HelpButton alongside a label
                .story("Default", context -> labelled(context, "Some label", helpButton(
                        "Pipeline Filters", "<p>Filters control which data streams are processed.</p>")))
                // HelpButton removed from the tab order for dense form navigation
                .story("NoTabFocus", context -> {
                    final HelpButton helpButton = helpButton(
                            "Tab Behaviour", "<p>Removed from tab order for form navigation.</p>");
                    helpButton.preventTabFocus();
                    return labelled(context, "Form field", helpButton);
                });
    }

    /// A help button as React's `<HelpButton heading content>` makes one: its title falls back to
    /// the heading, as `HelpButton.create(title)` is given in Stroom.
    private static HelpButton helpButton(final String heading, final String html) {
        final HelpButton helpButton = HelpButton.create(heading);
        helpButton.setHelpContent(heading, SafeHtmlUtils.fromTrustedString(html));
        return helpButton;
    }

    private static Widget labelled(final StoryContext context, final String label, final HelpButton helpButton) {
        StoryPopups.create(context).withHelp();
        return StoryPanels.row(8, new InlineLabel(label), helpButton);
    }
}
