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

package stroom.security.impl;

import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused test for the per-node authorisation in {@link SessionListListener}: a user may terminate their
 * own sessions, but terminating another user's requires MANAGE_USERS. The multi-node fan-out itself is
 * covered by {@link TestSessionListListener}, which uses an always-admin security context; here the
 * identity is controllable so both sides of the gate can be exercised.
 */
@ExtendWith(MockitoExtension.class)
class TestSessionListListenerPermissions {

    private static final String THIS_NODE = "node1";

    @Mock
    private NodeInfo nodeInfo;
    @Mock
    private NodeService nodeService;
    @Mock
    private TaskContextFactory taskContextFactory;
    @Mock
    private WebTargetFactory webTargetFactory;
    @Mock
    private StroomUserIdentityFactory stroomUserIdentityFactory;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private ExecutorProvider executorProvider;

    private SessionListListener listener;

    @BeforeEach
    void setUp() {
        lenient().when(executorProvider.get()).thenReturn(Runnable::run);
        // Force the local-node path so evictUserSessionsOnThisNode (the authorised code) actually runs.
        lenient().when(nodeInfo.getThisNodeName()).thenReturn(THIS_NODE);
        listener = new SessionListListener(nodeInfo, nodeService, taskContextFactory,
                webTargetFactory, stroomUserIdentityFactory, securityContext, executorProvider);
    }

    @Test
    void terminatingYourOwnSessionsSkipsThePermissionCheck() {
        when(securityContext.getUserRef()).thenReturn(userRef("alice"));

        listener.evictUserSessionsOnNode("alice", null, THIS_NODE);

        // A user terminating their own sessions must not be held to the admin bar.
        verify(securityContext, never()).secureResult(any(AppPermission.class), any());
    }

    @Test
    void terminatingAnotherUsersSessionsRequiresManageUsers() {
        when(securityContext.getUserRef()).thenReturn(userRef("alice"));
        stubSecureResultToRun();

        listener.evictUserSessionsOnNode("bob", null, THIS_NODE);

        verify(securityContext).secureResult(eq(AppPermission.MANAGE_USERS_PERMISSION), any());
    }

    @Test
    void terminatingWithNoLoggedInUserRequiresManageUsers() {
        // The @Unauthenticated reset path runs as the processing user, so it is not "self" and must clear
        // the same MANAGE_USERS gate (which the processing user passes). Any caller lacking an identity is
        // held to the admin bar here.
        when(securityContext.getUserRef()).thenReturn(null);
        stubSecureResultToRun();

        listener.evictUserSessionsOnNode("bob", null, THIS_NODE);

        verify(securityContext).secureResult(eq(AppPermission.MANAGE_USERS_PERMISSION), any());
    }

    private void stubSecureResultToRun() {
        when(securityContext.secureResult(eq(AppPermission.MANAGE_USERS_PERMISSION), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
    }

    private UserRef userRef(final String subjectId) {
        return UserRef.builder().uuid(subjectId).subjectId(subjectId).build();
    }
}
