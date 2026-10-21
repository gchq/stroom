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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.McpElicitations;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.time.StroomDuration;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestToolCallContext {

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final McpElicitations elicitations = Mockito.mock(McpElicitations.class);
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final ChatToolCallContexts contexts = new ChatToolCallContexts(workspace, () -> elicitations,
            () -> runManager);

    @Test
    void aChatsContextHasTheChatsSessionAndWorkspace() {
        final AiWorkspaceConfig limits = new AiWorkspaceConfig();
        Mockito.when(workspace.limits()).thenReturn(limits);
        final WorkspacePath path = WorkspacePath.parse("/outputs/a.txt");

        final ToolCallContext context = contexts.forChat(5);
        context.workspace().orElseThrow().write(path, "x", null);

        assertThat(context.session()).isEqualTo(ToolSession.chat(5));
        assertThat(context.caller()).isEqualTo(ToolCaller.STROOM_AI_CHAT);
        assertThat(context.requireWorkspace("ls")).isSameAs(context.workspace().orElseThrow());
        // The workspace reaches only the chat's own files.
        Mockito.verify(workspace).write(5, path, "x", null);
        assertThat(context.timeLimit()).hasValue(limits.getToolTimeLimit().getDuration());
        assertThat(context.progress()).isSameAs(ToolProgress.NONE);
        assertThat(context.runContext()).isEmpty();
    }

    @Test
    void aChatsContextIsPartOfTheRunThisThreadIsWorkingOn() {
        final ToolRunContext run = new ToolRunContext(null, null, () -> false);

        final ToolCallContext context = run.run(() -> contexts.forChat(5));

        assertThat(context.runContext()).containsSame(run);
    }

    @Test
    void aChatsQuestionsAreAskedAsAFormInTheChatWhileTheUserIsWatching() {
        final ElicitResult answer = new ElicitResult(Action.ACCEPT, Map.of("q", "yes"));
        Mockito.when(elicitations.askUser(5, "ask_user", "Intro", Map.of())).thenReturn(answer);
        final UserQuestions questions = contexts.forChat(5).userQuestions();

        assertThat(questions.canAsk()).isTrue();
        assertThat(questions.ask("ask_user", "Intro", Map.of())).isSameAs(answer);

        Mockito.when(runManager.isUnattended(5)).thenReturn(true);
        assertThat(questions.canAsk()).isFalse();
    }

    @Test
    void aChatsMemoryIdIsItsId() {
        assertThat(contexts.forMemoryId(5).session()).isEqualTo(ToolSession.chat(5));
        assertThat(contexts.forMemoryId(null)).isNull();
        assertThat(contexts.forMemoryId("5")).isNull();
    }

    @Test
    void aContextWithoutAWorkspaceSaysSo() {
        final ToolCallContext context = ToolCalls.outsideChat();

        assertThat(context.workspace()).isEmpty();
        assertThatThrownBy(() -> context.requireWorkspace("ls"))
                .isInstanceOf(ToolSessionException.class)
                .hasMessage("ls can only be used in a chat.");
        assertThat(context.userQuestions().canAsk()).isFalse();
        assertThat(context.userQuestions().ask("ask_user", "Intro", Map.of()).action()).isEqualTo(Action.DECLINE);
        assertThat(context.timeLimit()).isEmpty();
        assertThat(context.runContext()).isEmpty();
    }

    @Test
    void aChatsLimitsAreTheWorkspaceLimitsLookedUpEachTimeTheyAreRead() {
        final AiWorkspaceConfig first = new AiWorkspaceConfig();
        final AiWorkspaceConfig second = limits(Duration.ofSeconds(5), 7);
        Mockito.when(workspace.limits()).thenReturn(first, second);

        final ToolCallContext context = contexts.forChat(5);

        assertThat(context.limits().workspace()).isSameAs(first);
        assertThat(context.limits().workspace()).isSameAs(second);
        assertThat(context.timeLimit()).hasValue(Duration.ofSeconds(5));
    }

    @Test
    void aSessionWithoutLimitsOfItsOwnHasTheDefaultsAndNoTimeLimit() {
        final ToolCallContext context = ToolCalls.outsideChat();

        assertThat(context.limits()).isSameAs(SessionLimits.DEFAULTS);
        assertThat(context.timeLimit()).isEmpty();
    }

    @Test
    void aTimeLimitGivenToTheContextIsUsedOverTheSessions() {
        final ToolCallContext context = ToolCallContext.builder(ToolSession.chat(5))
                .limits(SessionLimits.of(limits(Duration.ofSeconds(5), 7)))
                .timeLimit(Duration.ofSeconds(9))
                .build();

        assertThat(context.timeLimit()).hasValue(Duration.ofSeconds(9));
        assertThat(context.limits().workspace().getMaxLinesPerRead()).isEqualTo(7);
    }

    @Test
    void aWorkspaceToolIsBoundByItsSessionsLimitsNotItsOwnWorkspaces() {
        final WorkspacePath path = WorkspacePath.parse("/outputs/a.txt");
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        Mockito.when(workspace.lineCount(5, path)).thenReturn(10L);
        Mockito.when(workspace.readLines(Mockito.eq(5), Mockito.eq(path), Mockito.anyLong(), Mockito.anyInt()))
                .thenAnswer(invocation -> List.of("a", "b").subList(0, invocation.<Integer>getArgument(3)));
        // A session (e.g. an MCP client's) with limits of its own: one line per read, two seconds per call.
        final ToolCallContext context = ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A client")))
                .workspace(new SessionWorkspace(workspace, 5))
                .limits(SessionLimits.of(limits(Duration.ofSeconds(2), 1)))
                .build();

        final String result = new ReadFileTool(workspace).execute(ToolCalls.call(ReadFileTool.NAME,
                "{\"path\":\"/outputs/a.txt\",\"count\":2}"), context).text();

        assertThat(result).startsWith("/outputs/a.txt (lines 1-1 of 10):");
        Mockito.verify(workspace).withTimeLimit(Mockito.eq(Duration.ofSeconds(2)), Mockito.any());
        Mockito.verify(workspace, Mockito.never()).limits();
    }

    @Test
    void failuresAreCountedByTheRunTheCallIsPartOf() {
        final ToolRunContext run = new ToolRunContext(null, null, () -> false);
        final ToolCallContext inRun = run.run(() -> contexts.forChat(5));

        assertThat(inRun.recordFailure("table:/a.csv")).isEqualTo(1);
        assertThat(inRun.recordFailure("table:/a.csv")).isEqualTo(2);
        assertThat(inRun.recordFailure("table:/b.csv")).isEqualTo(1);
        // Outside a run, nothing is counted.
        assertThat(contexts.forChat(5).recordFailure("table:/a.csv")).isZero();
    }

    @Test
    void aWorkspaceToolRefusesASessionWithoutAWorkspace() {
        final LsTool tool = new LsTool(workspace);

        assertThat(tool.execute(ToolCalls.call(LsTool.NAME, "{}"), ToolCalls.outsideChat()).text())
                .isEqualTo("Error: ls can only be used in a chat.");
        Mockito.verifyNoInteractions(workspace);
    }

    // Limits with the given tool time limit and lines per read, and the defaults otherwise.
    private static AiWorkspaceConfig limits(final Duration toolTimeLimit, final int maxLinesPerRead) {
        return new AiWorkspaceConfig(null, null, null, maxLinesPerRead, null, StroomDuration.of(toolTimeLimit),
                null, null, null, null, null, null, null);
    }
}
