package stroom.config.global.impl;

import stroom.security.api.SecurityContext;
import stroom.ui.config.shared.UserPreferences;

import javax.inject.Inject;

public class PreferencesService {

    private final PreferencesDao preferencesDao;
    private final SecurityContext securityContext;

    @Inject
    public PreferencesService(final PreferencesDao preferencesDao,
                              final SecurityContext securityContext) {
        this.preferencesDao = preferencesDao;
        this.securityContext = securityContext;
    }

    public UserPreferences fetch() {
        return securityContext.secureResult(() -> preferencesDao.fetch(securityContext.getUserId()));
    }

    public int update(final UserPreferences userPreferences) {
        return securityContext.secureResult(() -> preferencesDao.update(securityContext.getUserId(), userPreferences));
    }

    public int delete() {
        return securityContext.secureResult(() -> preferencesDao.delete(securityContext.getUserId()));
    }
}
