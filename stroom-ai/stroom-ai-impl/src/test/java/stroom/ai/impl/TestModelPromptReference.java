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
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.TableProfiler;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMemory;
import stroom.ai.shared.AiMemoryScope;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The whole prompt [ContextAssembler] builds for a few fixed chats, in reference files (see [GoldenFiles]): the
/// system prompt with its tool, tab and mode guidance and memory, earlier turns as digests or a summary, attached
/// tables, documents and tabs, and the current turn's tool calls so far. Times, users and ids are fixed values, so
/// the files only change when what the model is given does.
class TestModelPromptReference {

    private static final int CHAT_ID = 7;
    private static final String USER_UUID = "user-uuid-1";
    private static final long TIME_MS = 1_767_225_600_000L;

    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final AiMemoryDao memoryDao = Mockito.mock(AiMemoryDao.class);
    private final AiWorkspaceDao workspaceDao = Mockito.mock(AiWorkspaceDao.class);
    private final WorkspaceStore workspaceStore = Mockito.mock(WorkspaceStore.class);
    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final List<AiMemory> userMemory = new ArrayList<>();
    private final List<AiMemory> chatMemory = new ArrayList<>();
    @TempDir
    private Path tempDir;
    private ContextAssembler assembler;

    @BeforeEach
    void setUp() {
        final AiChat chat = Mockito.mock(AiChat.class);
        Mockito.when(chat.getId()).thenReturn(CHAT_ID);
        Mockito.when(chat.getUserUuid()).thenReturn(USER_UUID);
        Mockito.when(aiService.getChat(CHAT_ID)).thenReturn(chat);
        Mockito.when(memoryDao.list(USER_UUID, AiMemoryScope.USER, "")).thenReturn(userMemory);
        Mockito.when(memoryDao.list(USER_UUID, AiMemoryScope.CHAT, String.valueOf(CHAT_ID))).thenReturn(chatMemory);
        Mockito.when(workspaceDao.getFileInfo(Mockito.anyInt())).thenReturn(Optional.empty());
        Mockito.when(workspaceDao.findFile(Mockito.anyInt(), Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(workspace.find(Mockito.anyInt(), Mockito.any(WorkspacePath.class))).thenReturn(Optional.empty());
        Mockito.when(documentAccess.canView(Mockito.any())).thenReturn(true);
        final AiMemoryService memoryService = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(AiMemoryDao.class).toInstance(memoryDao);
                bind(AiService.class).toInstance(aiService);
                final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
                bind(AiChatRunManager.class).toInstance(runManager);
                bind(ToolConsentGate.class).toInstance(ConsentGates.chat(null, aiService, runManager));
                bind(SecurityContext.class).toInstance(Mockito.mock(SecurityContext.class));
                bind(AiDao.class).toInstance(aiDao);
            }
        }).getInstance(AiMemoryService.class);
        final TabSnapshots tabSnapshots = new TabSnapshots(aiService, workspaceDao, workspaceStore, workspace,
                documentAccess, AiConfig::new);
        assembler = new ContextAssembler(() -> null, AiConfig::new, workspaceStore, aiService, aiDao, workspaceDao,
                Mockito.mock(ContextUsageTracker.class), memoryService,
                new DocumentReferences(aiService, documentAccess), tabSnapshots);
    }

    /// (a) A fresh question in a new chat, to a model with tools, in Agent mode, with nothing in memory.
    @Test
    void aFreshQuestionWithTools() {
        final List<AiChatMessage> history = List.of(
                message(1, AiMessageType.USER_MESSAGE, null, "How many logons failed yesterday?", 1));

        final List<ChatMessage> messages = assembler.assemble(request("How many logons failed yesterday?"),
                CHAT_ID, Map.of(), history, CompactionRecord.NONE, true);

        GoldenFiles.assertMatches("prompts/a-fresh-question-with-tools.txt", render(messages));
    }

    /// (b) A question asked with a Query tab attached (with its query text and a table of results), after a table
    /// and a document were attached, in Ask mode, with a user memory.
    @Test
    void aQuestionWithAQueryTabAndATableAttached() throws IOException {
        Mockito.when(aiDao.getAgentMode(CHAT_ID)).thenReturn(AiAgentMode.ASK);
        userMemory.add(memory(AiMemoryScope.USER, "", "preferences.md", "Answer in short bullet points."));
        final AiChatAttachment table = tableAttachment(50, "Failed logons (Dashboard 'Logons')", true);
        final DocRef dictionary = new DocRef("Dictionary", "dict-uuid-1", "Admin accounts");
        final AiChatAttachment reference = new AiChatAttachment(60, CHAT_ID, TIME_MS, TIME_MS,
                AiAttachmentStatus.READY, AiAttachmentType.DOCUMENT, "Dictionary 'Admin accounts'", null, false,
                null, dictionary, "/Security/Lookups");
        final int snapshotId = queryTab();
        final List<AiChatMessage> history = List.of(
                message(1, AiMessageType.ATTACHMENT, 50, "Failed logons (Dashboard 'Logons')", 1),
                message(2, AiMessageType.ATTACHMENT, 60, "Dictionary 'Admin accounts'", 1),
                message(3, AiMessageType.USER_MESSAGE, snapshotId, "Which of these users are admins?", 1));

        final List<ChatMessage> messages = assembler.assemble(request("Which of these users are admins?"),
                CHAT_ID, Map.of(50, table, 60, reference), history, CompactionRecord.NONE, true);

        GoldenFiles.assertMatches("prompts/b-query-tab-and-table-attached.txt", render(messages));
    }

    /// (c) A long chat in Agent mode: the first turn summarised, the second as a digest, the third in full, and
    /// the question being answered part way through, with a task list, tool calls made and the user steering.
    /// With user and chat memory.
    @Test
    void anAgentModeChatWithATaskListAndACompactionDigest() {
        Mockito.when(aiDao.getAgentMode(CHAT_ID)).thenReturn(AiAgentMode.AGENT);
        userMemory.add(memory(AiMemoryScope.USER, "", "preferences.md", "Answer in short bullet points."));
        chatMemory.add(memory(AiMemoryScope.CHAT, String.valueOf(CHAT_ID), "feed.md",
                "The feed being onboarded is ACME-FIREWALL-EVENTS."));
        final List<AiChatMessage> history = new ArrayList<>(List.of(
                message(1, AiMessageType.USER_MESSAGE, null, "What feeds do we have for firewalls?", 1),
                message(2, AiMessageType.AI_RESPONSE, null, "There are two: FW-A and FW-B.", 1),
                message(3, AiMessageType.USER_MESSAGE, null, "Show me the latest stream of FW-A.", 3),
                record(4, AiMessageType.TOOL_CALL, new ToolCallRecord("call-4", "stream_meta",
                        "{\"feed\":\"FW-A\",\"limit\":1}", "Finding the latest stream."), 3),
                record(5, AiMessageType.TOOL_RESULT, new ToolResultRecord("call-4", "stream_meta", false,
                        "Stream 1234 (Raw Events), 2026-01-01T00:00:00.000Z", null, 49, 12, null), 3),
                message(6, AiMessageType.AI_RESPONSE, null, "The latest is stream 1234.\n\nIt has 50 events.", 3),
                message(7, AiMessageType.COMPACTION_SUMMARY, null, JsonUtil.writeValueAsString(
                        new CompactionRecord(3, 1, "The user asked which firewall feeds there are: FW-A and FW-B.",
                                9_000, 1_000, 6, false, false), false), 3),
                message(8, AiMessageType.USER_MESSAGE, null, "Translate it to event-logging XML.", 8),
                message(9, AiMessageType.AI_RESPONSE, null, "Done: see Feeds/FW-A/Event translation.", 8),
                message(10, AiMessageType.USER_MESSAGE, null, "Now add a dashboard of failed logons.", 10),
                message(11, AiMessageType.TASK_LIST, null,
                        "[{\"task\":\"Find the logon events\",\"status\":\"done\"},"
                        + "{\"task\":\"Propose the dashboard\",\"status\":\"in_progress\"}]", 10),
                record(12, AiMessageType.TOOL_CALL, new ToolCallRecord("call-12", "run_stroomql",
                        "{\"query\":\"from FW-A select UserId, count() group by UserId\"}",
                        "Counting the failed logons."), 10),
                record(13, AiMessageType.TOOL_RESULT, new ToolResultRecord("call-12", "run_stroomql", false,
                        "The result was too large to show in full.", "/outputs/turn-10/call-12.txt", 9_000, 30,
                        null), 10),
                message(14, AiMessageType.STEER_MESSAGE, null, "Only for the last week, please.", 10),
                // Still waiting for the user's approval: left to the agent loop.
                record(15, AiMessageType.TOOL_CALL, new ToolCallRecord("call-15", "write_file",
                        "{\"path\":\"/notes.md\",\"content\":\"x\"}", null), 10)));
        final CompactionRecord compaction = ContextAssembler.currentCompaction(history);

        final List<ChatMessage> messages = assembler.assemble(request("Now add a dashboard of failed logons."),
                CHAT_ID, Map.of(), history, compaction, true);

        GoldenFiles.assertMatches("prompts/c-agent-mode-task-list-and-digest.txt", render(messages));
    }

    /// (d) A model without tools, given an attached table in full rather than its profile, and an earlier turn.
    @Test
    void aModelWithoutTools() throws IOException {
        userMemory.add(memory(AiMemoryScope.USER, "", "preferences.md", "Answer in short bullet points."));
        final AiChatAttachment table = tableAttachment(50, "Failed logons", false);
        final List<AiChatMessage> history = List.of(
                message(1, AiMessageType.ATTACHMENT, 50, "Failed logons", 0),
                message(2, AiMessageType.USER_MESSAGE, null, "Who failed most?", 2),
                message(3, AiMessageType.AI_RESPONSE, null, "alice, with 12.", 2),
                message(4, AiMessageType.USER_MESSAGE, null, "And second?", 4));

        final List<ChatMessage> messages = assembler.assemble(request("And second?"), CHAT_ID, Map.of(50, table),
                history, CompactionRecord.NONE, false);

        GoldenFiles.assertMatches("prompts/d-model-without-tools.txt", render(messages));
    }

    // A table attached as its workspace file, with its profile if asked for, as AskStroomAIService keeps them.
    private AiChatAttachment tableAttachment(final int id,
                                             final String description,
                                             final boolean withProfile) throws IOException {
        final String markdown = """
                | UserId | Host | StreamId | EventId |
                | --- | --- | --- | --- |
                | alice | fw1 | 1234 | 1 |
                | bob | fw1 | 1234 | 2 |
                | alice | fw2 | 1234 | 3 |
                """;
        final Path tableFile = tempDir.resolve(id + ".md");
        Files.writeString(tableFile, markdown, StandardCharsets.UTF_8);
        Mockito.when(workspaceStore.localFile(id)).thenReturn(tableFile);
        if (withProfile) {
            final int profileId = id + 1;
            final Path profileFile = tempDir.resolve(profileId + ".md");
            Files.writeString(profileFile, TableProfiler.profile(new StringReader(markdown), description),
                    StandardCharsets.UTF_8);
            Mockito.when(workspaceStore.localFile(profileId)).thenReturn(profileFile);
            Mockito.when(workspaceDao.findFile(CHAT_ID, AskStroomAIService.profilePath(id)))
                    .thenReturn(Optional.of(profileId));
        }
        return new AiChatAttachment(id, CHAT_ID, TIME_MS, TIME_MS, AiAttachmentStatus.READY,
                AiAttachmentType.DASHBOARD, description, 3, false, null);
    }

    // A snapshot of a Query tab, with its query text and the table of results it shows, as TabSnapshots keeps it.
    // Returns the id of its record's file.
    private int queryTab() throws IOException {
        final int snapshotId = 70;
        final int partId = 71;
        final String query = "from Logons\nwhere Outcome = 'Failure'\nselect UserId, count() as Failures";
        final TabSnapshots.StoredTabSnapshot snapshot = new TabSnapshots.StoredTabSnapshot(0, 2,
                new DocRef("Query", "query-uuid-1", "Failed logons"), "/Security/Queries", "Failed logons", "Query",
                true, null, "hash-1", TIME_MS,
                List.of(new TabSnapshots.StoredPart("query", "text/plain", "/tabs/2/query.stroomql",
                        query.length(), query.length(), false)),
                List.of(new AiTabSearch("Table", "table-1", null, null, List.of("UserId", "Failures"), 2L, false)),
                null);
        final Path snapshotFile = tempDir.resolve("snapshot.json");
        Files.writeString(snapshotFile, JsonUtil.writeValueAsString(snapshot, false), StandardCharsets.UTF_8);
        final Path queryFile = tempDir.resolve("query.stroomql");
        Files.writeString(queryFile, query, StandardCharsets.UTF_8);
        Mockito.when(workspaceDao.getFileInfo(snapshotId)).thenReturn(Optional.of(new AiWorkspaceDao.FileInfo(
                snapshotId, CHAT_ID, "/tabs/2/snapshot.json", AiAttachmentStatus.READY, null, null, null,
                100L)));
        Mockito.when(workspaceStore.localFile(snapshotId)).thenReturn(snapshotFile);
        Mockito.when(workspace.find(CHAT_ID, WorkspacePath.parse("/tabs/2/query.stroomql")))
                .thenReturn(Optional.of(partId));
        Mockito.when(workspaceStore.localFile(partId)).thenReturn(queryFile);
        return snapshotId;
    }

    private static AskStroomAiRequest request(final String question) {
        return new AskStroomAiRequest(null, null, null, question);
    }

    private static AiMemory memory(final AiMemoryScope scope,
                                   final String scopeKey,
                                   final String name,
                                   final String content) {
        return new AiMemory(1, 1, scope, scopeKey, name, content, TIME_MS, USER_UUID);
    }

    private static AiChatMessage message(final int id,
                                         final AiMessageType type,
                                         final Integer attachmentId,
                                         final String text,
                                         final int turnId) {
        return new AiChatMessage(id, CHAT_ID, TIME_MS + id, type, attachmentId, text, turnId, null);
    }

    private static AiChatMessage record(final int id,
                                        final AiMessageType type,
                                        final Object record,
                                        final int turnId) {
        return message(id, type, null, JsonUtil.writeValueAsString(record, false), turnId);
    }

    /// @param messages The messages for the model.
    /// @return Each message in turn, with its type and everything of it the model is sent.
    static String render(final List<ChatMessage> messages) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            final ChatMessage message = messages.get(i);
            sb.append("=== [").append(i).append("] ").append(message.type()).append(" ===\n");
            switch (message) {
                case final SystemMessage system -> sb.append(system.text()).append('\n');
                case final UserMessage user -> sb.append(user.singleText()).append('\n');
                case final AiMessage ai -> {
                    if (ai.text() != null) {
                        sb.append(ai.text()).append('\n');
                    }
                    for (final ToolExecutionRequest toolRequest : ai.toolExecutionRequests()) {
                        sb.append("--- tool call id=").append(toolRequest.id()).append(" name=")
                                .append(toolRequest.name()).append(" ---\n")
                                .append(toolRequest.arguments()).append('\n');
                    }
                }
                case final ToolExecutionResultMessage result -> sb.append("--- result of id=").append(result.id())
                        .append(" name=").append(result.toolName()).append(" ---\n")
                        .append(result.text()).append('\n');
                default -> sb.append(message).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
