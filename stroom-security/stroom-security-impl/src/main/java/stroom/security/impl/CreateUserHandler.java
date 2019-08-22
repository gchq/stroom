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
import stroom.security.shared.CreateUserAction;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class CreateUserHandler extends AbstractTaskHandler<CreateUserAction, User> {
    private final UserService userService;
    private final SecurityContext securityContext;

    @Inject
    CreateUserHandler(final UserService userService,
                      final SecurityContext securityContext) {
        this.userService = userService;
        this.securityContext = securityContext;
    }

    @Override
    public User exec(final CreateUserAction action) {
        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            if (action.isGroup()) {
                return userService.createUserGroup(action.getName());
            } else {
                return userService.createUser(action.getName());
            }
        });
    }
}
