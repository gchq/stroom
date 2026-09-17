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

package stroom.security.identity.token;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Every endpoint here is gated on ADMINISTRATOR, and the DAO tests reach the DAO directly, so nothing else
 * fails if a gate is dropped. Revoking a signing key signs out the whole cluster, so the gate is the feature.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestSigningKeyResourceImplPermissions {

    @Mock
    private JwkDao jwkDao;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private SigningKeyRefreshFanOut signingKeyRefreshFanOut;
    @Mock
    private StroomEventLoggingService stroomEventLoggingService;

    private SigningKeyResourceImpl resource() {
        return new SigningKeyResourceImpl(
                () -> jwkDao, () -> securityContext, () -> signingKeyRefreshFanOut, () -> stroomEventLoggingService);
    }

    /**
     * Stand in for a caller who holds the permission: the guarded work runs.
     */
    private void givenAnAdministrator() {
        when(securityContext.secureResult(any(AppPermission.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        when(securityContext.getUserIdentityForAudit()).thenReturn("anAdmin");
    }

    /**
     * Stand in for a caller who does not: the guarded work must not run at all.
     */
    private void givenPermissionRefused() {
        when(securityContext.secureResult(any(AppPermission.class), any()))
                .thenThrow(new PermissionException(null, "Not an administrator"));
    }

    @Test
    void listingKeysAsksForAdministrator() {
        givenAnAdministrator();
        when(jwkDao.list()).thenReturn(List.of());

        resource().list();

        verify(securityContext).secureResult(eq(AppPermission.ADMINISTRATOR), any());
    }

    @Test
    void revokingAKeyAsksForAdministrator() {
        givenAnAdministrator();
        when(jwkDao.list()).thenReturn(List.of());
        when(jwkDao.revoke(anyInt(), anyString())).thenReturn(1);

        resource().revoke(1);

        verify(securityContext).secureResult(eq(AppPermission.ADMINISTRATOR), any());
    }

    @Test
    void revokingEveryKeyAsksForAdministrator() {
        givenAnAdministrator();
        when(jwkDao.revokeAll(anyString())).thenReturn(2);

        resource().revokeAll();

        verify(securityContext).secureResult(eq(AppPermission.ADMINISTRATOR), any());
    }

    @Test
    void refreshingANodeAsksForAdministrator() {
        // Reachable directly, not only from the fan-out, and it discards the cache the whole cluster's
        // token verification reads from.
        givenAnAdministrator();
        when(signingKeyRefreshFanOut.refreshOnNode(anyString())).thenReturn(true);

        resource().refreshOnNode("node1");

        verify(securityContext).secureResult(eq(AppPermission.ADMINISTRATOR), any());
    }

    @Test
    void refusedCallerRevokesNothingAndLearnsNothing() {
        // The stronger half: not merely that the permission was asked for, but that a refusal leaves the
        // keys untouched and hands back no part of the list.
        givenPermissionRefused();
        final SigningKeyResourceImpl resource = resource();

        assertThatThrownBy(resource::list).isInstanceOf(PermissionException.class);
        assertThatThrownBy(() -> resource.revoke(1)).isInstanceOf(PermissionException.class);
        assertThatThrownBy(resource::revokeAll).isInstanceOf(PermissionException.class);
        assertThatThrownBy(() -> resource.refreshOnNode("node1")).isInstanceOf(PermissionException.class);

        verifyNoInteractions(jwkDao);
        verifyNoInteractions(signingKeyRefreshFanOut);
        // Nor is a refusal recorded as though a revocation had happened.
        verifyNoInteractions(stroomEventLoggingService);
    }

    @Test
    void revokingNothingPropagatesNothing() {
        // A revoke that matched no row changed no state, so telling the cluster to drop its caches would be
        // cost with no cause, and an audit entry would record an act that did not occur.
        givenAnAdministrator();
        when(jwkDao.list()).thenReturn(List.of());
        when(jwkDao.revoke(anyInt(), anyString())).thenReturn(0);

        resource().revoke(1);

        verify(signingKeyRefreshFanOut, never()).refreshAllNodes();
        verifyNoInteractions(stroomEventLoggingService);
    }
}
