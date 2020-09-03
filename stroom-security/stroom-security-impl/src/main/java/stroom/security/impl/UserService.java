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

import java.util.List;
import java.util.Set;

public interface UserService {
    User createUser(String name);

    User createUserGroup(String name);

    User getUserByName(String name);

    User loadByUuid(String uuid);

    User update(User user);

    Boolean delete(String userUuid);

    List<User> find(FindUserCriteria criteria);

    default List<User> findUsersInGroup(String groupUuid) {
        return findUsersInGroup(groupUuid, null);
    }

    List<User> findUsersInGroup(String groupUuid, String quickFilter);

    default List<User> findGroupsForUser(String userUuid) {
        return findGroupsForUser(userUuid, null);
    }

    List<User> findGroupsForUser(String userUuid, String quickFilter);

    Set<String> findGroupUuidsForUser(String userUuid);

    List<User> findGroupsForUserName(String userName);

    Boolean addUserToGroup(String userUuid, String groupUuid);

    Boolean removeUserFromGroup(String userUuid, String groupUuid);

    List<String> getAssociates(String filter);
}
