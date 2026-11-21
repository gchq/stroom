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
    // The catalogue form's message says so ("Accept all N event types, or type your amendments").
    private static final String CATALOGUE_FORM = "event types";
    private static final String CATALOGUE = "catalogue_event_types";
    private static final String AMENDMENT = "The logouts are session timeouts, not logoffs";
    private static final String QUESTION = "What does a logon record look like?";

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
    // The forms the user was shown of the kind they type a message instead of answering.
    private int sourceFormsShown;
    // The kind of form (part of its message) the user types a message instead of answering, and that message.
    private String typedInsteadOf = SOURCE_FORM;
    private String typedMessage = TYPED;
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

    // The catalogue form ("Accept all N event types, or type your amendments") is a choice the user may amend in the
    // chat, but the event types are confirmed only through the form (stroom-ai/DESIGN.md, Consent; the catalogue's
    // Confirmation). The user types an amendment instead of answering it: the re-made call tells the agent to reply
    // first; the agent sends the amendment as a change; nothing is confirmed from the chat, and the form is not
    // shown again until the agent asks.
    @Test
    void aMessageTypedInsteadOfAnsweringTheCatalogueFormIsAnAmendmentNeverAnAcceptance() {
        typedInsteadOf = CATALOGUE_FORM;
        typedMessage = AMENDMENT;
        final String build = logonsCatalogued();
        final DocRef modelRef = asUser(user, this::fakeModel);
        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        final String confirm = JsonUtil.writeValueAsString(Map.of("build", build, "confirm", true), false);
        final String amended = JsonUtil.writeValueAsString(Map.of("build", build, "proposals", List.of(Map.of(
                "op", "change", "type_id", "Authenticate-Logoff", "title", "Session timeout", "description",
                "A session timed out"))), false);
        model.callTool(CATALOGUE, confirm)
                .callTool(CATALOGUE, confirm)
                .callTool(CATALOGUE, amended)
                .answer("Changed: the logouts are session timeouts. Confirm the event types in the form.");

        ask(chat, modelRef, "Confirm the event types.");

        assertThat(sourceFormsShown).as("the catalogue form was shown once").isEqualTo(1);
        final Map<String, Object> after = model.agentRequests().stream()
                .filter(request -> JsonUtil.writeValueAsString(request.get("messages"), false).contains(AMENDMENT))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The model was never given the user's message: "
                                                      + model.agentRequests()));
        final List<Map<String, Object>> messages = messages(after);
        final String result = String.valueOf(messages.get(lastIndexOf(messages, "tool")).get("content"));
        assertThat(result).as("what the re-made catalogue_event_types call tells the agent")
                .containsIgnoringCase("sent a message instead")
                .doesNotContain("closed or ran out")
                .doesNotContain("\"status\":\"confirmed\"");
        // The amendment applied as a change; nothing confirmed from the chat.
        final String listed = JsonUtil.writeValueAsString(client.json(CATALOGUE, Map.of("build", build)), false);
        assertThat(listed).contains("Session timeout", "\"status\":\"proposed\"")
                .doesNotContain("\"status\":\"confirmed\"");
    }

    // Decided by the user 2026-11-20: the user may ask about any item put to them. A question typed instead of
    // answering the catalogue form is answered in the chat (explain=, which changes nothing), the set is put to them
    // again, and they accept it in the form; no amendment is recorded, and nothing is accepted from the chat.
    @Test
    void aQuestionTypedInsteadOfAnsweringTheCatalogueFormIsAnsweredAndTheSetPutAgain() {
        typedInsteadOf = CATALOGUE_FORM;
        typedMessage = QUESTION;
        final String build = logonsCatalogued();
        final DocRef modelRef = asUser(user, this::fakeModel);
        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        final String confirm = JsonUtil.writeValueAsString(Map.of("build", build, "confirm", true), false);
        final String explain = JsonUtil.writeValueAsString(Map.of("build", build, "explain",
                List.of("Authenticate-Logon")), false);
        // Shown, its form put aside by the question, explained, then put to the user again (once more in case the
        // table has to be shown again first); every record catalogued, the catch-all stands with no form.
        model.callTool(CATALOGUE, confirm)
                .callTool(CATALOGUE, confirm)
                .callTool(CATALOGUE, explain)
                .callTool(CATALOGUE, confirm)
                .callTool(CATALOGUE, confirm)
                .answer("A logon record has action=login. Both event types are confirmed.");

        ask(chat, modelRef, "Confirm the event types.");

        final List<String> results = toolResults(QUESTION);
        assertThat(results).as("the catalogue_event_types results after the user's question").isNotEmpty();
        assertThat(results.getFirst()).as("what the re-made call tells the agent")
                .containsIgnoringCase("sent a message instead")
                .contains(QUESTION, "explain=", "never take their message as acceptance")
                .doesNotContain("\"status\":\"confirmed\"");
        assertThat(results).as("explain's result").anySatisfy(result -> assertThat(result)
                .contains("\"status\":\"explained\"", "Nothing was changed", "\"examples\""));
        // Put to the user again, and accepted in the form: the first set is confirmed as it was.
        assertThat(sourceFormsShown).as("the catalogue form shown again after the question").isEqualTo(2);
        final String listed = JsonUtil.writeValueAsString(client.json(CATALOGUE, Map.of("build", build)), false);
        assertThat(listed).contains("\"type_id\":\"Authenticate-Logon\",\"title\":\"Authenticate-Logon\","
                                    + "\"status\":\"confirmed\"")
                .contains("\"type_id\":\"Authenticate-Logoff\",\"title\":\"Authenticate-Logoff\","
                          + "\"status\":\"confirmed\"");
    }

    // The results of the tool calls the model was given after the user's message, distinct, in order.
    private List<String> toolResults(final String typed) {
        final List<String> out = new ArrayList<>();
        for (final Map<String, Object> request : model.agentRequests()) {
            final List<Map<String, Object>> messages = messages(request);
            if (!JsonUtil.writeValueAsString(messages, false).contains(typed)) {
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

    // A build of logons and logoffs, surveyed, its two event types proposed (high confidence) through the real
    // tools, for the chat to confirm. Returns the build's name.
    private String logonsCatalogued() {
        final StringBuilder sample = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sample.append("time=2026-11-06T09:%02d:00Z action=%s user=user%d\n".formatted(i, i % 2 == 0
                    ? "login"
                    : "logout", i % 3));
        }
        final Map<String, Object> started = client.json("start_onboarding", Map.of("source_name", "Logons",
                "samples", List.of(sample.toString())));
        final String build = (String) started.get("build");
        client.json("create_feed", Map.of("build", build, "name", "CHAT-LOGONS"));
        client.json("upload_sample", Map.of("feed", "CHAT-LOGONS", "sample", sample.toString()));
        client.json("survey_feed", Map.of("feed", "CHAT-LOGONS", "build", build));
        client.json(CATALOGUE, Map.of("build", build));
        client.json(CATALOGUE, Map.of("build", build, "proposals", List.of(
                logged("Authenticate-Logon", "login", "Logon"),
                logged("Authenticate-Logoff", "logout", "Logoff"))));
        return build;
    }

    // An event type of the logons, recognised by its action.
    private static Map<String, Object> logged(final String typeId, final String value, final String action) {
        return Map.of("op", "add", "type_id", typeId, "title", typeId, "description", "E2E: " + typeId,
                "event_detail", "Authenticate", "action", action, "actor", "user", "confidence", "high",
                "basis", "agent_knowledge", "recognised_by", List.of(Map.of("when", List.of(Map.of("field",
                        "action", "equals", value)), "names", List.of())));
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

    // The first form of the kind the user types instead of answering: the user types a message in the chat
    // instead. Any other form, or that form shown again: answered.
    private void respond(final AiChat chat,
                         final DocRef modelRef,
                         final AiChatMessage message,
                         final ElicitationRecord form) {
        final boolean sourceForm = String.valueOf(form.message()).contains(typedInsteadOf);
        if (sourceForm) {
            sourceFormsShown++;
        }
        if (sourceForm && typedAfterId == 0) {
            typedAfterId = message.getId();
            asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                    AskStroomAiConfig.builder().modelRef(modelRef).build(), null, typedMessage, AiAgentMode.AGENT,
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
