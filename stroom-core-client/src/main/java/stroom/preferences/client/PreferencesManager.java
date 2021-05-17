package stroom.preferences.client;

import stroom.config.global.shared.PreferencesResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.ui.config.shared.UserPreferences;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.RootPanel;

import java.util.Locale;
import java.util.function.Consumer;
import javax.inject.Inject;

public class PreferencesManager {

    private static final PreferencesResource PREFERENCES_RESOURCE = GWT.create(PreferencesResource.class);
    private final RestFactory restFactory;

    @Inject
    public PreferencesManager(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void fetch(final Consumer<UserPreferences> consumer) {
        final Rest<UserPreferences> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(PREFERENCES_RESOURCE)
                .fetch();
    }

    public void update(final UserPreferences userPreferences, final Consumer<UserPreferences> consumer) {
        final Rest<UserPreferences> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(PREFERENCES_RESOURCE)
                .update(userPreferences);
    }

    public void updateClassNames(final UserPreferences userPreferences) {
        final com.google.gwt.dom.client.Element element = RootPanel.getBodyElement().getParentElement();
        element.setClassName("stroom " + "stroom-theme-" + userPreferences.getTheme().toLowerCase(Locale.ROOT));
    }
}
