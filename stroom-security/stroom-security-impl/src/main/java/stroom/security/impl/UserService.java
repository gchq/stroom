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
 *
 */

package stroom.security.impl;

import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDesc;

import java.util.Optional;
import java.util.function.Consumer;

public interface UserService {

    default User getOrCreateUser(String subjectId) {
        return getOrCreateUser(UserDesc.builder().subjectId(subjectId).displayName(subjectId).build(), null);
    }

    default User getOrCreateUser(UserDesc userDesc) {
        return getOrCreateUser(userDesc, null);
    }

    User getOrCreateUser(UserDesc userDesc, final Consumer<User> onCreateAction);

    Optional<User> getUserBySubjectId(String subjectId);

    default User getOrCreateUserGroup(String groupName) {
        return getOrCreateUserGroup(groupName, null);
    }

    User getOrCreateUserGroup(String groupName, final Consumer<User> onCreateAction);

    Optional<User> getGroupByName(String groupName);

    Optional<User> loadByUuid(String uuid);

    User update(User user);

    ResultPage<User> find(FindUserCriteria criteria);

    ResultPage<User> findUsersInGroup(String groupUuid, FindUserCriteria criteria);

    ResultPage<User> findGroupsForUser(String userUuid, FindUserCriteria criteria);

    Boolean addUserToGroup(String userUuid, String groupUuid);

    Boolean removeUserFromGroup(String userUuid, String groupUuid);
}
