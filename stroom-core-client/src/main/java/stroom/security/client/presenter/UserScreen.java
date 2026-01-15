/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.client.presenter;

import java.util.EnumSet;
import java.util.Set;

public enum UserScreen {
    USERS,
    USER,
    USER_GROUPS,
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
