package stroom.config.global.impl;

import stroom.ui.config.shared.UserPreferences;

import java.util.Optional;

public interface UserPreferencesDao {

    Optional<UserPreferences> fetch(String userId);

    int update(String userId, UserPreferences userPreferences);

    int delete(String userId);
}
