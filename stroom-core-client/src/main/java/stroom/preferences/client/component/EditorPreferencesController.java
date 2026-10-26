/*
 * Copyright 2023 Crown Copyright
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

package stroom.preferences.client.component;

import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.shared.AceEditorTheme;
import stroom.ui.config.shared.Theme;
import stroom.ui.config.shared.ThemeType;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// The Editor tab of the user's preferences ([EditorPreferences]): it offers the editor themes for
/// the user's theme, light or dark, and changes them when that does.
public class EditorPreferencesController extends Controller {

    private final EditorPreferences view;
    private final UserPreferencesManager userPreferencesManager;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The tab.
    /// @param userPreferencesManager Gives the editor themes.
    @Inject
    public EditorPreferencesController(final EventBus eventBus,
                                       final EditorPreferences view,
                                       final UserPreferencesManager userPreferencesManager) {
        super(eventBus);
        this.view = view;
        this.userPreferencesManager = userPreferencesManager;
        initWidget(view);
    }

    /// Offers the editor themes of a new theme type, and chooses its default, if the user's theme
    /// changed type, e.g. from light to dark.
    ///
    /// @param themeTypeBefore The type of the theme before.
    /// @param themeTypeAfter  The type of the theme now.
    /// @return Whether the editor's theme changed.
    public boolean updateTheme(final ThemeType themeTypeBefore, final ThemeType themeTypeAfter) {
        if (!Objects.equals(themeTypeBefore, themeTypeAfter)) {
            // As we have changed theme type, we need to set an editor theme for that new type
            view.setEditorThemes(userPreferencesManager.getEditorThemes(themeTypeAfter),
                    userPreferencesManager.getDefaultEditorTheme(themeTypeAfter));
            return true;
        }
        return false;
    }

    /// Shows the user's editor preferences.
    ///
    /// @param userPreferences The preferences.
    public void read(final UserPreferences userPreferences) {
        final ThemeType themeType = Theme.getThemeType(userPreferences.getTheme());
        String editorThemeName = userPreferences.getEditorTheme();
        if (!AceEditorTheme.isValidThemeName(editorThemeName)
            || !AceEditorTheme.matchesThemeType(editorThemeName, themeType)) {
            // e.g. light editor theme with a dark stroom theme, so use the default dark editor theme
            editorThemeName = AceEditorTheme.getDefaultEditorTheme(themeType).getName();
        }
        // The editor themes for the stroom theme's type
        view.setEditorThemes(userPreferencesManager.getEditorThemes(themeType), editorThemeName);
        view.read(userPreferences);
    }

    /// Writes the preferences shown into the user's preferences' builder.
    ///
    /// @param builder The builder.
    public void write(final UserPreferences.Builder builder) {
        view.write(builder);
    }

    /// @param onChange What to run when the user changes an editor preference.
    public void setOnChange(final Runnable onChange) {
        view.setOnChange(onChange);
    }
}
