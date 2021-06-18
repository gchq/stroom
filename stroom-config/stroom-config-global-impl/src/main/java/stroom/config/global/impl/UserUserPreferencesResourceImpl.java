package stroom.config.global.impl;

import stroom.config.global.shared.UserPreferencesResource;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.ui.config.shared.UserPreferences;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
public class UserUserPreferencesResourceImpl implements UserPreferencesResource {

    private final Provider<UserPreferencesService> preferencesServiceProvider;

    @Inject
    UserUserPreferencesResourceImpl(final Provider<UserPreferencesService> preferencesServiceProvider) {

        this.preferencesServiceProvider = Objects.requireNonNull(preferencesServiceProvider);
    }

    //    @AutoLogged(OperationType.UNLOGGED) // Called constantly by UI code not user. No need to log.
    @Override
    public UserPreferences fetch() {
        return preferencesServiceProvider.get().fetch();
    }

    @Override
    public boolean update(final UserPreferences userPreferences) {
        return preferencesServiceProvider.get().update(userPreferences) > 0;
    }

    @Override
    public UserPreferences setDefaultUserPreferences(final UserPreferences userPreferences) {
        return preferencesServiceProvider.get().setDefaultUserPreferences(userPreferences);
    }

    @Override
    public UserPreferences resetToDefaultUserPreferences() {
        preferencesServiceProvider.get().delete();
        return preferencesServiceProvider.get().fetch();
    }
}
