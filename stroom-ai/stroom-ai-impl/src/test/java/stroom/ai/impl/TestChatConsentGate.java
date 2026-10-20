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

package stroom.ai.impl;

import stroom.ai.api.AiService;
import stroom.ai.impl.ToolApprovalRequest.PendingCall;
import stroom.ai.impl.memory.MemoryChange;
import stroom.ai.impl.memory.MemoryChange.ApprovalStatus;
import stroom.ai.impl.memory.MemoryChange.Operation;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.ToolSessionException;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestChatConsentGate {

    private static final int CHAT_ID = 5;
    private static final ToolSession SESSION = ToolSession.chat(CHAT_ID);

    private final ToolConsent toolConsent = Mockito.mock(ToolConsent.class);
    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final McpElicitations elicitations = Mockito.mock(McpElicitations.class);
    private final ChatConsentGate gate = new ChatConsentGate(() -> toolConsent, () -> aiService, () -> runManager,
            () -> elicitations);

    @Test
    void theUserIsAwayWhileTheChatsRunIsUnattended() {
        assertThat(gate.isUserAway(SESSION)).isFalse();

        Mockito.when(runManager.isUnattended(CHAT_ID)).thenReturn(true);

        assertThat(gate.isUserAway(SESSION)).isTrue();
    }

    @Test
    void aCallIsDecidedByTheChatsConsentRules() {
        final BuiltInTools builtInTools = Mockito.mock(BuiltInTools.class);
        Mockito.when(toolConsent.decide(CHAT_ID, "server__tool", "{}", builtInTools, McpToolset.EMPTY, true))
                .thenReturn(ToolConsent.Verdict.RUN);

        assertThat(gate.checkCall(SESSION, "server__tool", "{}", builtInTools, McpToolset.EMPTY, true))
                .isSameAs(ToolConsent.Verdict.RUN);
        Mockito.verify(toolConsent).decide(CHAT_ID, "server__tool", "{}", builtInTools, McpToolset.EMPTY, true);
    }

    @Test
    void callsNeedingApprovalArePutOnACardInTheChat() {
        final ToolApprovalRequest request = new ToolApprovalRequest(ToolApprovalRequest.KIND,
                ToolApprovalRequest.Status.PENDING, "Why", List.of(new PendingCall("1", "server__tool", "server",
                "tool", "{}", List.of("can modify"), "hash", "serverHash", null, null)), true, false);
        Mockito.when(aiService.storeMessage(Mockito.eq(CHAT_ID), Mockito.eq(AiMessageType.APPROVAL_REQUEST),
                Mockito.anyString())).thenReturn(message(42));

        final ApprovalOutcome outcome = gate.requestCallApproval(SESSION, request);

        assertThat(outcome.isPending()).isTrue();
        assertThat(outcome.id()).isEqualTo("42");
        Mockito.verify(aiService).storeMessage(CHAT_ID, AiMessageType.APPROVAL_REQUEST,
                JsonUtil.writeValueAsString(request, false));
    }

    @Test
    void aCardNotGivenBackHasNoId() {
        final ApprovalOutcome outcome = gate.requestCallApproval(SESSION, new ToolApprovalRequest(
                ToolApprovalRequest.KIND, ToolApprovalRequest.Status.PENDING, null, List.of(), false, false));

        assertThat(outcome.isPending()).isTrue();
        assertThat(outcome.id()).isNull();
    }

    @Test
    void aLastingChangeNeedsApprovalOnceTheRunHasReadDataOrWhileTheUserIsAway() {
        assertThat(gate.lastingChangeApprovalReason(SESSION)).isEmpty();

        Mockito.when(runManager.isTainted(CHAT_ID)).thenReturn(true);
        assertThat(gate.lastingChangeApprovalReason(SESSION)).hasValue(ChatConsentGate.READ_DATA_REASON);

        Mockito.when(runManager.isTainted(CHAT_ID)).thenReturn(false);
        Mockito.when(runManager.isUnattended(CHAT_ID)).thenReturn(true);
        assertThat(gate.lastingChangeApprovalReason(SESSION)).hasValue(ChatConsentGate.AWAY_REASON);

        // Away is said over having read data.
        Mockito.when(runManager.isTainted(CHAT_ID)).thenReturn(true);
        assertThat(gate.lastingChangeApprovalReason(SESSION)).hasValue(ChatConsentGate.AWAY_REASON);
    }

    @Test
    void aChangeNeedingApprovalIsPutOnACardInTheChat() {
        final MemoryChange change = new MemoryChange(MemoryChange.KIND, Operation.CREATE, "/memories/a.md", null,
                null, "a", ApprovalStatus.PENDING, ChatConsentGate.AWAY_REASON);
        Mockito.when(aiService.storeMessage(Mockito.eq(CHAT_ID), Mockito.eq(AiMessageType.APPROVAL_REQUEST),
                Mockito.anyString())).thenReturn(message(7));

        final ApprovalOutcome outcome = gate.requestChangeApproval(SESSION, change);

        assertThat(outcome).isEqualTo(ApprovalOutcome.pending("7"));
        Mockito.verify(aiService).storeMessage(CHAT_ID, AiMessageType.APPROVAL_REQUEST,
                JsonUtil.writeValueAsString(change, false));
    }

    @Test
    void questionsAreAskedAsAFormInTheChatWhileTheUserIsWatching() {
        final ElicitResult answer = new ElicitResult(Action.ACCEPT, Map.of("q", "yes"));
        Mockito.when(elicitations.askUser(CHAT_ID, "ask_user", "Intro", Map.of())).thenReturn(answer);
        final UserQuestions questions = gate.userQuestions(SESSION);

        assertThat(questions.canAsk()).isTrue();
        assertThat(questions.ask("ask_user", "Intro", Map.of())).isSameAs(answer);

        Mockito.when(runManager.isUnattended(CHAT_ID)).thenReturn(true);
        assertThat(questions.canAsk()).isFalse();
    }

    @Test
    void aSessionThatIsntAChatIsRefused() {
        final ToolSession mcp = new ToolSession(ToolSession.Kind.MCP, "mcp-1",
                new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A client"));

        assertThatThrownBy(() -> gate.isUserAway(mcp)).isInstanceOf(ToolSessionException.class);
        assertThatThrownBy(() -> gate.lastingChangeApprovalReason(mcp)).isInstanceOf(ToolSessionException.class);
        assertThatThrownBy(() -> gate.userQuestions(mcp)).isInstanceOf(ToolSessionException.class);
        Mockito.verifyNoInteractions(runManager, aiService, toolConsent, elicitations);
    }

    private static AiChatMessage message(final int id) {
        return new AiChatMessage(id, CHAT_ID, 0L, AiMessageType.APPROVAL_REQUEST, null, "{}", 1, null);
    }
}
