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

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import java.io.Serializable;

public class UserPermission implements Serializable, Comparable<UserPermission> {
    private static final long serialVersionUID = 2536752322307664050L;

    private String userUuid;
    private String permission;

    public UserPermission() {
    }

    public UserPermission(final String userUuid, final String permission) {
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
    public int hashCode() {
        final HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        hashCodeBuilder.append(userUuid);
        hashCodeBuilder.append(permission);
        return hashCodeBuilder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof UserPermission)) {
            return false;
        }

        final UserPermission keyByName = (UserPermission) o;
        final EqualsBuilder equalsBuilder = new EqualsBuilder();
        equalsBuilder.append(userUuid, keyByName.userUuid);
        equalsBuilder.append(permission, keyByName.permission);
        return equalsBuilder.isEquals();
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
