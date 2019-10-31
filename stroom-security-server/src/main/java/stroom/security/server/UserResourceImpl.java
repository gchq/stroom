/*
 * Copyright 2017 Crown Copyright
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

import com.codahale.metrics.health.HealthCheck.Result;
import org.springframework.stereotype.Component;
import stroom.entity.shared.NamedEntity;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.UserRef;
import stroom.security.shared.UserResource;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserResourceImpl implements UserResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserResourceImpl.class);

    private final UserService userService;
    private final SecurityContext securityContext;

    @Inject
    public UserResourceImpl(final UserService userService,
                            final SecurityContext securityContext) {
        this.userService = userService;
        this.securityContext = securityContext;
    }

    @Override
    public List<String> getAssociates(final String filter) {
        Set<String> userSet;

        // Admin users will see all.
        if (securityContext.isAdmin()) {
            final FindUserCriteria findUserCriteria = new FindUserCriteria();
            findUserCriteria.setGroup(false);
            final List<User> users = userService.find(findUserCriteria);

            userSet = users
                    .stream()
                    .filter(User::isStatusEnabled)
                    .filter(user -> user.getUuid().length() > 5)
                    .filter(user -> !user.isGroup())
                    .map(NamedEntity::getName)
                    .collect(Collectors.toSet());

        } else {
            final UserRef user = userService.getUserByName(securityContext.getUserId());
            final Set<UserRef> userRefSet = new HashSet<>();
            userRefSet.add(user);

            final List<UserRef> groups = userService.findGroupsForUser(user);
            groups.forEach(userGroup -> {
                final List<UserRef> usersInGroup = userService.findUsersInGroup(userGroup);
                if (usersInGroup != null) {
                    userRefSet.addAll(usersInGroup);
                }
            });

            userSet = userRefSet
                    .stream()
                    .filter(UserRef::isEnabled)
                    .filter(u -> u.getUuid().length() > 5)
                    .filter(u -> !user.isGroup())
                    .map(DocRef::getName)
                    .collect(Collectors.toSet());
        }

        return userSet
                .stream()
                .filter(value -> filter == null || value.toLowerCase().contains(filter.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}