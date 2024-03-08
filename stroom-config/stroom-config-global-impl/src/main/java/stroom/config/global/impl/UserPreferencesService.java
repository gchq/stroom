package stroom.config.global.impl;

import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.ui.config.shared.UserPreferences;

import jakarta.inject.Inject;

import java.util.Optional;

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
            Optional<UserPreferences> optionalUserPreferences = userPreferencesDao.fetch(securityContext.getUserUuid());
            if (optionalUserPreferences.isEmpty()) {
                optionalUserPreferences = userPreferencesDao.fetch(DEFAULT_PREFERENCES);
            }
            return optionalUserPreferences.orElseGet(() -> UserPreferences.builder().build());
        });
    }

    public int update(final UserPreferences userPreferences) {
        return securityContext.secureResult(() ->
                userPreferencesDao.update(
                        securityContext.getUserUuid(),
                        securityContext.getUserIdentityForAudit(),
                        userPreferences));
    }

    public int delete() {
        return securityContext.secureResult(() ->
                userPreferencesDao.delete(securityContext.getUserUuid()));
    }

    public UserPreferences setDefaultUserPreferences(final UserPreferences userPreferences) {
        return securityContext.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            // Special userUuid for the default prefs
            userPreferencesDao.update(
                    DEFAULT_PREFERENCES,
                    securityContext.getUserIdentityForAudit(),
                    userPreferences);
            delete();
            return userPreferences;
        });
    }
}
