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

package stroom.security;

import stroom.security.shared.PermissionNames;
import stroom.security.shared.FetchUserAppPermissionsAction;
import stroom.security.shared.UserAppPermissions;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchUserAppPermissionsAction.class)
class FetchUserAppPermissionsHandler
        extends AbstractTaskHandler<FetchUserAppPermissionsAction, UserAppPermissions> {
    private final UserAppPermissionsCache userAppPermissionsCache;
    private final Security security;

    @Inject
    FetchUserAppPermissionsHandler(final UserAppPermissionsCache userAppPermissionsCache,
                                   final Security security) {
        this.userAppPermissionsCache = userAppPermissionsCache;
        this.security = security;
    }

    @Override
    public UserAppPermissions exec(final FetchUserAppPermissionsAction action) {
        return security.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> userAppPermissionsCache.get(action.getUserRef()));
    }
}
