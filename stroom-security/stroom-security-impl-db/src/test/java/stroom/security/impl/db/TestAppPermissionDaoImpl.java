package stroom.security.impl.db;

import stroom.query.api.v2.ExpressionOperator;
import stroom.security.impl.AppPermissionDao;
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

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestAppPermissionDaoImpl {

    private static final Set<AppPermission> PERMISSION_SET = Set.of(
            AppPermission.STEPPING_PERMISSION, AppPermission.CHANGE_OWNER_PERMISSION);

    @Inject
    private UserDao userDao;
    @Inject
    private AppPermissionDao appPermissionDao;
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
        assertThrows(IntegrityConstraintViolationException.class, () ->
                appPermissionDao.addPermission(userUuid, AppPermission.STEPPING_PERMISSION));
    }

    @Test
    void testPermissionStory() {
        final String userName = String.format("SomePerson_%s", UUID.randomUUID());

        final User user = createUser(userName);
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
        final User user1 = createUser("user1");
        appPermissionDao.addPermission(user1.getUuid(), AppPermission.STEPPING_PERMISSION);
        appPermissionDao.addPermission(user1.getUuid(), AppPermission.CHANGE_OWNER_PERMISSION);
        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()).size()).isEqualTo(2);

        final FetchAppUserPermissionsRequest request = new FetchAppUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<AppUserPermissions> resultPage = appPermissionDao.fetchAppUserPermissions(request);
        assertThat(resultPage.size()).isOne();
        validateAppPermissions(resultPage, "user1", PERMISSION_SET, Set.of());
    }

    @Test
    void testEffectivePermissions2() {
        final User user1 = createUser("user1");
        final User group1 = createGroup("group1");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        appPermissionDao.addPermission(group1.getUuid(), AppPermission.STEPPING_PERMISSION);
        appPermissionDao.addPermission(group1.getUuid(), AppPermission.CHANGE_OWNER_PERMISSION);

        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()).size()).isEqualTo(0);
        assertThat(appPermissionDao.getPermissionsForUser(group1.getUuid()).size()).isEqualTo(2);

        final FetchAppUserPermissionsRequest request = new FetchAppUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<AppUserPermissions> resultPage = appPermissionDao.fetchAppUserPermissions(request);
        assertThat(resultPage.size()).isEqualTo(2);
        validateAppPermissions(resultPage, "user1", Set.of(), PERMISSION_SET);
        validateAppPermissions(resultPage, "group1", PERMISSION_SET, Set.of());
    }

    @Test
    void testEffectivePermissions3() {
        final User user1 = createUser("user1");
        final User group1 = createGroup("group1");
        final User group2 = createGroup("group2");
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
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<AppUserPermissions> resultPage = appPermissionDao.fetchAppUserPermissions(request);
        assertThat(resultPage.size()).isEqualTo(3);
        validateAppPermissions(resultPage, "user1", Set.of(), PERMISSION_SET);
        validateAppPermissions(resultPage, "group1", Set.of(), PERMISSION_SET);
        validateAppPermissions(resultPage, "group2", PERMISSION_SET, Set.of());
    }

    @Test
    void testEffectivePermissions3None() {
        final User user1 = createUser("user1");
        final User group1 = createGroup("group1");
        final User group2 = createGroup("group2");
        userDao.addUserToGroup(user1.getUuid(), group1.getUuid());
        userDao.addUserToGroup(group1.getUuid(), group2.getUuid());

        assertThat(appPermissionDao.getPermissionsForUser(user1.getUuid()).size()).isEqualTo(0);
        assertThat(appPermissionDao.getPermissionsForUser(group1.getUuid()).size()).isEqualTo(0);
        assertThat(appPermissionDao.getPermissionsForUser(group2.getUuid()).size()).isEqualTo(0);

        final FetchAppUserPermissionsRequest request = new FetchAppUserPermissionsRequest(
                PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<AppUserPermissions> resultPage = appPermissionDao.fetchAppUserPermissions(request);
        assertThat(resultPage.size()).isEqualTo(0);
    }

    @Test
    void testEffectivePermissions3Split() {
        final User user1 = createUser("user1");
        final User group1 = createGroup("group1");
        final User group2 = createGroup("group2");
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
        final User user1 = createUser("user1");
        final User group1 = createGroup("group1");
        final User group2 = createGroup("group2");
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
                PermissionShowLevel.SHOW_EFFECTIVE);
        final ResultPage<AppUserPermissions> resultPage = appPermissionDao.fetchAppUserPermissions(request);
        assertThat(resultPage.size()).isEqualTo(2);
        validateAppPermissions(resultPage, "user1", Set.of(), Set.of(AppPermission.STEPPING_PERMISSION));
        validateAppPermissions(resultPage, "group1", Set.of(AppPermission.STEPPING_PERMISSION), Set.of());
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

    private User createUser(final String name) {
        return create(name, false);
    }

    private User createGroup(final String name) {
        return create(name, true);
    }

    private User create(final String name, final boolean group) {
        User user = User.builder()
                .subjectId(name)
                .displayName(name)
                .uuid(UUID.randomUUID().toString())
                .group(group)
                .build();
        AuditUtil.stamp(() -> "test", user);
        return userDao.create(user);
    }
}
