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

package stroom.mcp.impl;

import stroom.ai.impl.AiDao;
import stroom.ai.impl.AiRetention;
import stroom.ai.shared.AiChat;
import stroom.security.api.SecurityContext;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestMcpWorkspaces {

    private static final UserRef USER = UserRef.builder().uuid("user1").build();

    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final AiRetention retention = Mockito.mock(AiRetention.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final McpWorkspaces workspaces = new McpWorkspaces(() -> aiDao, () -> retention, securityContext);
    private final McpSession session = new McpSession("s1", USER, new Implementation("client", "1"),
            ClientCapabilities.builder().build());

    @Test
    void aSessionsWorkspaceIsMadeWhenFirstNeededThenKept() {
        final AiChat chat = Mockito.mock(AiChat.class);
        Mockito.when(chat.getId()).thenReturn(42);
        Mockito.when(aiDao.findSessionChat("s1")).thenReturn(Optional.empty());
        Mockito.when(aiDao.createSessionChat(USER, "s1")).thenReturn(chat);

        assertThat(workspaces.chatIdFor(session)).isEqualTo(42);
        assertThat(workspaces.chatIdFor(session)).isEqualTo(42);

        Mockito.verify(aiDao, Mockito.times(1)).createSessionChat(USER, "s1");
        // Marked as used on each call, so it isn't taken for idle.
        Mockito.verify(aiDao, Mockito.times(2)).touchChat(42, McpWorkspaces.TOUCH_INTERVAL.toMillis());
    }

    @Test
    void aSessionOnAnotherNodeFindsItsWorkspace() {
        Mockito.when(aiDao.findSessionChat("s1")).thenReturn(Optional.of(7));

        assertThat(workspaces.chatIdFor(session)).isEqualTo(7);

        Mockito.verify(aiDao, Mockito.never()).createSessionChat(Mockito.any(), Mockito.any());
    }

    @Test
    void aSessionsWorkspaceIsDeletedWhenItEnds() {
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(securityContext).asProcessingUser(Mockito.any(Runnable.class));
        Mockito.when(aiDao.findSessionChat("s1")).thenReturn(Optional.of(7));
        workspaces.chatIdFor(session);

        workspaces.delete(session);
        workspaces.delete(session);

        Mockito.verify(retention, Mockito.times(1)).deleteChatAndFiles(7);
    }

    @Test
    void callsMadeAtOnceMakeOneWorkspace() throws Exception {
        // Seen in review: the chat was made inside AtomicReference.updateAndGet, which runs again when another
        // thread sets it first, so calls made at once each made a hidden chat (and finding it then found several).
        final AiChat chat = Mockito.mock(AiChat.class);
        Mockito.when(chat.getId()).thenReturn(42);
        final CountDownLatch bothLooking = new CountDownLatch(2);
        Mockito.when(aiDao.findSessionChat("s1")).thenAnswer(invocation -> {
            bothLooking.countDown();
            // Long enough for the other call to look too, if it can.
            bothLooking.await(500, TimeUnit.MILLISECONDS);
            return Optional.empty();
        });
        Mockito.when(aiDao.createSessionChat(USER, "s1")).thenReturn(chat);

        final CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> workspaces.chatIdFor(session));
        final CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> workspaces.chatIdFor(session));

        assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(42);
        assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(42);
        Mockito.verify(aiDao, Mockito.times(1)).createSessionChat(USER, "s1");
    }

    @Test
    void aWorkspaceIsDeletedOnlyOnceTheCallsUsingItHaveEnded() {
        // Seen in review: a session ended while a call ran had its workspace deleted under the call.
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(securityContext).asProcessingUser(Mockito.any(Runnable.class));
        Mockito.when(aiDao.findSessionChat("s1")).thenReturn(Optional.of(7));
        assertThat(workspaces.callStarted(session)).isTrue();
        assertThat(workspaces.callStarted(session)).isTrue();
        workspaces.chatIdFor(session);

        workspaces.delete(session);
        Mockito.verifyNoInteractions(retention);
        // No call can start, or make it a new workspace, once the session has ended.
        assertThat(workspaces.callStarted(session)).isFalse();
        assertThatThrownBy(() -> workspaces.chatIdFor(session)).hasMessageContaining("session has ended");
        workspaces.callEnded(session);
        Mockito.verifyNoInteractions(retention);

        workspaces.callEnded(session);

        Mockito.verify(retention, Mockito.times(1)).deleteChatAndFiles(7);
    }

    @Test
    void aSessionWithNoWorkspaceHasNothingToDelete() {
        workspaces.delete(session);

        Mockito.verifyNoInteractions(retention);
    }
}
