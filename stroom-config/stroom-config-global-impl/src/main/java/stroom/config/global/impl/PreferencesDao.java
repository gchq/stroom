package stroom.config.global.impl;

import stroom.ui.config.shared.UserPreferences;

public interface PreferencesDao {

    UserPreferences fetch(String userId);

    int update(String userId, UserPreferences userPreferences);

    int delete(String userId);
}
