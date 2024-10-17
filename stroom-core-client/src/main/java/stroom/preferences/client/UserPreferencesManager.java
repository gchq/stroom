package stroom.preferences.client;

import stroom.config.global.shared.UserPreferencesResource;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.presenter.CurrentPreferences;
import stroom.expression.api.UserTimeZone;
import stroom.expression.api.UserTimeZone.Use;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.shared.AceEditorTheme;
import stroom.ui.config.shared.Theme;
import stroom.ui.config.shared.ThemeCssUtil;
import stroom.ui.config.shared.ThemeType;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.GwtNullSafe;
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

@Singleton
public class UserPreferencesManager {

    private static final UserPreferencesResource PREFERENCES_RESOURCE = GWT.create(UserPreferencesResource.class);
    private final RestFactory restFactory;
    private final CurrentPreferences currentPreferences;

    private UserPreferences currentUserPreferences;

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

    public void setCurrentPreferences(final UserPreferences userPreferences) {
        this.currentUserPreferences = userPreferences;
        applyUserPreferences(this.currentPreferences, userPreferences);

        final Element element = RootPanel.getBodyElement().getParentElement();
        String className = getCurrentPreferenceClasses();
        element.setClassName(className);

        ClientTimeZone.setTimeZone(getTimeZone(currentUserPreferences));
    }

    private String getTimeZone(final UserPreferences userPreferences) {
        final UserTimeZone userTimeZone = userPreferences.getTimeZone();
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

        final int hours = GwtNullSafe.requireNonNullElse(userTimeZone.getOffsetHours(), 0);
        int minutes = GwtNullSafe.requireNonNullElse(userTimeZone.getOffsetMinutes(), 0);

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

    public UserPreferences getCurrentUserPreferences() {
        return currentUserPreferences;
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
        return ThemeCssUtil.getCurrentPreferenceClasses(currentUserPreferences);
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
        if (currentUserPreferences != null &&
                currentUserPreferences.getTimeZone() != null &&
                currentUserPreferences.getTimeZone().getUse() != null &&
                currentUserPreferences.getTimeZone().getUse() != Use.UTC) {
            return false;
        }
        return true;
    }

    static CurrentPreferences buildCurrentPreferences(final UserPreferences userPreferences) {
        Objects.requireNonNull(userPreferences);
        final CurrentPreferences currentPreferences = new CurrentPreferences();
        applyUserPreferences(currentPreferences, userPreferences);
        return currentPreferences;
    }

    static void applyUserPreferences(final CurrentPreferences currentPreferences,
                                     final UserPreferences userPreferences) {
        currentPreferences.setTheme(userPreferences.getTheme());
        currentPreferences.setEditorTheme(userPreferences.getEditorTheme());
        currentPreferences.setEditorKeyBindings(userPreferences.getEditorKeyBindings().name());
        currentPreferences.setEditorLiveAutoCompletion(userPreferences.getEditorLiveAutoCompletion());
    }
}
