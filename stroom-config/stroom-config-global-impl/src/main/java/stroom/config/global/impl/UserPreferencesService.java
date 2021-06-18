package stroom.config.global.impl;

import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.ui.config.shared.UserPreferences;

import java.util.Optional;
import javax.inject.Inject;

public class UserPreferencesService {

    private static final String DEFAULT_PREFERENCES = "__default__";

    private final UserPreferencesDao userPreferencesDao;
    private final SecurityContext securityContext;

    @Inject
    public UserPreferencesService(final UserPreferencesDao userPreferencesDao,
                                  final SecurityContext securityContext) {
        this.userPreferencesDao = userPreferencesDao;
        this.securityContext = securityContext;
    }

    public UserPreferences fetch() {
        return securityContext.secureResult(() -> {
            Optional<UserPreferences> optionalUserPreferences = userPreferencesDao.fetch(securityContext.getUserId());
            if (optionalUserPreferences.isEmpty()) {
                optionalUserPreferences = userPreferencesDao.fetch(DEFAULT_PREFERENCES);
            }
            return optionalUserPreferences.orElseGet(() -> UserPreferences.builder().build());
        });
    }

    public int update(final UserPreferences userPreferences) {
        return securityContext.secureResult(() ->
                userPreferencesDao.update(securityContext.getUserId(), userPreferences));
    }

    public int delete() {
        return securityContext.secureResult(() ->
                userPreferencesDao.delete(securityContext.getUserId()));
    }

    public UserPreferences setDefaultUserPreferences(final UserPreferences userPreferences) {
        return securityContext.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            userPreferencesDao.update(DEFAULT_PREFERENCES, userPreferences);
            delete();
            return userPreferences;
        });
    }
}
