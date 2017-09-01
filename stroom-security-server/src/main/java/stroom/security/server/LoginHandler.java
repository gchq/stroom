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
import stroom.security.shared.LoginAction;
import stroom.security.shared.UserAndPermissions;
import stroom.security.shared.UserRef;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = LoginAction.class)
@Scope(value = StroomScope.TASK)
@Insecure
public class LoginHandler extends AbstractTaskHandler<LoginAction, UserAndPermissions> {
    private final AuthenticationService authenticationService;
    private final UserAndPermissionsHelper userAndPermissionsHelper;
    private final UserService userService;

    @Inject
    LoginHandler(final AuthenticationService authenticationService, final UserAndPermissionsHelper userAndPermissionsHelper, final UserService userService) {
        this.authenticationService = authenticationService;
        this.userAndPermissionsHelper = userAndPermissionsHelper;
        this.userService = userService;
    }

    @Override
    public UserAndPermissions exec(final LoginAction task) {
        final UserRef userRef = authenticationService.login(task.getUserName(), task.getPassword());
        if (userRef == null) {
            return null;
        }

        final User user = userService.loadByUuid(userRef.getUuid());
        Integer daysToExpiry = null;
        if (user.isLoginExpiry()) {
            daysToExpiry = getDaysToExpiry(user.getPasswordExpiryMs());
        }
        return new UserAndPermissions(userRef, userAndPermissionsHelper.get(userRef), daysToExpiry);
    }

    private Integer getDaysToExpiry(final Long expiry) {
        if (expiry == null) {
            return null;
        }

        final long milliseconds = expiry - System.currentTimeMillis();
        final int days = (int) (milliseconds / 1000 / 60 / 60 / 24);
        return days;
    }
}
