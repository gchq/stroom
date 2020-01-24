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

package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.shared.FetchUserAndPermissionsAction;
import stroom.security.shared.User;
import stroom.security.shared.UserAndPermissions;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class FetchUserAndPermissionsHandler extends AbstractTaskHandler<FetchUserAndPermissionsAction, UserAndPermissions> {
    private final SecurityContext securityContext;
    private final UserAndPermissionsHelper userAndPermissionsHelper;
    private final AuthenticationConfig authenticationConfig;

    @Inject
    FetchUserAndPermissionsHandler(final SecurityContext securityContext,
                                   final UserAndPermissionsHelper userAndPermissionsHelper,
                                   final AuthenticationConfig authenticationConfig) {
        this.securityContext = securityContext;
        this.userAndPermissionsHelper = userAndPermissionsHelper;
        this.authenticationConfig = authenticationConfig;
    }

    @Override
    public UserAndPermissions exec(final FetchUserAndPermissionsAction task) {
        final UserIdentity userIdentity = CurrentUserState.current();
        if (userIdentity == null) {
            return null;
        }
        User user = null;
        if (userIdentity instanceof UserIdentityImpl) {
            user = ((UserIdentityImpl) userIdentity).getUser();
        }
        if (user == null) {
            return null;
        }

        final boolean preventLogin = authenticationConfig.isPreventLogin();
        if (preventLogin) {
            if (!securityContext.isAdmin()) {
                throw new AuthenticationException("Stroom is down for maintenance. Please try again later.");
            }
        }

        return new UserAndPermissions(user.getName(), userAndPermissionsHelper.get(user));
    }
}
