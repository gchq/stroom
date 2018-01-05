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

package stroom.security.server;

import org.springframework.context.annotation.Scope;
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.security.UserTokenUtil;
import stroom.security.server.exception.AuthenticationException;
import stroom.security.shared.FetchUserAndPermissionsAction;
import stroom.security.shared.UserAndPermissions;
import stroom.security.shared.UserRef;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.config.StroomProperties;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchUserAndPermissionsAction.class)
@Scope(value = StroomScope.TASK)
@Insecure
public class FetchUserAndPermissionsHandler extends AbstractTaskHandler<FetchUserAndPermissionsAction, UserAndPermissions> {
    private static final String PREVENT_LOGIN_PROPERTY = "stroom.maintenance.preventLogin";

    private final SecurityContext securityContext;
    private final UserAndPermissionsHelper userAndPermissionsHelper;

    @Inject
    FetchUserAndPermissionsHandler(final SecurityContext securityContext,
                                   final UserAndPermissionsHelper userAndPermissionsHelper) {
        this.securityContext = securityContext;
        this.userAndPermissionsHelper = userAndPermissionsHelper;
    }

    @Override
    public UserAndPermissions exec(final FetchUserAndPermissionsAction task) {
        final UserRef userRef = CurrentUserState.currentUserRef();
        if (userRef == null) {
            return null;
        }

        final boolean preventLogin = StroomProperties.getBooleanProperty(PREVENT_LOGIN_PROPERTY, false);
        if (preventLogin) {
            try (final SecurityHelper securityHelper = SecurityHelper.asUser(securityContext, UserTokenUtil.create(userRef.getName(), null))) {
                if (!securityContext.isAdmin()) {
                    throw new AuthenticationException("You are not allowed access at this time");
                }
            }
        }

        return new UserAndPermissions(userRef, userAndPermissionsHelper.get(userRef));
    }
}
