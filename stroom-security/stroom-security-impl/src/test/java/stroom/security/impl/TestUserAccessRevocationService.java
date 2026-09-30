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

package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.openid.api.TokenRevoker;
import stroom.security.shared.AppPermission;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestUserAccessRevocationService {

    private static final String SUBJECT = "jbloggs";

    @Mock
    private SessionListService sessionListService;
    @Mock
    private TokenRevoker tokenRevoker;
    @Mock
    private SecurityContext securityContext;

    private UserAccessRevocationService service;

    @BeforeEach
    void setUp() {
        lenient().when(securityContext.secureResult(any(AppPermission.class), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        service = new UserAccessRevocationService(
                () -> sessionListService, () -> tokenRevoker, securityContext);
    }

    @Test
    void bothHalvesAreDoneSoNeitherApiNorUiAccessSurvives() {
        // Session eviction alone leaves a stolen bearer token working until it expires; token revocation alone
        // leaves the UI session usable. Only doing both actually cuts the user off.
        when(securityContext.getUserRef()).thenReturn(userRef("admin"));
        when(tokenRevoker.revokeForSubject(SUBJECT)).thenReturn(4);

        assertThat(service.revokeAccessForUser(SUBJECT)).isEqualTo(4);

        verify(tokenRevoker).revokeForSubject(SUBJECT);
        verify(sessionListService).evictUserSessions(eq(SUBJECT), isNull());
    }

    @Test
    void tokensAreRevokedBeforeSessionsAreEvicted() {
        // Order matters: while a session is still alive it could refresh. Killing the refresh family first
        // means the session being evicted cannot mint a replacement on its way out.
        when(securityContext.getUserRef()).thenReturn(userRef("admin"));

        service.revokeAccessForUser(SUBJECT);

        final InOrder inOrder = Mockito.inOrder(tokenRevoker, sessionListService);
        inOrder.verify(tokenRevoker).revokeForSubject(SUBJECT);
        inOrder.verify(sessionListService).evictUserSessions(eq(SUBJECT), isNull());
    }

    @Test
    void noSessionIsSparedUnlikeTheSelfServiceSignOut() {
        // terminateOtherSessions deliberately spares the caller's own session; revoking access must not, or an
        // admin revoking their own compromised account would keep the very session an attacker is riding.
        when(securityContext.getUserRef()).thenReturn(userRef(SUBJECT));

        service.revokeAccessForUser(SUBJECT);

        verify(sessionListService).evictUserSessions(eq(SUBJECT), isNull());
    }

    @Test
    void revokingYourOwnAccessDoesNotRequireManageUsers() {
        when(securityContext.getUserRef()).thenReturn(userRef(SUBJECT));

        service.revokeAccessForUser(SUBJECT);

        verify(securityContext, never()).secureResult(any(AppPermission.class), any());
    }

    @Test
    void revokingAnotherUsersAccessRequiresManageUsers() {
        when(securityContext.getUserRef()).thenReturn(userRef("someone-else"));

        service.revokeAccessForUser(SUBJECT);

        verify(securityContext).secureResult(eq(AppPermission.MANAGE_USERS_PERMISSION), any());
    }

    @Test
    void zeroTokensRevokedIsNotTreatedAsFailure() {
        // The normal external-IdP case: nothing was minted here, so there is nothing to revoke, but the
        // sessions must still be terminated.
        when(securityContext.getUserRef()).thenReturn(userRef("admin"));
        when(tokenRevoker.revokeForSubject(SUBJECT)).thenReturn(0);

        assertThat(service.revokeAccessForUser(SUBJECT)).isZero();

        verify(sessionListService).evictUserSessions(eq(SUBJECT), isNull());
    }

    @Test
    void blankSubjectDoesNothing() {
        assertThat(service.revokeAccessForUser(null)).isZero();
        assertThat(service.revokeAccessForUser(" ")).isZero();

        verify(tokenRevoker, never()).revokeForSubject(anyString());
        verify(sessionListService, never()).evictUserSessions(anyString(), any());
    }

    private UserRef userRef(final String subjectId) {
        return UserRef.builder().uuid(subjectId).subjectId(subjectId).build();
    }
}
