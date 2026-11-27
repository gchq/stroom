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
import stroom.ai.shared.AnswerAiElicitationRequest;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.docref.DocRef;
import stroom.index.impl.IndexStore;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.test.AbstractAiIntegrationTest;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// Ask Stroom AI for a timeline with no sources named, end to end on a real Elasticsearch, with only the chat model
/// replaced by a scripted model server ([FakeModelServer]): of three Elastic Indexes, one holding documents in the
/// period, one whose index is empty and one whose index name stands for nothing on the cluster, and a Lucene index
/// with no shards, the "Which data sources?" form and the confirming form offer only the one holding documents.
///
/// Seen in user testing (2026-11-27): the forms offered data sources holding no data at all.
///
/// Skipped without an Elasticsearch node ([ElasticTestNode]) unless `STROOM_TEST_ELASTIC_REQUIRED=true`.
class TestAskStroomAiEmptySources extends AbstractAiIntegrationTest {

    private static final String SOURCES_FORM = "Which data sources should the timeline read?";
    private static final String CONFIRM_FORM = "Which of these?";

    @Inject
    private AskStroomAIService askStroomAIService;
    @Inject
    private AiService aiService;
    @Inject
    private OpenAIModelStore modelStore;
    @Inject
    private AiChatRunManager runManager;
    @Inject
    private ElasticClusterStore elasticClusterStore;
    @Inject
    private ElasticIndexStore elasticIndexStore;
    @Inject
    private IndexStore indexStore;

    private FakeModelServer model;
    private UserRef user;
    private ElasticTestNode node;
    private String heldName;
    private String emptyName;
    // The forms shown, by their message, with the options of their first field.
    private final Map<String, List<String>> forms = new LinkedHashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        node = ElasticTestNode.getOrSkip();
        model = FakeModelServer.start();
        user = administrator();
        toolPolicy("Everything", AiToolAccess.READ_WRITE, user);
        heldName = ElasticTestNode.createIndexName("empty-sources-held");
        emptyName = ElasticTestNode.createIndexName("empty-sources-empty");
    }

    @AfterEach
    void tearDown() {
        if (model != null) {
            model.close();
        }
        if (node != null) {
            if (heldName != null) {
                node.deleteIndices(heldName);
            }
            if (emptyName != null) {
                node.deleteIndices(emptyName);
            }
        }
    }

    @Test
    void theTimelinesSourceFormsOfferOnlyTheIndexHoldingDocumentsInThePeriod() {
        createIndex(heldName);
        final ArrayNode documents = JsonUtil.getMapper().createArrayNode();
        final ObjectNode document = documents.addObject();
        document.put("_id", "1");
        document.put("EventTime", "2026-11-10T09:00:00Z");
        document.put("UserName", "jsmith");
        node.bulkIndex(heldName, documents);
        createIndex(emptyName);
        final String missingName = ElasticTestNode.createIndexName("empty-sources-missing");
        asUser(user, () -> {
            final DocRef cluster = elasticClusterStore.createDocument("Empty sources cluster " + heldName);
            final ElasticConnectionConfig connection = new ElasticConnectionConfig();
            connection.setConnectionUrls(List.of(node.getUrl()));
            elasticClusterStore.writeDocument(elasticClusterStore.readDocument(cluster).copy()
                    .connection(connection)
                    .build());
            elasticIndex("Held events", cluster, heldName);
            elasticIndex("Empty events", cluster, emptyName);
            elasticIndex("Missing events", cluster, missingName);
            indexStore.createDocument("Lucene events");
        });
        final DocRef modelRef = asUser(user, this::fakeModel);
        model.callTool("activate_timelines", "{}")
                .callTool("activity_timeline", JsonUtil.writeValueAsString(Map.of("entity", "jsmith",
                        "timeFrom", "2026-11-09T00:00:00Z", "timeTo", "2026-11-14T00:00:00Z", "timeZone", "UTC"),
                        false))
                .answer("Done.");

        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                AskStroomAiConfig.builder().modelRef(modelRef).build(), null,
                "What was jsmith doing last week?", AiAgentMode.AGENT)));
        asUser(user, () -> answered(chat));

        final String sourcesForm = forms.keySet().stream().filter(message -> message.contains(SOURCES_FORM))
                .findFirst().orElseThrow(() -> new AssertionError("No sources form: " + forms.keySet()));
        assertThat(sourcesForm).contains("Supported: the 1 Elastic Index(es) you can use holding documents in the "
                                         + "period.")
                .contains("Left out, holding none: Empty events, Missing events.")
                .doesNotContain("Lucene events");
        final String confirmForm = forms.keySet().stream().filter(message -> message.contains(CONFIRM_FORM))
                .findFirst().orElseThrow(() -> new AssertionError("No confirming form: " + forms.keySet()));
        assertThat(forms.get(confirmForm)).containsExactly("Held events");
    }

    private void createIndex(final String name) {
        node.createIndex(name, """
                {"settings": {"number_of_shards": 1, "number_of_replicas": 0},
                 "mappings": {"properties": {
                  "EventTime": {"type": "date"},
                  "UserName": {"type": "keyword"}}}}""");
    }

    private void elasticIndex(final String name, final DocRef cluster, final String indexName) {
        final DocRef index = elasticIndexStore.createDocument(name);
        elasticIndexStore.writeDocument(elasticIndexStore.readDocument(index).copy()
                .clusterRef(cluster)
                .indexName(indexName)
                .timeField("EventTime")
                .build());
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

    // The chat's messages once the run has ended, each form recorded and answered: the sources form with the
    // user's own words (matching no index, so the confirming form lists every one offered), the confirming form
    // declined, so nothing more is read.
    private List<AiChatMessage> answered(final AiChat chat) {
        final int chatId = chat.getId();
        final Instant until = Instant.now().plus(Duration.ofMinutes(3));
        while (Instant.now().isBefore(until)) {
            final List<AiChatMessage> messages = aiService.getMessages(chatId);
            for (final AiChatMessage message : messages) {
                if (message.getMessageType() != AiMessageType.ELICITATION) {
                    continue;
                }
                final ElicitationRecord form = JsonUtil.readValue(message.getMessage(), ElicitationRecord.class);
                if (form.status() != ElicitationRecord.Status.PENDING) {
                    continue;
                }
                final String text = String.valueOf(form.message());
                forms.put(text, options(form.schema()));
                if (text.contains(SOURCES_FORM)) {
                    askStroomAIService.answerElicitation(new AnswerAiElicitationRequest(chat, message.getId(),
                            "ACCEPT", Map.of("sources", "Other (say which)", "other", "zzqx")));
                } else {
                    askStroomAIService.answerElicitation(new AnswerAiElicitationRequest(chat, message.getId(),
                            "DECLINE", Map.of()));
                }
            }
            final Optional<AiChatMessage> last = messages.stream()
                    .filter(message -> message.getMessageType() == AiMessageType.AI_RESPONSE
                                       || message.getMessageType() == AiMessageType.ERROR)
                    .findFirst();
            if (last.isPresent() && !runManager.isRunningHere(chatId)) {
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

    // The options of a form's first field: its enum, or its items' enum.
    @SuppressWarnings("unchecked")
    private static List<String> options(final Map<String, Object> schema) {
        final Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null || properties.isEmpty()) {
            return List.of();
        }
        final Map<String, Object> field = (Map<String, Object>) properties.values().iterator().next();
        final Object options = field.get("enum") != null
                ? field.get("enum")
                : field.get("items") instanceof final Map<?, ?> items
                        ? items.get("enum")
                        : null;
        return options instanceof final List<?> list
                ? new ArrayList<>(list.stream().map(String::valueOf).toList())
                : List.of();
    }
}
