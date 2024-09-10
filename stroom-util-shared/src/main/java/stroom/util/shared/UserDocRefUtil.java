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

package stroom.util.shared;

import stroom.docref.DocRef;

public final class UserDocRefUtil {

    public static final String USER = "User";

    private UserDocRefUtil() {
        // Utility class.
    }

    public static DocRef createDocRef(final UserRef userRef) {
        if (userRef == null || userRef.getUuid() == null) {
            return null;
        }

        return new DocRef(USER, userRef.getUuid(), null);
    }

    public static UserRef createUserRef(final DocRef docRef) {
        if (docRef == null || docRef.getUuid() == null) {
            return null;
        }

        return UserRef.builder().uuid(docRef.getUuid()).build();
    }
}
