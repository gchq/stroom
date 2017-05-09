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
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.security.Secured;
import stroom.security.SecurityContext;
import stroom.security.shared.FetchUserRefAction;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserRef;
import stroom.security.shared.UserService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@TaskHandlerBean(task = FetchUserRefAction.class)
@Scope(StroomScope.TASK)
@Secured(User.MANAGE_USERS_PERMISSION)
public class FetchUserRefHandler
        extends AbstractTaskHandler<FetchUserRefAction, ResultList<UserRef>> {
    private final UserService userService;
    private final SecurityContext securityContext;

    @Inject
    public FetchUserRefHandler(final UserService userService, final SecurityContext securityContext) {
        this.userService = userService;
        this.securityContext = securityContext;
    }

    @Override
    public ResultList<UserRef> exec(final FetchUserRefAction action) {
        final FindUserCriteria findUserCriteria = action.getCriteria();
        findUserCriteria.setOrderBy(FindUserCriteria.ORDER_BY_NAME);
        if (findUserCriteria.getRelatedUser() != null) {
            UserRef userRef = findUserCriteria.getRelatedUser();
            List<UserRef> list;
            if (userRef.isGroup()) {
                list = userService.findUsersInGroup(userRef);
            } else {
                list = userService.findGroupsForUser(userRef);
            }

            if (action.getCriteria().getName() != null) {
                list = list.stream().filter(user -> action.getCriteria().getName().isMatch(user.getName())).collect(Collectors.toList());
            }
            return BaseResultList.createCriterialBasedList(list,
                    findUserCriteria);
        }

        final BaseResultList<User> users = userService.find(findUserCriteria);
        final List<UserRef> userRefs = new ArrayList<>();
        users.stream().forEachOrdered(user -> userRefs.add(UserRef.create(user)));
        return new BaseResultList<>(userRefs, users.getPageResponse().getOffset(), users.getPageResponse().getTotal(), users.getPageResponse().isMore());
    }
}
