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

package stroom.security.user.api;

import stroom.security.shared.FindUserContext;
import stroom.util.shared.UserRef;

import java.util.Optional;

public interface UserRefLookup {

    /**
     * Look up a user by their Stroom user uuid.
     * This is user may or may not be enabled and is NOT a deleted user.
     */
    default Optional<UserRef> getByUuid(final String userUuid) {
        return getByUuid(userUuid, null);
    }

    /**
     * Look up a user by their Stroom user uuid but limit to the context of the lookup.
     */
    Optional<UserRef> getByUuid(String userUuid, FindUserContext context);
}
