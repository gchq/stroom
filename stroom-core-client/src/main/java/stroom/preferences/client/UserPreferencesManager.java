/*
 * Copyright 2021 Crown Copyright
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

package stroom.preferences.client;

import stroom.config.global.shared.UserPreferencesResource;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.presenter.CurrentPreferences;
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.shared.AceEditorTheme;
import stroom.ui.config.shared.Theme;
import stroom.ui.config.shared.ThemeCssUtil;
import stroom.ui.config.shared.ThemeType;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.datepicker.client.ClientTimeZone;
import stroom.widget.util.client.ClientStringUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.RootPanel;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/// Holds and applies the current user's preferences.
///
/// The app fetches the user's preferences, and sets them with
/// [#setCurrentPreferences(UserPreferences)], before it shows anything else (see `App` and
/// `DashboardApp`), so after start up they are always the user's. Before then (and on pages
/// shown without signing in) [#getCurrentUserPreferences()] gives defaults rather than null.
/// Code that changes and saves the preferences uses [#whenLoaded(Consumer)], so that it never
/// saves the defaults over the user's preferences.
@Singleton
public class UserPreferencesManager {

    private static final UserPreferencesResource PREFERENCES_RESOURCE = GWT.create(UserPreferencesResource.class);
    private final RestFactory restFactory;
    private final CurrentPreferences currentPreferences;

    private final UserPreferencesHolder userPreferencesHolder = new UserPreferencesHolder();

    @Inject
    public UserPreferencesManager(final RestFactory restFactory,
                                  final CurrentPreferences currentPreferences) {
        this.restFactory = restFactory;
        this.currentPreferences = currentPreferences;
    }

    public void fetch(final Consumer<UserPreferences> consumer, final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PREFERENCES_RESOURCE)
                .method(UserPreferencesResource::fetch)
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void update(final UserPreferences userPreferences,
                       final Consumer<Boolean> consumer,
                       final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PREFERENCES_RESOURCE)
                .method(res -> res.update(userPreferences))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Saves the user's preferences, telling the caller if they can't be saved, e.g. so that a
    /// dialog waiting for the save can be used again.
    ///
    /// @param userPreferences    The preferences to save.
    /// @param consumer           Called once they have been saved.
    /// @param errorHandler       Called if they can't be saved.
    /// @param taskMonitorFactory Shows that the save is in progress.
    public void update(final UserPreferences userPreferences,
                       final Consumer<Boolean> consumer,
                       final RestErrorHandler errorHandler,
                       final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PREFERENCES_RESOURCE)
                .method(res -> res.update(userPreferences))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void setDefaultUserPreferences(final UserPreferences userPreferences,
                                          final Consumer<UserPreferences> consumer,
                                          final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PREFERENCES_RESOURCE)
                .method(res -> res.setDefaultUserPreferences(userPreferences))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void resetToDefaultUserPreferences(final Consumer<UserPreferences> consumer,
                                              final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(PREFERENCES_RESOURCE)
                .method(UserPreferencesResource::resetToDefaultUserPreferences)
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Sets and applies the user's preferences, e.g. once they have been fetched when the app
    /// starts or after the user changes them. The first time, this calls any consumers waiting in
    /// [#whenLoaded(Consumer)].
    ///
    /// @param userPreferences The user's preferences.
    public void setCurrentPreferences(final UserPreferences userPreferences) {
        Objects.requireNonNull(userPreferences, "userPreferences");
        applyUserPreferences(this.currentPreferences, userPreferences);

        final Element element = RootPanel.getBodyElement().getParentElement();
        final String className = ThemeCssUtil.getCurrentPreferenceClasses(userPreferences);
        element.setClassName(className);

        ClientTimeZone.setTimeZone(getTimeZone(userPreferences));

        // Last, so that waiting consumers see the preferences applied
        userPreferencesHolder.set(userPreferences);
    }

    /// @return True once the user's preferences have been loaded and set.
    public boolean isLoaded() {
        return userPreferencesHolder.isLoaded();
    }

    /// Calls the consumer with the user's preferences: at once if they have been loaded,
    /// otherwise once, when they are. Unlike [#getCurrentUserPreferences()] it never gives the
    /// defaults, so use it to change and save the preferences without losing the user's.
    ///
    /// @param consumer Called once with the user's preferences.
    public void whenLoaded(final Consumer<UserPreferences> consumer) {
        userPreferencesHolder.whenLoaded(consumer);
    }

    private String getTimeZone(final UserPreferences userPreferences) {
        final UserTimeZone userTimeZone = userPreferences.getTimeZone();
        if (userTimeZone == null) {
            // e.g. the server's own default preferences, which have no time zone
            return "UTC";
        }
        String timeZone = null;
        switch (userTimeZone.getUse()) {
            case UTC: {
                timeZone = "UTC";
                break;
            }
            case ID: {
                timeZone = userTimeZone.getId();
                break;
            }
            case OFFSET: {
                timeZone = getPosixOffset(userTimeZone);
                break;
            }
        }
        return timeZone;
    }

    /**
     * An offset specifies the hours, and optionally minutes and seconds, difference from UTC.
     * It has the format hh[:mm[:ss]] optionally with a leading sign (+ or -).
     * The positive sign is used for zones west of Greenwich.
     * (Note that this is the opposite of the ISO-8601 sign convention which is output on format.)
     * hh can have one or two digits; mm and ss (if used) must have two.
     *
     * @param userTimeZone The user time zone to get the POSIX compliant offset string for.
     * @return The POSIX compliant timezone offset string.
     */
    private String getPosixOffset(final UserTimeZone userTimeZone) {

        final int hours = Objects.requireNonNullElse(userTimeZone.getOffsetHours(), 0);
        int minutes = Objects.requireNonNullElse(userTimeZone.getOffsetMinutes(), 0);

        // FIXME:  Browsers don't support minute offsets so disable this for now.
        minutes = 0;

        String offset = "";
        if (hours != 0 && minutes != 0) {
            final String hoursString = "" + hours;
            final String minutesString = ClientStringUtil.zeroPad(2, minutes);
            offset = hoursString + ":" + minutesString;
            if (hours >= 0 && minutes >= 0) {
                offset = "-" + offset;
            } else {
                offset = "+" + offset;
            }
        } else if (hours != 0) {
            offset = "" + hours;
            if (hours >= 0) {
                offset = "-" + offset;
            } else {
                offset = "+" + offset;
            }
        }

        return "Etc/GMT" + offset;
    }

    /// @return The user's preferences, or defaults if they haven't been loaded yet (see the class
    /// description). Never null.
    public UserPreferences getCurrentUserPreferences() {
        return userPreferencesHolder.get();
    }

    public CurrentPreferences getCurrentPreferences() {
        return currentPreferences;
    }

    public ThemeType geCurrentThemeType() {
        return Theme.getThemeType(currentPreferences.getTheme());
    }

    /**
     * @return A space delimited list of css classes for theme, density, font and font size.
     */
    public String getCurrentPreferenceClasses() {
        return ThemeCssUtil.getCurrentPreferenceClasses(getCurrentUserPreferences());
    }

    public boolean isHideConditionalStyles() {
        return Objects.requireNonNullElse(getCurrentUserPreferences().getHideConditionalStyles(), false);
    }

    public List<String> getThemes() {
        return Theme.getThemeNames();
    }

    public List<String> getFonts() {
        return ThemeCssUtil.getFonts();
    }

    public List<String> getEditorThemes(final ThemeType themeType) {
        return AceEditorTheme.getThemesByType(Objects.requireNonNull(themeType))
                .stream()
                .map(AceEditorTheme::getName)
                .collect(Collectors.toList());
    }

    public String getDefaultEditorTheme(final ThemeType themeType) {
        final AceEditorTheme aceEditorTheme = themeType.isLight()
                ? AceEditorTheme.DEFAULT_LIGHT_THEME
                : AceEditorTheme.DEFAULT_DARK_THEME;
        return aceEditorTheme.getName();
    }

    public boolean isUtc() {
        final UserPreferences userPreferences = getCurrentUserPreferences();
        return userPreferences.getTimeZone() == null
               || userPreferences.getTimeZone().getUse() == null
               || userPreferences.getTimeZone().getUse() == Use.UTC;
    }

    static void applyUserPreferences(final CurrentPreferences currentPreferences,
                                     final UserPreferences userPreferences) {
        currentPreferences.setTheme(userPreferences.getTheme());
        currentPreferences.setEditorTheme(userPreferences.getEditorTheme());
        currentPreferences.setEditorKeyBindings(userPreferences.getEditorKeyBindings().name());
        currentPreferences.setEditorLiveAutoCompletion(userPreferences.getEditorLiveAutoCompletion());
    }
}
