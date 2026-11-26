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
import stroom.ai.shared.AiChatPollRequest;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AnswerAiApprovalRequest;
import stroom.ai.shared.AnswerAiElicitationRequest;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.docref.DocRef;
import stroom.mcp.impl.McpTestClient;
import stroom.mcp.impl.McpTestClients;
import stroom.mcp.impl.e2e.LoggerFeed;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.test.AbstractAiIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Ask Stroom AI with survey_feed's "Which field says which source a record came from?" form waiting on an
/// aggregated feed of a logging framework's records ([LoggerFeed]), and the user typing a message in the chat
/// instead of answering it, end to end, with only the chat model replaced by a scripted model server
/// ([FakeModelServer]).
///
/// Seen in external testing (2026-11-26): the agent showed the same form again instead of replying. Tried with a new
/// stream arriving in the feed between the form being shown and the call being made again, and without one.
class TestAskStroomAiSteerOverSourceForm extends AbstractAiIntegrationTest {

    private static final String SOURCE = "Platform logs";
    // The source type field form's message says so.
    private static final String SOURCE_FORM = "aggregates several sources";
    private static final String TYPED = "Which of those fields did you read, and from how many streams?";

    @Inject
    private AskStroomAIService askStroomAIService;
    @Inject
    private AiService aiService;
    @Inject
    private OpenAIModelStore modelStore;
    @Inject
    private McpTestClients mcpTestClients;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private AiChatRunManager runManager;

    private FakeModelServer model;
    private UserRef user;
    private McpTestClient client;
    // The messages of the source type field forms the user was shown.
    private final List<String> sourceForms = new ArrayList<>();
    // The feed a new stream arrives in as the user types, or null for none.
    private String newStreamFeed;
    private boolean typed;

    @BeforeEach
    void setUp() throws IOException {
        model = FakeModelServer.start();
        user = administrator();
        asUser(user, () -> commonTestControl.createRequiredXMLSchemas());
        toolPolicy("Everything", AiToolAccess.READ_WRITE, user);
        client = mcpTestClients.client(user, true);
    }

    @AfterEach
    void tearDown() {
        model.close();
    }

    @Test
    void aMessageTypedOverTheSourceFormIsRepliedToWithNoNewStream() {
        typedInsteadOfAnswering("PLATFORM-STEER-1", false);
    }

    @Test
    void aMessageTypedOverTheSourceFormIsRepliedToThoughANewStreamArrived() {
        typedInsteadOfAnswering("PLATFORM-STEER-2", true);
    }

    private void typedInsteadOfAnswering(final String feed, final boolean newStream) {
        final String build = sampleInABuild(feed);
        newStreamFeed = newStream
                ? feed
                : null;
        final DocRef modelRef = asUser(user, this::fakeModel);
        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        final String arguments = JsonUtil.writeValueAsString(Map.of("feed", feed, "build", build), false);
        final String answered = JsonUtil.writeValueAsString(Map.of("feed", feed, "build", build,
                "source_type_field", "event>logger_name"), false);
        model.callTool("survey_feed", arguments)
                .callTool("survey_feed", arguments)
                .callTool("survey_feed", answered)
                .answer("I read logger_name and appender_ref.");

        ask(chat, modelRef, "Survey the feed.");

        final List<String> later = toolResults();
        assertThat(sourceForms).as("the source type field form was shown").isNotEmpty();
        assertThat(later).as("the survey_feed results after the user's message").isNotEmpty();
        assertThat(later.getFirst()).as("what the re-made survey_feed call tells the agent")
                .containsIgnoringCase("sent a message instead")
                .doesNotContain("closed or ran out");
        assertThat(sourceForms).as("the source type field form shown again").hasSize(1);
    }

    // The results of the tool calls the model was given after the user's message, distinct, in order.
    private List<String> toolResults() {
        final List<String> out = new ArrayList<>();
        for (final Map<String, Object> request : model.agentRequests()) {
            final List<Map<String, Object>> messages = messages(request);
            if (!JsonUtil.writeValueAsString(messages, false).contains(TYPED)) {
                continue;
            }
            final int lastTool = lastIndexOf(messages, "tool");
            final String result = lastTool < 0
                    ? null
                    : String.valueOf(messages.get(lastTool).get("content"));
            if (result != null && !out.contains(result)) {
                out.add(result);
            }
        }
        return out;
    }

    // The build, its feed, and a stream of the platform's records uploaded to it. Returns the build's name.
    private String sampleInABuild(final String feed) {
        final String sample = LoggerFeed.lines(120, 0);
        final Map<String, Object> started = client.json("start_onboarding", Map.of("source_name", SOURCE,
                "samples", List.of(sample)));
        final String build = (String) started.get("build");
        client.json("create_feed", Map.of("build", build, "name", feed));
        client.json("upload_sample", Map.of("feed", feed, "sample", sample));
        return build;
    }

    // Asks the question, answering the forms and approvals the run stops for, as the user would in the chat, except
    // the first source type field form, over which the user types a message in the chat.
    private void ask(final AiChat chat, final DocRef modelRef, final String question) {
        final int answersBefore = answers(chat.getId()).size();
        asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                AskStroomAiConfig.builder().modelRef(modelRef).build(), null, question, AiAgentMode.AGENT, null)));
        final Set<Integer> handled = new HashSet<>();
        final Instant until = Instant.now().plus(Duration.ofMinutes(3));
        while (Instant.now().isBefore(until)) {
            final List<AiChatMessage> messages = asUser(user, () -> {
                askStroomAIService.pollMessages(chat.getId(), new AiChatPollRequest(0));
                return aiService.getMessages(chat.getId());
            });
            for (final AiChatMessage message : messages) {
                if (handled.contains(message.getId())) {
                    continue;
                }
                if (message.getMessageType() == AiMessageType.ELICITATION) {
                    final ElicitationRecord form = JsonUtil.readValue(message.getMessage(), ElicitationRecord.class);
                    if (form.status() == ElicitationRecord.Status.PENDING) {
                        handled.add(message.getId());
                        respond(chat, modelRef, message, form);
                    }
                } else if (message.getMessageType() == AiMessageType.APPROVAL_REQUEST) {
                    handled.add(message.getId());
                    asUser(user, () -> askStroomAIService.answerApproval(new AnswerAiApprovalRequest(chat,
                            message.getId(), true)));
                }
            }
            final List<AiChatMessage> answers = answers(chat.getId());
            if (answers.size() > answersBefore && !runManager.isRunningHere(chat.getId())) {
                return;
            }
            sleep();
        }
        throw new AssertionError("The question was not answered: " + aiService.getMessages(chat.getId()));
    }

    // The first source type field form: a new stream arrives (where the test says), and the user types a message
    // instead of answering. Any other form, or that form shown again: answered.
    private void respond(final AiChat chat,
                         final DocRef modelRef,
                         final AiChatMessage message,
                         final ElicitationRecord form) {
        final boolean sourceForm = String.valueOf(form.message()).contains(SOURCE_FORM);
        if (sourceForm) {
            sourceForms.add(String.valueOf(form.message()));
        }
        if (sourceForm && !typed) {
            typed = true;
            if (newStreamFeed != null) {
                client.json("upload_sample", Map.of("feed", newStreamFeed, "sample", LoggerFeed.lines(90, 120)));
            }
            asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                    AskStroomAiConfig.builder().modelRef(modelRef).build(), null, TYPED, AiAgentMode.AGENT,
                    null)));
            return;
        }
        asUser(user, () -> askStroomAIService.answerElicitation(new AnswerAiElicitationRequest(chat,
                message.getId(), "ACCEPT", answersTo(form.schema()))));
    }

    // The chat's answers and errors so far.
    private List<AiChatMessage> answers(final int chatId) {
        return asUser(user, () -> aiService.getMessages(chatId)).stream()
                .filter(message -> message.getMessageType() == AiMessageType.AI_RESPONSE
                                   || message.getMessageType() == AiMessageType.ERROR)
                .toList();
    }

    // The user's answer to a form: yes to a confirmation, else the first option or suggestion.
    private static Map<String, String> answersTo(final Map<String, Object> schema) {
        final Map<String, String> answers = new LinkedHashMap<>();
        ((Map<?, ?>) schema.get("properties")).forEach((name, field) -> {
            final Map<?, ?> definition = (Map<?, ?>) field;
            if ("boolean".equals(definition.get("type"))) {
                answers.put(String.valueOf(name), "true");
            } else if (definition.get("enum") instanceof final List<?> options && !options.isEmpty()) {
                answers.put(String.valueOf(name), String.valueOf(options.getFirst()));
            } else if (definition.get("examples") instanceof final List<?> suggested && !suggested.isEmpty()) {
                answers.put(String.valueOf(name), String.valueOf(suggested.getFirst()));
            }
        });
        return answers;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> messages(final Map<String, Object> request) {
        return new ArrayList<>((List<Map<String, Object>>) request.get("messages"));
    }

    private static int lastIndexOf(final List<Map<String, Object>> messages, final String role) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (role.equals(messages.get(i).get("role"))) {
                return i;
            }
        }
        return -1;
    }

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

    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
