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

package stroom.mcp.impl.e2e;

import stroom.ai.impl.tools.AgentKnowledge;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.docref.DocRef;
import stroom.documentation.impl.DocumentationStore;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.mcp.impl.McpTestClient;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.xslt.XsltStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.util.json.JsonUtil;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_elastic_handover.py` in its default mode: the Elasticsearch template hand-over
/// on a real Stroom without Elasticsearch, driving the REAL tools. As the script does, a fixture indexing template
/// (the content pack's `Indexing` with an ElasticIndexingFilter in place of the Lucene one, and a JSON schema filter
/// before it, as the live one has) and an Elastic Cluster doc pointing nowhere are added; the documents come from
/// stepping the indexing XSLT, so no Elasticsearch server is needed.
///
/// Covered: part 1 (stage 1 on the CSV sample, `e2e_translation.py`'s `onboard`), part 2 (an ES indexing pipeline
/// from the fixture template, the template proposal self-checked against the documents the pipeline writes; with
/// the script's index field documentation), part 3 (the user's changed template: not compatible, with the pipeline
/// changes it needs), part 4 (indexing refused until a template is agreed; the plan waits for it; a standalone
/// example; the components of the example, the user's and their standing instructions'; the user's example with a
/// component template, confirmed; their correction, confirmed instead), part 5 (the approval of the sample's filter
/// says the template must be committed; declined here, as there is no Elasticsearch, so nothing runs), and what the
/// script does after it: an ECS field added by hand kept through the agent's next change, a field edited by hand
/// that the change changes too (the user decides), a key renamed by hand, and a draft replacing the index saying what
/// the old one wrote.
///
/// Left out: `--live` (`live` and `live_structure`), which applies templates to Elasticsearch 9, indexes into it and
/// searches it; and the script's direct call of the server's internal `indexing._replaced` (no tool), whose case
/// `draft_index_mapping replaces=` covers. Where the script was told `needs_confirmation` or `needs_approval` (its
/// consent store had no forms), Stroom's tools ask in a form, which the user here answers as the script's user
/// does: agreeing, or, for the approval of a filter that would write to an Elasticsearch that isn't there, declining.
@SuppressWarnings("unchecked")
class TestE2eElasticHandover extends AbstractE2eTest {

    // The run stamp the scripts put in every name (`run_stamp()`); the test database is fresh for each run.
    private static final String STAMP = "101";
    private static final String FIXTURE_TEMPLATE = "E2E Events to Elasticsearch";
    private static final String FIXTURE_CLUSTER = "E2E_LOCAL_ES";
    private static final String DECLARED =
            " xsi:schemaLocation=\"http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd\"";
    private static final String CREATED = "<string key=\"created\"><xsl:value-of select=\"EventTime/TimeCreated\" />"
                                          + "</string>";

    @Inject
    private ExplorerService explorerService;
    @Inject
    private ExplorerNodeService explorerNodeService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private XsltStore xsltStore;
    @Inject
    private DocumentationStore documentationStore;
    @Inject
    private AgentKnowledge knowledge;

    @Test
    void anElasticsearchTemplateIsHandedOverWithoutElasticsearch() {
        final DocRef cluster = fixtures();
        final Onboarded csv = onboard(CSV_CASE);
        final Map<String, Object> waited = client.json("wait_for_processing", Map.of(
                "pipeline_uuid", csv.pipelineUuid(), "stream_ids", List.of(csv.raw()), "timeout_seconds", 10));
        final List<Long> events = ((List<Number>) ((List<Map<String, Object>>) waited.get("streams")).getFirst()
                .get("events")).stream().map(Number::longValue).toList();

        // Part 2: the Elasticsearch indexing pipeline.
        final Map<String, Object> esTemplate = templates("indexing").stream()
                .filter(candidate -> FIXTURE_TEMPLATE.equals(candidate.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The fixture template isn't an indexing candidate"));
        assertThat(esTemplate).as("fixture template reads as an Elasticsearch template")
                .containsEntry("backend", "elasticsearch");
        final String index = "e2e-acme-" + STAMP + "-v1";
        // The user is offered their example first, in a form (the script's client had none: it was given the
        // options); the user takes the first, an index template, and nothing is drafted until they paste it.
        int before = client.forms().size();
        final Map<String, Object> offered = client.json("draft_index_mapping", Map.of("backend", "elasticsearch",
                "index_name", index, "convention", "ecs", "events_stream_ids", events));
        dump("draft_index_mapping offered", offered);
        final String offeredForms = formsText(formsSince(before));
        assertThat(offered).as("a convention alone is not drafted: the user is offered their example first")
                .containsEntry("drafted", false);
        assertThat(offeredForms).contains("From an index template");
        assertThat(offeredForms.indexOf("From an index template")).as("the example offered first")
                .isLessThan(offeredForms.indexOf("ECS (Elastic Common Schema) convention"));
        // This user has no example: they say so in the confirmation.
        final Map<String, Object> draft = agreed("draft_index_mapping", Map.of("backend", "elasticsearch",
                "index_name", index, "convention", "ecs", "events_stream_ids", events, "without_example", true));
        final Map<String, Object> plan = map(draft, "plan");
        // As the agent does: the indexing XSLT saved from its plan, which is kept with it for the documentation.
        final String xsltUuid = uuidOf(client.json("save_xslt", Map.of("build", csv.build(), "name", index + "-XSLT",
                "index_plan", plan)));
        final Map<String, Object> pipeline = agreed("create_indexing_pipeline", Map.of("build", csv.build(),
                "name", index + " - Indexing", "template_uuid", esTemplate.get("uuid"), "xslt_uuid", xsltUuid,
                "index_name", index, "cluster_uuid", cluster.getUuid()));
        final String pipelineUuid = uuidOf(pipeline);
        final Map<String, Object> sample = client.json("step_sample", Map.of("pipeline_uuid", pipelineUuid,
                "stream_ids", events));
        dump("step_sample", sample.get("groups"));
        assertThat(sample).as("stepped clean through the template's JSON schema filter (as live)")
                .containsEntry("verdict", "clean");
        // The live template's schema filter (group JSON) needs the output to say which schema it follows.
        final String savedXslt = readXslt(xsltUuid).getData();
        final String undeclared = savedXslt.replace(DECLARED, "");
        final Map<String, Object> bare = client.json("step_sample", Map.of("pipeline_uuid", pipelineUuid,
                "stream_ids", events, "draft_code", Map.of("xsltFilter", undeclared)));
        final String reasons = ((List<Map<String, Object>>) bare.get("groups")).stream()
                .map(group -> String.valueOf(group.get("reason")))
                .collect(Collectors.joining(" "));
        assertThat(undeclared).isNotEqualTo(savedXslt);
        assertThat(bare).as("without its schema declaration, every record fails as on live")
                .containsEntry("verdict", "blocking");
        assertThat(reasons).as("and triage says how to fix the output")
                .contains("xsi:schemaLocation", "Do not change or remove the schema filter");

        // The index field documentation, with the values from the sample.
        final Map<String, Object> doc = agreed("write_documentation", Map.of("build", csv.build(),
                "pipeline_uuid", pipelineUuid,
                "markdown", "## Purpose and data\n\nCSV events indexed into Elasticsearch.\n", "change", "Created",
                "stream_ids", events));
        final String section = String.valueOf(doc.get("field_mapping"));
        assertThat(section).as("the index fields, each with its event-logging path, how often it is populated and its "
                               + "sampled values")
                .contains("| Index field | Description | Type | From (event-logging path) | In sample "
                          + "| Sample values |");
        assertThat(section).as("sampled values from the documents the pipeline writes")
                .contains("Sample values are what the 3 documents written from the sample got", "`alice`", "`bob`");
        final String userField = plannedName(plan, "EventSource/User/Id");
        documentedToTheField(uuidOf(doc), fieldNames(plan), Map.of(userField, "alice"));

        // The proposed template.
        final Map<String, Object> unasked = client.json("propose_index_template", Map.of(
                "pipeline_uuid", pipelineUuid, "plan", plan, "events_stream_ids", events));
        assertThat(unasked).as("without an example nothing is built to commit: the user is asked for theirs")
                .containsEntry("needs", "example_template")
                .doesNotContainKey("dev_tools");
        // This user has none: built from the plan, shown for them to confirm (not confirmed yet).
        final Map<String, Object> proposal = client.json("propose_index_template", Map.of(
                "pipeline_uuid", pipelineUuid, "plan", plan, "events_stream_ids", events, "without_example", true));
        assertThat(proposal).as("built from the plan alone, shown to the user to review first")
                .containsEntry("status", "needs_review");
        final Map<String, Object> proposed = body((String) proposal.get("dev_tools"));
        assertThat((List<Object>) proposed.get("index_patterns")).as("template covers the pipeline's index")
                .containsExactly(index + "*");
        assertThat(proposed).as("built from the plan alone: a data stream").containsEntry("data_stream", Map.of());
        final Map<String, Object> selfCheck = map(proposal, "self_check");
        assertThat(selfCheck).as("proposal fits the documents the pipeline writes: %s", selfCheck.get("blocking"))
                .containsEntry("compatible", true);
        assertThat(((Number) selfCheck.get("documents_checked")).intValue()).as("the documents checked")
                .isEqualTo(3);
        // The standard ECS fields left to ecs@mappings, only the rest mapped here.
        final Set<String> mapped = mappedFields(proposed);
        final EcsSchema ecs = EcsSchema.from(knowledge).orElseThrow();
        assertThat((List<Object>) proposed.get("composed_of")).containsExactly("ecs@mappings");
        assertThat(map(map(proposed, "template"), "mappings")).containsEntry("dynamic", true);
        assertThat(((List<Map<String, Object>>) plan.get("fields")).stream()
                .filter(field -> ecs.leftToComponent((String) field.get("name"), (String) field.get("type")))
                .map(field -> (String) field.get("name")))
                .as("ECS fields left to ecs@mappings: only %s mapped here", mapped)
                .noneMatch(mapped::contains);
        assertThat(mapped).contains("StreamId", "EventId");
        assertThat((List<String>) selfCheck.get("notes")).noneMatch(note -> note.contains("not mapped"));

        // Part 3: the user's changed template.
        final Map<String, Object> changed = changeTemplate(proposed);
        final Map<String, Object> check = client.json("check_index_template", Map.of("pipeline_uuid", pipelineUuid,
                "template", "PUT _index_template/" + proposal.get("template_name") + "\n"
                            + JsonUtil.writeValueAsString(changed, false), "events_stream_ids", events));
        final Set<Object> fields = ((List<Map<String, Object>>) check.get("pipeline_changes")).stream()
                .map(change -> change.get("field"))
                .collect(Collectors.toSet());
        assertThat(check).as("not compatible: %s", check.get("blocking")).containsEntry("compatible", false);
        assertThat(fields).as("with the rename and the type change flagged").contains("user.name", "host.name");
        final Map<String, Object> same = client.json("check_index_template", Map.of("pipeline_uuid", pipelineUuid,
                "template", JsonUtil.writeValueAsString(proposed, false), "events_stream_ids", events));
        assertThat(same).as("the unchanged proposal checks as compatible").containsEntry("compatible", true);

        // Part 4: no template agreed yet, indexing is refused.
        final String refused = McpTestClient.text(client.call("create_processor_filter", Map.of(
                "pipeline_uuid", pipelineUuid, "stream_ids", events, "source_pipeline_uuid", csv.pipelineUuid())));
        assertThat(refused).as("refused").startsWith("No Elasticsearch index template has been agreed");
        assertThat(refused).as("the refusal names the call that agrees it, with the example the user gave")
                .contains("propose_index_template pipeline_uuid=" + pipelineUuid, "example_template=");

        // Stepped clean is not indexed: the plan waits for the template (seen in VS Code).
        final Map<String, Object> status = client.json("build_status", Map.of("build", csv.build()));
        final Map<String, Object> state = states(status);
        assertThat(state).as("template and indexing still to do")
                .containsEntry("index_template", "to do")
                .containsEntry("indexed", "to do");
        assertThat(nextStep(status)).as("promotion not next").isNotIn("index_documented", "promoted");
        assertThat((List<String>) status.get("before_promotion")).as("promotion's approval would warn of both")
                .anyMatch(problem -> problem.contains("no Elasticsearch index template agreed"))
                .anyMatch(problem -> problem.contains("has not been indexed and verified"));
        final long startedWait = System.nanoTime();
        final Map<String, Object> waiting = client.json("wait_for_processing", Map.of("pipeline_uuid", pipelineUuid,
                "stream_ids", events, "timeout_seconds", 60, "expect_events", false));
        assertThat((System.nanoTime() - startedWait) / 1_000_000_000L).as("nothing to wait for, said at once")
                .isLessThan(30);
        assertThat(String.valueOf(((List<?>) waiting.get("problems")).getFirst())).as("no processor filter")
                .contains("no processor filter");

        // A standalone example: no component templates.
        final String standalone = "PUT _index_template/ecs-standalone-v1\n" + JsonUtil.writeValueAsString(Map.of(
                "index_patterns", List.of("ecs-standalone-v1*"), "priority", 120,
                "template", Map.of("mappings", ordered("dynamic", "strict", "properties", ordered(
                        "StreamId", Map.of("type", "long"), "EventId", Map.of("type", "long"),
                        "@timestamp", Map.of("type", "date"),
                        "user", Map.of("properties", Map.of("name", Map.of("type", "keyword",
                                "ignore_above", 128))))))), false);
        final Map<String, Object> alone = client.json("propose_index_template", Map.of("pipeline_uuid", pipelineUuid,
                "plan", plan, "events_stream_ids", events, "example_template", standalone));
        final Map<String, Object> built = body((String) alone.get("dev_tools"));
        final Map<String, Object> props = map(map(map(built, "template"), "mappings"), "properties");
        assertThat(alone).as("built with no component templates of the example, none asked for")
                .containsEntry("status", "needs_review");
        assertThat(built).containsEntry("priority", 120);
        assertThat(alone.get("component_templates")).as("none asked for").satisfiesAnyOf(
                components -> assertThat(components).isNull(),
                components -> assertThat((List<?>) components).isEmpty());
        // ecs@mappings only when the user opts for ECS field mapping: this example doesn't reference it, so the
        // template follows the example, strict as it is, and maps every field itself.
        assertThat(built).as("composed of nothing, as the example is").doesNotContainKey("composed_of");
        assertThat(map(map(built, "template"), "mappings")).as("and strict").containsEntry("dynamic", "strict");
        assertThat(props.get("StreamId")).isEqualTo(Map.of("type", "long"));
        assertThat(props.get("@timestamp")).isEqualTo(Map.of("type", "date"));
        assertThat(map(map(props, "user"), "properties").get("name"))
                .isEqualTo(Map.of("type", "keyword", "ignore_above", 128));
        assertThat(map(map(props, "host"), "properties")).as("the plan's other fields mapped here: %s",
                props.keySet()).containsKey("name");

        // The template from the user's example, confirmed by the user.
        final Map<String, Object> siblingUser = Map.of("user", Map.of("properties", Map.of("name",
                Map.of("type", "keyword", "ignore_above", 256))));
        final Map<String, Object> siblingTemplate = ordered("settings", Map.of("index", Map.of("number_of_shards", 2)),
                "aliases", Map.of("sibling", Map.of()),
                "mappings", ordered("dynamic", "strict", "properties", siblingUser));
        final String example = "PUT _index_template/ecs-sibling-v1\n" + JsonUtil.writeValueAsString(ordered(
                "index_patterns", List.of("ecs-sibling-v1*"), "priority", 300, "composed_of", List.of("ecs-base"),
                "template", siblingTemplate), false);
        final String base = "PUT _component_template/ecs-base\n" + JsonUtil.writeValueAsString(Map.of("template",
                Map.of("mappings", Map.of("properties", Map.of("@timestamp", Map.of("type", "date"))))), false);
        componentChoices(csv, pipelineUuid, plan, events, base);
        final Map<String, Object> asked = client.json("propose_index_template", Map.of("pipeline_uuid", pipelineUuid,
                "plan", plan, "events_stream_ids", events, "example_template", example,
                "component_templates", List.of(base)));
        assertThat(asked).as("the template is shown to the user first, in full, to review before they confirm it")
                .containsEntry("status", "needs_review");
        assertThat((String) asked.get("dev_tools")).startsWith("PUT _index_template/" + index + "\n");
        final Map<String, Object> agreedTemplate = agreed("propose_index_template", Map.of(
                "pipeline_uuid", pipelineUuid, "plan", plan, "events_stream_ids", events,
                "example_template", example, "component_templates", List.of(base)));
        dump("agreed template", agreedTemplate);
        final Map<String, Object> agreedBody = JsonUtil.readValue(JsonUtil.writeValueAsString(
                map(agreedTemplate, "template"), false), Map.class);
        assertThat((List<Object>) agreedBody.get("index_patterns")).as("the new index's pattern")
                .containsExactly(index + "*");
        assertThat((List<Object>) agreedBody.get("composed_of")).as("with the example's components")
                .containsExactly("ecs-base");
        assertThat(map(map(map(agreedBody, "template"), "settings"), "index")).as("and settings")
                .containsEntry("number_of_shards", 2);
        assertThat(map(agreedBody, "template")).as("without its aliases").doesNotContainKey("aliases");
        assertThat(map(map(agreedBody, "template"), "mappings")).as("the example's strict kept")
                .containsEntry("dynamic", "strict");
        assertThat(map(map(map(agreedBody, "template"), "mappings"), "properties"))
                .as("fields its components map left to them").doesNotContainKey("@timestamp");
        assertThat(map(agreedTemplate, "self_check")).as("the final template fits the documents")
                .containsEntry("compatible", true);
        final Optional<Map<String, Object>> kept = KeptMapping.readAgreedTemplate(pipelineDoc(pipelineUuid)
                .getDescription());
        assertThat(agreedTemplate).as("agreed").containsEntry("agreed", true);
        assertThat(kept).as("and kept with the pipeline").hasValueSatisfying(template ->
                assertThat(template).containsEntry("dev_tools", agreedTemplate.get("dev_tools")));

        // The user's correction, confirmed instead.
        final Map<String, Object> correctedBody = new LinkedHashMap<>(agreedBody);
        correctedBody.put("priority", 400);
        final Map<String, Object> fixed = agreed("check_index_template", Map.of("pipeline_uuid", pipelineUuid,
                "template", "PUT _index_template/" + index + "\n" + JsonUtil.writeValueAsString(correctedBody, false),
                "events_stream_ids", events, "component_templates", List.of(base)));
        assertThat(fixed).as("the correction is agreed").containsEntry("agreed", true);
        assertThat(String.valueOf(KeptMapping.readAgreedTemplate(pipelineDoc(pipelineUuid).getDescription())
                .orElseThrow().get("dev_tools"))).as("the correction is the agreed template now")
                .contains("\"priority\": 400");
        assertThat(states(client.json("build_status", Map.of("build", csv.build()))))
                .as("agreed; indexing still to do")
                .containsEntry("index_template", "done")
                .containsEntry("indexed", "to do");

        // Part 5: the sample's filter runs once the user approves it, the template committed. No Elasticsearch here:
        // the user doesn't approve, so nothing runs in the background.
        client.answeringWith(McpTestClient::decline);
        before = client.forms().size();
        final String approval = McpTestClient.text(client.call("create_processor_filter", Map.of(
                "pipeline_uuid", pipelineUuid, "stream_ids", events, "source_pipeline_uuid", csv.pipelineUuid())));
        final String approvalForms = formsText(formsSince(before));
        dump("create_processor_filter declined", Map.of("text", approval, "forms", approvalForms));
        client.answeringWith(McpTestClient::agree);
        assertThat(approvalForms).as("approved in a form, once the template is committed")
                .contains("template is committed")
                .contains("the agreed index template '" + index + "' for Elasticsearch index '" + index
                          + "' is committed to cluster " + FIXTURE_CLUSTER);
        final Map<String, Object> processing = client.json("processing_status", Map.of("pipeline_uuid",
                pipelineUuid));
        assertThat((List<?>) processing.get("filters")).as("no filter until the user approves").isEmpty();

        handEdits(csv, index, xsltUuid, pipelineUuid, plan, events);
    }

    // What the script does after part 5: hand edits of the indexing XSLT, and a draft replacing the index.
    private void handEdits(final Onboarded csv,
                           final String index,
                           final String xsltUuid,
                           final String pipelineUuid,
                           final Map<String, Object> plan,
                           final List<Long> events) {
        // An ECS field added by hand in Stroom's editor is kept through the agent's next change.
        final XsltDoc doc = readXslt(xsltUuid);
        final String code = doc.getData().contains("<map key=\"event\">")
                ? doc.getData().replaceFirst("<map key=\"event\">", "<map key=\"event\">" + CREATED)
                : doc.getData().replaceFirst("<xsl:template match=\"Event\">\n    <map>",
                        "<xsl:template match=\"Event\">\n    <map><map key=\"event\">" + CREATED + "</map>");
        assertThat(code).as("the edit made").contains(CREATED);
        saveByHand(doc, code);
        assertThat((List<String>) client.json("build_status", Map.of("build", csv.build())).get("before_promotion"))
                .as("build_status says the XSLT was edited by hand")
                .anyMatch(problem -> problem.contains("edited by hand since the server saved it")
                                     && problem.contains("carry it into the index plan"));
        final String undone = McpTestClient.text(client.call("save_xslt", Map.of("build", csv.build(),
                "name", index + "-XSLT", "index_plan", plan, "uuid", xsltUuid, "change", "Saved again from its plan")));
        assertThat(undone).as("regenerating from the plan without the edit is refused, saying what it would undo")
                .startsWith("Not saved:")
                .contains("no longer writes string created");
        assertThat(readXslt(xsltUuid).getData()).as("the edit is still in Stroom").contains(CREATED);
        final Map<String, Object> carried = withFields(plan, fields -> fields.add(Map.of("name", "event.created",
                "type", "date", "source", "EventTime/TimeCreated")));
        client.json("save_xslt", Map.of("build", csv.build(), "name", index + "-XSLT", "index_plan", carried,
                "uuid", xsltUuid, "change", "event.created, added by hand, carried into the plan"));
        final XsltDoc after = readXslt(xsltUuid);
        final List<Map<String, Object>> keptFields = (List<Map<String, Object>>) KeptMapping.readMapping(
                after.getDescription()).orElseThrow().payload().get("fields");
        assertThat(after.getData()).as("carried into the plan, saved: the field written").contains("key=\"created\"");
        assertThat(keptFields.getLast()).as("and kept in the plan").containsEntry("name", "event.created");
        assertThat(client.json("step_sample", Map.of("pipeline_uuid", pipelineUuid, "stream_ids", events)))
                .as("stepped clean with the carried field").containsEntry("verdict", "clean");
        assertThat((List<String>) client.json("build_status", Map.of("build", csv.build())).get("before_promotion"))
                .as("and no longer reported as edited by hand")
                .noneMatch(problem -> problem.contains("edited by hand since the server saved it"));

        // A field edited by hand that the agent's change changes too: the user decides.
        final XsltDoc edited = readXslt(xsltUuid);
        final int start = edited.getData().indexOf("mode=\"event\">");
        final int end = edited.getData().indexOf("</xsl:template>", start);
        final String mine = edited.getData().substring(0, start)
                            + edited.getData().substring(start, end).replace("select=\"EventTime/TimeCreated\"",
                "select=\"current-dateTime()\"")
                            + edited.getData().substring(end);
        assertThat(mine).as("the field's source changed by hand: when the event was indexed")
                .isNotEqualTo(edited.getData());
        saveByHand(edited, mine);
        final Map<String, Object> outcome = withFields(carried, fields -> fields.replaceAll(field ->
                "event.created".equals(field.get("name"))
                        ? withEntry(field, "source", "EventDetail/*/Outcome/Success")
                        : field));
        // The script's client had no forms: it was told needs_guidance, the question first, then per field, and
        // called again with hand_edit_choices {'*': 'overwrite'}. Here the user is asked in forms, and overwrites.
        client.answeringWith(choosing("Overwrite the XSLT with the change"));
        int before = client.forms().size();
        client.json("save_xslt", Map.of("build", csv.build(), "name", index + "-XSLT", "index_plan", outcome,
                "uuid", xsltUuid, "change", "event.created from the outcome"));
        final String handEditForms = formsText(formsSince(before));
        client.answeringWith(McpTestClient::agree);
        assertThat(handEditForms).as("asked first whether to overwrite the XSLT or decide field by field")
                .contains("Overwrite the XSLT with the change", "event.created");
        final String overwritten = readXslt(xsltUuid).getData();
        assertThat(overwritten).as("the user chose to overwrite: saved over their edit")
                .doesNotContain("current-dateTime()")
                .contains("Outcome/Success");

        // A key renamed by hand is its field still: field by field, the user keeps their edit.
        final XsltDoc renamedByHand = readXslt(xsltUuid);
        saveByHand(renamedByHand, renamedByHand.getData().replaceFirst("<string key=\"created\">",
                "<string key=\"created_at\">"));
        final Map<String, Object> back = withFields(outcome, fields -> fields.replaceAll(field ->
                "event.created".equals(field.get("name"))
                        ? withEntry(field, "source", "EventTime/TimeCreated")
                        : field));
        client.answeringWith(choosing("Decide field by field", "Keep my hand edit"));
        before = client.forms().size();
        final String kept = McpTestClient.text(client.call("save_xslt", Map.of("build", csv.build(),
                "name", index + "-XSLT", "index_plan", back, "uuid", xsltUuid)));
        final List<ElicitFormRequest> keptForms = formsSince(before);
        client.answeringWith(McpTestClient::agree);
        assertThat(formsText(keptForms)).as("the renamed key (created_at) is linked to event.created, and asked "
                                            + "about").contains("field event.created");
        assertThat(kept).as("kept: the change to it left out, the rename to carry into the plan")
                .contains("The user keeps their edit of event.created", "string created_at");
        final Map<String, Object> renamed = withFields(outcome, fields -> fields.replaceAll(field ->
                "event.created".equals(field.get("name"))
                        ? withEntry(field, "name", "event.created_at")
                        : field));
        client.json("save_xslt", Map.of("build", csv.build(), "name", index + "-XSLT", "index_plan", renamed,
                "uuid", xsltUuid, "change", "event.created renamed event.created_at, as the user did by hand"));
        assertThat(readXslt(xsltUuid).getData()).as("the rename carried into the plan: saved, written as "
                                                + "event.created_at")
                .contains("key=\"created_at\"")
                .doesNotContain("key=\"created\"");

        // A draft replacing an index says what the old one wrote that it doesn't (no reading its XSLT).
        final Map<String, Object> fresh = agreed("draft_index_mapping", Map.of("backend", "elasticsearch",
                "index_name", index, "convention", "ecs", "events_stream_ids", events, "without_example", true,
                "replaces", pipelineUuid));
        final Map<String, Object> replaced = map(fresh, "replaced");
        dump("replaced", replaced);
        assertThat(((Number) replaced.get("fields")).intValue()).as("the old index's fields")
                .isGreaterThanOrEqualTo(10);
        assertThat((List<?>) replaced.get("not_in_draft")).as("every field the old index wrote is covered by the "
                                                              + "draft (by its source, renamed or not)").isEmpty();
        final String described = McpTestClient.text(client.call("read_document", Map.of("type", "XSLT",
                "uuid", xsltUuid)));
        assertThat(described).as("the indexing XSLT described by its fields, not its JSON elements")
                .contains("event.created_at");
    }

    // The script's change_template: what a user might send back.
    private static Map<String, Object> changeTemplate(final Map<String, Object> body) {
        final Map<String, Object> changed = JsonUtil.readValue(JsonUtil.writeValueAsString(body, false), Map.class);
        final Map<String, Object> mappings = map(map(changed, "template"), "mappings");
        final Map<String, Object> props = map(mappings, "properties");
        ((Map<String, Object>) ((Map<String, Object>) props.computeIfAbsent("user",
                key -> new LinkedHashMap<>(Map.of("properties", new LinkedHashMap<>()))))
                .computeIfAbsent("properties", key -> new LinkedHashMap<>())).put("id", Map.of("type", "keyword"));
        ((Map<String, Object>) ((Map<String, Object>) props.computeIfAbsent("host",
                key -> new LinkedHashMap<>(Map.of("properties", new LinkedHashMap<>()))))
                .computeIfAbsent("properties", key -> new LinkedHashMap<>())).put("name", Map.of("type", "ip"));
        mappings.put("dynamic", "strict");
        return changed;
    }

    // The script's component_choices: components of the user's choosing, and their AGENTS doc's rules for them.
    private void componentChoices(final Onboarded csv,
                                  final String pipelineUuid,
                                  final Map<String, Object> plan,
                                  final List<Long> events,
                                  final String base) {
        final ExplorerNode folder = child(child(child(explorerNodeService.getRoot(), "MCP Workspace"), csv.build()),
                null);
        final ExplorerNode agents = asUser(admin, () -> explorerService.create(DocumentationDoc.TYPE, "AGENTS",
                folder, PermissionInheritance.DESTINATION));
        asUser(admin, () -> {
            final DocumentationDoc doc = documentationStore.readDocument(agents.getDocRef());
            documentationStore.writeDocument(doc.copy()
                    .data("## Component templates\n- include: org-ilm\n- exclude: legacy-base\n")
                    .build());
        });
        try {
            final Map<String, Object> kind = Map.of("event", Map.of("properties", Map.of("kind",
                    Map.of("type", "keyword"))));
            final String example = "PUT _index_template/ecs-sibling-v1\n" + JsonUtil.writeValueAsString(ordered(
                    "index_patterns", List.of("ecs-sibling-v1*"),
                    "composed_of", List.of("ecs-base", "ecs-unused", "legacy-base"),
                    "template", Map.of("mappings", Map.of("properties", kind))), false);
            final String unused = "PUT _component_template/ecs-unused\n" + JsonUtil.writeValueAsString(Map.of(
                    "template", Map.of("mappings", Map.of("properties", Map.of("zzz_never_written",
                            Map.of("type", "keyword"))))), false);
            final String users = "PUT _component_template/org-users\n" + JsonUtil.writeValueAsString(Map.of(
                    "template", Map.of("mappings", Map.of("properties", Map.of("user", Map.of("properties",
                            Map.of("name", Map.of("type", "keyword"))))))), false);
            final String ilm = "PUT _component_template/org-ilm\n" + JsonUtil.writeValueAsString(Map.of(
                    "template", Map.of("settings", Map.of("index.lifecycle.name", "logs-90d"))), false);
            final Map<String, Object> asked = client.json("propose_index_template", Map.of(
                    "pipeline_uuid", pipelineUuid, "plan", plan, "events_stream_ids", events,
                    "example_template", example, "component_templates", List.of(base),
                    "include_components", List.of("org-users")));
            assertThat(asked).as("every component without its content is asked for first, legacy-base (excluded) "
                                 + "not: %s", asked.get("request"))
                    .containsEntry("status", "needs_components")
                    .containsEntry("missing", List.of("ecs-unused", "org-users", "org-ilm"));
            final Map<String, Object> shown = client.json("propose_index_template", Map.of(
                    "pipeline_uuid", pipelineUuid, "plan", plan, "events_stream_ids", events,
                    "example_template", example, "component_templates", List.of(base, unused, users, ilm),
                    "include_components", List.of("org-users")));
            // Shown for review: the template is its Dev Tools request.
            final Map<String, Object> body = body((String) shown.get("dev_tools"));
            assertThat((List<Object>) body.get("composed_of")).as("composes ecs-base (used), org-users (nominated) "
                                                                  + "and org-ilm (instructed); not ecs-unused "
                                                                  + "(unused) or legacy-base (excluded)")
                    .containsExactly("ecs-base", "org-users", "org-ilm");
            final Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
                    body.getOrDefault("template", Map.of())).getOrDefault("mappings", Map.of()))
                    .getOrDefault("properties", Map.of());
            assertThat(props).as("fields the components map left to them").doesNotContainKeys("user", "@timestamp");
            final List<String> notes = (List<String>) shown.getOrDefault("from_example", List.of());
            assertThat(notes).as("the notes say which standing instructions applied")
                    .anyMatch(note -> note.contains("AGENTS doc") && note.contains("org-ilm"))
                    .anyMatch(note -> note.contains("excluded by standing"));
        } finally {
            asUser(admin, () -> explorerService.delete(List.of(agents)));
        }
    }

    // The script's fixtures: the fixture ES indexing template (beside 'Indexing') and a cluster doc, with the JSON
    // schema filter the live template has (with_json_schema_filter).
    private DocRef fixtures() {
        final Map<String, Object> lucene = templates("indexing").stream()
                .filter(candidate -> "Indexing".equals(candidate.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No 'Indexing' template pipeline"));
        return asUser(admin, () -> {
            final DocRef luceneRef = new DocRef(PipelineDoc.TYPE, (String) lucene.get("uuid"));
            final ExplorerNode destination = explorerNodeService.getParent(luceneRef).orElseThrow();
            final ExplorerNode node = explorerService.create(PipelineDoc.TYPE, FIXTURE_TEMPLATE, destination,
                    PermissionInheritance.DESTINATION);
            final PipelineDoc source = pipelineStore.readDocument(luceneRef);
            final String json = JsonUtil.writeValueAsString(source.getPipelineData(), false)
                    .replace("\"indexingFilter\"", "\"elasticIndexingFilter\"")
                    .replace("\"IndexingFilter\"", "\"ElasticIndexingFilter\"");
            final Map<String, Object> data = JsonUtil.readValue(json, Map.class);
            final List<Map<String, Object>> elements = (List<Map<String, Object>>) ((Map<String, Object>)
                    data.computeIfAbsent("elements", key -> new LinkedHashMap<>()))
                    .computeIfAbsent("add", key -> new ArrayList<>());
            elements.add(Map.of("id", "schemaFilter", "type", "SchemaFilter"));
            ((List<Map<String, Object>>) ((Map<String, Object>) data.computeIfAbsent("properties",
                    key -> new LinkedHashMap<>())).computeIfAbsent("add", key -> new ArrayList<>()))
                    .add(Map.of("element", "schemaFilter", "name", "schemaGroup", "value", Map.of("string", "JSON")));
            final List<Map<String, Object>> links = (List<Map<String, Object>>) ((Map<String, Object>)
                    data.computeIfAbsent("links", key -> new LinkedHashMap<>()))
                    .computeIfAbsent("add", key -> new ArrayList<>());
            for (final Map<String, Object> link : links) {
                if ("xsltFilter".equals(link.get("from")) && "elasticIndexingFilter".equals(link.get("to"))) {
                    link.put("to", "schemaFilter");
                }
            }
            links.add(Map.of("from", "schemaFilter", "to", "elasticIndexingFilter"));
            final PipelineDoc fixture = pipelineStore.readDocument(node.getDocRef());
            pipelineStore.writeDocument(fixture.copy()
                    .description("Fixture for dev/e2e_elastic_handover.py")
                    .pipelineData(JsonUtil.readValue(JsonUtil.writeValueAsString(data, false), PipelineData.class))
                    .build());
            return explorerService.create(ElasticClusterDoc.TYPE, FIXTURE_CLUSTER, destination,
                    PermissionInheritance.DESTINATION).getDocRef();
        });
    }

    private ExplorerNode child(final ExplorerNode parent, final String name) {
        if (name == null) {
            return parent;
        }
        return asUser(admin, () -> explorerNodeService.getNodesByNameAndType(parent, name, "Folder").stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("No folder " + name)));
    }

    private void saveByHand(final XsltDoc doc, final String code) {
        asUser(admin, () -> xsltStore.writeDocument(doc.copy().data(code).build()));
    }

    private PipelineDoc pipelineDoc(final String uuid) {
        return asUser(admin, () -> pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE, uuid)));
    }

    // build_status's steps, by step: their state (as it gives them since its result was made brief; full=true
    // gives the list).
    private static Map<String, Object> states(final Map<String, Object> status) {
        if (status.get("steps") instanceof final Map<?, ?> brief) {
            return (Map<String, Object>) brief;
        }
        final Map<String, Object> states = new LinkedHashMap<>();
        for (final Map<String, Object> step : (List<Map<String, Object>>) status.get("steps")) {
            states.put((String) step.get("step"), step.get("state"));
        }
        return states;
    }

    // A Dev Tools request's body: the JSON after its request line.
    private static Map<String, Object> body(final String devTools) {
        assertThat(devTools).as("a Dev Tools request").isNotNull();
        return JsonUtil.readValue(devTools.split("\n", 2)[1], Map.class);
    }

    // The script's read_mapping_fields: the fields a template maps, dotted.
    private static Set<String> mappedFields(final Map<String, Object> template) {
        final Set<String> names = new TreeSet<>();
        final Object properties = ((Map<String, Object>) ((Map<String, Object>) template.getOrDefault("template",
                Map.of())).getOrDefault("mappings", Map.of())).get("properties");
        collect("", properties, names);
        return names;
    }

    private static void collect(final String prefix, final Object properties, final Set<String> names) {
        if (properties instanceof final Map<?, ?> map) {
            map.forEach((name, spec) -> {
                final String dotted = prefix + name;
                if (spec instanceof final Map<?, ?> field && field.get("properties") != null) {
                    collect(dotted + ".", field.get("properties"), names);
                } else {
                    names.add(dotted);
                }
            });
        }
    }

    private static Map<String, Object> withFields(final Map<String, Object> plan,
                                                  final Consumer<List<Map<String, Object>>> change) {
        final Map<String, Object> copy = new LinkedHashMap<>(plan);
        final List<Map<String, Object>> fields = new ArrayList<>((List<Map<String, Object>>) plan.get("fields"));
        change.accept(fields);
        copy.put("fields", fields);
        return copy;
    }

    private static Map<String, Object> withEntry(final Map<String, Object> map, final String key, final Object value) {
        final Map<String, Object> copy = new LinkedHashMap<>(map);
        copy.put(key, value);
        return copy;
    }

    private static Map<String, Object> ordered(final Object... keysAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static String plannedName(final Map<String, Object> plan, final String source) {
        return ((List<Map<String, Object>>) plan.get("fields")).stream()
                .filter(field -> source.equals(field.get("source")))
                .map(field -> (String) field.get("name"))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> fieldNames(final Map<String, Object> plan) {
        return ((List<Map<String, Object>>) plan.get("fields")).stream()
                .map(field -> (String) field.get("name"))
                .toList();
    }
}
