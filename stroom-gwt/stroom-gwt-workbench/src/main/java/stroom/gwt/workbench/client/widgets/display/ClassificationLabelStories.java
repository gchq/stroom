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

package stroom.gwt.workbench.client.widgets.display;

import stroom.data.client.view.ClassificationLabel;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [ClassificationLabel], matching `Widgets/Display/ClassificationLabel` in
/// the React Storybook.
///
/// Stroom's label reads the label colours from the theme's `labelColours` in the UI config (the
/// React port takes them as its `labelColoursCsv` prop), so each story serves them in the UI
/// config fixture of a [ScreenHarness], and creates the label once the config has been fetched.
public final class ClassificationLabelStories {

    // Arg names, the same as the React ClassificationLabel's props
    private static final String TEXT = "text";
    private static final String LABEL_COLOURS_CSV = "labelColoursCsv";

    private ClassificationLabelStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Display/ClassificationLabel", ClassificationLabelStories.class)
                .layout(StoryLayout.PADDED)
                .argType(ArgType.text(TEXT).description("The classification (GWT setClassification(...)), "
                                                        + "upper-cased for display and matching."))
                .argType(ArgType.text(LABEL_COLOURS_CSV)
                        .description("NAME=COLOUR pairs, e.g. 'OFFICIAL=green,SECRET=#ff0000' (in GWT, the "
                                     + "UI config's theme.labelColours)."))
                .args(Args.of(TEXT, "OFFICIAL", LABEL_COLOURS_CSV, DisplayWidgets.COLOURS))
                // Matched against a NAME=COLOUR entry (keyword substring match)
                .story("Official", ClassificationLabelStories::fromArgs)
                .withArgs(Args.of(TEXT, "OFFICIAL", LABEL_COLOURS_CSV, DisplayWidgets.COLOURS))
                // Keyword match: "TOP SECRET" contains "SECRET" too, but the first match wins
                .story("Secret", ClassificationLabelStories::fromArgs)
                .withArgs(Args.of(TEXT, "SECRET", LABEL_COLOURS_CSV, DisplayWidgets.COLOURS))
                // No matching entry falls back to the default #888888 grey
                .story("Unmatched", ClassificationLabelStories::fromArgs)
                .withArgs(Args.of(TEXT, "RESTRICTED", LABEL_COLOURS_CSV, DisplayWidgets.COLOURS))
                // Lower case input is upper-cased before display and matching
                .story("LowerCaseInput", ClassificationLabelStories::fromArgs)
                .withArgs(Args.of(TEXT, "official", LABEL_COLOURS_CSV, DisplayWidgets.COLOURS))
                // All three levels (and an unmatched one) stacked
                .story("AllLevels", context -> {
                    final ScreenHarness harness = DisplayWidgets.classificationHarness(context,
                            DisplayWidgets.COLOURS);
                    final FlowPanel column = StoryPanels.column(8);
                    DisplayWidgets.whenUiConfigLoaded(harness, () -> {
                        for (final String text : new String[]{"OFFICIAL", "SECRET", "TOP SECRET", "RESTRICTED"}) {
                            column.add(label(harness, text));
                        }
                    });
                    harness.add(column);
                    return harness.asWidget();
                });
    }

    /// A label made entirely from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        final Args args = context.getArgs();
        final ScreenHarness harness = DisplayWidgets.classificationHarness(context,
                args.getString(LABEL_COLOURS_CSV));
        DisplayWidgets.whenUiConfigLoaded(harness, () -> harness.add(label(harness, args.getString(TEXT, ""))));
        return harness.asWidget();
    }

    private static ClassificationLabel label(final ScreenHarness harness, final String text) {
        final ClassificationLabel label = new ClassificationLabel(harness.getUiConfigCache());
        label.setClassification(text);
        return label;
    }
}
