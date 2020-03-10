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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.Objects;

@JsonPropertyOrder({"userUuid", "permission"})
@JsonInclude(Include.NON_NULL)
public class UserPermission implements Serializable, Comparable<UserPermission> {
    private static final long serialVersionUID = 2536752322307664050L;

    @JsonProperty
    private final String userUuid;
    @JsonProperty
    private final String permission;

    @JsonCreator
    public UserPermission(@JsonProperty("userUuid") final String userUuid,
                          @JsonProperty("permission") final String permission) {
        this.userUuid = userUuid;
        this.permission = permission;
    }

    public String getUserUuid() {
        return userUuid;
    }

    public String getPermission() {
        return permission;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UserPermission that = (UserPermission) o;
        return Objects.equals(userUuid, that.userUuid) &&
                Objects.equals(permission, that.permission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userUuid, permission);
    }

    @Override
    public String toString() {
        return userUuid + "-" + permission;
    }

    @Override
    public int compareTo(final UserPermission userPermission) {
        return toString().compareTo(userPermission.toString());
    }
}
