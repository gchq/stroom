/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.common.impl;

import stroom.security.api.UserIdentity;
import stroom.test.common.MockHttpSessionFactory;
import stroom.util.shared.UserRef;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TestUserIdentitySessionUtil {

    private static final UserRef ALICE = UserRef.builder().uuid("alice-uuid").subjectId("alice").build();

    private final HttpSession session = MockHttpSessionFactory.create("session-1");

    @Test
    void edgeUserIsReadBack() {
        UserIdentitySessionUtil.setEdgeUserInSession(session, ALICE);

        assertThat(UserIdentitySessionUtil.getEdgeUserFromSession(session))
                .contains(ALICE);
    }

    @Test
    void edgeUserIsNeverReturnedAsAnIdentity() {
        // The identity in the session authenticates later requests. A recorded edge user is for display
        // only and must never be able to stand in for it.
        UserIdentitySessionUtil.setEdgeUserInSession(session, ALICE);

        assertThat(UserIdentitySessionUtil.getUserFromSession(session))
                .isEmpty();
    }

    @Test
    void identityIsNotReturnedAsAnEdgeUser() {
        UserIdentitySessionUtil.setUserInSession(session, mock(UserIdentity.class));

        assertThat(UserIdentitySessionUtil.getEdgeUserFromSession(session))
                .isEmpty();
    }

    @Test
    void settingAnIdentityClearsTheEdgeUser() {
        // A session has one owner, so an identity replaces any edge user recorded before it.
        UserIdentitySessionUtil.setEdgeUserInSession(session, ALICE);
        UserIdentitySessionUtil.setUserInSession(session, mock(UserIdentity.class));

        assertThat(UserIdentitySessionUtil.getEdgeUserFromSession(session))
                .isEmpty();
    }

    @Test
    void clearingTheIdentityLeavesTheEdgeUser() {
        UserIdentitySessionUtil.setEdgeUserInSession(session, ALICE);
        UserIdentitySessionUtil.setUserInSession(session, null);

        assertThat(UserIdentitySessionUtil.getEdgeUserFromSession(session))
                .contains(ALICE);
    }

    @Test
    void edgeUserCanBeCleared() {
        UserIdentitySessionUtil.setEdgeUserInSession(session, ALICE);
        UserIdentitySessionUtil.setEdgeUserInSession(session, null);

        assertThat(UserIdentitySessionUtil.getEdgeUserFromSession(session))
                .isEmpty();
    }

    @Test
    void nullSessionHasNoEdgeUser() {
        assertThat(UserIdentitySessionUtil.getEdgeUserFromSession(null))
                .isEmpty();
    }
}
