/*
 * Copyright 2024 Crown Copyright
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

import stroom.util.shared.string.CIKey;

import java.util.Map;
import java.util.Set;

public class ParamKeys {

    /**
     * The display name (or subjectId if there isn't one) of the current logged-in user
     */
    public static final String CURRENT_USER = "currentUser()";
    public static final CIKey CURRENT_USER_KEY = CIKey.ofStaticKey(CURRENT_USER);

    /**
     * The subjectId of the current logged-in user
     */
    public static final String CURRENT_USER_SUBJECT_ID = "currentUserSubjectId()";
    public static final CIKey CURRENT_USER_SUBJECT_ID_KEY = CIKey.ofStaticKey(CURRENT_USER_SUBJECT_ID);

    /**
     * The full name of the current logged-in user. May be null
     */
    public static final String CURRENT_USER_FULL_NAME = "currentUserFullName()";
    public static final CIKey CURRENT_USER_FULL_NAME_KEY = CIKey.ofStaticKey(CURRENT_USER_FULL_NAME);

    private static final Set<CIKey> INTERNAL_PARAM_KEYS_AS_KEYS = Set.of(
            CURRENT_USER_KEY,
            CURRENT_USER_SUBJECT_ID_KEY,
            CURRENT_USER_FULL_NAME_KEY
    );

    public static final Map<String, CIKey> KNOWN_KEYS_MAP = Map.of(
            CURRENT_USER, CURRENT_USER_KEY,
            CURRENT_USER_SUBJECT_ID, CURRENT_USER_SUBJECT_ID_KEY,
            CURRENT_USER_FULL_NAME, CURRENT_USER_FULL_NAME_KEY);

    static boolean isInternalParamKey(final CIKey key) {
        if (key == null) {
            return false;
        } else {
            return INTERNAL_PARAM_KEYS_AS_KEYS.contains(key);
        }
    }

    private ParamKeys() {
    }
}
