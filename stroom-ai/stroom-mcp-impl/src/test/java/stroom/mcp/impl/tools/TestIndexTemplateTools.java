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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.InstructionRules.ComponentRule;
import stroom.ai.impl.tools.InstructionRules.ComponentRules;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.StroomLinks;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.indexing.Discovery;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.indexing.IndexingHelpers.TemplateTarget;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.indexing.TemplateCheck;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.docref.DocRef;
import stroom.mcp.impl.BundledKnowledge;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;

/// propose_index_template and check_index_template: the pipeline's destination and the documents it writes are
/// given; the pipeline itself, its description and the user's confirmations are the in-memory world's.
class TestIndexTemplateTools {

    private static final String OUTPUT = """
            <?xml version="1.1"?><array xmlns="http://www.w3.org/2005/xpath-functions">
            <map><number key="StreamId">7</number><number key="EventId">1</number><string key="@timestamp">\
            2026-09-28T10:00:00.000Z</string>
            <string key="user.name">alice</string><string key="source.ip">10.0.0.1</string><boolean \
            key="event.outcome">false</boolean>
            <map key="host"><string key="name">ws01</string></map></map>
            <map><number key="StreamId">7</number><number key="EventId">2</number><string key="@timestamp">\
            2026-09-28T10:05:00.000Z</string>
            <string key="user.name">bob</string><string key="source.ip">unknown</string><boolean \
            key="event.outcome">true</boolean>
            <map key="host"><string key="name">ws02</string></map></map></array>""".strip();
    private static final List<Map<String, Object>> DOCS = TemplateCheck.jsonXmlDocuments(OUTPUT);
    private static final String EXAMPLE = """
            PUT _index_template/ecs-keyfob-v1
            {"index_patterns": ["ecs-keyfob-v1*"], "priority": 300, "composed_of": ["ecs-base", "ecs-source"],
             "template": {"settings": {"index": {"lifecycle": {"name": "logs-90d"}, "number_of_shards": 2}},
                          "aliases": {"keyfob": {}},
                          "mappings": {"dynamic": "strict", "date_detection": false,
                                       "properties": {"user": {"properties": {"name": {"type": "keyword", \
            "ignore_above": 256}}},
                                                      "keyfob": {"properties": {"id": {"type": "keyword"}}}}}}}""";
    private static final List<String> COMPONENTS = List.of("""
            PUT _component_template/ecs-base
            {"template": {"mappings": {"properties": {"@timestamp": {"type": "date"}, "host": {"properties": \
            {"name": {"type": "keyword"}}}}}}}""", ToolJson.dumps(Map.of("component_templates", List.of(Map.of(
            "name", "ecs-source", "component_template", Map.of("template", Map.of("mappings", Map.of("properties",
                    Map.of("source", Map.of("properties", Map.of("ip", Map.of("type", "ip"))))))))))));
    private static final DocRef P1 = ProcessingWorld.P1;

    private final ProcessingWorld world = new ProcessingWorld();
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final IndexingMemory memory = new IndexingMemory(securityContext);
    private final IndexPlans plans = Mockito.mock(IndexPlans.class);
    private final IndexingInputs inputs = Mockito.mock(IndexingInputs.class);
    private final BuildIndexes buildIndexes = Mockito.mock(BuildIndexes.class);
    private final TemplatePipelines pipelines = Mockito.mock(TemplatePipelines.class);
    private final IndexExamples examples = new IndexExamples(Mockito.mock(DocumentAccess.class), null, null, null,
            Mockito.mock(WriteGuard.class), Mockito.mock(BuildPlan.class), memory, Mockito.mock(DocumentWrites.class));
    private final TemplateReviews reviews = new TemplateReviews(world.feeds.consent, memory, world.pipelineDocs,
            world.world.guard(), world.agreements);
    private List<Map<String, Object>> documents = DOCS;

    @BeforeEach
    void setUp() {
        user("alice");
        world.pipeline(P1, true, "Indexes Acme.");
        world.elastic(P1.getUuid());
        Mockito.when(plans.given(any(), anyList(), any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(plans.withConvention(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(plans.ecs()).thenReturn(ecs());
        destination("ecs-acme-v2");
        Mockito.when(pipelines.documents(any(), anyString(), anyList(), anyInt(), anyString())).thenAnswer(inv ->
                new TemplatePipelines.Documents(documents, documents.size(), false));
        Mockito.when(pipelines.componentChoices(any(), anyString(), anyList())).thenReturn(
                new TemplatePipelines.ComponentChoices(List.of(), List.of(), List.of()));
    }

    @Test
    void testProposedTemplateTargetsThePipelinesIndexAndLinksToIt() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "whatever", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id")));
        final Map<String, Object> result = propose(args("plan", plan.toJson()));
        // Without the user's example nothing is built to commit: they are asked for it first.
        assertThat(result).containsEntry("agreed", false).containsEntry("needs", "example_template")
                .doesNotContainKey("dev_tools").containsEntry("index", "ecs-acme-v2")
                .containsEntry("cluster", "ES_DEV")
                .containsEntry("pipeline_link", "https://stroom.example/?action=open-doc&docType=Pipeline"
                                                + "&docUuid=p1");
        @SuppressWarnings("unchecked") final Map<String, Object> rendered = (Map<String, Object>) plan
                .withIndexName("ecs-acme-v2").elasticTemplate("ecs-acme-v2", 200L, true, ecs()).get("body");
        assertThat(rendered.get("index_patterns")).isEqualTo(List.of("ecs-acme-v2*"));
        // The plan leaves some written fields unmapped (dynamic false): noted, not blocking.
        assertThat(TemplateCheck.compare(rendered, DOCS, "ecs-acme-v2", ecs()).get("compatible")).isEqualTo(true);
    }

    @Test
    void testCheckReportsChangesAndUncheckedComponentTemplates() {
        final Map<String, Object> properties = new LinkedHashMap<>(good());
        properties.put("source", Map.of("properties", Map.of("ip", Map.of("type", "ip"))));
        final Map<String, Object> body = template(properties);
        body.put("composed_of", List.of("ecs-base"));
        final String text = "PUT _index_template/ecs-acme\n" + ToolJson.dumps(body);
        // Asked for by the user: the fields a template maps are its own and its components' together, so a
        // component not given is asked for before anything is checked.
        final Map<String, Object> asked = check(args("template", text));
        assertThat(asked).containsEntry("status", "needs_components").containsEntry("missing", List.of("ecs-base"))
                .containsEntry("request", "GET _component_template/ecs-base");
        // The user can't get it: checked without it, and said so.
        final Map<String, Object> result = check(args("template", text, "without_components", List.of("ecs-base")));
        assertThat(result).containsEntry("compatible", false).containsEntry("template_name", "ecs-acme");
        assertThat(String.valueOf(((List<?>) result.get("notes")).getFirst()))
                .startsWith("composed_of ['ecs-base'] not given, so not checked");
        assertThat(String.valueOf(result.get("hint"))).contains("Show the user pipeline_changes");
        final ToolResult bad = checkTool().execute(call(CheckIndexTemplateTool.NAME, args("template", "{",
                "pipeline_uuid", "p1", "events_stream_ids", List.of(8))),
                context(UserQuestions.NONE));
        assertThat(bad.isError()).isTrue();
        assertThat(bad.text()).contains("not valid JSON");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTheUserConfirmsTheTemplateOrTheirCorrectionAndItIsKeptWithThePipeline() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "whatever", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
                PlannedField.of("event.outcome", "keyword", "EventDetail/Authenticate/Outcome/Success")));
        documents = DOCS.stream().map(doc -> {
            final Map<String, Object> copy = new LinkedHashMap<>(doc);
            copy.remove("source.ip");
            return copy;
        }).toList();
        final Map<String, Object> given = args("plan", plan.toJson(), "example_template", EXAMPLE,
                "component_templates", COMPONENTS);
        // Shown first: the whole request for the chat, formatted (seen in VS Code: in a form it was one
        // unformatted line, and an invalid template was agreed).
        final Map<String, Object> shown = propose(given);
        assertThat(shown).containsEntry("status", "needs_review");
        assertThat(KeptMapping.readAgreedTemplate(description())).isEmpty();
        assertThat(String.valueOf(shown.get("dev_tools"))).startsWith("PUT _index_template/ecs-acme-v2\n{\n  ");
        assertThat(String.valueOf(shown.get("hint"))).contains("json code block")
                .contains("reviewed='" + shown.get("review_id") + "'");
        // Then confirmed in a short form: lines, not the JSON. Without a form, it can't be.
        final Map<String, Object> reviewedArgs = with(given, "reviewed", shown.get("review_id"));
        assertThat(refused(proposeTool(), ProposeIndexTemplateTool.NAME, reviewedArgs))
                .startsWith("Not done: Use Elasticsearch index template 'ecs-acme-v2'");
        assertThat(KeptMapping.readAgreedTemplate(description())).isEmpty();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final Map<String, Object> agreed = propose(reviewedArgs, user);
        final Map<String, Object> asked = user.forms().getFirst();
        assertThat(asked).containsEntry("status", "needs_confirmation").containsEntry("summary",
                "Use Elasticsearch index template 'ecs-acme-v2' for index 'ecs-acme-v2' (cluster ES_DEV), as shown "
                + "in the chat");
        final Map<String, Object> details = (Map<String, Object>) asked.get("details");
        assertThat(details).containsEntry("index template", "ecs-acme-v2, exactly as shown in the chat (review id "
                                                            + shown.get("review_id") + ")")
                .doesNotContainKey("notes");
        assertThat(String.valueOf(details.get("applies to"))).startsWith("ecs-acme-v2* (priority ");
        assertThat(Integer.parseInt(String.valueOf(details.get("fields mapped here")))).isPositive();
        // Short (seen in testing: every field, note and component made it 2,000 characters, and the client
        // declined it as too long): the notes are beside it in the reply, for the chat.
        assertThat(user.shown().getFirst()).hasSizeLessThan(400);
        assertThat(agreed.get("from_example")).isNotNull();
        assertThat(agreed).containsEntry("agreed", true);
        assertThat(String.valueOf(agreed.get("hint"))).contains("commit to the cluster");
        // As a button for the cluster admin to copy (asked for by the user): the request exactly as agreed.
        final List<Map<String, Object>> actions = (List<Map<String, Object>>) agreed.get("user_actions");
        assertThat(actions).hasSize(1);
        assertThat(actions.getFirst()).containsEntry("action", "elasticsearch_request")
                .containsEntry("request", String.valueOf(agreed.get("dev_tools")).strip())
                .containsEntry("by", "cluster admin");
        assertThat(String.valueOf(actions.getFirst().get("summary"))).contains("'ecs-acme-v2'");
        final Map<String, Object> kept = KeptMapping.readAgreedTemplate(description()).orElseThrow();
        assertThat(kept).containsEntry("name", "ecs-acme-v2").containsEntry("index", "ecs-acme-v2")
                .containsEntry("xslt", XsltCode.digest()).containsEntry("dev_tools", agreed.get("dev_tools"));
        assertThat(description()).startsWith("Indexes Acme.");

        // The agreed template checked again, retyped on one line: already agreed, not reviewed and confirmed again
        // (seen in Ask Stroom AI, once the user said it was committed).
        final String again = "PUT _index_template/ecs-acme-v2 " + ToolJson.dumps(agreed.get("template"));
        final Map<String, Object> checked = check(args("template", again, "component_templates", COMPONENTS));
        assertThat(checked).containsEntry("agreed", true).doesNotContainKey("status");

        // The user corrects it (a higher priority): their version is confirmed and replaces the agreed one.
        final Map<String, Object> corrected = new LinkedHashMap<>((Map<String, Object>) agreed.get("template"));
        corrected.put("priority", 500);
        final String text = "PUT _index_template/ecs-acme-v2\n" + ToolJson.dumps(corrected);
        // Reviewed only as shown: the first template's id, or true, is no review of this one.
        for (final Object reviewed : List.of(true, shown.get("review_id"))) {
            final Map<String, Object> stale = check(args("template", text, "reviewed", reviewed));
            assertThat(stale).containsEntry("status", "needs_review");
            assertThat(stale.get("review_id")).isNotEqualTo(shown.get("review_id"));
        }
        final Map<String, Object> shownAgain = check(args("template", text));
        assertThat(shownAgain).containsEntry("status", "needs_review");
        assertThat(String.valueOf(shownAgain.get("dev_tools"))).contains("\"priority\": 500");
        final BuildToolCalls.FormUser again2 = BuildToolCalls.user(Map.of("value", true));
        check(args("template", text, "reviewed", shownAgain.get("review_id")), again2);
        final Map<String, Object> askedAgain = again2.forms().getFirst();
        assertThat(askedAgain).containsEntry("status", "needs_confirmation");
        assertThat(String.valueOf(((Map<String, Object>) askedAgain.get("details")).get("applies to")))
                .contains("(priority 500)");
        final String devTools = String.valueOf(KeptMapping.readAgreedTemplate(description()).orElseThrow()
                .get("dev_tools"));
        assertThat(((Map<String, Object>) ToolJson.loads(devTools.split("\n", 2)[1])).get("priority"))
                .isEqualTo(500L);
        assertThat(description().split("<!-- stroom-mcp agreed index template: kept by the server", -1))
                .hasSize(2);
    }

    @Test
    void testAnExampleGivenAsJsonRatherThanTextIsReadTheSame() {
        // Seen in testing: an example saved to the workspace and given as "$workspace:..." went in as JSON, an
        // object, which the tool read as Java writes a map ("{index_patterns=[...") and refused as not JSON.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "whatever", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id")));
        final Object asJson = ToolJson.loads(EXAMPLE.split("\n", 2)[1]);

        final Map<String, Object> asText = propose(args("plan", plan.toJson(), "example_template", EXAMPLE,
                "component_templates", COMPONENTS));
        final Map<String, Object> asObject = propose(args("plan", plan.toJson(), "example_template", asJson,
                "component_templates", COMPONENTS));

        assertThat(asText.get("template")).as("built from the example").isNotNull();
        assertThat(asObject).containsEntry("template", asText.get("template"))
                .containsEntry("dev_tools", asText.get("dev_tools"));
    }

    @Test
    void testTheUsersOwnNominationWinsOverAnInstructionToExclude() {
        final AgentInstructions instructions = Mockito.mock(AgentInstructions.class);
        Mockito.when(instructions.componentsRuled(any(), any())).thenReturn(new ComponentRules(
                List.of(new ComponentRule("org-base", "System")),
                List.of(new ComponentRule("legacy", "System"), new ComponentRule("mine", "System"))));
        final TemplatePipelines.ComponentChoices choices = new TemplatePipelines(null, null, instructions)
                .componentChoices(context(UserQuestions.NONE), "p", List.of("mine"));
        assertThat(choices.include()).containsExactly("mine", "org-base");
        assertThat(choices.exclude()).containsExactly("legacy");
        assertThat(choices.notes()).noneMatch(note -> note.contains("'mine'"));
    }

    @Test
    void testAUsersTemplateIsCheckedAgainstTheComponentRules() {
        final Map<String, Object> body = template(good());
        body.put("composed_of", List.of("legacy-base"));
        Mockito.when(pipelines.componentChoices(any(), anyString(), anyList())).thenReturn(
                new TemplatePipelines.ComponentChoices(List.of("org-ilm"), List.of("legacy-base"), List.of()));
        final Map<String, Object> result = check(args("template", "PUT _index_template/ecs-acme\n"
                                                                  + ToolJson.dumps(body),
                "component_templates", List.of("PUT _component_template/legacy-base\n{\"template\": "
                                               + "{\"mappings\": {}}}")));
        assertThat(JsonValues.strings(result.get("notes")))
                .contains("composed_of lacks 'org-ilm', which standing instructions include",
                        "composed_of has 'legacy-base', which standing instructions exclude");
    }

    @Test
    void testComponentTemplatesGivenOnceAreKeptForTheSession() {
        final Map<String, Map<String, Object>> first = examples.givenComponents(
                List.of("PUT _component_template/ecs-base {\"template\": {}}"), List.of("ecs-base"), null);
        final Map<String, Map<String, Object>> again = examples.givenComponents(List.of(),
                List.of("ecs-base", "other"), null);
        assertThat(first).isEqualTo(again).isEqualTo(Map.of("ecs-base", Map.of("template", Map.of())));
        // Only those the template composes.
        assertThat(examples.givenComponents(List.of(), List.of("other"), null)).isEmpty();
    }

    @Test
    void testATemplateShownForReviewNeedntBeSentAgain() {
        // Seen in testing: the agent retyped the template (and its components) for the call after the review, and
        // they came back corrupted.
        final String shown = "PUT _index_template/ecs-acme-v2\n{\"index_patterns\": [\"ecs-acme-v2*\"]}";
        memory.showTemplate(shown);
        assertThat(memory.shownTemplate(Consent.reviewId(shown).toUpperCase())).hasValue(shown);
        assertThat(memory.shownTemplate("abc")).isEmpty();
        // Another user's review isn't theirs.
        user("bob");
        assertThat(memory.shownTemplate(Consent.reviewId(shown))).isEmpty();
    }

    @Test
    void testADiscoveryTemplateWaitsForTheUsersExampleLikeAnyOther() {
        final FieldPlan plan = FieldPlan.forDiscovery("stroom-discovery-acme-v1", Discovery.of("json", "ts"));
        destination("stroom-discovery-acme-v1");
        final Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("StreamId", new TemplateCheck.DocValue("number", "7"));
        doc.put("EventId", new TemplateCheck.DocValue("number", "1"));
        doc.put("@timestamp", new TemplateCheck.DocValue("string", "2026-10-01T09:00:00Z"));
        doc.put("host", new TemplateCheck.DocValue("string", "web01"));
        documents = List.of(doc);
        final Map<String, Object> result = propose(args("plan", plan.toJson()));
        // Without an example there is no template to commit: one applied to the cluster unagreed would still be
        // refused at indexing (seen in VS Code: the user committed it, and indexing was refused again).
        assertThat(result).containsEntry("agreed", false).containsEntry("needs", "example_template")
                .doesNotContainKey("template").doesNotContainKey("dev_tools");
        assertThat(String.valueOf(result.get("hint"))).contains("a sibling discovery index uses")
                .contains("without_example=true");
        // Only when the user says they have none: built from the plan, and they confirm it as shown.
        final Map<String, Object> shown = propose(args("plan", plan.toJson(), "without_example", true));
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final Map<String, Object> asked = propose(args("plan", plan.toJson(), "without_example", true,
                "reviewed", shown.get("review_id")), user);
        assertThat(user.forms()).extracting(form -> form.get("status")).containsExactly("needs_confirmation");
        assertThat(asked).containsEntry("agreed", true)
                .containsEntry("template_name", "stroom-discovery-acme-v1");
        final List<?> notes = (List<?>) asked.get("notes");
        assertThat(notes.getFirst()).isEqualTo("built from the field plan alone: the user has no example index "
                                               + "template");
        // Every sample record has its time: a data stream, as the user's indices are (asked for by the user).
        assertThat(String.valueOf(notes.get(1))).startsWith("a data stream:");
    }

    @Test
    void testATemplateNeverTakesTheExamplesOwnName() {
        assertThatThrownBy(() -> TemplateReviews.refuseTheirName("ecs-sibling-v1", "ecs-sibling-v1",
                "ecs-sibling-v1", List.of()))
                .hasMessageContaining("the example index template's own name")
                .hasMessageContaining("Leave template_name out");
        assertThatThrownBy(() -> TemplateReviews.refuseTheirName("ecs-sibling-v1", null, "ecs-sibling-v1",
                List.of()))
                .hasMessageContaining("The index name is the example's: rename_index");
        assertThatThrownBy(() -> TemplateReviews.refuseTheirName("ecs@mappings", "ecs@mappings", "ecs-sibling-v1",
                List.of("ecs@mappings")))
                .hasMessageContaining("a component template's name");
        TemplateReviews.refuseTheirName("acme-v1", null, "ecs-sibling-v1", List.of("ecs@mappings"));
    }

    @Test
    void testATemplateForAnIndexNameBuiltFromValuesMatchesTheIndicesItMakes() {
        // Seen: 'ecs-windows{_suffix}v1*' as the pattern (matching no index) and the name, its commit button
        // dropped.
        assertThat(IndexingHelpers.templateTarget("acme-v1")).isEqualTo(new TemplateTarget("acme-v1", "acme-v1"));
        assertThat(IndexingHelpers.templateTarget("ecs-windows-{_suffix}-v1"))
                .isEqualTo(new TemplateTarget("ecs-windows-v1", "ecs-windows-*-v1"));
        assertThat(IndexingHelpers.templateTarget("stroom-index-{_year}"))
                .isEqualTo(new TemplateTarget("stroom-index", "stroom-index-*"));
    }

    @Test
    void testTheIndexDocAndThePipelineMustNameTheSameIndex() {
        final String build = ProcessingWorld.BUILD;
        world.world.inBuild(build, ElasticIndexDoc.TYPE, "ACME-ES", "i");
        indexDoc("acme-v1");
        final DocRef ix = new DocRef("Pipeline", "ix", "ACME-Indexing");
        world.pipeline(ix, true, null);
        writes("ix", "acme-test");
        final BuildIndexes indexes = new BuildIndexes(world.world.guard(), world.world.pipelineShapes,
                world.world.documentAccess, world.pipelineDocs, null, Mockito.mock(BuildPlan.class));
        assertThat(indexes.otherIndex(build, "acme-test", "ix")).hasValueSatisfying(said -> assertThat(said)
                .contains("writes to 'acme-test'").contains("'ACME-ES' names 'acme-v1'"));
        assertThat(indexes.otherIndex(build, "acme-v1", "ix")).isEmpty();
        // A second index of the build, its doc to come (e2e_elastic_handover): the doc there is another
        // pipeline's.
        world.pipeline(new DocRef("Pipeline", "ix2", "ACME-Flat"), true, null);
        writes("ix", "acme-v1");
        writes("ix2", "acme-v1");
        assertThat(indexes.otherIndex(build, "acme-flat-v1", "ix2")).isEmpty();
        // Outside a build: nothing to compare.
        assertThat(indexes.otherIndex(null, "acme-test", null)).isEmpty();
        // A doc naming a pattern takes each index under it; a name built from values, each it makes.
        writes("ix", "acme-test");
        writes("ix2", "acme-test");
        indexDoc("acme-*");
        assertThat(indexes.otherIndex(build, "acme-v1", null)).isEmpty();
        indexDoc("acme-2026");
        assertThat(indexes.otherIndex(build, "acme-{_year}", null)).isEmpty();
    }

    private void indexDoc(final String indexName) {
        world.world.docs.put("i", ElasticIndexDoc.builder().uuid("i").name("ACME-ES").version("v0")
                .indexName(indexName).build());
    }

    private void writes(final String uuid, final String indexName) {
        world.layers(uuid, world.layer(uuid, "xsltFilter", "XSLTFilter", "elasticIndexingFilter",
                "ElasticIndexingFilter").property("elasticIndexingFilter", "indexName", indexName));
    }

    private void destination(final String index) {
        final Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("index name", index);
        destination.put("cluster", "ES_DEV");
        Mockito.when(pipelines.destination(anyString())).thenReturn(destination);
    }

    private void user(final String uuid) {
        Mockito.when(securityContext.getUserRef()).thenReturn(UserRef.builder().uuid(uuid).build());
    }

    private String description() {
        return world.pipelineDoc(P1.getUuid()).getDescription();
    }

    private Map<String, Object> propose(final Map<String, Object> arguments) {
        return run(proposeTool(), ProposeIndexTemplateTool.NAME, with(arguments, "pipeline_uuid", "p1"),
                UserQuestions.NONE);
    }

    private Map<String, Object> propose(final Map<String, Object> arguments, final UserQuestions user) {
        return run(proposeTool(), ProposeIndexTemplateTool.NAME, with(arguments, "pipeline_uuid", "p1"), user);
    }

    private Map<String, Object> check(final Map<String, Object> arguments) {
        return check(arguments, UserQuestions.NONE);
    }

    private Map<String, Object> check(final Map<String, Object> arguments, final UserQuestions user) {
        return run(checkTool(), CheckIndexTemplateTool.NAME, with(arguments, "pipeline_uuid", "p1"), user);
    }

    private Map<String, Object> run(final stroom.ai.impl.tools.StroomTool tool,
                                    final String name,
                                    final Map<String, Object> arguments,
                                    final UserQuestions user) {
        final Map<String, Object> withStreams = with(arguments, "events_stream_ids", List.of(8));
        final ToolResult result = tool.execute(call(name, withStreams), context(user));
        assertThat(result.isError()).as(result.text()).isFalse();
        return BuildToolCalls.json(result.text());
    }

    // The refusal of a call without forms.
    private String refused(final stroom.ai.impl.tools.StroomTool tool,
                           final String name,
                           final Map<String, Object> arguments) {
        final ToolResult result = tool.execute(call(name, with(with(arguments, "pipeline_uuid", "p1"),
                "events_stream_ids", List.of(8))), context(UserQuestions.NONE));
        assertThat(result.isError()).as(result.text()).isTrue();
        return result.text();
    }

    private ProposeIndexTemplateTool proposeTool() {
        return new ProposeIndexTemplateTool(plans, examples, inputs, buildIndexes, pipelines, reviews);
    }

    private CheckIndexTemplateTool checkTool() {
        return new CheckIndexTemplateTool(plans, examples, inputs, buildIndexes, pipelines, reviews, memory);
    }

    private static ToolCall call(final String name, final Map<String, Object> arguments) {
        return BuildToolCalls.call(name, JsonUtil.writeValueAsString(arguments, false));
    }

    private static ToolCallContext context(final UserQuestions user) {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-session-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                .userQuestions(user)
                .links(StroomLinks.absolute("https://stroom.example"))
                .build();
    }

    private static Map<String, Object> args(final Object... pairs) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            arguments.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return arguments;
    }

    private static Map<String, Object> with(final Map<String, Object> arguments, final Object... pairs) {
        final Map<String, Object> out = new LinkedHashMap<>(arguments);
        out.putAll(args(pairs));
        return out;
    }

    private static Map<String, Object> good() {
        final Map<String, Object> good = new LinkedHashMap<>();
        good.put("StreamId", Map.of("type", "long"));
        good.put("EventId", Map.of("type", "long"));
        good.put("@timestamp", Map.of("type", "date"));
        good.put("user", Map.of("properties", Map.of("name", Map.of("type", "keyword"))));
        good.put("source", Map.of("properties", Map.of("ip", Map.of("type", "keyword"))));
        good.put("event", Map.of("properties", Map.of("outcome", Map.of("type", "boolean"))));
        good.put("host", Map.of("properties", Map.of("name", Map.of("type", "keyword"))));
        return good;
    }

    private static Map<String, Object> template(final Map<String, Object> properties) {
        final Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("dynamic", false);
        mappings.put("properties", properties);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("index_patterns", new ArrayList<>(List.of("ecs-acme-v2*")));
        body.put("template", Map.of("mappings", mappings));
        return body;
    }

    private static EcsSchema ecs() {
        return EcsSchema.from(new BundledKnowledge()).orElseThrow();
    }
}
