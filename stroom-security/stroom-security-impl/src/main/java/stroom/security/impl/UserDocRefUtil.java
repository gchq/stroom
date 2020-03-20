/*
 * Copyright 2017 Crown Copyright
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

package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.security.shared.User;

final class UserDocRefUtil {
    public static final String USER = "User";

    private UserDocRefUtil() {
        // Utility class.
    }

    static DocRef createDocRef(final String userUuid) {
        if (userUuid == null) {
            return null;
        }

        return new DocRef(USER, userUuid, null);
    }

    static User createUser(final DocRef docRef) {
        if (docRef == null || !USER.equals(docRef.getType())) {
            return null;
        }

        return new User.Builder().uuid(docRef.getUuid()).name(docRef.getName()).build();
    }
}