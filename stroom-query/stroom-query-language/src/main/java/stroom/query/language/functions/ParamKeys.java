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

package stroom.query.language.functions;

import java.util.Set;

public class ParamKeys {

    /**
     * The uuid of the current logged-in user
     */
    public static final String CURRENT_USER_UUID = CurrentUserUuid.KEY;

    /**
     * The display name (or subjectId if there isn't one) of the current logged-in user
     */
    public static final String CURRENT_USER = CurrentUser.KEY;

    /**
     * The subjectId of the current logged-in user
     */
    public static final String CURRENT_USER_SUBJECT_ID = CurrentUserSubjectId.KEY;

    /**
     * The display name of the current logged-in user. May be null
     */
    public static final String CURRENT_USER_DISPLAY_NAME = CurrentUserDisplayName.KEY;

    /**
     * The full name of the current logged-in user. May be null
     */
    public static final String CURRENT_USER_FULL_NAME = CurrentUserFullName.KEY;

    static final Set<String> INTERNAL_PARAM_KEYS = Set.of(
            CURRENT_USER_UUID,
            CURRENT_USER,
            CURRENT_USER_SUBJECT_ID,
            CURRENT_USER_DISPLAY_NAME,
            CURRENT_USER_FULL_NAME
    );

    private ParamKeys() {
    }
}
