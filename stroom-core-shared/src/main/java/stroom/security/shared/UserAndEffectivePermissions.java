/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.shared;

import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * This class holds a reference to the current authenticated user and their effective application permissions arising
 * from direct application permissions and inherited permissions from group membership.
 */
@JsonInclude(Include.NON_NULL)
public class UserAndEffectivePermissions {

    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final Set<AppPermission> effectivePermissions;

    @JsonCreator
    public UserAndEffectivePermissions(@JsonProperty("userRef") final UserRef userRef,
                                       @JsonProperty("effectivePermissions") final Set<AppPermission>
                                               effectivePermissions) {
        this.userRef = userRef;
        this.effectivePermissions = effectivePermissions;
    }

    /**
     * Get the current authenticated user.
     *
     * @return the current authenticated user.
     */
    public UserRef getUserRef() {
        return userRef;
    }

    /**
     * Get the effective application permissions arising from direct application permissions and inherited permissions
     * from group membership.
     *
     * @return A set of currently held application permissions.
     */
    public Set<AppPermission> getEffectivePermissions() {
        return effectivePermissions;
    }

    @Override
    public String toString() {
        return "UserAndPermissions{" +
                "userRef='" + userRef + '\'' +
                ", effectivePermissions=" + effectivePermissions +
                '}';
    }
}
