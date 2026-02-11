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

import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionSet;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.User;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class TestSecurityContextImpl {

    private static final UserIdentity ADMIN = createUser(User.ADMIN_USER_SUBJECT_ID);
    private static final UserIdentity PROC_USER = createUser("proc_user");
    private static final UserIdentity USER_1 = createUser("user1");
    private static final UserIdentity GROUP_1 = createUser("group1");
    private static final UserIdentity GROUP_2 = createUser("group2");

    @Mock
    private UserDocumentPermissionsCache mockUserDocumentPermissionsCache;
    @Mock
    private UserDocumentCreatePermissionsCache mocUserDocumentCreatePermissionsCache;
    @Mock
    private UserGroupsCache mockUserGroupsCache;
    @Mock
    private UserCache mockUserCache;
    @Mock
    private UserAppPermissionsCache mockUserAppPermissionsCache;
    @Mock
    private UserIdentityFactory mockUserIdentityFactory;

    @InjectMocks
    private SecurityContextImpl securityContextImpl;

    @Test
    void test() {
        assertThatThrownBy(
                () -> {
                    final SecurityContext securityContext = new SecurityContextImpl(
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
                    securityContext.secure(() ->
                            Assertions.fail("Should never get here"));
                })
                .isInstanceOf(AuthenticationException.class);
    }

    private void doSecureTestWhichThrows(final UserIdentity userIdentity,
                                         final AppPermissionSet requiredPermissions,
                                         final Class<? extends Exception> expectedException) {
        doSecureTest(userIdentity, requiredPermissions, expectedException, false);
    }

    private void doSecureTestWhichDoesWork(final UserIdentity userIdentity,
                                           final AppPermissionSet requiredPermissions) {
        doSecureTest(userIdentity, requiredPermissions, null, true);
    }

    private void doSecureTest(final UserIdentity userIdentity,
                              final AppPermissionSet requiredPermissions,
                              final Class<? extends Exception> expectedException,
                              final boolean shouldWorkRun) {
        final Runnable asUserWork = () -> {
            final AtomicBoolean didWorkRun = new AtomicBoolean(false);
            final Supplier<Boolean> work = () -> {
                didWorkRun.set(true);
                return true;
            };

            if (expectedException != null) {
                assertThatThrownBy(
                        () -> {
                            securityContextImpl.secure(requiredPermissions, work::get);
                        })
                        .isInstanceOf(expectedException);
                assertThat(didWorkRun.get())
                        .isEqualTo(shouldWorkRun);
                didWorkRun.set(false);

                assertThatThrownBy(
                        () -> {
                            securityContextImpl.secureResult(requiredPermissions, work);
                        })
                        .isInstanceOf(expectedException);
                didWorkRun.set(false);
            } else {
                didWorkRun.set(false);
                securityContextImpl.secure(requiredPermissions, supplierAsRunnable(work));
                assertThat(didWorkRun.get())
                        .isEqualTo(shouldWorkRun);

                didWorkRun.set(false);
                assertThat(securityContextImpl.secureResult(requiredPermissions, work))
                        .isTrue();
                assertThat(didWorkRun.get())
                        .isEqualTo(shouldWorkRun);
            }
        };

        if (userIdentity == null) {
            asUserWork.run();
        } else {
            securityContextImpl.asUser(userIdentity, asUserWork);
        }
    }

    private void doSecureTestWhichThrows(final UserIdentity userIdentity,
                                         final Class<? extends Exception> expectedException) {
        doSecureTest(userIdentity, expectedException, false);
    }

    private void doSecureTestWhichDoesWork(final UserIdentity userIdentity) {
        doSecureTest(userIdentity, null, true);
    }

    private void doSecureTest(final UserIdentity userIdentity,
                              final Class<? extends Exception> expectedException,
                              final boolean shouldWorkRun) {
        final Runnable asUserWork = () -> {
            final AtomicBoolean didWorkRun = new AtomicBoolean(false);
            final Supplier<Boolean> work = () -> {
                didWorkRun.set(true);
                return true;
            };

            if (expectedException != null) {
                didWorkRun.set(false);
                assertThatThrownBy(
                        () -> {
                            securityContextImpl.secure(work::get);
                        })
                        .isInstanceOf(expectedException);
                assertThat(didWorkRun.get())
                        .isEqualTo(shouldWorkRun);
                didWorkRun.set(false);

                assertThatThrownBy(
                        () -> {
                            securityContextImpl.secureResult(work);
                        })
                        .isInstanceOf(expectedException);
                assertThat(didWorkRun.get())
                        .isEqualTo(shouldWorkRun);
                didWorkRun.set(false);

            } else {
                didWorkRun.set(false);
                securityContextImpl.secure(supplierAsRunnable(work));
                assertThat(didWorkRun.get())
                        .isEqualTo(shouldWorkRun);

                didWorkRun.set(false);
                assertThat(securityContextImpl.secureResult(work))
                        .isTrue();
                assertThat(didWorkRun.get())
                        .isEqualTo(shouldWorkRun);
            }
        };

        if (userIdentity == null) {
            asUserWork.run();
        } else {
            securityContextImpl.asUser(userIdentity, asUserWork);
        }
    }

    @Test
    void testSecure_notLoggedIn1() {
        doSecureTestWhichThrows(null, AuthenticationException.class);
    }

    @Test
    void testSecure_notLoggedIn2() {
        doSecureTestWhichThrows(
                null,
                AppPermissionSet.of(AppPermission.MANAGE_USERS_PERMISSION),
                AuthenticationException.class);
    }

    @Test
    void testSecure_admin1() {
        final UserIdentity userIdentity = setupAdminUser();
        setProcUserState(userIdentity, false);

        doSecureTestWhichDoesWork(userIdentity);
    }

    @Test
    void testSecure_admin2() {
        final UserIdentity userIdentity = setupAdminUser();
        setProcUserState(userIdentity, false);

        doSecureTestWhichDoesWork(userIdentity, AppPermissionSet.of(AppPermission.MANAGE_USERS_PERMISSION));
    }

    @Test
    void testSecure_procUser1() {
        final UserIdentity userIdentity = setupProcUser();

        doSecureTestWhichDoesWork(userIdentity);
    }

    @Test
    void testSecure_procUser2() {
        final UserIdentity userIdentity = setupProcUser();

        doSecureTestWhichDoesWork(userIdentity, AppPermissionSet.of(AppPermission.MANAGE_USERS_PERMISSION));
    }

    @Test
    void testSecure_normalUser_no_perms1() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(USER_1, EnumSet.noneOf(AppPermission.class));

        doSecureTestWhichDoesWork(userIdentity);
    }

    @Test
    void testSecure_normalUser_no_perms2() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(USER_1, EnumSet.of(AppPermission.VIEW_DATA_PERMISSION));

        doSecureTestWhichThrows(
                userIdentity,
                AppPermissionSet.of(AppPermission.MANAGE_USERS_PERMISSION),
                PermissionException.class);
    }

    @Test
    void testSecure_normalUser_with_perms() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(USER_1, EnumSet.of(
                AppPermission.VIEW_DATA_PERMISSION,
                AppPermission.MANAGE_USERS_PERMISSION));

        doSecureTestWhichDoesWork(
                userIdentity,
                AppPermissionSet.of(AppPermission.MANAGE_USERS_PERMISSION));
    }

    @Test
    void testSecure_normalUser_multipleRequiredPerms1() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(USER_1, EnumSet.of(
                AppPermission.VIEW_DATA_PERMISSION,
                AppPermission.MANAGE_USERS_PERMISSION));

        doSecureTestWhichDoesWork(
                userIdentity,
                AppPermissionSet.allOf(
                        AppPermission.VIEW_DATA_PERMISSION,
                        AppPermission.MANAGE_USERS_PERMISSION));
    }

    @Test
    void testSecure_normalUser_multipleRequiredPerms2() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(USER_1, EnumSet.of(
                AppPermission.MANAGE_USERS_PERMISSION));

        doSecureTestWhichThrows(
                userIdentity,
                AppPermissionSet.allOf(
                        AppPermission.VIEW_DATA_PERMISSION,
                        AppPermission.MANAGE_USERS_PERMISSION),
                PermissionException.class);
    }

    @Test
    void testSecure_normalUser_multipleRequiredPerms3() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(USER_1, EnumSet.of(
                AppPermission.MANAGE_USERS_PERMISSION));

        doSecureTestWhichDoesWork(
                userIdentity,
                AppPermissionSet.oneOf(
                        AppPermission.VIEW_DATA_PERMISSION,
                        AppPermission.MANAGE_USERS_PERMISSION));
    }

    @Test
    void testSecure_normalUser_multipleRequiredPerms4() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(USER_1, EnumSet.of(
                AppPermission.VIEW_DATA_PERMISSION));

        doSecureTestWhichDoesWork(
                userIdentity,
                AppPermissionSet.oneOf(
                        AppPermission.VIEW_DATA_PERMISSION,
                        AppPermission.MANAGE_USERS_PERMISSION));
    }

    @Test
    void testSecure_normalUser_noPermsRequired() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(USER_1, EnumSet.of(
                AppPermission.VIEW_DATA_PERMISSION));

        doSecureTestWhichDoesWork(
                userIdentity,
                AppPermissionSet.empty());
    }

    @Test
    void testHasAppPermission_noRequiredPerms1() {
        final UserIdentity userIdentity = USER_1;

        securityContextImpl.asUser(userIdentity, () -> {
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(AppPermissionSet.empty()))
                    .isTrue();
        });
    }

    @Test
    void testHasAppPermission_noRequiredPerms2() {
        final UserIdentity userIdentity = USER_1;

        securityContextImpl.asUser(userIdentity, () -> {
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(null))
                    .isTrue();
        });
    }

    @Test
    void testHasAppPermission_missingPerm() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(USER_1, EnumSet.of(
                AppPermission.VIEW_DATA_PERMISSION));

        securityContextImpl.asUser(userIdentity, () -> {
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(
                    AppPermissionSet.of(AppPermission.MANAGE_USERS_PERMISSION)))
                    .isFalse();
        });
    }

    @Test
    void testHasAppPermission_procUser() {
        final UserIdentity userIdentity = PROC_USER;
        setProcUserState(userIdentity, true);

        securityContextImpl.asUser(userIdentity, () -> {
            assertThat(securityContextImpl.isProcessingUser())
                    .isTrue();
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(
                    AppPermissionSet.of(AppPermission.MANAGE_USERS_PERMISSION)))
                    .isTrue();
        });
    }

    @Test
    void testHasAppPermission_admin() {
        final UserIdentity userIdentity = setupAdminUser();
        setProcUserState(userIdentity, false);

        securityContextImpl.asUser(userIdentity, () -> {
            assertThat(securityContextImpl.isAdmin())
                    .isTrue();
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(
                    AppPermissionSet.of(AppPermission.MANAGE_USERS_PERMISSION)))
                    .isTrue();
        });
    }

    @Test
    void testHasAppPermission_hasPerm() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(userIdentity, EnumSet.of(AppPermission.MANAGE_USERS_PERMISSION));

        securityContextImpl.asUser(userIdentity, () -> {
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(
                    AppPermissionSet.of(AppPermission.MANAGE_USERS_PERMISSION)))
                    .isTrue();
        });
    }

    @Test
    void testHasAppPermission_allOf_success() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(userIdentity, EnumSet.of(
                AppPermission.MANAGE_USERS_PERMISSION,
                AppPermission.VIEW_DATA_PERMISSION,
                AppPermission.STEPPING_PERMISSION));

        securityContextImpl.asUser(userIdentity, () -> {
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(
                    AppPermissionSet.allOf(
                            AppPermission.MANAGE_USERS_PERMISSION,
                            AppPermission.VIEW_DATA_PERMISSION)))
                    .isTrue();
        });
    }

    @Test
    void testHasAppPermission_allOf_fail() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(userIdentity, EnumSet.of(
                AppPermission.MANAGE_USERS_PERMISSION,
                AppPermission.STEPPING_PERMISSION));

        securityContextImpl.asUser(userIdentity, () -> {
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(
                    AppPermissionSet.allOf(
                            AppPermission.MANAGE_USERS_PERMISSION,
                            AppPermission.VIEW_DATA_PERMISSION)))
                    .isFalse();
        });
    }

    @Test
    void testHasAppPermission_oneOf_success() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(userIdentity, EnumSet.of(
                AppPermission.MANAGE_USERS_PERMISSION,
                AppPermission.VIEW_DATA_PERMISSION,
                AppPermission.STEPPING_PERMISSION));

        securityContextImpl.asUser(userIdentity, () -> {
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(
                    AppPermissionSet.allOf(
                            AppPermission.MANAGE_USERS_PERMISSION,
                            AppPermission.VIEW_DATA_PERMISSION)))
                    .isTrue();
        });
    }

    @Test
    void testHasAppPermission_oneOf_fail() {
        final UserIdentity userIdentity = USER_1;
        setProcUserState(userIdentity, false);
        setUserAppPerms(userIdentity, EnumSet.of(
                AppPermission.STEPPING_PERMISSION,
                AppPermission.MANAGE_VOLUMES_PERMISSION));

        securityContextImpl.asUser(userIdentity, () -> {
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(
                    AppPermissionSet.allOf(
                            AppPermission.MANAGE_USERS_PERMISSION,
                            AppPermission.VIEW_DATA_PERMISSION)))
                    .isFalse();
        });
    }

    @Test
    void testHasAppPermission_allOfViaGroups_success() {
        setProcUserState(USER_1, false);
        setGroups(USER_1, Set.of(GROUP_1, GROUP_2));

        setUserAppPerms(USER_1, EnumSet.noneOf(AppPermission.class));
        setUserAppPerms(GROUP_1, EnumSet.of(
                AppPermission.MANAGE_USERS_PERMISSION));
        setUserAppPerms(GROUP_2, EnumSet.of(
                AppPermission.VIEW_DATA_PERMISSION));

        securityContextImpl.asUser(USER_1, () -> {
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(
                    AppPermissionSet.allOf(
                            AppPermission.MANAGE_USERS_PERMISSION,
                            AppPermission.VIEW_DATA_PERMISSION)))
                    .isTrue();
        });
    }

    @Test
    void testHasAppPermission_allOfViaGroups_success2() {
        // group2 => group1 => user1
        setProcUserState(USER_1, false);
        setGroups(USER_1, Set.of(GROUP_1));
        setGroups(GROUP_1, Set.of(GROUP_2));

        setUserAppPerms(USER_1, EnumSet.noneOf(AppPermission.class));
        setUserAppPerms(GROUP_1, EnumSet.of(
                AppPermission.MANAGE_USERS_PERMISSION));
        setUserAppPerms(GROUP_2, EnumSet.of(
                AppPermission.VIEW_DATA_PERMISSION));

        securityContextImpl.asUser(USER_1, () -> {
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(
                    AppPermissionSet.allOf(
                            AppPermission.MANAGE_USERS_PERMISSION,
                            AppPermission.VIEW_DATA_PERMISSION)))
                    .isTrue();
        });
    }

    @Test
    void testHasAppPermission_oneOfViaGroups_success() {
        setProcUserState(USER_1, false);
        setGroups(USER_1, Set.of(GROUP_1));
        setGroups(GROUP_1, Set.of(GROUP_2));

        setUserAppPerms(USER_1, EnumSet.noneOf(AppPermission.class));
        setUserAppPerms(GROUP_1, EnumSet.of(
                AppPermission.STEPPING_PERMISSION));
        setUserAppPerms(GROUP_2, EnumSet.of(
                AppPermission.VIEW_DATA_PERMISSION));

        securityContextImpl.asUser(USER_1, () -> {
            // No required perms
            assertThat(securityContextImpl.hasAppPermissions(
                    AppPermissionSet.oneOf(
                            AppPermission.MANAGE_USERS_PERMISSION,
                            AppPermission.VIEW_DATA_PERMISSION)))
                    .isTrue();
        });
    }

    @Test
    void testGetUserIdentity_null() {
        assertThat(securityContextImpl.getUserIdentity())
                .isNull();
    }

    @Test
    void testGetUserIdentity_nonNull() {
        securityContextImpl.asUser(USER_1, () -> {
            assertThat(securityContextImpl.getUserIdentity())
                    .isEqualTo(USER_1);
        });
    }

    @Test
    void testGetUserRef_null() {
        // Inconsistent outcome with getUserIdentity(), which just returns null
        assertThatThrownBy(
                () -> {
                    securityContextImpl.getUserRef();
                })
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void testGetUserRef_nonNull() {
        securityContextImpl.asUser(USER_1, () -> {
            assertThat(securityContextImpl.getUserRef())
                    .isEqualTo(((BasicUserIdentity) USER_1).getUserRef());
        });
    }

    @Test
    void testUseAsRead1() {
        final DocRef docRef = DocRef.builder()
                .randomUuid()
                .type("MyType")
                .name("MyDoc")
                .build();
        final UserIdentity userIdentity = USER_1;
        setUserDocPerm(userIdentity, docRef, DocumentPermission.USE);

        securityContextImpl.asUser(userIdentity, () -> {

            assertThat(securityContextImpl.hasDocumentPermission(docRef, DocumentPermission.USE))
                    .isTrue();
            assertThat(securityContextImpl.hasDocumentPermission(docRef, DocumentPermission.VIEW))
                    .isFalse();
            assertThat(securityContextImpl.hasDocumentPermission(docRef, DocumentPermission.EDIT))
                    .isFalse();

            assertThat(securityContextImpl.isUseAsRead())
                    .isFalse();

            securityContextImpl.useAsRead(() -> {
                assertThat(securityContextImpl.isUseAsRead())
                        .isTrue();
                assertThat(securityContextImpl.hasDocumentPermission(docRef, DocumentPermission.USE))
                        .isTrue();
                // Now have VIEW, via useAsRead
                assertThat(securityContextImpl.hasDocumentPermission(docRef, DocumentPermission.VIEW))
                        .isTrue();
                assertThat(securityContextImpl.hasDocumentPermission(docRef, DocumentPermission.EDIT))
                        .isFalse();
            });
        });
    }

    @Test
    void testUseAsRead2() {
        final DocRef docRef = DocRef.builder()
                .randomUuid()
                .type("MyType")
                .name("MyDoc")
                .build();
        final UserIdentity userIdentity = USER_1;

        securityContextImpl.asUser(userIdentity, () -> {

            assertThat(securityContextImpl.hasDocumentPermission(docRef, DocumentPermission.USE))
                    .isFalse();
            assertThat(securityContextImpl.hasDocumentPermission(docRef, DocumentPermission.VIEW))
                    .isFalse();
            assertThat(securityContextImpl.hasDocumentPermission(docRef, DocumentPermission.EDIT))
                    .isFalse();

            assertThat(securityContextImpl.isUseAsRead())
                    .isFalse();

            securityContextImpl.useAsRead(() -> {
                assertThat(securityContextImpl.isUseAsRead())
                        .isTrue();
                // No change in useAsRead, as user doesn't have USE
                assertThat(securityContextImpl.hasDocumentPermission(docRef, DocumentPermission.USE))
                        .isFalse();
                assertThat(securityContextImpl.hasDocumentPermission(docRef, DocumentPermission.VIEW))
                        .isFalse();
                assertThat(securityContextImpl.hasDocumentPermission(docRef, DocumentPermission.EDIT))
                        .isFalse();
            });
        });
    }

    @Test
    void asProcessingUser() {
        final AtomicBoolean didWork = new AtomicBoolean(false);
        final Supplier<Boolean> asProcUserWork = () -> {
            didWork.set(true);
            return true;
        };

        final UserIdentity procUser = setupProcUser();
        Mockito.when(mockUserIdentityFactory.getServiceUserIdentity())
                .thenReturn(PROC_USER);
        Mockito.when(mockUserIdentityFactory.isServiceUser(Mockito.eq(USER_1)))
                .thenReturn(false);

        final UserIdentity userIdentity = USER_1;
        securityContextImpl.asUser(userIdentity, () -> {
            assertThat(securityContextImpl.isProcessingUser())
                    .isFalse();
            assertThat(securityContextImpl.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION))
                    .isFalse();
//
            securityContextImpl.asProcessingUser(() -> {
                assertThat(securityContextImpl.isProcessingUser())
                        .isTrue();

                assertThat(securityContextImpl.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION))
                        .isTrue();
            });

            assertThat(securityContextImpl.asProcessingUserResult(() -> {
                assertThat(securityContextImpl.isProcessingUser())
                        .isTrue();

                assertThat(securityContextImpl.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION))
                        .isTrue();
                return 123;
            })).isEqualTo(123);
        });
    }

    @Test
    void testInGroup_false() {
        final UserIdentity userIdentity = USER_1;
        setGroups(userIdentity, Set.of());

        securityContextImpl.asUser(userIdentity, () -> {
            assertThat(securityContextImpl.inGroup(GROUP_1.subjectId()))
                    .isFalse();
        });
    }

    @Test
    void testInGroup_true() {
        final UserIdentity userIdentity = USER_1;
        setGroups(userIdentity, Set.of(GROUP_1, GROUP_2));

        securityContextImpl.asUser(userIdentity, () -> {
            assertThat(securityContextImpl.inGroup(GROUP_1.subjectId()))
                    .isTrue();
        });
    }

    @Test
    void testInGroup_indirect_true() {
        final UserIdentity userIdentity = USER_1;
        setGroups(userIdentity, Set.of(GROUP_1));
        setGroups(GROUP_1, Set.of(GROUP_2));

        securityContextImpl.asUser(userIdentity, () -> {
            assertThat(securityContextImpl.inGroup(GROUP_1.subjectId()))
                    .isTrue();
            assertThat(securityContextImpl.inGroup(GROUP_2.subjectId()))
                    .isTrue();
        });
    }

    private UserRef asUserRef(final UserIdentity userIdentity) {
        return ((BasicUserIdentity) userIdentity).getUserRef();
    }

    private void setUserAppPerms(final UserIdentity userIdentity,
                                 final Set<AppPermission> permissionsHeld) {
        Mockito.when(mockUserAppPermissionsCache.get(Mockito.eq(asUserRef(userIdentity))))
                .thenReturn(permissionsHeld);
    }

    private void setUserDocPerm(final UserIdentity userIdentity,
                                final DocRef docRef,
                                final DocumentPermission documentPermission) {
        Mockito.when(mockUserDocumentPermissionsCache.hasDocumentPermission(
                        Mockito.eq(asUserRef(userIdentity)),
                        Mockito.eq(docRef),
                        Mockito.eq(documentPermission)))
                .thenReturn(true);
    }

    private void setGroups(final UserIdentity userIdentity,
                           final Set<UserIdentity> groups) {
        Mockito.when(mockUserGroupsCache.getGroups(Mockito.eq(asUserRef(userIdentity))))
                .thenReturn(groups.stream()
                        .map(this::asUserRef)
                        .collect(Collectors.toSet()));
    }

    private void setProcUserState(final UserIdentity userIdentity, final boolean isProcUser) {
        Mockito.when(mockUserIdentityFactory.isServiceUser(Mockito.eq(userIdentity)))
                .thenReturn(isProcUser);
    }

    private static UserIdentity createUser(final String subjectId) {
        return new BasicUserIdentity(UserRef.builder()
                .uuid(UUID.randomUUID().toString())
                .subjectId(Objects.requireNonNull(subjectId))
                .user()
                .build());
    }

    private static UserIdentity createGroup(final String subjectId) {
        return new BasicUserIdentity(UserRef.builder()
                .uuid(UUID.randomUUID().toString())
                .subjectId(Objects.requireNonNull(subjectId))
                .group()
                .build());
    }

    private Supplier<Void> runnableAsSupplier(final Runnable runnable) {
        return () -> {
            runnable.run();
            return null;
        };
    }

    private Runnable supplierAsRunnable(final Supplier<?> supplier) {
        return supplier::get;
    }

    private UserIdentity setupAdminUser() {
        final UserIdentity userIdentity = ADMIN;
        setUserAppPerms(userIdentity, EnumSet.of(AppPermission.ADMINISTRATOR));
        return userIdentity;
    }

    private UserIdentity setupProcUser() {
        final UserIdentity userIdentity = PROC_USER;
        setProcUserState(userIdentity, true);
        return userIdentity;
    }
}
