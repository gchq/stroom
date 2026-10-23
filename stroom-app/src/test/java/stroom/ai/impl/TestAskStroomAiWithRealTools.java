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
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.PermissionInheritance;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaProperties;
import stroom.meta.shared.Meta;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.test.AbstractAiIntegrationTest;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// An Ask Stroom AI chat run through the REAL chat service ([AskStroomAIService], [McpAgentChat]) and the real
/// tools, on real test data, with only the chat model replaced: a scripted model server ([FakeModelServer]) that
/// Stroom's own model client streams from. The model calls a few of the tools that read Stroom, and what they
/// return reaches the model and is kept in the chat as the user would see it.
class TestAskStroomAiWithRealTools extends AbstractAiIntegrationTest {

    private static final String FEED = "ACME-CHAT";
    private static final String RAW = "user=alice action=logon\nuser=bob action=logoff\n";

    @Inject
    private AskStroomAIService askStroomAIService;
    @Inject
    private AiService aiService;
    @Inject
    private OpenAIModelStore modelStore;
    @Inject
    private ExplorerService explorerService;
    @Inject
    private ExplorerNodeService explorerNodeService;
    @Inject
    private Store store;

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
    void aChatsModelCallsRealToolsOnRealDataAndTheirResultsAreKept() {
        // Made as the user makes one, in the explorer.
        final DocRef feed = asUser(user, () -> explorerService.create(FeedDoc.TYPE, FEED,
                explorerNodeService.getRoot(), PermissionInheritance.DESTINATION).getDocRef());
        final Meta stream = asUser(user, () -> rawStream(FEED, RAW));
        final DocRef modelRef = asUser(user, this::fakeModel);
        model.callTool("stream_meta", JsonUtil.writeValueAsString(Map.of("feeds", List.of(FEED)), false))
                .callTool("read_document", JsonUtil.writeValueAsString(Map.of("uuid", feed.getUuid()), false))
                .callTool("folder_contents", JsonUtil.writeValueAsString(Map.of("folder", "System"), false))
                .callTool("run_stroomql", JsonUtil.writeValueAsString(Map.of("query",
                        "from \"Stream Store\" where Feed = \"" + FEED + "\" select Feed, Type"), false))
                .answer("ACME-CHAT has one Raw Events stream.");

        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                AskStroomAiConfig.builder().modelRef(modelRef).build(), null, "What is in ACME-CHAT?",
                AiAgentMode.AGENT)));
        final List<AiChatMessage> messages = asUser(user, () -> answered(chat.getId()));

        // The model's answer is kept, after its calls and their results, with the feed its tools gave linked.
        assertThat(messages)
                .filteredOn(message -> message.getMessageType() == AiMessageType.AI_RESPONSE)
                .extracting(AiChatMessage::getMessage)
                .singleElement()
                .asString()
                .contains("[ACME-CHAT](?action=open-doc&docType=Feed&docUuid=" + feed.getUuid()
                          + ") has one Raw Events stream.");
        final List<ToolResultRecord> results = messages.stream()
                .filter(message -> message.getMessageType() == AiMessageType.TOOL_RESULT)
                .map(message -> JsonUtil.readValue(message.getMessage(), ToolResultRecord.class))
                .toList();
        assertThat(results)
                .extracting(ToolResultRecord::tool)
                .containsExactly("stream_meta", "read_document", "folder_contents", "run_stroomql");
        final Map<String, ToolResultRecord> byTool = results.stream()
                .collect(Collectors.toMap(ToolResultRecord::tool, result -> result));
        // Each result is what the real tool read from Stroom.
        assertThat(byTool.get("stream_meta").error()).isFalse();
        assertThat(byTool.get("stream_meta").text())
                .contains(String.valueOf(stream.getId()))
                .contains(StreamTypeNames.RAW_EVENTS);
        assertThat(byTool.get("read_document").error()).isFalse();
        assertThat(byTool.get("read_document").text()).contains(FEED);
        assertThat(byTool.get("folder_contents").error()).isFalse();
        assertThat(byTool.get("folder_contents").text()).contains(FEED);
        // A StroomQL query of the stream store, run as the user.
        assertThat(byTool.get("run_stroomql").error()).as(byTool.get("run_stroomql").text()).isFalse();
        assertThat(byTool.get("run_stroomql").text()).contains(FEED, StreamTypeNames.RAW_EVENTS);

        // And each went back to the model, which was offered the real tools.
        final List<Map<String, Object>> turns = model.agentRequests();
        assertThat(turns).hasSize(5);
        assertThat(JsonUtil.writeValueAsString(turns.getFirst().get("tools"), false))
                .contains("stream_meta", "read_document", "folder_contents", "run_stroomql", "start_onboarding");
        assertThat(JsonUtil.writeValueAsString(turns.getLast().get("messages"), false))
                .contains(String.valueOf(stream.getId()));
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

    private Meta rawStream(final String feedName, final String data) {
        final MetaProperties properties = MetaProperties.builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        try (final Target target = store.openTarget(properties)) {
            TargetUtil.write(target, data);
            return target.getMeta();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The chat's messages once the run has answered (or failed), as the user's client would poll for them.
    private List<AiChatMessage> answered(final int chatId) {
        final Instant until = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(until)) {
            final List<AiChatMessage> messages = aiService.getMessages(chatId);
            final Optional<AiChatMessage> last = messages.stream()
                    .filter(message -> message.getMessageType() == AiMessageType.AI_RESPONSE
                                       || message.getMessageType() == AiMessageType.ERROR)
                    .findFirst();
            if (last.isPresent()) {
                assertThat(last.get().getMessageType())
                        .as("The run failed: %s", last.get().getMessage())
                        .isEqualTo(AiMessageType.AI_RESPONSE);
                return messages;
            }
            try {
                Thread.sleep(200);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("The chat was not answered: " + aiService.getMessages(chatId));
    }
}
