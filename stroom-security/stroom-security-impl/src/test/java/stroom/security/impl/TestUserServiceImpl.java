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

import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.User;
import stroom.util.shared.UserDesc;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicReference;

@ExtendWith(MockitoExtension.class)
class TestUserServiceImpl {

    public static final String SUBJECT_ID = "user123";
    public static final String DISPLAY_NAME = "jbloggs";
    public static final String FULL_NAME = "Joe Bloggs";
    @Mock
    private UserDao mockUserDao;

    @Test
    void testGetOrCreateUser_new() {
        final UserServiceImpl userService = new UserServiceImpl(
                new MockSecurityContext(),
                mockUserDao,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        final UserDesc userDesc = UserDesc.builder(SUBJECT_ID)
                .build();

        final AtomicReference<User> onCreateConsumer = new AtomicReference<>();

        Mockito.when(mockUserDao.tryCreate(Mockito.any(User.class), Mockito.any()))
                .thenAnswer(invocation -> {
                    return invocation.getArgument(0);
                });

        final User user = userService.getOrCreateUser(userDesc, onCreateConsumer::set);

        Assertions.assertThat(user.getSubjectId())
                .isEqualTo(SUBJECT_ID);
        Assertions.assertThat(user.getDisplayName())
                .isEqualTo(SUBJECT_ID);  // No display name so use sub
        Assertions.assertThat(user.getFullName())
                .isNull();
    }

    @Test
    void testGetOrCreateUser_new2() {
        final UserServiceImpl userService = new UserServiceImpl(
                new MockSecurityContext(),
                mockUserDao,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        final UserDesc userDesc = UserDesc.builder(SUBJECT_ID)
                .displayName(DISPLAY_NAME)
                .fullName(FULL_NAME)
                .build();

        final AtomicReference<User> onCreateConsumer = new AtomicReference<>();

        Mockito.when(mockUserDao.tryCreate(Mockito.any(User.class), Mockito.any()))
                .thenAnswer(invocation -> {
                    return invocation.getArgument(0);
                });

        final User user = userService.getOrCreateUser(userDesc, onCreateConsumer::set);

        Assertions.assertThat(user.getSubjectId())
                .isEqualTo(SUBJECT_ID);
        Assertions.assertThat(user.getDisplayName())
                .isEqualTo(DISPLAY_NAME);  // No display name so use sub
        Assertions.assertThat(user.getFullName())
                .isEqualTo(FULL_NAME);
    }

    // --- Disabling a user cuts off live access ------------------------------------------------------

    @Test
    void disablingAUserRevokesTheirLiveAccess() {
        // Disabling only changes a row. Without this, a bearer token the user already holds keeps working
        // until it expires - token verification consults no user cache - and a node that missed the
        // permission-change event keeps serving the cached, still-enabled user.
        final UserAccessRevocationService revocationService =
                Mockito.mock(UserAccessRevocationService.class);
        final UserServiceImpl userService = userServiceWith(revocationService);
        final User disabled = userBuilder().enabled(false).build();
        Mockito.when(mockUserDao.update(Mockito.any(User.class))).thenReturn(disabled);

        userService.update(disabled);

        Mockito.verify(revocationService).revokeAccessForUser(SUBJECT_ID);
    }

    @Test
    void updatingAStillEnabledUserRevokesNothing() {
        final UserAccessRevocationService revocationService =
                Mockito.mock(UserAccessRevocationService.class);
        final UserServiceImpl userService = userServiceWith(revocationService);
        final User enabled = userBuilder().enabled(true).build();
        Mockito.when(mockUserDao.update(Mockito.any(User.class))).thenReturn(enabled);

        userService.update(enabled);

        Mockito.verifyNoInteractions(revocationService);
    }

    @Test
    void disabledGroupIsUnrepresentableSoTheGroupGuardIsBeltAndBraces() {
        // UserRef refuses to model a disabled group at all, so the isGroup() guard in
        // revokeAccessIfDisabled can never actually fire. Kept as defence in depth, and pinned here so that
        // if the domain ever does allow it, this documents what the guard is for: a group holds no sessions
        // or tokens of its own, so revoking against it would be meaningless.
        Assertions.assertThatThrownBy(() -> userBuilder().group(true).enabled(false).build().asRef())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Groups cannot be disabled");
    }

    private UserServiceImpl userServiceWith(final UserAccessRevocationService revocationService) {
        return new UserServiceImpl(
                new MockSecurityContext(),
                mockUserDao,
                Mockito.mock(stroom.security.impl.event.PermissionChangeEventBus.class),
                null,
                null,
                null,
                null,
                null,
                () -> revocationService);
    }

    private static User.Builder userBuilder() {
        return User.builder()
                .uuid(SUBJECT_ID)
                .subjectId(SUBJECT_ID)
                .displayName(DISPLAY_NAME)
                .group(false);
    }
}
