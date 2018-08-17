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

package stroom.security;

import stroom.entity.EntityService;
import stroom.entity.FindService;
import stroom.entity.shared.HasLoadByUuid;
import stroom.entity.shared.ProvidesNamePattern;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.UserRef;

import java.util.List;

public interface UserService extends EntityService<User>, FindService<User, FindUserCriteria>, HasLoadByUuid<User>, ProvidesNamePattern {
    String ADMIN_USER_NAME = "admin";
    String STROOM_SERVICE_USER_NAME = "stroomServiceUser";

    UserRef getUserByName(String name);

    List<UserRef> findUsersInGroup(UserRef userGroup);

    List<UserRef> findGroupsForUser(UserRef user);

    UserRef createUser(String name);

    UserRef createUserGroup(String name);

    void addUserToGroup(final UserRef user, final UserRef userGroup);

    void removeUserFromGroup(final UserRef user, final UserRef userGroup);
}
