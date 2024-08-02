/*
 * Copyright 2018 Crown Copyright
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

package stroom.security.mock;

import stroom.security.api.SecurityContext;
import stroom.security.impl.UserDao;
import stroom.security.shared.User;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.Optional;
import java.util.UUID;

/**
 * Another mock security context module but one that provides a real user for the purposes of DB correctness for
 * `runAsUser` processor filter and other task execution.
 */
public class MockUserSecurityContextModule extends AbstractModule {

    @Override
    protected void configure() {
    }

    @Provides
    public SecurityContext securityContext(final UserDao userDao) {
        return new MockSecurityContext() {
            @Override
            public UserRef getUserRef() {
                final Optional<User> optional = userDao.getUserBySubjectId("admin");
                final User persisted = optional.orElseGet(() -> {
                    final User user = User.builder()
                            .subjectId("admin")
                            .uuid(UUID.randomUUID().toString())
                            .group(false)
                            .build();
                    user.setCreateUser("admin");
                    user.setUpdateUser("admin");
                    user.setCreateTimeMs(System.currentTimeMillis());
                    user.setUpdateTimeMs(System.currentTimeMillis());
                    return userDao.create(user);
                });
                return persisted.asRef();
            }
        };
    }
}
