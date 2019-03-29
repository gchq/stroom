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

import stroom.security.api.Security;
import stroom.security.shared.FetchUserAction;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;


class FetchUserHandler
        extends AbstractTaskHandler<FetchUserAction, ResultList<User>> {
    private final UserService userService;
    private final Security security;

    @Inject
    FetchUserHandler(final UserService userService,
                     final Security security) {
        this.userService = userService;
        this.security = security;
    }

    @Override
    public ResultList<User> exec(final FetchUserAction action) {
        return security.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            final FindUserCriteria findUserCriteria = action.getCriteria();
            findUserCriteria.setSort(FindUserCriteria.FIELD_NAME);
            if (findUserCriteria.getRelatedUser() != null) {
                User userRef = findUserCriteria.getRelatedUser();
                List<User> list;
                if (userRef.isGroup()) {
                    list = userService.findUsersInGroup(userRef.getUuid());
                } else {
                    list = userService.findGroupsForUser(userRef.getUuid());
                }

                if (action.getCriteria().getName() != null) {
                    list = list.stream().filter(user -> action.getCriteria().getName().isMatch(user.getName())).collect(Collectors.toList());
                }

                // Create a result list limited by the page request.
                return BaseResultList.createPageLimitedList(list, findUserCriteria.getPageRequest());
            }

            final List<User> users = userService.find(findUserCriteria);
            return new BaseResultList<>(users, 0L, (long) users.size(), false);
        });
    }
}
