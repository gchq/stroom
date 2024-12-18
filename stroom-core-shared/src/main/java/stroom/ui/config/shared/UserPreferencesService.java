package stroom.ui.config.shared;

import stroom.util.shared.UserRef;

public interface UserPreferencesService {

    UserPreferences fetchDefault();

    /**
     * Delete the user preferences for a user.
     */
    boolean delete(final UserRef userRef);
}
