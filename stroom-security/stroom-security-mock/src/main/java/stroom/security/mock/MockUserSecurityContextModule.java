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

package stroom.security.mock;

import stroom.security.api.CommonSecurityContext;
import stroom.security.api.SecurityContext;
import stroom.security.impl.AppPermissionDao;
import stroom.security.impl.UserDao;
import stroom.security.shared.AppPermission;
import stroom.security.shared.User;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
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

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MockUserSecurityContextModule.class);

    private static final String ADMINISTRATORS = "Administrators";
    private static final String ADMIN = "admin";

    @Override
    protected void configure() {
    }

    @Provides
    public CommonSecurityContext commonSecurityContext(final SecurityContext securityContext) {
        return securityContext;
    }

    @Provides
    public SecurityContext securityContext(final UserDao userDao,
                                           final AppPermissionDao appPermissionDao) {
        return new MockSecurityContext() {
            @Override
            public UserRef getUserRef() {
                final Optional<User> optionalGroup = userDao.getGroupByName(ADMINISTRATORS);
                final User group = optionalGroup.orElseGet(() -> {
                    final User user = User.builder()
                            .subjectId(ADMINISTRATORS)
                            .uuid(UUID.randomUUID().toString())
                            .group(true)
                            .build();
                    user.setCreateUser(ADMIN);
                    user.setUpdateUser(ADMIN);
                    user.setCreateTimeMs(System.currentTimeMillis());
                    user.setUpdateTimeMs(System.currentTimeMillis());
                    final User created = userDao.create(user);
                    appPermissionDao.addPermission(created.getUuid(), AppPermission.ADMINISTRATOR);
                    return created;
                });

                final Optional<User> optional = userDao.getUserBySubjectId(ADMIN);
                final User persisted = optional.orElseGet(() -> {
                    final User user = User.builder()
                            .subjectId(ADMIN)
                            .uuid(UUID.randomUUID().toString())
                            .group(false)
                            .build();
                    user.setCreateUser(ADMIN);
                    user.setUpdateUser(ADMIN);
                    user.setCreateTimeMs(System.currentTimeMillis());
                    user.setUpdateTimeMs(System.currentTimeMillis());
                    final User created = userDao.create(user);
                    userDao.addUserToGroup(created.getUuid(), group.getUuid());
                    LOGGER.info(() -> LogUtil.message("Created user with subjectId: {}, UUID: {}",
                            user.getSubjectId(), user.getUuid()));
                    return created;
                });
                return persisted.asRef();
            }
        };
    }
}
