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
import stroom.ai.impl.ToolApprovalRequest.Decision;
import stroom.ai.impl.ToolApprovalRequest.PendingCall;
import stroom.ai.impl.ToolApprovalRequest.Status;
import stroom.ai.impl.dao.AiDaoModule;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.AiDbModule;
import stroom.ai.shared.AiApprovalScope;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiToolApproval;
import stroom.ai.shared.AiToolApprovalDecision;
import stroom.ai.shared.AnswerAiApprovalRequest;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.security.api.SecurityContext;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TestToolApprovalService {

    private static final List<String> TABLES = List.of(
            "ai_chat_file_chunk",
            "ai_chat_run",
            "ai_memory",
            "ai_tool_approval",
            "ai_chat_message",
            "ai_chat_attachment",
            "ai_chat");

    @Inject
    private AiDao aiDao;
    @Inject
    private AiToolApprovalDao approvalDao;
    @Inject
    private AiDbConnProvider aiDbConnProvider;

    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private ToolApprovalService service;
    private AiChat chat;

    @BeforeEach
    void setUp() throws SQLException {
        final Injector injector = Guice.createInjector(
                new DbTestModule(),
                new AiDbModule(),
                new AiDaoModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
                        bind(CollectionService.class).toInstance(Mockito.mock(CollectionService.class));
                        bind(DocFinder.class).toInstance(Mockito.mock(DocFinder.class));
                    }
                });
        injector.injectMembers(this);
        try (final Connection connection = aiDbConnProvider.getConnection()) {
            DbTestUtil.clearTables(connection, TABLES);
        }
        chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        Mockito.when(aiService.getChat(chat.getId())).thenReturn(chat);
        Mockito.when(aiService.getMessages(Mockito.anyInt()))
                .thenAnswer(invocation -> aiDao.getMessages(invocation.getArgument(0)));
        Mockito.when(securityContext.getUserIdentityForAudit()).thenReturn("user1");
        service = new ToolApprovalService(aiService, aiDao, approvalDao, runManager, securityContext);
    }

    @Test
    void testAnswerAll() {
        final AiChatMessage message = request(0, "a", "b");

        assertThat(service.pending(chat.getId())).contains(message);
        assertThat(service.answer(chat.getId(), message, answer(message, true, AiApprovalScope.ONCE, null, null)))
                .isEqualTo(Status.ANSWERED);

        final ToolApprovalRequest stored = stored(message);
        assertThat(stored.calls()).extracting(PendingCall::decision)
                .containsExactly(Decision.ALLOWED, Decision.ALLOWED);
        assertThat(service.pending(chat.getId())).isEmpty();
        // Only once, e.g. from a second tab.
        assertThatThrownBy(() -> service.answer(chat.getId(), message,
                answer(message, false, AiApprovalScope.ONCE, null, null)))
                .hasMessageContaining("already been answered");
        // Allow once is not remembered.
        assertThat(approvalDao.list(chat.getId())).isEmpty();
    }

    @Test
    void testAnswerSomeCallsThenTheRest() {
        final AiChatMessage message = request(0, "a", "b");

        assertThat(service.answer(chat.getId(), message,
                answer(message, false, AiApprovalScope.ONCE, "not that one", List.of("a"))))
                .isEqualTo(Status.PENDING);
        final AiChatMessage partly = aiDao.getMessages(chat.getId()).getLast();
        assertThat(service.answer(chat.getId(), partly, answer(partly, true, AiApprovalScope.ONCE, null, null)))
                .isEqualTo(Status.ANSWERED);

        assertThat(stored(message).calls()).extracting(PendingCall::decision, PendingCall::note)
                .containsExactly(tuple(Decision.DENIED, "not that one"),
                        tuple(Decision.ALLOWED, null));
    }

    @Test
    void testAllowForTheChatAndForTheServersReadOnlyTools() {
        final AiChatMessage forChat = request(0, "a");
        service.answer(chat.getId(), forChat, answer(forChat, true, AiApprovalScope.CHAT, null, null));
        final AiChatMessage forServer = request(0, "b");
        service.answer(chat.getId(), forServer, answer(forServer, true, AiApprovalScope.SERVER_READ_ONLY, null,
                null));

        assertThat(approvalDao.list(chat.getId()))
                .extracting(AiToolApprovalDao.Approval::serverKey, AiToolApprovalDao.Approval::toolName,
                        AiToolApprovalDao.Approval::toolHash, AiToolApprovalDao.Approval::decision)
                .containsExactlyInAnyOrder(
                        tuple("server", "*", "server-hash",
                                AiToolApprovalDecision.ALLOW),
                        tuple("server", "tool", "tool-hash",
                                AiToolApprovalDecision.ALLOW));
        // They go with the chat.
        aiDao.deleteChat(chat.getId());
        assertThat(approvalDao.list(chat.getId())).isEmpty();
    }

    @Test
    void testListRemoveAndReset() {
        final AiChat other = aiDao.createChat(UserRef.builder().uuid("user1").build());
        approvalDao.save("user1", chat.getId(), "server", "a", "hash-a", AiToolApprovalDecision.ALLOW, "user1");
        approvalDao.save("user1", chat.getId(), "server", "b", "hash-b", AiToolApprovalDecision.DENY, "user1");
        approvalDao.save("user1", other.getId(), "server", "a", "hash-a", AiToolApprovalDecision.ALLOW, "user1");

        final List<AiToolApproval> approvals = service.list(chat.getId());
        assertThat(approvals).extracting(AiToolApproval::getToolName, AiToolApproval::getDecision)
                .containsExactly(tuple("a", AiToolApprovalDecision.ALLOW), tuple("b", AiToolApprovalDecision.DENY));

        // Another chat's decision can't be removed through this one.
        final int otherId = service.list(other.getId()).getFirst().getId();
        assertThat(service.remove(chat.getId(), otherId)).isFalse();
        assertThat(service.list(other.getId())).hasSize(1);

        assertThat(service.remove(chat.getId(), approvals.getFirst().getId())).isTrue();
        assertThat(service.list(chat.getId())).extracting(AiToolApproval::getToolName).containsExactly("b");

        assertThat(service.reset(chat.getId())).isEqualTo(1);
        assertThat(service.list(chat.getId())).isEmpty();
        assertThat(service.list(other.getId())).hasSize(1);
    }

    @Test
    void testDenyAll() {
        final AiChatMessage message = request(0, "a", "b");

        assertThat(service.denyAll(message, "The user sent a message instead: stop")).isTrue();
        assertThat(stored(message).status()).isEqualTo(Status.ANSWERED);
        assertThat(stored(message).calls()).allSatisfy(call -> {
            assertThat(call.decision()).isEqualTo(Decision.DENIED);
            assertThat(call.note()).contains("stop");
        });
        assertThat(service.denyAll(aiDao.getMessages(chat.getId()).getLast(), "again")).isFalse();
    }

    @Test
    void testExpiry() throws SQLException {
        final AiChatMessage message = request(ToolApprovalService.EXPIRY.toMillis() + 1_000, "a");

        assertThat(service.answer(chat.getId(), message, answer(message, true, AiApprovalScope.ONCE, null, null)))
                .isEqualTo(Status.EXPIRED);

        assertThat(stored(message).calls()).extracting(PendingCall::decision).containsExactly(Decision.EXPIRED);
        Mockito.verify(runManager).stop(chat.getId(), AiChatRunStopReason.APPROVAL_EXPIRED);
    }

    private AiChatMessage request(final long ageMs, final String... callIds) {
        final List<PendingCall> calls = Arrays.stream(callIds)
                .map(id -> new PendingCall(id, "server__tool", "server", "tool", "{}", List.of("can change things"),
                        "tool-hash", "server-hash", null, null))
                .toList();
        final AiChatMessage message = aiDao.storeMessage(chat.getId(), AiMessageType.APPROVAL_REQUEST,
                JsonUtil.writeValueAsString(new ToolApprovalRequest(ToolApprovalRequest.KIND, Status.PENDING,
                        "reason", calls, true, true), false));
        if (ageMs > 0) {
            try (final Connection connection = aiDbConnProvider.getConnection();
                    final PreparedStatement statement = connection.prepareStatement(
                            "UPDATE ai_chat_message SET create_time_ms = ? WHERE id = ?")) {
                statement.setLong(1, System.currentTimeMillis() - ageMs);
                statement.setInt(2, message.getId());
                statement.executeUpdate();
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
            return aiDao.getMessages(chat.getId()).stream()
                    .filter(m -> m.getId() == message.getId())
                    .findFirst()
                    .orElseThrow();
        }
        return message;
    }

    private ToolApprovalRequest stored(final AiChatMessage message) {
        return aiDao.getMessages(chat.getId()).stream()
                .filter(m -> m.getId() == message.getId())
                .findFirst()
                .flatMap(ToolApprovalService::read)
                .orElseThrow();
    }

    private AnswerAiApprovalRequest answer(final AiChatMessage message,
                                           final boolean approved,
                                           final AiApprovalScope scope,
                                           final String reason,
                                           final List<String> callIds) {
        return new AnswerAiApprovalRequest(chat, message.getId(), approved, scope, reason, callIds);
    }
}
