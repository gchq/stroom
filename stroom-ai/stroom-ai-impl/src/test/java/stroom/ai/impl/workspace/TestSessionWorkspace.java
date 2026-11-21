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

package stroom.ai.impl.workspace;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestSessionWorkspace {

    private static final Instant NOW = Instant.parse("2026-11-21T00:49:49.123Z");
    // Each test uses its own chats, as reservations are held for the node.
    private static final AtomicInteger CHAT_IDS = new AtomicInteger(1_000_000);

    @Test
    void aNewFileIsNamedByThePrefixAndTheTime() {
        final SessionWorkspace workspace = new SessionWorkspace(workspaceWith(Set.of()), CHAT_IDS.incrementAndGet());

        assertThat(workspace.newFile("/outputs/queries/query", ".md", NOW).path())
                .isEqualTo("/outputs/queries/query-20261121-004949.md");
    }

    @Test
    void twoCallsInTheSameSecondGetDifferentPaths() {
        // Seen in eval case 72: two parallel run_stroomql calls both wrote query-20261121-004949.md.
        final ChatWorkspace chatWorkspace = workspaceWith(Set.of());
        final int chatId = CHAT_IDS.incrementAndGet();
        // Each tool call has its own SessionWorkspace.
        final WorkspacePath first = new SessionWorkspace(chatWorkspace, chatId).newFile("/outputs/queries/query",
                ".md", NOW);
        final WorkspacePath second = new SessionWorkspace(chatWorkspace, chatId).newFile("/outputs/queries/query",
                ".md", NOW);

        assertThat(first.path()).isEqualTo("/outputs/queries/query-20261121-004949.md");
        assertThat(second.path()).isEqualTo("/outputs/queries/query-20261121-004949-2.md");
    }

    @Test
    void aPathAlreadyInTheWorkspaceIsNotReused() {
        final ChatWorkspace chatWorkspace = workspaceWith(Set.of("/outputs/esql/esql-20261121-004949.md",
                "/outputs/esql/esql-20261121-004949-2.md"));
        final SessionWorkspace workspace = new SessionWorkspace(chatWorkspace, CHAT_IDS.incrementAndGet());

        assertThat(workspace.newFile("/outputs/esql/esql", ".md", NOW).path())
                .isEqualTo("/outputs/esql/esql-20261121-004949-3.md");
    }

    @Test
    void aWrittenFileIsSeenInTheWorkspaceSoItsPathIsStillNotReused() {
        final Set<String> stored = ConcurrentHashMap.newKeySet();
        final ChatWorkspace chatWorkspace = workspaceWith(stored);
        Mockito.when(chatWorkspace.write(ArgumentMatchers.anyInt(), ArgumentMatchers.any(WorkspacePath.class),
                ArgumentMatchers.anyString(), ArgumentMatchers.any())).thenAnswer(invocation -> {
                    stored.add(invocation.<WorkspacePath>getArgument(1).path());
                    return 1;
                });
        final int chatId = CHAT_IDS.incrementAndGet();
        final SessionWorkspace one = new SessionWorkspace(chatWorkspace, chatId);
        final WorkspacePath first = one.newFile("/outputs/sequences/sequence", ".md", NOW);
        one.write(first, "| a |", null);

        final WorkspacePath second = new SessionWorkspace(chatWorkspace, chatId).newFile(
                "/outputs/sequences/sequence", ".md", NOW);

        assertThat(second).isNotEqualTo(first);
        assertThat(second.path()).isEqualTo("/outputs/sequences/sequence-20261121-004949-2.md");
    }

    @Test
    void anotherChatMayUseTheSameName() {
        final ChatWorkspace chatWorkspace = workspaceWith(Set.of());

        final WorkspacePath one = new SessionWorkspace(chatWorkspace, CHAT_IDS.incrementAndGet()).newFile(
                "/outputs/queries/query", ".md", NOW);
        final WorkspacePath other = new SessionWorkspace(chatWorkspace, CHAT_IDS.incrementAndGet()).newFile(
                "/outputs/queries/query", ".md", NOW);

        assertThat(other).isEqualTo(one);
    }

    @Test
    void parallelCallsInTheSameSecondAllGetDifferentPaths() throws Exception {
        final ChatWorkspace chatWorkspace = workspaceWith(Set.of());
        final int chatId = CHAT_IDS.incrementAndGet();
        final int calls = 16;
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(calls);
        try {
            final List<Future<WorkspacePath>> futures = new ArrayList<>();
            for (int i = 0; i < calls; i++) {
                final Callable<WorkspacePath> call = () -> {
                    start.await();
                    return new SessionWorkspace(chatWorkspace, chatId).newFile("/outputs/queries/query", ".md", NOW);
                };
                futures.add(executor.submit(call));
            }
            start.countDown();
            final Set<String> paths = new HashSet<>();
            for (final Future<WorkspacePath> future : futures) {
                paths.add(future.get().path());
            }

            assertThat(paths).hasSize(calls)
                    .contains("/outputs/queries/query-20261121-004949.md",
                            "/outputs/queries/query-20261121-004949-" + calls + ".md");
        } finally {
            executor.shutdownNow();
        }
    }

    // A chat workspace holding files at these paths.
    private static ChatWorkspace workspaceWith(final Set<String> paths) {
        final ChatWorkspace chatWorkspace = Mockito.mock(ChatWorkspace.class);
        Mockito.when(chatWorkspace.find(ArgumentMatchers.anyInt(), ArgumentMatchers.any(WorkspacePath.class)))
                .thenAnswer(invocation -> paths.contains(invocation.<WorkspacePath>getArgument(1).path())
                        ? Optional.of(1)
                        : Optional.empty());
        return chatWorkspace;
    }
}
