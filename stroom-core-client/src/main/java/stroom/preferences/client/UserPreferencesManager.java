package stroom.preferences.client;

import stroom.config.global.shared.UserPreferencesResource;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.presenter.CurrentPreferences;
import stroom.expression.api.UserTimeZone;
import stroom.expression.api.UserTimeZone.Use;
import stroom.ui.config.shared.Themes;
import stroom.ui.config.shared.Themes.ThemeType;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.datepicker.client.ClientTimeZone;
import stroom.widget.util.client.ClientStringUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.RootPanel;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorTheme;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UserPreferencesManager {

    private static final UserPreferencesResource PREFERENCES_RESOURCE = GWT.create(UserPreferencesResource.class);
    private final RestFactory restFactory;
    private final CurrentPreferences currentPreferences;

    private static final Map<String, String> densityMap = new HashMap<>();
    private static final Map<String, String> fontMap = new HashMap<>();
    private static final Map<String, String> fontSizeMap = new HashMap<>();
    private UserPreferences currentUserPreferences;

    @Inject
    public UserPreferencesManager(final RestFactory restFactory,
                                  final CurrentPreferences currentPreferences) {
        this.restFactory = restFactory;
        this.currentPreferences = currentPreferences;

        densityMap.put("Comfortable", "stroom-density-comfortable");
        densityMap.put("Compact", "stroom-density-compact");

        fontMap.put("Arial", "stroom-font-arial");
        fontMap.put("Open Sans", "stroom-font-open-sans");
        fontMap.put("Roboto", "stroom-font-roboto");
        fontMap.put("Tahoma", "stroom-font-tahoma");
        fontMap.put("Verdana", "stroom-font-verdana");

        fontSizeMap.put("Small", "stroom-font-size-small");
        fontSizeMap.put("Medium", "stroom-font-size-medium");
        fontSizeMap.put("Large", "stroom-font-size-large");
    }

    public void fetch(final Consumer<UserPreferences> consumer) {
        restFactory
                .forType(UserPreferences.class)
                .onSuccess(consumer)
                .call(PREFERENCES_RESOURCE)
                .fetch();
    }

    public void update(final UserPreferences userPreferences,
                       final Consumer<Boolean> consumer) {
        restFactory
                .forType(Boolean.class)
                .onSuccess(consumer)
                .call(PREFERENCES_RESOURCE)
                .update(userPreferences);
    }

    public void setDefaultUserPreferences(final UserPreferences userPreferences,
                                          final Consumer<UserPreferences> consumer) {
        restFactory
                .forType(UserPreferences.class)
                .onSuccess(consumer)
                .call(PREFERENCES_RESOURCE)
                .setDefaultUserPreferences(userPreferences);
    }

    public void resetToDefaultUserPreferences(final Consumer<UserPreferences> consumer) {
        restFactory
                .forType(UserPreferences.class)
                .onSuccess(consumer)
                .call(PREFERENCES_RESOURCE)
                .resetToDefaultUserPreferences();
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
        return Themes.getThemeType(currentPreferences.getTheme());
    }

    /**
     * @return A space delimited list of css classes for theme, density, font and font size.
     */
    public String getCurrentPreferenceClasses() {
        final StringJoiner classJoiner = new StringJoiner(" ")
                .add("stroom");

        if (currentUserPreferences != null) {
            GwtNullSafe.consume(currentUserPreferences.getTheme(), theme ->
                    classJoiner.add(Themes.getClassName(theme)));

            if (GwtNullSafe.requireNonNullElse(currentUserPreferences.getEnableTransparency(), true)) {
                classJoiner.add("transparency");
            }

            Optional.ofNullable(currentUserPreferences.getDensity())
                    .map(densityMap::get)
                    .ifPresent(classJoiner::add);

            Optional.ofNullable(currentUserPreferences.getFont())
                    .map(fontMap::get)
                    .ifPresent(classJoiner::add);

            Optional.ofNullable(currentUserPreferences.getFontSize())
                    .map(fontSizeMap::get)
                    .ifPresent(classJoiner::add);
        }
        return classJoiner.toString();
    }

    public List<String> getThemes() {
        return Themes.getThemeNames();
    }

    public List<String> getFonts() {
        return fontMap.keySet()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> getEditorThemes(final ThemeType themeType) {

        final List<AceEditorTheme> themes = Objects.requireNonNull(themeType).equals(ThemeType.LIGHT)
                ? AceEditorTheme.getLightThemes()
                : AceEditorTheme.getDarkThemes();
        return themes.stream()
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
