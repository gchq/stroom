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

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.entity.server.MockEntityService;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.UserRef;
import stroom.util.spring.StroomSpringProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * <p>
 * Very simple mock user manager with just one user.
 * </p>
 */
@Profile(StroomSpringProfiles.TEST)
@Component("userService")
public class MockUserService extends MockEntityService<User, FindUserCriteria> implements UserService {
    @Override
    public User loadByUuid(final String uuid) throws RuntimeException {
        return loadByUuid(uuid, null);
    }

    @Override
    public User loadByUuid(final String uuid, final Set<String> fetchSet) throws RuntimeException {
        final List<User> list = find(null);
        for (final User e : list) {
            if (e.getUuid() != null && e.getUuid().equals(uuid)) {
                return e;
            }
        }

        return null;
    }

    @Override
    public Class<User> getEntityClass() {
        return User.class;
    }

    @Override
    public UserRef getUserByName(final String name) {
        return null;
    }

    @Override
    public UserRef getUserGroupByName(final String name) {
        return null;
    }

    @Override
    public List<UserRef> findUsersInGroup(final UserRef userGroup) {
        return null;
    }

    @Override
    public List<UserRef> findGroupsForUser(final UserRef user) {
        return null;
    }

    @Override
    public UserRef createUser(final String name) {
        final User user = new User();
        user.setUuid(UUID.randomUUID().toString());
        user.setName(name);
        return UserRefFactory.create(save(user));
    }

    @Override
    public UserRef createUserGroup(final String name) {
        final User user = new User();
        user.setUuid(UUID.randomUUID().toString());
        user.setName(name);
        user.setGroup(true);
        return UserRefFactory.create(save(user));
    }

    @Override
    public void addUserToGroup(final UserRef user, final UserRef userGroup) {
    }

    @Override
    public void removeUserFromGroup(final UserRef user, final UserRef userGroup) {
    }

    @Override
    public String getNamePattern() {
        return null;
    }
}
