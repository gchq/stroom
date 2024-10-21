package stroom.config.global.impl;

import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.UserRef;

import java.util.Optional;

public interface UserPreferencesDao {

    Optional<UserPreferences> fetchDefault();

    Optional<UserPreferences> fetch(UserRef userRef);

    int update(UserRef userRef,
               UserPreferences userPreferences);

    int updateDefault(UserRef userRef,
                      UserPreferences userPreferences);

    int delete(UserRef userRef);
}
