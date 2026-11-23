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
import stroom.ai.api.OpenAIModelStore;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.MermaidGantt;
import stroom.ai.shared.MermaidGanttParser;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.test.AbstractAiIntegrationTest;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// gantt_chart end to end in Ask Stroom AI's chat: a table written to the chat's workspace, the dashboards group
/// turned on, and the table drawn as a Mermaid gantt block the chat's renderer reads, with only the chat model
/// replaced by a scripted model server ([FakeModelServer]).
class TestAskStroomAiGanttChart extends AbstractAiIntegrationTest {

    private static final String TABLE = "/outputs/sessions.csv";
    // Times in UTC; Canberra is 11 hours ahead in November.
    private static final String SESSIONS = """
            Start,End,Application,Host
            2026-11-22T22:00:00Z,2026-11-22T22:30:00Z,Outlook,ws-01
            2026-11-22T22:32:00Z,2026-11-22T22:40:00Z,Outlook,ws-01
            2026-11-22T22:45:00Z,,USB copy,ws-01
            2026-11-22T23:00:00Z,2026-11-22T23:30:00Z,Chrome,ws-02
            """;

    @Inject
    private AskStroomAIService askStroomAIService;
    @Inject
    private AiService aiService;
    @Inject
    private OpenAIModelStore modelStore;
    @Inject
    private AiChatRunManager runManager;

    private FakeModelServer model;
    private UserRef user;

    @BeforeEach
    void setUp() throws IOException {
        model = FakeModelServer.start();
        user = administrator();
        toolPolicy("Everything", AiToolAccess.READ_WRITE, user);
    }

    @AfterEach
    void tearDown() {
        model.close();
    }

    @Test
    void aWorkspaceTableIsDrawnAsAGanttBlockTheChatCanRead() {
        final DocRef modelRef = asUser(user, this::fakeModel);
        model.callTool("write_file", JsonUtil.writeValueAsString(Map.of("path", TABLE, "content", SESSIONS),
                        false))
                .callTool("activate_dashboards", "{}")
                .callTool("gantt_chart", JsonUtil.writeValueAsString(Map.of("path", TABLE,
                        "start", "Start", "end", "End", "label", "Application", "lane", "Host",
                        "title", "Window focus of alice", "timeZone", "Australia/Canberra"), false))
                .answer("Here is alice's morning.");

        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                AskStroomAiConfig.builder().modelRef(modelRef).build(), null,
                "Show me a timeline of alice's window focus", AiAgentMode.AGENT)));
        final List<AiChatMessage> messages = asUser(user, () -> answered(chat.getId()));

        final ToolResultRecord written = result(messages, "write_file");
        assertThat(written.error()).as(written.text()).isFalse();
        final ToolResultRecord chart = result(messages, "gantt_chart");
        assertThat(chart.error()).as(chart.text()).isFalse();
        assertThat(chart.text())
                .startsWith("Put this block in your reply exactly as it is")
                .contains("title Window focus of alice (times in Australia/Canberra)",
                        "section ws-01",
                        "Outlook :2026-11-23 09:00:00, 2026-11-23 09:40:00",
                        "USB copy :milestone, 2026-11-23 09:45:00, 0d",
                        "section ws-02",
                        "Chrome :2026-11-23 10:00:00, 2026-11-23 10:30:00",
                        "2 bar(s) and 1 marker(s) in 2 section(s), from 4 row(s) of " + TABLE,
                        "- Merged 1 bar(s) into the bar before of the same label in the same lane")
                .endsWith("never a chat chart that approximates it.");
        // The block reads under the parser the chat draws it with.
        final String block = chart.text().substring(chart.text().indexOf("```mermaid\n") + 11,
                chart.text().indexOf("```\n\n"));
        final MermaidGantt gantt = MermaidGanttParser.parse(block);
        assertThat(gantt.taskCount()).isEqualTo(3);

        // The model was offered the dashboards group's tool once the group was turned on, not before.
        final List<Map<String, Object>> turns = model.agentRequests();
        assertThat(JsonUtil.writeValueAsString(turns.getFirst().get("tools"), false))
                .contains("activate_dashboards")
                .doesNotContain("\"gantt_chart\"");
        assertThat(JsonUtil.writeValueAsString(turns.get(2).get("tools"), false)).contains("gantt_chart");
    }

    private static ToolResultRecord result(final List<AiChatMessage> messages, final String tool) {
        return messages.stream()
                .filter(message -> message.getMessageType() == AiMessageType.TOOL_RESULT)
                .map(message -> JsonUtil.readValue(message.getMessage(), ToolResultRecord.class))
                .filter(record -> tool.equals(record.tool()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No result of " + tool));
    }

    // A model doc pointing at the fake model server, with tools turned on.
    private DocRef fakeModel() {
        final DocRef ref = modelStore.createDocument("Fake model");
        final OpenAIModelDoc doc = modelStore.readDocument(ref).copy()
                .baseUrl(model.baseUrl())
                .apiKey("key")
                .modelId("fake")
                .toolsEnabled(true)
                .build();
        modelStore.writeDocument(doc);
        return ref;
    }

    // The chat's messages once the run has answered (or failed) and has ended, so that nothing it does is left to
    // happen once the test's database has gone.
    private List<AiChatMessage> answered(final int chatId) {
        final Instant until = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(until)) {
            final List<AiChatMessage> messages = aiService.getMessages(chatId);
            final Optional<AiChatMessage> last = messages.stream()
                    .filter(message -> message.getMessageType() == AiMessageType.AI_RESPONSE
                                       || message.getMessageType() == AiMessageType.ERROR)
                    .findFirst();
            if (last.isPresent() && !runManager.isRunningHere(chatId)) {
                assertThat(last.get().getMessageType())
                        .as("The run failed: %s", last.get().getMessage())
                        .isEqualTo(AiMessageType.AI_RESPONSE);
                return messages;
            }
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("The chat wasn't answered in time");
    }
}
