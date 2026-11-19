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

/// Ask Stroom AI with one of Stroom's own forms waiting, and the user typing a message in the chat instead of
/// answering it, end to end: survey_feed's "Which field says which source a record came from?" form, through the
/// real chat service and tools, with only the chat model replaced by a scripted model server ([FakeModelServer]).
///
/// Seen in testing: the user typed a question rather than answering the form. The call, made again, was told the
/// form was closed or ran out and to call survey_feed again with the same arguments, which came after the user's
/// message in the model's context, so the form was shown again and the question never answered.
class TestAskStroomAiMessageInsteadOfForm extends AbstractAiIntegrationTest {

    private static final String SOURCE = "Aggregated app";
    private static final String FEED = "AGGREGATED-APP";
    // The source type field form's message says so.
    private static final String SOURCE_FORM = "aggregates several sources";
    private static final String TYPED = "What's the difference between the app and source fields?";

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
    // The source type field forms the user was shown.
    private int sourceFormsShown;
    // The form the user typed a message instead of answering, or 0 before they have.
    private int typedAfterId;

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
    void aMessageTypedInsteadOfAnsweringSurveyFeedsSourceFormIsGivenToTheAgentNotTheFormAgain() {
        final String build = sampleInABuild();
        final DocRef modelRef = asUser(user, this::fakeModel);
        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        // Twice: as for a real model, the first call may only turn the build tools on. Then once with the user's
        // answer, as they said it in the chat.
        final String arguments = JsonUtil.writeValueAsString(Map.of("feed", FEED, "build", build), false);
        final String answered = JsonUtil.writeValueAsString(Map.of("feed", FEED, "build", build,
                "source_type_field", "source"), false);
        model.callTool("survey_feed", arguments)
                .callTool("survey_feed", arguments)
                .callTool("survey_feed", answered)
                .answer("app is the application, source the component.");

        ask(chat, modelRef, "Survey the feed.");

        assertThat(sourceFormsShown).as("the source type field form was shown").isGreaterThanOrEqualTo(1);
        // The model's first turn after the user typed their message.
        final Map<String, Object> after = model.agentRequests().stream()
                .filter(request -> JsonUtil.writeValueAsString(request.get("messages"), false).contains(TYPED))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The model was never given the user's message: "
                                                      + model.agentRequests()));
        final List<Map<String, Object>> messages = messages(after);
        final int lastTool = lastIndexOf(messages, "tool");
        assertThat(lastTool).as("the re-made survey_feed call's result is in %s", messages).isNotNegative();
        final String result = String.valueOf(messages.get(lastTool).get("content"));
        assertThat(result).as("what the re-made survey_feed call tells the agent")
                .containsIgnoringCase("sent a message instead")
                .doesNotContain("closed or ran out")
                .doesNotContain("again with the same arguments");
        // Nothing the agent reads after the user's message tells it to ask the same question again.
        assertThat(sourceFormsShown).as("the source type field form shown again").isEqualTo(1);
        // Each later survey_feed call: without the user's answer, told to pass it; with it, taken (no form).
        final List<String> later = model.agentRequests().stream()
                .map(TestAskStroomAiMessageInsteadOfForm::messages)
                .filter(request -> JsonUtil.writeValueAsString(request, false).contains(TYPED))
                .map(request -> String.valueOf(request.get(lastIndexOf(request, "tool")).get("content")))
                .distinct()
                .toList();
        // The last, with the user's answer, surveyed the feed by it: the next step is its source types (the result
        // is large, so the agent is given its next step and a preview).
        assertThat(later).as("the survey_feed results after the user's message").last().asString()
                .contains("\"step\":\"source_types\"").doesNotContain("needs_source_type_field");
        assertThat(later.subList(1, later.size() - 1)).as("calls without the user's answer, of %s", later)
                .allSatisfy(text -> assertThat(text).contains("needs_source_type_field")
                        .contains("source_type_field"));
    }

    // The build, its feed, and a sample of an application's log, JSON lines naming the component that wrote
    // each record, uploaded to it, made through the real tools. Returns the build's name.
    private String sampleInABuild() {
        final String sample = sample();
        final Map<String, Object> started = client.json("start_onboarding", Map.of("source_name", SOURCE,
                "samples", List.of(sample)));
        final String build = (String) started.get("build");
        client.json("create_feed", Map.of("build", build, "name", FEED));
        client.json("upload_sample", Map.of("feed", FEED, "sample", sample));
        return build;
    }

    // Asks the question, answering the forms and approvals the run stops for, as the user would in the chat, except
    // the first source type field form, which the user types a message in the chat instead of answering.
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

    // The first source type field form: the user types a message in the chat instead. Any other form, or that
    // form shown again: answered.
    private void respond(final AiChat chat,
                         final DocRef modelRef,
                         final AiChatMessage message,
                         final ElicitationRecord form) {
        final boolean sourceForm = String.valueOf(form.message()).contains(SOURCE_FORM);
        if (sourceForm) {
            sourceFormsShown++;
        }
        if (sourceForm && typedAfterId == 0) {
            typedAfterId = message.getId();
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

    // JSON lines of an application, each naming the component that wrote it, each component's messages in a
    // format of its own.
    private static String sample() {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            final String message = switch (i % 3) {
                case 0 -> "{\\\"eventType\\\": \\\"" + (i % 2 == 0
                        ? "login"
                        : "logout") + "\\\", \\\"outcome\\\": \\\"failure\\\"}";
                case 1 -> i % 2 == 0
                        ? "Executing query took " + i + "ms"
                        : "Connection pool grew to " + i;
                default -> "Request: <Envelope><Body>" + i + "</Body></Envelope>";
            };
            final String source = List.of("Security", "Database", "Integration").get(i % 3);
            out.append("{\"app\": \"acme\", \"source\": \"").append(source).append("\", \"message\": \"")
                    .append(message).append("\"}\n");
        }
        return out.toString();
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
