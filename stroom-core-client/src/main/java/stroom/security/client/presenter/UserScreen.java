package stroom.security.client.presenter;

import java.util.EnumSet;
import java.util.Set;

public enum UserScreen {
    USERS,
    USER,
    USERS_AND_GROUPS,
    APP_PERMISSIONS,
    API_KEYS,
    ACCOUNTS,
    ;

    private static final EnumSet<UserScreen> ALL_SCREENS = EnumSet.allOf(UserScreen.class);

    public static Set<UserScreen> allExcept(final UserScreen userScreen) {
        if (userScreen == null) {
            return ALL_SCREENS;
        } else {
            return EnumSet.complementOf(EnumSet.of(userScreen));
        }
    }

    public static Set<UserScreen> allExcept(final UserScreen... userScreens) {
        if (userScreens == null) {
            return ALL_SCREENS;
        } else {
            return EnumSet.complementOf(EnumSet.of(userScreens[0], userScreens));
        }
    }

    public static Set<UserScreen> all() {
        return ALL_SCREENS;
    }
}
