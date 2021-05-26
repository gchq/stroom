package stroom.config.global.impl;

import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.ui.config.shared.UserPreferences;

import java.util.Optional;
import javax.inject.Inject;

public class PreferencesService {

    private static final String DEFAULT_PREFERENCES = "__default__";

    private final PreferencesDao preferencesDao;
    private final SecurityContext securityContext;

    @Inject
    public PreferencesService(final PreferencesDao preferencesDao,
                              final SecurityContext securityContext) {
        this.preferencesDao = preferencesDao;
        this.securityContext = securityContext;
    }

    public UserPreferences fetch() {
        return securityContext.secureResult(() -> {
            Optional<UserPreferences> optionalUserPreferences = preferencesDao.fetch(securityContext.getUserId());
            if (optionalUserPreferences.isEmpty()) {
                optionalUserPreferences = preferencesDao.fetch(DEFAULT_PREFERENCES);
            }
            return optionalUserPreferences.orElseGet(() -> UserPreferences.builder().build());
        });
    }

    public int update(final UserPreferences userPreferences) {
        return securityContext.secureResult(() -> preferencesDao.update(securityContext.getUserId(), userPreferences));
    }

    public int delete() {
        return securityContext.secureResult(() -> preferencesDao.delete(securityContext.getUserId()));
    }

    public UserPreferences setDefaultUserPreferences(final UserPreferences userPreferences) {
        return securityContext.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            preferencesDao.update(DEFAULT_PREFERENCES, userPreferences);
            delete();
            return userPreferences;
        });
    }
}
