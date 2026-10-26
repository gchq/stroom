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
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The Theme tab of the user's preferences ([ThemePreferences]), offering the themes and fonts
/// Stroom has.
public class ThemePreferencesController extends Controller {

    private final ThemePreferences view;
    private final UserPreferencesManager userPreferencesManager;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The tab.
    /// @param userPreferencesManager Gives the themes and fonts.
    @Inject
    public ThemePreferencesController(final EventBus eventBus,
                                      final ThemePreferences view,
                                      final UserPreferencesManager userPreferencesManager) {
        super(eventBus);
        this.view = view;
        this.userPreferencesManager = userPreferencesManager;
        initWidget(view);
    }

    /// Shows the user's theme preferences.
    ///
    /// @param userPreferences The preferences.
    public void read(final UserPreferences userPreferences) {
        view.read(userPreferences, userPreferencesManager.getThemes(), userPreferencesManager.getFonts());
    }

    /// Writes the preferences shown into the user's preferences' builder.
    ///
    /// @param builder The builder.
    public void write(final UserPreferences.Builder builder) {
        view.write(builder);
    }

    /// Puts the focus on the theme, the tab's first preference.
    public void focus() {
        view.focus();
    }

    /// @param onChange What to run when the user changes a theme preference.
    public void setOnChange(final Runnable onChange) {
        view.setOnChange(onChange);
    }
}
