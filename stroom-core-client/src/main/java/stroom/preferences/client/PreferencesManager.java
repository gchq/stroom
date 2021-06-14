package stroom.preferences.client;

import stroom.config.global.shared.PreferencesResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.presenter.CurrentTheme;
import stroom.ui.config.shared.UserPreferences;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.RootPanel;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorTheme;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PreferencesManager {

    private static final PreferencesResource PREFERENCES_RESOURCE = GWT.create(PreferencesResource.class);
    private final RestFactory restFactory;
    private final CurrentTheme currentTheme;

    private static final Map<String, String> themeMap = new HashMap<>();
    private UserPreferences currentPreferences;

    @Inject
    public PreferencesManager(final RestFactory restFactory,
                              final CurrentTheme currentTheme) {
        this.restFactory = restFactory;
        this.currentTheme = currentTheme;

        themeMap.put("Light", "stroom stroom-theme-light");
        themeMap.put("Dark", "stroom stroom-theme-dark");
        themeMap.put("Dark 2", "stroom stroom-theme-dark stroom-theme-dark2");
    }

    public void fetch(final Consumer<UserPreferences> consumer) {
        final Rest<UserPreferences> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(PREFERENCES_RESOURCE)
                .fetch();
    }

    public void update(final UserPreferences userPreferences,
                       final Consumer<Boolean> consumer) {
        final Rest<Boolean> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(PREFERENCES_RESOURCE)
                .update(userPreferences);
    }

    public void setDefaultUserPreferences(final UserPreferences userPreferences,
                                          final Consumer<UserPreferences> consumer) {
        final Rest<UserPreferences> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(PREFERENCES_RESOURCE)
                .setDefaultUserPreferences(userPreferences);
    }

    public void resetToDefaultUserPreferences(final Consumer<UserPreferences> consumer) {
        final Rest<UserPreferences> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(PREFERENCES_RESOURCE)
                .resetToDefaultUserPreferences();
    }

    public void setCurrentPreferences(final UserPreferences userPreferences) {
        this.currentPreferences = userPreferences;

        currentTheme.setTheme(userPreferences.getTheme());
        currentTheme.setEditorTheme(userPreferences.getEditorTheme());

        final com.google.gwt.dom.client.Element element = RootPanel.getBodyElement().getParentElement();
        final String className = themeMap.get(currentTheme.getTheme());
        element.setClassName(className);
    }

    public UserPreferences getCurrentPreferences() {
        return currentPreferences;
    }

    public List<String> getThemes() {
        return themeMap.keySet().stream().sorted().collect(Collectors.toList());
    }

    public List<String> getEditorThemes() {
        return Arrays.stream(AceEditorTheme.values()).map(AceEditorTheme::getName).collect(Collectors.toList());
    }
}
