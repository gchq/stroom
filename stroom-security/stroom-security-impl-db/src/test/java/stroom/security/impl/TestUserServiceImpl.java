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

package stroom.security.impl;


import stroom.query.api.ExpressionOperator;
import stroom.security.api.UserService;
import stroom.security.impl.db.SecurityDbConnProvider;
import stroom.security.impl.db.SecurityTestUtil;
import stroom.security.shared.FindUserContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserServiceImpl {

    @Inject
    private UserService userService;
    @Inject
    private SecurityDbConnProvider securityDbConnProvider;

    @BeforeEach
    void beforeEach() {
        final Injector injector = Guice.createInjector(new TestModule());
        injector.injectMembers(this);
    }

    @AfterEach
    void tearDown() {
        SecurityTestUtil.teardown(securityDbConnProvider);
    }

    @Test
    void testSaveAndGetBasic() {
        createUser("saveAndGet");
    }

    @Test
    void testSaveAndGetUserGroups() {
        final User user1 = createUser("saveGetUser1");
        final User user2 = createUser("saveGetUser2");
        final User userGroup1 = createUserGroup("saveGetGroup1");
        final User userGroup2 = createUserGroup("saveGetGroup2");

        checkGroupsForUser(user1);
        checkGroupsForUser(user2);
        checkUsersInGroup(userGroup1);
        checkUsersInGroup(userGroup2);

        userService.addUserToGroup(user1.asRef(), userGroup1.asRef());

        checkGroupsForUser(user1, userGroup1);
        checkGroupsForUser(user2);
        checkUsersInGroup(userGroup1, user1);
        checkUsersInGroup(userGroup2);

        userService.addUserToGroup(user1.asRef(), userGroup2.asRef());

        checkGroupsForUser(user1, userGroup1, userGroup2);
        checkGroupsForUser(user2);
        checkUsersInGroup(userGroup1, user1);
        checkUsersInGroup(userGroup2, user1);

        userService.addUserToGroup(user2.asRef(), userGroup1.asRef());

        checkGroupsForUser(user1, userGroup1, userGroup2);
        checkGroupsForUser(user2, userGroup1);
        checkUsersInGroup(userGroup1, user1, user2);
        checkUsersInGroup(userGroup2, user1);

        userService.removeUserFromGroup(user2.asRef(), userGroup1.asRef());

        checkGroupsForUser(user1, userGroup1, userGroup2);
        checkGroupsForUser(user2);
        checkUsersInGroup(userGroup1, user1);
        checkUsersInGroup(userGroup2, user1);
    }

    private void checkGroupsForUser(final User user, final User... groups) {
        final ResultPage<User> result = userService.findGroupsForUser(user.getUuid(), new FindUserCriteria());
        assertThat(result.size()).isEqualTo(groups.length);
        for (final User group : groups) {
            assertThat(result.getValues().contains(group)).isTrue();
        }
    }

    private void checkUsersInGroup(final User group, final User... users) {
        final ResultPage<User> result = userService.findUsersInGroup(group.getUuid(), new FindUserCriteria());
        assertThat(result.size()).isEqualTo(users.length);
        for (final User user : users) {
            assertThat(result.getValues().contains(user)).isTrue();
        }
    }

    @Test
    void testFindUsers() {
        final User user1 = createUser("findUser1");
        final User user2 = createUser("findUser2");
        final User userGroup1 = createUserGroup("findGroup1");
        final User userGroup2 = createUserGroup("findGroup2");

        final ExpressionOperator expression = QuickFilterExpressionParser
                .parse(user1.getSubjectId(), UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELDS_MAP);

        assertThat(userService.find(createCriteria(user1.getSubjectId())).size()).isEqualTo(1);
        assertThat(userService.find(createCriteria(user2.getSubjectId())).size()).isEqualTo(1);
        assertThat(userService.find(createCriteria(userGroup1.getSubjectId())).size()).isEqualTo(1);
        assertThat(userService.find(createCriteria(userGroup2.getSubjectId())).size()).isEqualTo(1);

        final Set<String> findUsers = userService.find(createCriteria("isgroup:false"))
                .stream()
                .map(User::getUuid)
                .collect(Collectors.toSet());
        final Set<String> findGroups = userService.find(createCriteria("isgroup:true"))
                .stream()
                .map(User::getUuid)
                .collect(Collectors.toSet());

        assertThat(findUsers).contains(user1.getUuid(), user2.getUuid());
        assertThat(findUsers).doesNotContain(userGroup1.getUuid(), userGroup2.getUuid());
        assertThat(findGroups).contains(userGroup1.getUuid(), userGroup2.getUuid());
        assertThat(findGroups).doesNotContain(user1.getUuid(), user2.getUuid());
    }

    private FindUserCriteria createCriteria(final String filter) {
        final PageRequest pageRequest = new PageRequest(0, 100);
        final ExpressionOperator expression = QuickFilterExpressionParser
                .parse(filter, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELDS_MAP);
        return new FindUserCriteria(pageRequest, null, expression, FindUserContext.PERMISSIONS);
    }

    private User createUser(final String baseName) {
        final User userRef = userService.getOrCreateUser(String.format("%s_%s", baseName, UUID.randomUUID()));
        assertThat(userRef).isNotNull();
        final Optional<User> user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isPresent();
        return user.get();
    }

    private User createUserGroup(final String baseName) {
        final User userRef = userService.getOrCreateUserGroup(String.format("%s_%s", baseName, UUID.randomUUID()));
        assertThat(userRef).isNotNull();
        final Optional<User> user = userService.loadByUuid(userRef.getUuid());
        assertThat(user).isPresent();
        return user.get();
    }
}
