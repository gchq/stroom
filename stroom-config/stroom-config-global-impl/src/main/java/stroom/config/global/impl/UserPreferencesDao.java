package stroom.config.global.impl;

import stroom.ui.config.shared.UserPreferences;

import java.util.Optional;

public interface UserPreferencesDao {

    Optional<UserPreferences> fetch(final String userUuid);

    int update(final String userUuid,
               final String userIdentityForAudit,
               final UserPreferences userPreferences);

    int delete(final String userUuid);
}
