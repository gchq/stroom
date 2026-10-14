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

package stroom.gwt.workbench.client.widgets.editorsandviewers;

import stroom.editor.client.presenter.EditorPresenter;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.ui.config.shared.AceEditorTheme;

import com.google.gwt.user.client.ui.Widget;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

/// Stories for Ace's editor themes: Stroom's default light and dark themes, and the dark themes
/// that are candidates for the default. Each shows XML and a Stroom query, so the colours of a
/// theme can be compared, and checked for contrast, before and after an upgrade of Ace.
public final class AceEditorThemeStories {

    private static final String QUERY = """
            from "Example Index"
            where EventTime > now() - 1d and UserId = "admin"
            select EventTime, UserId, count() as Events""";

    private AceEditorThemeStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Editors & Viewers/AceEditorThemes", AceEditorThemeStories.class)
                .layout(StoryLayout.CENTERED)
                // Stroom's defaults
                .story("Chrome", context -> themed(AceEditorTheme.CHROME))
                .story("TomorrowNight", context -> themed(AceEditorTheme.TOMORROW_NIGHT))
                // Dark themes that are candidates for the default
                .story("TomorrowNightEighties", context -> themed(AceEditorTheme.TOMORROW_NIGHT_EIGHTIES))
                .story("Dracula", context -> themed(AceEditorTheme.DRACULA));
    }

    /// XML and a Stroom query in the theme.
    private static Widget themed(final AceEditorTheme theme) {
        return StoryPanels.column(8,
                editor(AceEditorStories.SAMPLE_XML, AceEditorMode.XML, theme),
                editor(QUERY, AceEditorMode.STROOM_QUERY, theme));
    }

    private static Widget editor(final String text, final AceEditorMode mode, final AceEditorTheme theme) {
        final EditorPresenter editor = AceEditorFeatureStories.editor(text, mode, true);
        editor.getView().setTheme(theme);
        return AceEditorFeatureStories.framed(editor);
    }
}
