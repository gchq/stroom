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

package stroom.dashboard.client.vis;

import stroom.preferences.client.DateTimeFormatter;
import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.shared.Theme;
import stroom.ui.config.shared.ThemeType;
import stroom.ui.config.shared.UserPreferences;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;

import javax.inject.Inject;
import javax.inject.Singleton;

/// Builds the `context` object passed to a visualisation's `setData`, so a visualisation can follow
/// the user's preferences: `{preferences: {theme, dateTimePattern, compactDateTimePattern, timeZone}}`.
/// `theme` is `light` or `dark`; the rest is as [DateTimeFormatter#getTimeDisplaySettings()] describes.
@Singleton
public class VisContextFactory {

    private final UserPreferencesManager userPreferencesManager;
    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public VisContextFactory(final UserPreferencesManager userPreferencesManager,
                             final DateTimeFormatter dateTimeFormatter) {
        this.userPreferencesManager = userPreferencesManager;
        this.dateTimeFormatter = dateTimeFormatter;
    }

    /// Creates a new context from the user's current preferences.
    ///
    /// @return The context, as a JavaScript object to post to the visualisation's frame.
    public JavaScriptObject create() {
        final JSONObject preferences = dateTimeFormatter.getTimeDisplaySettings();
        preferences.put("theme", new JSONString(themeName()));

        final JSONObject context = new JSONObject();
        context.put("preferences", preferences);
        return context.getJavaScriptObject();
    }

    private String themeName() {
        final UserPreferences userPreferences = userPreferencesManager.getCurrentUserPreferences();
        final String theme = userPreferences != null
                ? userPreferences.getTheme()
                : null;
        return ThemeType.LIGHT.equals(Theme.fromName(theme).getThemeType())
                ? "light"
                : "dark";
    }
}
