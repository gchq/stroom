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

import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.story.StoryContext;

/// Helpers shared by the `Widgets/Display/*` stories.
final class DisplayWidgets {

    /// The React stories' `labelColoursCsv`.
    static final String COLOURS = "OFFICIAL=green,SECRET=#ff9800,TOP SECRET=#f44336";

    private static final String EMPTY_THEME = "\"theme\": {}";

    private DisplayWidgets() {
        // Static utility
    }

    /// Creates a harness whose UI config has a theme with the given label colours, which Stroom's
    /// `ClassificationLabel` reads from the `UiConfigCache` (React passes them as the
    /// `labelColoursCsv` prop).
    ///
    /// @param context         The story's context.
    /// @param labelColoursCsv The label colours, e.g. `OFFICIAL=green,SECRET=#ff9800`, or null for
    ///                        none.
    /// @return The harness.
    static ScreenHarness classificationHarness(final StoryContext context, final String labelColoursCsv) {
        final String theme = labelColoursCsv == null
                ? EMPTY_THEME
                : "\"theme\": {\"labelColours\": \"" + jsonEscape(labelColoursCsv) + "\"}";
        return ScreenHarness.builder(context, RestFixtures.none())
                .uiConfig(StartupFixtures.DEFAULT_UI_CONFIG.replace(EMPTY_THEME, theme))
                .build();
    }

    private static String jsonEscape(final String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /// Runs an action once the harness's UI config has been fetched, so that a
    /// `ClassificationLabel` created by the action reads the label colours at once (it reads them
    /// from the cache when it is created, and ignores a later change).
    ///
    /// @param harness The harness.
    /// @param action  What to do once the UI config is in the cache.
    static void whenUiConfigLoaded(final ScreenHarness harness, final Runnable action) {
        harness.getUiConfigCache().get(config -> {
            // Called once the config has been fetched (with null if fetching it failed)
            if (config != null) {
                action.run();
            }
        });
    }
}
