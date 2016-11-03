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

import stroom.util.shared.SharedObject;

import java.util.Set;

public class UserAppPermissions implements SharedObject {
    private static final long serialVersionUID = 2374084842679322202L;

    private UserRef user;
    private Set<String> allPermissions;
    private Set<String> userPermissons;

    public UserAppPermissions() {
    }

    public UserAppPermissions(final UserRef user, final Set<String> allPermissions,
                              final Set<String> userPermissons) {
        this.user = user;
        this.allPermissions = allPermissions;
        this.userPermissons = userPermissons;
    }

    public UserRef getUser() {
        return user;
    }

    public void setUser(final UserRef user) {
        this.user = user;
    }

    public Set<String> getAllPermissions() {
        return allPermissions;
    }

    public void setAllPermissions(final Set<String> allPermissions) {
        this.allPermissions = allPermissions;
    }

    public Set<String> getUserPermissons() {
        return userPermissons;
    }

    public void setUserPermissons(final Set<String> userPermissons) {
        this.userPermissons = userPermissons;
    }
}
