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
import stroom.util.shared.UserRef;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TestUserIdentitySessionUtil {

    private static final UserRef ALICE = UserRef.builder().uuid("alice-uuid").subjectId("alice").build();

    @Mock
    private HttpSession session;

    private final Map<String, Object> attributes = new HashMap<>();

    @BeforeEach
    void setUp() {
        // Back the mock session with a real attribute map.
        lenient().doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(session).setAttribute(anyString(), any());
        lenient().when(session.getAttribute(anyString()))
                .thenAnswer(invocation -> attributes.get((String) invocation.getArgument(0)));
    }

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
