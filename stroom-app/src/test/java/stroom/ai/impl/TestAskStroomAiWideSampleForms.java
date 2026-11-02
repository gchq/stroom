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
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.xslt.XsltStore;
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
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// Ask Stroom AI building a translation for a wide sample, end to end, with the user answering the build's forms
/// in the chat: a source of ~150 fields, many holding values that stand for none, and a user Id that carries a
/// domain. The build, feed, sample, splitter and draft are made through the real tools; the chat then runs
/// build_translation_xslt through the real chat service, its forms stopping the run and the user's answers
/// carrying it on, with only the chat model replaced by a scripted model server ([FakeModelServer]).
///
/// Seen in testing (a synthetic Netskope sample, ~147 fields): the form about the stand-ins, a question for each,
/// was too large to show, so the call failed before saving; the domain split answered before it was lost, and
/// asked again on every call after, so the user could never get past it.
class TestAskStroomAiWideSampleForms extends AbstractAiIntegrationTest {

    private static final String SOURCE = "Wide gateway";
    private static final String FEED = "WIDE-GATEWAY";
    private static final String CONVERTER = "WIDE-GATEWAY-Converter";
    private static final String EVENTS = "WIDE-GATEWAY-Events";
    private static final int RECORDS = 40;
    // Columns of numbers, each with a value of its own standing for none, more than one form can ask about.
    private static final int COUNT_COLUMNS = 25;
    // Columns of text, which hold no stand-ins.
    private static final int TEXT_COLUMNS = 118;
    // How many records each stand-in is in.
    private static final int STAND_IN_RECORDS = 4;
    // The stand-ins in the sample: one in each column of numbers, and the usual '-' and 'N/A' in two of text.
    private static final List<String> STAND_INS = standIns();
    private static final String DOMAIN_QUESTION = "Split the domain off";
    private static final String STAND_INS_INTRO = "Some fields in the sample hold values that look like stand-ins";

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
    private XsltStore xsltStore;
    @Inject
    private AiChatRunManager runManager;

    private FakeModelServer model;
    private UserRef user;
    private McpTestClient client;
    // The forms the user was shown, in order.
    private final List<ElicitationRecord> forms = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        model = FakeModelServer.start();
        user = administrator();
        // The template pipelines and the event-logging schemas.
        asUser(user, () -> commonTestControl.createRequiredXMLSchemas());
        toolPolicy("Everything", AiToolAccess.READ_WRITE, user);
        client = mcpTestClients.client(user, true);
    }

    @AfterEach
    void tearDown() {
        model.close();
    }

    @Test
    void aWideSamplesFormsAreEachAskedOnceAndTheTranslationIsSaved() {
        final Map<String, Object> build = buildArguments();
        final DocRef modelRef = asUser(user, this::fakeModel);
        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        // Twice: as for a real model, the first call only turns the build tools on.
        final String arguments = JsonUtil.writeValueAsString(build, false);
        model.callTool("build_translation_xslt", arguments)
                .callTool("build_translation_xslt", arguments)
                .answer("The translation is saved.");

        final ToolResultRecord saved = ask(chat, modelRef, "Build the translation from the draft.");

        // The domain is asked about once, and the stand-ins once, in a form that can be shown: the most common
        // one by one, and one more question for the rest.
        assertThat(forms).as("The build said: %s", saved.text())
                .filteredOn(form -> form.message().startsWith(DOMAIN_QUESTION)).hasSize(1);
        assertThat(forms).filteredOn(form -> form.message().startsWith(STAND_INS_INTRO))
                .singleElement()
                .satisfies(form -> {
                    final Map<?, ?> questions = (Map<?, ?>) form.schema().get("properties");
                    assertThat(questions).hasSize(ElicitationSchema.MAX_FIELDS);
                    assertThat(questions.values()).anySatisfy(question -> assertThat(
                            String.valueOf(((Map<?, ?>) question).get("title"))).startsWith("The other "));
                });
        assertThat(asUser(user, () -> aiService.getMessages(chat.getId())))
                .filteredOn(message -> message.getMessageType() == AiMessageType.WARNING)
                .noneSatisfy(warning -> assertThat(warning.getMessage()).contains("couldn't be shown"));

        // Saved, every stand-in left out as the user chose, the one-by-one answers and the answer for the rest.
        assertThat(saved.error()).as(saved.text()).isFalse();
        final XsltDoc xslt = asUser(user, () -> xsltStore.list().stream()
                .filter(docRef -> EVENTS.equals(docRef.getName()))
                .map(xsltStore::readDocument)
                .findFirst()
                .orElseThrow());
        assertThat(STAND_INS).allSatisfy(standIn -> assertThat(xslt.getData()).contains("'" + standIn + "'"));

        // Built again, the user isn't asked anything: their answers are kept with the XSLT.
        final int asked = forms.size();
        model.callTool("build_translation_xslt", arguments)
                .callTool("build_translation_xslt", arguments)
                .answer("Built again.");
        final ToolResultRecord again = ask(chat, modelRef, "Build it again.");
        assertThat(again.error()).as(again.text()).isFalse();
        assertThat(forms).hasSize(asked);
    }

    // The build, feed, sample, converter and draft, made through the real tools, and the arguments of the call
    // that saves the translation, the draft finished as an agent would finish it.
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildArguments() {
        final String sample = sample();
        final Map<String, Object> started = client.json("start_onboarding", Map.of("source_name", SOURCE,
                "samples", List.of(sample)));
        final String buildName = (String) started.get("build");
        client.json("create_feed", Map.of("build", buildName, "name", FEED));
        final long streamId = ((Number) client.json("upload_sample", Map.of("feed", FEED, "sample", sample))
                .get("stream_id")).longValue();
        client.json("build_data_splitter", Map.of("stream_ids", List.of(streamId), "build", buildName,
                "save_as", CONVERTER));
        final Map<String, Object> draft = client.json("draft_translation_mapping",
                Map.of("stream_ids", List.of(streamId)));
        final Map<String, Object> mapping = completed((Map<String, Object>) draft.get("mapping"));
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("build", buildName);
        arguments.put("name", EVENTS);
        arguments.put("mapping", mapping);
        arguments.put("schema_version", draft.get("schema_version"));
        arguments.put("stream_ids", List.of(streamId));
        arguments.put("splitter", draft.get("splitter"));
        return arguments;
    }

    // The draft finished as an agent finishes it: a device for the source, the gateway's actions as the network
    // permits and denies they are, and every other field the common part doesn't place kept as Data of the action
    // (seen: the ~147-field mapping).
    @SuppressWarnings("unchecked")
    private static Map<String, Object> completed(final Map<String, Object> draft) {
        final Map<String, Object> mapping = new LinkedHashMap<>(draft);
        final List<Map<String, Object>> common = new ArrayList<>(
                (List<Map<String, Object>>) draft.getOrDefault("common", List.of()));
        if (common.stream().noneMatch(entry -> String.valueOf(entry.get("path")).startsWith("EventSource/Device/"))) {
            common.add(Map.of("path", "EventSource/Device/HostName", "value", "wide-gw01"));
        }
        mapping.put("common", common);
        final Set<String> placed = new HashSet<>(Set.of("src_ip", "action"));
        common.forEach(entry -> {
            if (entry.get("field") instanceof final String field) {
                placed.add(field);
            }
        });
        final List<String> data = header().stream()
                .filter(field -> !placed.contains(field))
                .toList();
        mapping.put("events", List.of(
                networkRule("allow", "Permit", data),
                networkRule("block", "Deny", data)));
        return mapping;
    }

    private static Map<String, Object> networkRule(final String action,
                                                   final String element,
                                                   final List<String> data) {
        final String at = "EventDetail/Network/" + element;
        final List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(Map.of("path", at + "/Source/Device/IPAddress", "field", "src_ip"));
        data.forEach(field -> fields.add(Map.of("path", at + "/Data", "data_name", field, "field", field)));
        return Map.of("name", action, "when", List.of(Map.of("field", "action", "equals", action)),
                "fields", fields);
    }

    // Asks the question, answering the forms and approvals the run stops for, as the user would in the chat.
    // Returns the result of the question's last build_translation_xslt call.
    private ToolResultRecord ask(final AiChat chat, final DocRef modelRef, final String question) {
        final int answersBefore = answers(chat.getId()).size();
        asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                AskStroomAiConfig.builder().modelRef(modelRef).build(), null, question, AiAgentMode.AGENT, null)));
        final Set<Integer> handled = new HashSet<>();
        final Instant until = Instant.now().plus(Duration.ofMinutes(3));
        while (Instant.now().isBefore(until)) {
            final List<AiChatMessage> messages = asUser(user, () -> {
                // As the user's browser does, which also says the user is there to be asked.
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
                        forms.add(form);
                        asUser(user, () -> askStroomAIService.answerElicitation(new AnswerAiElicitationRequest(chat,
                                message.getId(), "ACCEPT", answersTo(form.schema()))));
                    }
                } else if (message.getMessageType() == AiMessageType.APPROVAL_REQUEST) {
                    handled.add(message.getId());
                    asUser(user, () -> askStroomAIService.answerApproval(new AnswerAiApprovalRequest(chat,
                            message.getId(), true)));
                }
            }
            final List<AiChatMessage> answers = answers(chat.getId());
            if (answers.size() > answersBefore && !runManager.isRunningHere(chat.getId())) {
                assertThat(answers.getLast().getMessageType())
                        .as("The run failed: %s", answers.getLast().getMessage())
                        .isEqualTo(AiMessageType.AI_RESPONSE);
                return messages.reversed().stream()
                        .filter(message -> message.getMessageType() == AiMessageType.TOOL_RESULT)
                        .map(message -> JsonUtil.readValue(message.getMessage(), ToolResultRecord.class))
                        .filter(result -> "build_translation_xslt".equals(result.tool()))
                        .findFirst()
                        .orElseThrow();
            }
            sleep();
        }
        throw new AssertionError("The question was not answered: " + aiService.getMessages(chat.getId()));
    }

    // The chat's answers and errors so far.
    private List<AiChatMessage> answers(final int chatId) {
        return asUser(user, () -> aiService.getMessages(chatId)).stream()
                .filter(message -> message.getMessageType() == AiMessageType.AI_RESPONSE
                                   || message.getMessageType() == AiMessageType.ERROR)
                .toList();
    }

    // The user's answer to a form: as the user in testing, the domain kept and every stand-in left out; yes to a
    // confirmation; else the recommended (first) option.
    private static Map<String, String> answersTo(final Map<String, Object> schema) {
        final Map<String, String> answers = new LinkedHashMap<>();
        ((Map<?, ?>) schema.get("properties")).forEach((name, field) -> {
            final Map<?, ?> definition = (Map<?, ?>) field;
            if ("boolean".equals(definition.get("type"))) {
                answers.put(String.valueOf(name), "true");
            } else if (definition.get("enum") instanceof final List<?> options && !options.isEmpty()) {
                answers.put(String.valueOf(name), options.contains("Keep as is")
                        ? "Keep as is"
                        : options.contains("Leave out")
                                ? "Leave out"
                                : String.valueOf(options.getFirst()));
            }
        });
        return answers;
    }

    // A gateway's log as CSV, ~150 columns: time, user (with a domain), source address and action; columns of
    // numbers, each with a stand-in of its own; and columns of text, two with the usual '-' and 'N/A'.
    private static String sample() {
        final Random random = new Random(11);
        final StringBuilder sb = new StringBuilder(String.join(",", header())).append('\n');
        for (int r = 0; r < RECORDS; r++) {
            final boolean standInRow = r < STAND_IN_RECORDS;
            final List<String> row = new ArrayList<>();
            row.add(String.format("2026-10-30T10:%02d:00", r));
            row.add("CORP\\user" + (r % 7));
            row.add("10.0." + random.nextInt(256) + "." + (1 + random.nextInt(254)));
            row.add(r % 3 == 0
                    ? "block"
                    : "allow");
            row.add(standInRow
                    ? "-"
                    : "https://site" + random.nextInt(1_000) + ".example.com/");
            row.add(standInRow
                    ? "N/A"
                    : "category " + random.nextInt(1_000));
            for (int n = 1; n <= COUNT_COLUMNS; n++) {
                row.add(standInRow
                        ? countStandIn(n)
                        : String.valueOf(random.nextInt(1_000_000)));
            }
            for (int n = 1; n <= TEXT_COLUMNS; n++) {
                row.add("value " + random.nextInt(1_000_000));
            }
            sb.append(String.join(",", row)).append('\n');
        }
        return sb.toString();
    }

    private static List<String> header() {
        final List<String> header = new ArrayList<>(List.of("time", "user", "src_ip", "action", "referrer",
                "category"));
        IntStream.rangeClosed(1, COUNT_COLUMNS).forEach(n -> header.add(String.format("count_%02d", n)));
        IntStream.rangeClosed(1, TEXT_COLUMNS).forEach(n -> header.add(String.format("attr_%03d", n)));
        return header;
    }

    private static String countStandIn(final int column) {
        return String.format("nocount%02d", column);
    }

    private static List<String> standIns() {
        final List<String> standIns = new ArrayList<>(List.of("-", "N/A"));
        IntStream.rangeClosed(1, COUNT_COLUMNS).forEach(n -> standIns.add(countStandIn(n)));
        return List.copyOf(standIns);
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
