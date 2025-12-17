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

package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class UserAndPermissionsHelper {

    private final SecurityContext securityContext;

    @Inject
    UserAndPermissionsHelper(final SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    public Set<AppPermission> getCurrentAppPermissions() {
        if (securityContext.isAdmin()) {
            return Collections.singleton(AppPermission.ADMINISTRATOR);
        } else {
            final Set<AppPermission> appPermissionSet = new HashSet<>();
            for (final AppPermission permission : AppPermission.values()) {
                if (securityContext.hasAppPermission(permission)) {
                    appPermissionSet.add(permission);
                }
            }
            return appPermissionSet;
        }
    }
}
