package stroom.config.global.impl;

import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.ui.config.shared.UserPreferences;
import stroom.ui.config.shared.UserPreferencesService;

import jakarta.inject.Inject;

import java.util.Optional;

public class UserPreferencesServiceImpl implements UserPreferencesService {

    private final UserPreferencesDao userPreferencesDao;
    private final SecurityContext securityContext;

    @Inject
    public UserPreferencesServiceImpl(final UserPreferencesDao userPreferencesDao,
                                      final SecurityContext securityContext) {
        this.userPreferencesDao = userPreferencesDao;
        this.securityContext = securityContext;
    }

    @Override
    public UserPreferences fetchDefault() {
        return userPreferencesDao.fetchDefault().orElseGet(() -> UserPreferences.builder().build());
    }

    public UserPreferences fetch() {
        return securityContext.secureResult(() -> {
            Optional<UserPreferences> optionalUserPreferences = userPreferencesDao.fetch(securityContext.getUserRef());
            if (optionalUserPreferences.isEmpty()) {
                optionalUserPreferences = userPreferencesDao.fetchDefault();
            }
            return optionalUserPreferences.orElseGet(() -> UserPreferences.builder().build());
        });
    }

    public int update(final UserPreferences userPreferences) {
        return securityContext.secureResult(() ->
                userPreferencesDao.update(
                        securityContext.getUserRef(),
                        userPreferences));
    }

    public int delete() {
        return securityContext.secureResult(() ->
                userPreferencesDao.delete(securityContext.getUserRef()));
    }

    public UserPreferences setDefaultUserPreferences(final UserPreferences userPreferences) {
        return securityContext.secureResult(AppPermission.MANAGE_PROPERTIES_PERMISSION, () -> {
            // Special userUuid for the default prefs
            userPreferencesDao.updateDefault(
                    securityContext.getUserRef(),
                    userPreferences);
            delete();
            return userPreferences;
        });
    }
}
