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

import stroom.security.exception.AuthenticationException;
import stroom.security.shared.FetchUserAndPermissionsAction;
import stroom.security.shared.UserAndPermissions;
import stroom.security.shared.UserRef;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.util.config.StroomProperties;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchUserAndPermissionsAction.class)
class FetchUserAndPermissionsHandler extends AbstractTaskHandler<FetchUserAndPermissionsAction, UserAndPermissions> {
    private static final String PREVENT_LOGIN_PROPERTY = "stroom.maintenance.preventLogin";

    private final Security security;
    private final SecurityContext securityContext;
    private final UserAndPermissionsHelper userAndPermissionsHelper;

    @Inject
    FetchUserAndPermissionsHandler(final Security security,
                                   final SecurityContext securityContext,
                                   final UserAndPermissionsHelper userAndPermissionsHelper) {
        this.security = security;
        this.securityContext = securityContext;
        this.userAndPermissionsHelper = userAndPermissionsHelper;
    }

    @Override
    public UserAndPermissions exec(final FetchUserAndPermissionsAction task) {
        return security.insecureResult(() -> {
            final UserRef userRef = CurrentUserState.currentUserRef();
            if (userRef == null) {
                return null;
            }

            final boolean preventLogin = StroomProperties.getBooleanProperty(PREVENT_LOGIN_PROPERTY, false);
            if (preventLogin) {
                security.asUser(UserTokenUtil.create(userRef.getName(), null), () -> {
                    if (!securityContext.isAdmin()) {
                        throw new AuthenticationException("You are not allowed access at this time");
                    }
                });
            }

            return new UserAndPermissions(userRef, userAndPermissionsHelper.get(userRef));
        });
    }
}
