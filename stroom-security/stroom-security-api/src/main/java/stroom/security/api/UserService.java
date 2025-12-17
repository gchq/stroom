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

package stroom.security.api;

import stroom.security.shared.FindUserContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.FindUserDependenciesCriteria;
import stroom.security.shared.User;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDependency;
import stroom.util.shared.UserDesc;
import stroom.util.shared.UserRef;

import java.util.Optional;
import java.util.function.Consumer;

public interface UserService {

    default User getOrCreateUser(final String subjectId) {
        return getOrCreateUser(UserDesc.builder(subjectId)
                .displayName(subjectId)
                .build(), null);
    }

    default User getOrCreateUser(final UserDesc userDesc) {
        return getOrCreateUser(userDesc, null);
    }

    User getOrCreateUser(UserDesc userDesc, final Consumer<User> onCreateAction);

    Optional<User> getUserBySubjectId(String subjectId);

    default User getOrCreateUserGroup(final String groupName) {
        return getOrCreateUserGroup(groupName, null);
    }

    User getOrCreateUserGroup(String groupName, final Consumer<User> onCreateAction);

    Optional<User> getGroupByName(String groupName);

    Optional<User> loadByUuid(String uuid);

    User update(User user);

    User copyGroupsAndPermissions(String fromUserUuid, String toUserUuid);

    ResultPage<User> find(FindUserCriteria criteria);

    UserRef getUserByUuid(String uuid, FindUserContext context);

    ResultPage<User> findUsersInGroup(String groupUuid, FindUserCriteria criteria);

    ResultPage<User> findGroupsForUser(String userUuid, FindUserCriteria criteria);

    Boolean addUserToGroup(UserRef userOrGroupRef, UserRef groupRef);

    Boolean removeUserFromGroup(UserRef userOrGroupRef, UserRef groupRef);

    /**
     * Physically delete a user
     */
    boolean delete(String userUuid);

    ResultPage<UserDependency> fetchUserDependencies(FindUserDependenciesCriteria criteria);
}
