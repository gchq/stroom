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

package stroom.security.api;

import stroom.util.shared.HasAuditableUserIdentity;
import stroom.util.shared.UserDesc;

import java.util.Optional;

/**
 * Represents the identity of a user for authentication purposes.
 */
public interface UserIdentity extends HasAuditableUserIdentity {

    /**
     * @return The unique identifier for the user. In the case of an Open ID Connect user
     * this would be the claim value that uniquely identifies the user on the IDP (often 'sub' or 'oid').
     * These values are often UUIDs and thus not pretty to look at for an admin.
     * For the internal IDP this would likely be a more human friendly username.
     */
    String subjectId();

    /**
     * @return The non-unique username for the user, e.g. 'jbloggs'. In the absence of a specific
     * value this should just return the subjectId.
     */
    default String getDisplayName() {
        return subjectId();
    }

    /**
     * @return The user's full name if known, e.g. 'Joe Bloggs'.
     */
    default Optional<String> getFullName() {
        return Optional.empty();
    }

    @Override
    default String getUserIdentityForAudit() {
        final String subjectId = subjectId();
        final String displayName = getDisplayName();
        // GWT so no Objects.requireNonNullElse()
        if (displayName != null) {
            return displayName;
        } else {
            return subjectId;
        }
    }

    default UserDesc asUserDesc() {
        return new UserDesc(subjectId(), getDisplayName(), getFullName().orElse(null));
    }

    // TODO: 28/11/2022 Potentially worth introducing scopes, e.g. a datafeed scope so only tokens
    //  with the datafeed scope can send data. Similarly we could have a scope per resource so people
    //  can create tokens that are very limited in what they can do. May need an 'api-all' scope to
    //  allow people to hit any resource.
//    /**
//     * @return The set of scopes that this user identity has. Scopes add restrictions
//     * on top of the things that a user has permission to do.
//     */
//    default Set<String> getScopes() {
//        return Collections.emptySet();
//    };
//
//    default UserName asUserName() {
//        final String subjectId = getSubjectId();
//        String displayName = getDisplayName();
//        if (Objects.equals(displayName, subjectId)) {
//            displayName = null;
//        }
//        return new SimpleUserName(
//                subjectId,
//                displayName,
//                getFullName().orElse(null));
//    }
}
