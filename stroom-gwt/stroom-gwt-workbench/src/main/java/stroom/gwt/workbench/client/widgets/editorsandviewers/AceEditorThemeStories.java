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

import stroom.editor.client.component.CodeEditorController;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.StroomThemeDecorator;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.ui.config.shared.AceEditorTheme;
import stroom.ui.config.shared.Theme;

import com.google.gwt.user.client.ui.Widget;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

/// Stories for the editor themes: Stroom's own default theme, Stroom's earlier defaults, the dark
/// themes that were candidates for the default, and the themes Ace 1.44 added. Each shows XML and a Stroom
/// query, so the colours of a theme can be compared, and checked for contrast, before and after an
/// upgrade of Ace.
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
                // Stroom's default, Stroom Light or Stroom Dark, which take their colours from the
                // Stroom theme, so are shown in the workbench's theme
                .story("Stroom", context -> themed(AceEditorTheme.getDefaultEditorTheme(
                        Theme.getThemeType(StroomThemeDecorator.getStroomThemeName()))))
                // Stroom's earlier defaults
                .story("Chrome", context -> themed(AceEditorTheme.CHROME))
                .story("TomorrowNight", context -> themed(AceEditorTheme.TOMORROW_NIGHT))
                // Dark themes that are candidates for the default
                .story("TomorrowNightEighties", context -> themed(AceEditorTheme.TOMORROW_NIGHT_EIGHTIES))
                .story("Dracula", context -> themed(AceEditorTheme.DRACULA))
                // Themes Ace 1.44 added
                .story("Cloud9Day", context -> themed(AceEditorTheme.CLOUD9_DAY))
                .story("Cloud9Night", context -> themed(AceEditorTheme.CLOUD9_NIGHT))
                .story("Cloud9NightLowColor", context -> themed(AceEditorTheme.CLOUD9_NIGHT_LOW_COLOR))
                .story("CloudEditor", context -> themed(AceEditorTheme.CLOUD_EDITOR))
                .story("CloudEditorDark", context -> themed(AceEditorTheme.CLOUD_EDITOR_DARK))
                .story("GithubDark", context -> themed(AceEditorTheme.GITHUB_DARK))
                .story("GithubLightDefault", context -> themed(AceEditorTheme.GITHUB_LIGHT_DEFAULT))
                .story("GruvboxDarkHard", context -> themed(AceEditorTheme.GRUVBOX_DARK_HARD))
                .story("GruvboxLightHard", context -> themed(AceEditorTheme.GRUVBOX_LIGHT_HARD))
                .story("OneDark", context -> themed(AceEditorTheme.ONE_DARK));
    }

    /// XML and a Stroom query in the theme, inside the Stroom theme of the same type (e.g. a dark
    /// editor theme in the dark theme, whichever the workbench shows), as Stroom only offers an
    /// editor theme with a Stroom theme of its type.
    private static Widget themed(final AceEditorTheme theme) {
        final Widget column = StoryPanels.column(8,
                editor(AceEditorStories.SAMPLE_XML, AceEditorMode.XML, theme),
                editor(QUERY, AceEditorMode.STROOM_QUERY, theme));
        column.addStyleName(theme.isDark()
                ? Theme.DARK.getCssClass()
                : Theme.LIGHT.getCssClass());
        return column;
    }

    private static Widget editor(final String text, final AceEditorMode mode, final AceEditorTheme theme) {
        final CodeEditorController editor = AceEditorFeatureStories.editor(text, mode, true);
        editor.getCodeEditor().setTheme(theme);
        return AceEditorFeatureStories.framed(editor);
    }
}
