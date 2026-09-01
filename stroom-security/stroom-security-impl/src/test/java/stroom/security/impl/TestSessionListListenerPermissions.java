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

import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.shared.AppPermission;
import stroom.security.shared.HasUserRef;
import stroom.security.shared.SessionListResponse;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.UserRef;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void terminatingYourOwnSessionByIdSkipsThePermissionCheck() {
        registerSession("sess1", userRef("alice"));
        when(securityContext.getUserRef()).thenReturn(userRef("alice"));

        assertThat(listener.evictSessionOnNode(SessionListListener.sessionHandle("sess1"), THIS_NODE)).isTrue();

        verify(securityContext, never()).secureResult(any(AppPermission.class), any());
    }

    @Test
    void terminatingAnotherUsersSessionByIdRequiresManageUsers() {
        registerSession("sess1", userRef("bob"));
        when(securityContext.getUserRef()).thenReturn(userRef("alice"));
        stubSecureResultToRun();

        assertThat(listener.evictSessionOnNode(SessionListListener.sessionHandle("sess1"), THIS_NODE)).isTrue();

        verify(securityContext).secureResult(eq(AppPermission.MANAGE_USERS_PERMISSION), any());
    }

    @Test
    void terminatingAnUnknownSessionIdIsANoOpAndNotAPermissionFailure() {
        // During the cluster fan-out every node except the one holding the session takes this path,
        // including when a non-admin terminates their own session. It must therefore return false rather
        // than consult - and potentially fail - the MANAGE_USERS gate.
        assertThat(listener.evictSessionOnNode(SessionListListener.sessionHandle("no-such-session"), THIS_NODE))
                .isFalse();

        verify(securityContext, never()).secureResult(any(AppPermission.class), any());
    }

    @Test
    void terminatingASessionWithNoIdentityRequiresManageUsers() {
        // An un-authenticated session has no owner to compare against, so it cannot be "self".
        registerSession("sess1", null);
        when(securityContext.getUserRef()).thenReturn(userRef("alice"));
        stubSecureResultToRun();

        assertThat(listener.evictSessionOnNode(SessionListListener.sessionHandle("sess1"), THIS_NODE)).isTrue();

        verify(securityContext).secureResult(eq(AppPermission.MANAGE_USERS_PERMISSION), any());
    }

    @Test
    void theSessionHandleIsNotTheSessionIdAndCannotBeReversedToIt() {
        // A session id IS the session cookie value. If a handle were the id, or trivially derived from it, then
        // listing sessions would hand a Manage Users holder a credential for impersonating their owner - turning
        // an administrative permission into the ability to become anybody.
        final String sessionId = "a-real-looking-session-id";
        final String handle = SessionListListener.sessionHandle(sessionId);

        assertThat(handle)
                .isNotNull()
                .isNotEqualTo(sessionId)
                .doesNotContain(sessionId);
        // Stable, or a list-then-terminate round trip could not name the same session twice.
        assertThat(SessionListListener.sessionHandle(sessionId)).isEqualTo(handle);
        // Distinct per session, or terminating one would hit another.
        assertThat(SessionListListener.sessionHandle("a-different-session-id")).isNotEqualTo(handle);
        assertThat(SessionListListener.sessionHandle(null)).isNull();
    }

    @Test
    void sessionIsTerminatedByItsHandleNotItsId() {
        registerSession("sess1", userRef("alice"));
        when(securityContext.getUserRef()).thenReturn(userRef("alice"));

        // The raw id must not work as a handle - otherwise the safe contract would be bypassable by anyone who
        // learned an id some other way.
        assertThat(listener.evictSessionOnNode("sess1", THIS_NODE)).isFalse();
        assertThat(listener.evictSessionOnNode(SessionListListener.sessionHandle("sess1"), THIS_NODE)).isTrue();
    }

    // --- Session map lifecycle ------------------------------------------------------------------------

    @Test
    void sessionIsForgottenAfterItsIdChangesAndItIsThenDestroyed() {
        // The container changes the session id on authentication, and sessionDestroyed removes by the current
        // id. Without re-keying on the change, every login would leave an entry pointing at a session that is
        // later invalidated - and any walk of the map would then hit a dead session.
        final HttpSession session = registerSession("original-id", userRef("alice"));

        Mockito.when(session.getId()).thenReturn("new-id");
        listener.sessionIdChanged(sessionEvent(session), "original-id");
        listener.sessionDestroyed(sessionEvent(session));

        // Not found, so it returns false without even reaching the permission check - which is itself the
        // proof that the map no longer holds it.
        assertThat(listener.evictSessionOnNode(SessionListListener.sessionHandle("new-id"), THIS_NODE))
                .as("the session should have been forgotten, not left behind under its old id")
                .isFalse();
    }

    @Test
    void oneInvalidatedSessionDoesNotHideEveryOtherSession() {
        // Accessors on an invalidated session throw, and the caller turns a failure into an empty response for
        // the whole node - so without tolerating it, a single dead session makes it look as though nobody is
        // signed in at all.
        registerSession("live-session", userRef("alice"));
        final HttpSession dead = registerSession("dead-session", userRef("bob"));
        Mockito.when(dead.getCreationTime()).thenThrow(new IllegalStateException("invalidated"));
        stubSecureResultToRun();

        final SessionListResponse response = listener.listSessions(THIS_NODE);

        assertThat(response.getValues())
                .as("the live session must still be listed")
                .hasSize(1);
        assertThat(response.getValues().getFirst().getUserRef().getSubjectId()).isEqualTo("alice");
    }

    @Test
    void anInvalidatedSessionDoesNotBreakEvictingAnotherUsersSessions() {
        registerSession("live-session", userRef("alice"));
        final HttpSession dead = registerSession("dead-session", userRef("bob"));
        Mockito.when(dead.getId()).thenThrow(new IllegalStateException("invalidated"));
        when(securityContext.getUserRef()).thenReturn(userRef("alice"));

        assertThat(listener.evictUserSessionsOnNode("alice", null, THIS_NODE)).isEqualTo(1);
    }

    /**
     * Put a session into the listener's map the same way the container would, owned by the given user
     * (or by nobody when userRef is null).
     */
    private HttpSession registerSession(final String sessionId, final UserRef userRef) {
        final HttpSession session = Mockito.mock(HttpSession.class);
        lenient().when(session.getId()).thenReturn(sessionId);
        if (userRef != null) {
            final SessionUserIdentity identity = Mockito.mock(SessionUserIdentity.class);
            lenient().when(identity.getUserRef()).thenReturn(userRef);
            lenient().when(session.getAttribute("SESSION_USER_IDENTITY")).thenReturn(identity);
        }
        listener.sessionCreated(sessionEvent(session));
        return session;
    }

    private HttpSessionEvent sessionEvent(final HttpSession session) {
        final HttpSessionEvent event = Mockito.mock(HttpSessionEvent.class);
        lenient().when(event.getSession()).thenReturn(session);
        return event;
    }

    private void stubSecureResultToRun() {
        when(securityContext.secureResult(eq(AppPermission.MANAGE_USERS_PERMISSION), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
    }

    private UserRef userRef(final String subjectId) {
        return UserRef.builder().uuid(subjectId).subjectId(subjectId).build();
    }


// --------------------------------------------------------------------------------


    /**
     * The session attribute holds a {@link UserIdentity}, but only identities that also implement
     * {@link HasUserRef} expose an owner, which is what the authorisation check compares.
     */
    private interface SessionUserIdentity extends UserIdentity, HasUserRef {

    }
}
