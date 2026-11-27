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
import stroom.ai.shared.AiUserActionResult;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.docref.DocRef;
import stroom.documentation.impl.DocumentationStore;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
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

/// Standing instructions suggested from the user's own chats, on a real Stroom with only the chat model replaced
/// (a scripted [FakeModelServer]): the time zone chosen the same way in two earlier chats is the evidence, the model
/// proposes an AGENTS doc for a folder that has none, and the user's apply creates and saves it; a later change to it
/// is applied to its editor, unsaved, as the user's Edit allows.
class TestAskStroomAiStandingInstructions extends AbstractAiIntegrationTest {

    private static final String TOOL = "build_translation_xslt";
    private static final String AGENTS = "# Standing instructions\n\n## Time\n\n- Times are Australia/Sydney.\n";

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
    private DocumentationStore documentationStore;
    @Inject
    private UserActionService userActionService;
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
    void aChoiceRepeatedInTheUsersChatsIsProposedAndCreatedAsTheFoldersStandingInstructions() {
        final DocRef folder = asUser(user, () -> explorerService.create(ExplorerConstants.FOLDER_TYPE, "Acme",
                explorerNodeService.getRoot(), PermissionInheritance.DESTINATION).getDocRef());
        // The evidence: the same time zone chosen in two earlier chats.
        for (int i = 0; i < 2; i++) {
            final AiChat earlier = asUser(user, () -> askStroomAIService.createChat());
            asUser(user, () -> aiService.storeMessage(earlier.getId(), AiMessageType.ELICITATION,
                    timeZoneAnswered("Australia/Sydney")));
        }
        final DocRef modelRef = asUser(user, this::fakeModel);
        model.callTool("activate_build", "{}")
                .callTool("instruction_evidence", "{}")
                .callTool("propose_instructions", JsonUtil.writeValueAsString(Map.of("folder", folder.getUuid(),
                        "markdown", AGENTS, "summary", "Times as you chose them in two chats."), false))
                .answer("I've proposed standing instructions for Acme.");

        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                AskStroomAiConfig.builder().modelRef(modelRef).build(), null, "Suggest standing instructions",
                AiAgentMode.AGENT)));
        final List<AiChatMessage> messages = asUser(user, () -> answered(chat.getId(), 0));

        assertThat(result(messages, "instruction_evidence").text())
                .contains("of the user's own chats", TOOL + " / Time zone: 'Australia/Sydney', in 2 chats");
        final AiChatMessage proposed = resultMessage(messages, "propose_instructions");
        final ToolResultRecord proposal = JsonUtil.readValue(proposed.getMessage(), ToolResultRecord.class);
        assertThat(proposal.error()).as(proposal.text()).isFalse();
        final UserActionRecord create = proposal.userActions().getFirst();
        assertThat(create.action()).isEqualTo(UserActions.CREATE_INSTRUCTIONS);
        assertThat(asUser(user, () -> agentsDocIn(folder))).as("nothing changes until the user applies it")
                .isEmpty();

        // The user applies it: the doc is created in the folder, saved, as the user.
        final AiUserActionResult created = asUser(user, () -> userActionService.doUserAction(chat.getId(),
                proposed.getId(), create.id()));

        assertThat(created.isDone()).as(created.getMessage()).isTrue();
        final DocRef agents = asUser(user, () -> agentsDocIn(folder)).orElseThrow();
        assertThat(created.getOpenDocument()).isEqualTo(agents);
        assertThat(asUser(user, () -> documentationStore.readDocument(agents)).getData()).isEqualTo(AGENTS);

        // A later change to it is the documentation change the user applies to its editor, unsaved.
        model.callTool("propose_instructions", JsonUtil.writeValueAsString(Map.of("folder", folder.getUuid(),
                        "edits", List.of(Map.of("oldText", "Australia/Sydney", "newText", "UTC")),
                        "summary", "Times in UTC now."), false))
                .answer("Proposed.");
        asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                AskStroomAiConfig.builder().modelRef(modelRef).build(), null, "Make it UTC", AiAgentMode.AGENT)));
        final List<AiChatMessage> later = asUser(user, () -> answered(chat.getId(), 1));
        final AiChatMessage change = later.stream()
                .filter(message -> message.getMessageType() == AiMessageType.TOOL_RESULT
                                   && message.getId() > proposed.getId()
                                   && "propose_instructions".equals(JsonUtil.readValue(message.getMessage(),
                        ToolResultRecord.class).tool()))
                .findFirst()
                .orElseThrow();
        final UserActionRecord apply = JsonUtil.readValue(change.getMessage(), ToolResultRecord.class)
                .userActions().getFirst();
        assertThat(apply.action()).isEqualTo(UserActions.APPLY_DOCUMENTATION_CHANGE);
        assertThat(apply.doc()).isEqualTo(agents);
        assertThat(apply.codeChange().baseCode()).isEqualTo(AGENTS);

        final AiUserActionResult applied = asUser(user, () -> userActionService.doUserAction(chat.getId(),
                change.getId(), apply.id()));
        assertThat(applied.isDone()).as(applied.getMessage()).isTrue();
        assertThat(applied.getMessage()).isEqualTo("Applied, not saved");
        assertThat(asUser(user, () -> documentationStore.readDocument(agents)).getData()).as("saved only by the user")
                .isEqualTo(AGENTS);
    }

    // An ELICITATION message: the time zone form the translation tool asked, accepted with the zone.
    private static String timeZoneAnswered(final String zone) {
        return JsonUtil.writeValueAsString(new ElicitationRecord(ElicitationRecord.KIND,
                ElicitationRecord.Status.ACCEPTED, "Stroom AI", TOOL, "Which time zone are the sample's times in?",
                Map.of("type", "object", "properties", Map.of("time_zone", Map.of("type", "string", "title",
                        "Time zone"))), Long.MAX_VALUE, Map.of("time_zone", zone), null), false);
    }

    private Optional<DocRef> agentsDocIn(final DocRef folder) {
        return explorerNodeService.getChildren(folder).stream()
                .map(ExplorerNode::getDocRef)
                .filter(child -> DocumentationDoc.TYPE.equals(child.getType()) && "AGENTS".equals(child.getName()))
                .findFirst();
    }

    private static ToolResultRecord result(final List<AiChatMessage> messages, final String tool) {
        return JsonUtil.readValue(resultMessage(messages, tool).getMessage(), ToolResultRecord.class);
    }

    private static AiChatMessage resultMessage(final List<AiChatMessage> messages, final String tool) {
        return messages.stream()
                .filter(message -> message.getMessageType() == AiMessageType.TOOL_RESULT
                                   && tool.equals(JsonUtil.readValue(message.getMessage(),
                        ToolResultRecord.class).tool()))
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

    // The chat's messages once the run has given its answer after the earlier ones (or failed), as the user's
    // client would poll for them, and has ended.
    private List<AiChatMessage> answered(final int chatId, final int answers) {
        final Instant until = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(until)) {
            final List<AiChatMessage> messages = aiService.getMessages(chatId);
            final long done = messages.stream()
                    .filter(message -> message.getMessageType() == AiMessageType.AI_RESPONSE
                                       || message.getMessageType() == AiMessageType.ERROR)
                    .count();
            if (done > answers && !runManager.isRunningHere(chatId)) {
                assertThat(messages)
                        .as("The run failed")
                        .noneMatch(message -> message.getMessageType() == AiMessageType.ERROR);
                return messages;
            }
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted", e);
            }
        }
        throw new AssertionError("No answer within 2 minutes");
    }
}
