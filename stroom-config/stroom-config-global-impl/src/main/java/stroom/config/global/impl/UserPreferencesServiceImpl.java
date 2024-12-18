package stroom.config.global.impl;

import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.ui.config.shared.UserPreferences;
import stroom.ui.config.shared.UserPreferencesService;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

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
        return userPreferencesDao.fetchDefault().orElseGet(() ->
                UserPreferences.builder().build());
    }

    @Override
    public boolean delete(final UserRef userRef) {
        // A user can't delete their own user preferences
        return securityContext.secureResult(() -> {
            if (securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
                || securityContext.isCurrentUser(userRef)) {

                return userPreferencesDao.delete(userRef) > 0;
            } else {
                throw new PermissionException(securityContext.getUserRef(),
                        "You must be the owner of the user preferences to delete them, or hold "
                        + AppPermission.MANAGE_USERS_PERMISSION.getDisplayValue() + " permission");
            }
        });
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

    public boolean delete() {
        return delete(securityContext.getUserRef());
    }

    public UserPreferences setDefaultUserPreferences(final UserPreferences userPreferences) {
        return securityContext.secureResult(AppPermission.MANAGE_PROPERTIES_PERMISSION, () -> {
            // Special userUuid for the default prefs
            final UserRef loggedInUserRef = securityContext.getUserRef();
            userPreferencesDao.updateDefault(loggedInUserRef, userPreferences);
            // The user has made the default prefs match their own, so there is no point in
            // them having a prefs record as it is the same as the default.
            userPreferencesDao.delete(loggedInUserRef);
            return userPreferences;
        });
    }
}
