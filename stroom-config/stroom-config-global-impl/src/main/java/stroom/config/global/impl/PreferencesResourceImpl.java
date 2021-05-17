package stroom.config.global.impl;

import stroom.config.global.shared.PreferencesResource;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.ui.config.shared.UserPreferences;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
public class PreferencesResourceImpl implements PreferencesResource {

    private final Provider<PreferencesService> preferencesServiceProvider;

    @Inject
    PreferencesResourceImpl(final Provider<PreferencesService> preferencesServiceProvider) {

        this.preferencesServiceProvider = Objects.requireNonNull(preferencesServiceProvider);
    }

    //    @AutoLogged(OperationType.UNLOGGED) // Called constantly by UI code not user. No need to log.
    @Override
    public UserPreferences fetch() {
        return preferencesServiceProvider.get().fetch();
    }

    @Override
    public int update(final UserPreferences userPreferences) {
        return preferencesServiceProvider.get().update(userPreferences);
    }
}
