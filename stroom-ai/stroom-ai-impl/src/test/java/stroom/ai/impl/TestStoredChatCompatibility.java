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
import stroom.ai.impl.memory.AiMemoryService;
import stroom.ai.impl.memory.MemoryChange;
import stroom.ai.impl.memory.ScriptedRunMemory;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Old chats: a chat's rows as Stroom 7.14 stores them (every kind of message a chat holds, with the tool calls,
/// results, forms, approvals, memory changes, digests and compaction written by today's record classes), kept as a
/// fixture that is never regenerated from code. Checks, against reference files (see [GoldenFiles]), what the
/// model is given from them, including the current turn rebuilt when a run carries on, and that the code that
/// reads the records back still reads them as it did, so a rename or a change to a record cannot quietly break the
/// chats users already have.
class TestStoredChatCompatibility {

    private static final String FIXTURE = "/stroom/ai/impl/fixtures/stored-chats/chat-7.14.json";
    private static final String REFERENCE_DIR = "stored-chats/";
    private static final int CHAT_ID = 21;
    // The message types a chat holds only while a run works (they are removed when it is done), or no longer
    // stores.
    private static final Set<AiMessageType> NOT_IN_FIXTURE = EnumSet.of(
            AiMessageType.STREAMING_ANSWER,
            AiMessageType.STREAMING_THINKING,
            AiMessageType.DASHBOARD_DATA,
            AiMessageType.QUERY_DATA,
            AiMessageType.TABLE_DATA);

    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final AiWorkspaceDao workspaceDao = Mockito.mock(AiWorkspaceDao.class);
    private List<AiChatMessage> history;

    @BeforeEach
    void setUp() {
        history = readFixture();
        Mockito.when(aiService.getMessages(CHAT_ID)).thenReturn(history);
        Mockito.when(aiService.getChat(CHAT_ID)).thenReturn(new AiChat(CHAT_ID, 0L, 0L, "user-uuid", "Chat"));
        Mockito.when(aiDao.getAgentMode(CHAT_ID)).thenReturn(AiAgentMode.AGENT);
        // The table attached at the start has since reached its retention age.
        Mockito.when(workspaceDao.getFileInfo(50)).thenReturn(Optional.of(new AiWorkspaceDao.FileInfo(50, CHAT_ID,
                "/attachments/50.md", AiAttachmentStatus.UNAVAILABLE, null, null, "it reached its retention age",
                0L)));
    }

    @Test
    void theFixtureHoldsEveryKindOfMessageAChatKeeps() {
        final Set<AiMessageType> types = history.stream()
                .map(AiChatMessage::getMessageType)
                .collect(Collectors.toSet());

        assertThat(types).containsExactlyInAnyOrderElementsOf(EnumSet.complementOf(EnumSet.copyOf(NOT_IN_FIXTURE)));
    }

    @Test
    void whatTheModelIsGivenFromAnOldChat() {
        final AiMemoryService memoryService = ScriptedRunMemory.memoryService(aiService,
                Mockito.mock(AiChatRunManager.class), Mockito.mock(SecurityContext.class), aiDao);
        final ContextAssembler assembler = new ContextAssembler(() -> null, AiConfig::new, null, aiService, aiDao,
                workspaceDao, Mockito.mock(ContextUsageTracker.class), memoryService, null);
        final String question = history.reversed().stream()
                .filter(message -> message.getMessageType() == AiMessageType.USER_MESSAGE)
                .findFirst()
                .orElseThrow()
                .getMessage();
        final CompactionRecord compaction = ContextAssembler.currentCompaction(history);

        // As a run with tools is given it, carrying on with the question (e.g. after the user approved a call).
        final List<ChatMessage> messages = assembler.assemble(new AskStroomAiRequest(null, null, null, question),
                CHAT_ID, Map.of(), history, compaction, true);

        assertThat(compaction).isNotEqualTo(CompactionRecord.NONE);
        final String transcript = "# What the model is given from a 7.14 chat\n\n"
                                  + "The chat's latest compaction:\n    " + compaction + "\n\n"
                                  + "== The messages for the model (ContextAssembler.assemble, with tools) ==\n"
                                  + ChatTranscripts.messages(withoutSystemPrompt(messages)) + "\n"
                                  + "== The current turn, rebuilt to carry on (ContextAssembler.currentTurn) ==\n"
                                  + ChatTranscripts.messages(ContextAssembler.currentTurn(history)) + "\n"
                                  + "== The digest of each turn (TurnDigests.digests: stored, or built) ==\n"
                                  + digests();
        GoldenFiles.assertMatches(REFERENCE_DIR + "model-context.txt", transcript);
    }

    @Test
    void theStoredRecordsStillReadAsTheyDid() {
        final StringBuilder sb = new StringBuilder("# How the server reads back the records of a 7.14 chat\n\n");
        for (final AiChatMessage message : history) {
            final List<String> readings = readings(message);
            if (!readings.isEmpty()) {
                sb.append("#").append(message.getId()).append(' ').append(message.getMessageType()).append("\n");
                readings.forEach(reading -> sb.append("    ").append(reading.replace("\n", "\n    ")).append("\n"));
            }
        }
        sb.append("\nThe latest task list (McpAgentChat.latestTaskList): ")
                .append(McpAgentChat.latestTaskList(history)).append("\n");
        sb.append("The pending tool approval (ToolApprovalService.pending): ")
                .append(pendingApproval().map(AiChatMessage::getId).orElse(null)).append("\n");

        // Every record still reads, with what identifies it.
        assertThat(history).filteredOn(message -> message.getMessageType() == AiMessageType.TOOL_CALL)
                .allSatisfy(message -> assertThat(read(message, ToolCallRecord.class).callId()).isNotBlank());
        assertThat(history).filteredOn(message -> message.getMessageType() == AiMessageType.TOOL_RESULT)
                .allSatisfy(message -> assertThat(read(message, ToolResultRecord.class).callId()).isNotBlank());
        assertThat(history).filteredOn(message -> message.getMessageType() == AiMessageType.APPROVAL_REQUEST)
                .allSatisfy(message -> assertThat(ToolApprovalService.read(message).isPresent()
                                                  || AiMemoryService.readChange(message.getMessage())
                                                          .filter(change -> MemoryChange.KIND.equals(change.kind()))
                                                          .isPresent())
                        .isTrue());
        assertThat(history).filteredOn(message -> message.getMessageType() == AiMessageType.ELICITATION)
                .allSatisfy(message -> assertThat(read(message, ElicitationRecord.class).kind())
                        .isEqualTo(ElicitationRecord.KIND));
        GoldenFiles.assertMatches(REFERENCE_DIR + "records-read-back.txt", sb.toString());
    }

    // What the code that reads a message back makes of it, as text: the record it reads (its fields, by name), and
    // what the chat's export writes of it.
    private List<String> readings(final AiChatMessage message) {
        final List<String> readings = new ArrayList<>();
        switch (message.getMessageType()) {
            case TOOL_CALL -> readings.add(String.valueOf(read(message, ToolCallRecord.class)));
            case TOOL_RESULT -> readings.add(String.valueOf(read(message, ToolResultRecord.class)));
            case THINKING -> readings.add("Reasoning: " + AiActivityExport.thinkingText(message.getMessage()));
            case ELICITATION -> readings.add(String.valueOf(read(message, ElicitationRecord.class)));
            case APPROVAL_REQUEST -> {
                ToolApprovalService.read(message).ifPresent(request -> readings.add(String.valueOf(request)));
                AiMemoryService.readChange(message.getMessage())
                        .filter(change -> MemoryChange.KIND.equals(change.kind()))
                        .ifPresent(change -> readings.add(String.valueOf(change)));
            }
            case MEMORY_UPDATE -> readings.add(String.valueOf(AiMemoryService.readChange(message.getMessage())
                    .orElse(null)));
            case COMPACTION_SUMMARY -> readings.add(String.valueOf(ContextAssembler.readCompaction(message)));
            default -> {
                // Plain text, read as it is.
            }
        }
        if (AiActivityExport.isActivity(message.getMessageType())) {
            readings.add("Exported as:\n" + AiActivityExport.body(message));
        }
        return readings;
    }

    // The system prompt has its own reference file, and nothing in it comes from the chat's rows.
    private static List<ChatMessage> withoutSystemPrompt(final List<ChatMessage> messages) {
        final List<ChatMessage> copy = new ArrayList<>(messages);
        assertThat(copy.getFirst()).isInstanceOf(SystemMessage.class);
        copy.set(0, SystemMessage.from("(The system prompt, which has its own reference file.)"));
        return copy;
    }

    private Optional<AiChatMessage> pendingApproval() {
        return new ToolApprovalService(aiService, aiDao, Mockito.mock(AiToolApprovalDao.class),
                Mockito.mock(AiChatRunManager.class), Mockito.mock(SecurityContext.class)).pending(CHAT_ID);
    }

    private String digests() {
        final StringBuilder sb = new StringBuilder();
        TurnDigests.digests(history, AiCompactionConfig.DEFAULT_DIGEST_MAX_RESULT_ROWS).forEach((turnId, digest) ->
                sb.append("Turn ").append(turnId).append(":\n    ").append(digest.replace("\n", "\n    "))
                        .append("\n"));
        return sb.toString();
    }

    private static <T> T read(final AiChatMessage message, final Class<T> type) {
        return JsonUtil.readValue(message.getMessage(), type);
    }

    // The chat's rows, read as the DAO reads them (see AiDaoImpl).
    private static List<AiChatMessage> readFixture() {
        try (final InputStream inputStream = TestStoredChatCompatibility.class.getResourceAsStream(FIXTURE)) {
            assertThat(inputStream).as("The fixture " + FIXTURE).isNotNull();
            final List<Map<String, Object>> rows = new ObjectMapper().readValue(inputStream,
                    new TypeReference<>() {
                    });
            return rows.stream()
                    .map(row -> new AiChatMessage(
                            (Integer) row.get("id"),
                            (Integer) row.get("fk_ai_chat_id"),
                            ((Number) row.get("create_time_ms")).longValue(),
                            AiMessageType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                                    ((Integer) row.get("message_type")).byteValue()),
                            (Integer) row.get("fk_attachment_id"),
                            (String) row.get("message"),
                            (Integer) row.get("turn_id"),
                            (Integer) row.get("token_count")))
                    .toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
