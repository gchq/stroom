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

package stroom.security.impl.db;

import stroom.db.util.JooqUtil;
import stroom.query.api.ExpressionOperator;
import stroom.security.impl.TestModule;
import stroom.security.impl.UserDao;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.security.shared.PermissionShowLevel;
import stroom.security.shared.User;
import stroom.util.AuditUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestAppPermissionDaoImpl {

    private static final Set<AppPermission> PERMISSION_SET = Set.of(
            AppPermission.STEPPING_PERMISSION, AppPermission.CHANGE_OWNER_PERMISSION);

    @Inject
    private UserDao userDao;
    @Inject
    private AppPermissionDaoImpl appPermissionDao;
    @Inject
    private SecurityDbConnProvider securityDbConnProvider;

    @BeforeEach
    void beforeEach() {
        final Injector injector = Guice.createInjector(
                new SecurityDbModule(),
                new SecurityDaoModule(),
                new TestModule());

        injector.injectMembers(this);
    }

    @AfterEach
    void tearDown() {
        SecurityTestUtil.teardown(securityDbConnProvider);
    }

    @Test
    void testPermissionInvalidUser() {
        // Given
        final String userUuid = UUID.randomUUID().toString();

        // When
        Assertions.assertThatThrownBy(() ->
                        appPermissionDao.addPermission(userUuid, AppPermission.STEPPING_PERMISSION))
                .isInstanceOf(IntegrityConstraintViolationException.class);
    }

    @Test
    void testPermissionStory() {
        final String userName = String.format("SomePerson_%s", UUID.randomUUID());

        final UserRef user = createUser(userName);
        appPermissionDao.addPermission(user.getUuid(), AppPermission.STEPPING_PERMISSION);
        appPermissionDao.addPermission(user.getUuid(), AppPermission.CHANGE_OWNER_PERMISSION);

        final Set<AppPermission> permissionsFound1 = appPermissionDao.getPermissionsForUser(user.getUuid());
        assertThat(permissionsFound1).isEqualTo(Set.of(AppPermission.STEPPING_PERMISSION,
                AppPermission.CHANGE_OWNER_PERMISSION));

        appPermissionDao.removePermission(user.getUuid(), AppPermission.STEPPING_PERMISSION);
        final Set<AppPermission> permissionsFound2 = appPermissionDao.getPermissionsForUser(user.getUuid());
        assertThat(permissionsFound2).isEqualTo(Set.of(AppPermission.CHANGE_OWNER_PERMISSION));
    }

    @Test
    void testEffectivePermissions1() {
        final UserRef user1 = createUser("user1");
        appPermissionDao.addPermission(user1.getUuid(), AppPermission.STEPPING_PERMISSION);
        appPermissionDao.addPermission(user1.getUuid(), AppPermission.CHANGE_OWNER_PERMISSION);
        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()).size()).isEqualTo(2);

        final FetchAppUserPermissionsRequest request = new FetchAppUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<AppUserPermissions> resultPage = appPermissionDao.fetchAppUserPermissions(request);
        assertThat(resultPage.size()).isOne();
        validateAppPermissions(resultPage, "user1", PERMISSION_SET, Set.of());
    }

    @Test
    void testEffectivePermissions2() {
        final UserRef user1 = createUser("user1");
        final UserRef group1 = createGroup("group1");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        appPermissionDao.addPermission(group1.getUuid(), AppPermission.STEPPING_PERMISSION);
        appPermissionDao.addPermission(group1.getUuid(), AppPermission.CHANGE_OWNER_PERMISSION);

        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()).size()).isEqualTo(0);
        assertThat(appPermissionDao.getPermissionsForUser(group1.getUuid()).size()).isEqualTo(2);

        final FetchAppUserPermissionsRequest request = new FetchAppUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<AppUserPermissions> resultPage = appPermissionDao.fetchAppUserPermissions(request);
        assertThat(resultPage.size()).isEqualTo(2);
        validateAppPermissions(resultPage, "user1", Set.of(), PERMISSION_SET);
        validateAppPermissions(resultPage, "group1", PERMISSION_SET, Set.of());
    }

    @Test
    void testEffectivePermissions3() {
        final UserRef user1 = createUser("user1");
        final UserRef group1 = createGroup("group1");
        final UserRef group2 = createGroup("group2");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        userDao.addUserToGroup(group1.getUuid(), group2.getUuid());
        appPermissionDao.addPermission(group2.getUuid(), AppPermission.STEPPING_PERMISSION);
        appPermissionDao.addPermission(group2.getUuid(), AppPermission.CHANGE_OWNER_PERMISSION);

        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()).size()).isEqualTo(0);
        assertThat(appPermissionDao.getPermissionsForUser(group1.getUuid()).size()).isEqualTo(0);
        assertThat(appPermissionDao.getPermissionsForUser(group2.getUuid()).size()).isEqualTo(2);

        final FetchAppUserPermissionsRequest request = new FetchAppUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<AppUserPermissions> resultPage = appPermissionDao.fetchAppUserPermissions(request);
        assertThat(resultPage.size()).isEqualTo(3);
        validateAppPermissions(resultPage, "user1", Set.of(), PERMISSION_SET);
        validateAppPermissions(resultPage, "group1", Set.of(), PERMISSION_SET);
        validateAppPermissions(resultPage, "group2", PERMISSION_SET, Set.of());
    }

    @Test
    void testEffectivePermissions3None() {
        final UserRef user1 = createUser("user1");
        final UserRef group1 = createGroup("group1");
        final UserRef group2 = createGroup("group2");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        userDao.addUserToGroup(group1.getUuid(), group2.getUuid());

        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()).size()).isEqualTo(0);
        assertThat(appPermissionDao.getPermissionsForUser(group1.getUuid()).size()).isEqualTo(0);
        assertThat(appPermissionDao.getPermissionsForUser(group2.getUuid()).size()).isEqualTo(0);

        final FetchAppUserPermissionsRequest request = new FetchAppUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<AppUserPermissions> resultPage = appPermissionDao.fetchAppUserPermissions(request);
        assertThat(resultPage.size()).isEqualTo(0);
    }

    @Test
    void testEffectivePermissions3Split() {
        final UserRef user1 = createUser("user1");
        final UserRef group1 = createGroup("group1");
        final UserRef group2 = createGroup("group2");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        userDao.addUserToGroup(group1.getUuid(), group2.getUuid());

        appPermissionDao.addPermission(user1.getUuid(), AppPermission.MANAGE_CACHE_PERMISSION);
        appPermissionDao.addPermission(group1.getUuid(), AppPermission.STEPPING_PERMISSION);
        appPermissionDao.addPermission(group2.getUuid(), AppPermission.CHANGE_OWNER_PERMISSION);

        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()).size()).isEqualTo(1);
        assertThat(appPermissionDao.getPermissionsForUser(group1.getUuid()).size()).isEqualTo(1);
        assertThat(appPermissionDao.getPermissionsForUser(group2.getUuid()).size()).isEqualTo(1);

        final FetchAppUserPermissionsRequest request = new FetchAppUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<AppUserPermissions> resultPage = appPermissionDao.fetchAppUserPermissions(request);
        assertThat(resultPage.size()).isEqualTo(3);
        validateAppPermissions(resultPage, "user1",
                Set.of(AppPermission.MANAGE_CACHE_PERMISSION),
                Set.of(AppPermission.STEPPING_PERMISSION, AppPermission.CHANGE_OWNER_PERMISSION));
        validateAppPermissions(resultPage, "group1",
                Set.of(AppPermission.STEPPING_PERMISSION),
                Set.of(AppPermission.CHANGE_OWNER_PERMISSION));
        validateAppPermissions(resultPage, "group2", Set.of(AppPermission.CHANGE_OWNER_PERMISSION), Set.of());
    }

    @Test
    void testEffectivePermissions3Mid() {
        final UserRef user1 = createUser("user1");
        final UserRef group1 = createGroup("group1");
        final UserRef group2 = createGroup("group2");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        userDao.addUserToGroup(group1.getUuid(), group2.getUuid());

        appPermissionDao.addPermission(group1.getUuid(), AppPermission.STEPPING_PERMISSION);

        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()).size()).isEqualTo(0);
        assertThat(appPermissionDao.getPermissionsForUser(group1.getUuid()).size()).isEqualTo(1);
        assertThat(appPermissionDao.getPermissionsForUser(group2.getUuid()).size()).isEqualTo(0);

        final FetchAppUserPermissionsRequest request = new FetchAppUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                null,
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<AppUserPermissions> resultPage = appPermissionDao.fetchAppUserPermissions(request);
        assertThat(resultPage.size()).isEqualTo(2);
        validateAppPermissions(resultPage, "user1", Set.of(), Set.of(AppPermission.STEPPING_PERMISSION));
        validateAppPermissions(resultPage, "group1", Set.of(AppPermission.STEPPING_PERMISSION), Set.of());
    }

    @Test
    void deletePermissionsForUser() {
        final UserRef user1 = createUser("user1");
        final UserRef user2 = createUser("user2");
        final UserRef group4 = createGroup("group4");

        appPermissionDao.addPermission(user1.getUuid(), AppPermission.STEPPING_PERMISSION);
        appPermissionDao.addPermission(user1.getUuid(), AppPermission.VIEW_DATA_PERMISSION);

        appPermissionDao.addPermission(user2.getUuid(), AppPermission.CHANGE_OWNER_PERMISSION);
        appPermissionDao.addPermission(user2.getUuid(), AppPermission.MANAGE_TASKS_PERMISSION);

        appPermissionDao.addPermission(group4.getUuid(), AppPermission.ADMINISTRATOR);
        appPermissionDao.addPermission(group4.getUuid(), AppPermission.ANNOTATIONS);

        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()))
                .containsExactlyInAnyOrder(
                        AppPermission.STEPPING_PERMISSION,
                        AppPermission.VIEW_DATA_PERMISSION);

        assertThat(appPermissionDao.getPermissionsForUser(user2.getUuid()))
                .containsExactlyInAnyOrder(
                        AppPermission.CHANGE_OWNER_PERMISSION,
                        AppPermission.MANAGE_TASKS_PERMISSION);

        assertThat(appPermissionDao.getPermissionsForUser(group4.getUuid()))
                .containsExactlyInAnyOrder(
                        AppPermission.ADMINISTRATOR,
                        AppPermission.ANNOTATIONS);


        final Integer delCount = JooqUtil.contextResult(securityDbConnProvider, context ->
                appPermissionDao.deletePermissionsForUser(context, user2.getUuid()));

        assertThat(delCount)
                .isEqualTo(2);

        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()))
                .containsExactlyInAnyOrder(
                        AppPermission.STEPPING_PERMISSION,
                        AppPermission.VIEW_DATA_PERMISSION);

        assertThat(appPermissionDao.getPermissionsForUser(user2.getUuid()))
                .isEmpty();

        assertThat(appPermissionDao.getPermissionsForUser(group4.getUuid()))
                .containsExactlyInAnyOrder(
                        AppPermission.ADMINISTRATOR,
                        AppPermission.ANNOTATIONS);

    }

    @Test
    void addPermission_idempotency() {
        final UserRef user1 = createUser("user1");
        appPermissionDao.addPermission(user1.getUuid(), AppPermission.STEPPING_PERMISSION);
        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()))
                .containsExactlyInAnyOrder(AppPermission.STEPPING_PERMISSION);

        // Do the same again
        appPermissionDao.addPermission(user1.getUuid(), AppPermission.STEPPING_PERMISSION);
        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()))
                .containsExactlyInAnyOrder(AppPermission.STEPPING_PERMISSION);
    }

    private void validateAppPermissions(final ResultPage<AppUserPermissions> resultPage,
                                        final String name,
                                        final Set<AppPermission> expectedPermissions,
                                        final Set<AppPermission> expectedInherited) {
        final AppUserPermissions appUserPermissions = get(resultPage, name);
        validateAppPermissions(appUserPermissions, expectedPermissions, expectedInherited);
    }

    private void validateAppPermissions(final AppUserPermissions appUserPermissions,
                                        final Set<AppPermission> expectedPermissions,
                                        final Set<AppPermission> expectedInherited) {
        validatePermissionSet(appUserPermissions.getPermissions(), expectedPermissions);
        validatePermissionSet(appUserPermissions.getInherited(), expectedInherited);
    }

    private void validatePermissionSet(final Set<AppPermission> actual,
                                       final Set<AppPermission> expected) {
        assertThat(actual.size()).isEqualTo(expected.size());
        assertThat(actual).containsAll(expected);
    }

    private AppUserPermissions get(final ResultPage<AppUserPermissions> resultPage,
                                   final String name) {
        final Optional<AppUserPermissions> optional = resultPage
                .getValues()
                .stream()
                .filter(aup -> name.equals(aup.getUserRef().getSubjectId()))
                .findAny();
        assertThat(optional).isPresent();
        return optional.get();
    }

    private UserRef createUser(final String name) {
        return createUserOrGroup(name, false);
    }

    private UserRef createGroup(final String name) {
        return createUserOrGroup(name, true);
    }

    private UserRef createUserOrGroup(final String name, final boolean group) {
        final User user = User.builder()
                .subjectId(name)
                .displayName(name)
                .uuid(UUID.randomUUID().toString())
                .group(group)
                .build();
        AuditUtil.stamp(() -> "test", user);
        return userDao.create(user).asRef();
    }
}
