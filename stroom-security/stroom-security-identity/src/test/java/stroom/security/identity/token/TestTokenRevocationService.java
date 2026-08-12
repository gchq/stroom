/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The contract that matters here is <b>durable write first, best-effort fan-out second</b>. A revocation must
 * survive every peer being unreachable, because correctness rests on the row rather than on the message.
 */
@ExtendWith(MockitoExtension.class)
class TestTokenRevocationService {

    private static final String THIS_NODE = "node1";
    private static final String SUBJECT = "jbloggs";

    @Mock
    private OAuthTokenDao oAuthTokenDao;
    @Mock
    private RevokedJtiCache revokedJtiCache;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private NodeInfo nodeInfo;
    @Mock
    private NodeService nodeService;
    @Mock
    private WebTargetFactory webTargetFactory;

    private TokenRevocationService service;

    @BeforeEach
    void setUp() {
        lenient().when(nodeInfo.getThisNodeName()).thenReturn(THIS_NODE);
        // Run the supplier rather than checking a real permission - the gate itself is asserted separately.
        lenient().when(securityContext.secureResult(any(AppPermission.class), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        lenient().doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(securityContext).asProcessingUser(any(Runnable.class));
        lenient().when(nodeService.findNodeNames(any(FindNodeCriteria.class)))
                .thenReturn(List.of(THIS_NODE));

        service = new TokenRevocationService(
                oAuthTokenDao, revokedJtiCache, securityContext, nodeInfo, nodeService, webTargetFactory);
    }

    @Test
    void revokingASubjectWritesToTheTableAndDropsTheLocalDenylist() {
        when(oAuthTokenDao.revokeBySubjectId(eq(SUBJECT), anyString(), anyLong())).thenReturn(3);
        when(securityContext.getUserRef()).thenReturn(userRef("admin"));

        assertThat(service.revokeForSubject(SUBJECT)).isEqualTo(3);

        verify(oAuthTokenDao).revokeBySubjectId(eq(SUBJECT), eq("admin"), anyLong());
        // Without this the revoking node itself would keep honouring the tokens until its cache expired.
        verify(revokedJtiCache).invalidate();
    }

    @Test
    void blankSubjectRevokesNothingAndTouchesNothing() {
        assertThat(service.revokeForSubject(null)).isZero();
        assertThat(service.revokeForSubject("  ")).isZero();

        verify(oAuthTokenDao, never()).revokeBySubjectId(anyString(), anyString(), anyLong());
        verify(revokedJtiCache, never()).invalidate();
    }

    @Test
    void revokingYourOwnTokensDoesNotRequireManageUsers() {
        when(securityContext.getUserRef()).thenReturn(userRef(SUBJECT));
        when(oAuthTokenDao.revokeBySubjectId(eq(SUBJECT), anyString(), anyLong())).thenReturn(1);

        service.revokeForSubject(SUBJECT);

        verify(securityContext, never()).secureResult(any(AppPermission.class), any());
    }

    @Test
    void revokingSomeoneElsesTokensRequiresManageUsers() {
        when(securityContext.getUserRef()).thenReturn(userRef("someone-else"));

        service.revokeForSubject(SUBJECT);

        verify(securityContext).secureResult(eq(AppPermission.MANAGE_USERS_PERMISSION), any());
    }

    @Test
    void revokingAFamilyAndAJtiAlsoPropagate() {
        when(oAuthTokenDao.revokeByFamilyId(eq("family-A"), anyString(), anyLong())).thenReturn(2);
        when(oAuthTokenDao.revokeByJti(eq("jti-1"), anyString(), anyLong())).thenReturn(true);

        assertThat(service.revokeFamily("family-A")).isEqualTo(2);
        assertThat(service.revokeJti("jti-1")).isTrue();

        verify(revokedJtiCache, org.mockito.Mockito.times(2)).invalidate();
    }

    @Test
    void revokingAJtiThatWasAlreadyRevokedDoesNotFanOut() {
        // Nothing changed, so there is nothing for peers to reload.
        when(oAuthTokenDao.revokeByJti(anyString(), anyString(), anyLong())).thenReturn(false);

        assertThat(service.revokeJti("jti-1")).isFalse();

        verify(revokedJtiCache, never()).invalidate();
    }

    @Test
    void theRevocationSurvivesEveryPeerBeingUnreachable() {
        // The whole point of writing first: if the fan-out were load-bearing, a network partition would mean
        // an admin's revoke silently did nothing. Instead it is durably recorded and peers converge when
        // their denylists next reload.
        when(nodeService.findNodeNames(any(FindNodeCriteria.class)))
                .thenReturn(List.of(THIS_NODE, "node2", "node3"));
        when(nodeService.getBaseEndpointUrl(anyString()))
                .thenThrow(new RuntimeException("node is down"));
        when(oAuthTokenDao.revokeBySubjectId(eq(SUBJECT), anyString(), anyLong())).thenReturn(5);
        when(securityContext.getUserRef()).thenReturn(userRef("admin"));

        assertThat(service.revokeForSubject(SUBJECT))
                .as("a revocation must not fail because peers could not be told")
                .isEqualTo(5);

        verify(oAuthTokenDao).revokeBySubjectId(eq(SUBJECT), eq("admin"), anyLong());
        verify(revokedJtiCache).invalidate();
    }

    @Test
    void oneDeadNodeDoesNotStopTheOthersBeingTold() {
        when(nodeService.findNodeNames(any(FindNodeCriteria.class)))
                .thenReturn(List.of(THIS_NODE, "node2", "node3"));
        // node2 blows up, node3 must still be attempted.
        when(nodeService.getBaseEndpointUrl("node2")).thenThrow(new RuntimeException("node is down"));
        when(nodeService.getBaseEndpointUrl("node3")).thenThrow(new RuntimeException("also down"));
        when(oAuthTokenDao.revokeBySubjectId(anyString(), anyString(), anyLong())).thenReturn(1);
        when(securityContext.getUserRef()).thenReturn(userRef("admin"));

        service.revokeForSubject(SUBJECT);

        verify(nodeService).getBaseEndpointUrl("node2");
        verify(nodeService).getBaseEndpointUrl("node3");
        // The local node is handled directly, not over REST.
        verify(nodeService, never()).getBaseEndpointUrl(THIS_NODE);
    }

    @Test
    void receivingTheFanOutInvalidatesTheLocalDenylist() {
        service.invalidateCacheOnThisNode();

        verify(revokedJtiCache).invalidate();
        // Even the plumbing endpoint is gated, so an ordinary user cannot force cluster-wide cache churn.
        verify(securityContext).secureResult(eq(AppPermission.MANAGE_USERS_PERMISSION), any());
    }

    private UserRef userRef(final String subjectId) {
        return UserRef.builder().uuid(subjectId).subjectId(subjectId).build();
    }
}
