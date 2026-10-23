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

import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.mcp.impl.McpTestClient;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_describe.py`: describe_feed through the REAL tools on a real Stroom, a feed
/// onboarded and indexed, its pipelines and documentation promoted, then asked about as a chat client would. The CSV
/// feed from the translation suite (`e2e_translation.onboard`: its hand-written converter and XSLT), indexed on
/// Lucene (`e2e_lucene_indexing.index_stage`), promoted. Then, as the script checks:
///
/// - the feed's overview, asked in another case: its events and indexing pipelines, each with its generated
///   documentation found by tag, and the index;
/// - a field asked about by the user's name for it (userid for UserId), traced from the index doc's row to its
///   event-logging path and the events doc's row, with how to search it;
/// - a field nothing names, and a feed that doesn't exist;
/// - a second events pipeline (a v2 being tried) whose processor filter names the feed only by its doc reference
///   (Feed IS_DOC_REF, no value): found all the same.
///
/// Of `index_stage`, only what this script needs is run (the index, its indexing pipeline, processing, a
/// verification and the documentation); its reprocessing, shard and search-by-search checks are
/// `e2e_lucene_indexing.py`'s own, as is `set_index_fields` (folded into create_index_doc's plan in the port).
@SuppressWarnings("unchecked")
class TestE2eDescribe extends AbstractE2eTest {

    private static final String STAMP = "1";
    private static final String BUILD = "e2e-csv-" + STAMP;
    private static final String FEED = "E2E-CSV-" + STAMP;
    private static final String INDEX_NAME = "E2E-CSV-" + STAMP + "-INDEX-V1";
    @Inject
    private ProcessorFilterService processorFilterService;

    @Override
    boolean enablesDisabledFilters() {
        return true;
    }

    @Test
    void aPromotedFeedIsDescribedFromItsPipelinesDocumentationAndIndex() {
        final CsvFeed csv = onboardCsv();
        final Indexed indexed = indexStage(csv);
        final String folder = "System/E2E Described " + STAMP;
        folder(folder);
        final Map<String, Object> destinations = new LinkedHashMap<>();
        for (final String type : List.of("Feed", "Pipeline", "XSLT", "TextConverter", "Documentation", "Index",
                "Dashboard")) {
            destinations.put(type, folder);
        }
        agreed("promote_build", args("build", BUILD, "destinations", destinations));

        // The feed, asked about: in another case, as a user might type it.
        final Map<String, Object> overview = client.json("describe_feed", args("feed", FEED.toLowerCase()));
        dump("overview", overview);
        final Map<String, Map<String, Object>> kinds = list(overview, "pipelines").stream()
                .collect(Collectors.toMap(pipeline -> (String) pipeline.get("kind"), pipeline -> pipeline,
                        (first, second) -> first, LinkedHashMap::new));
        assertThat(kinds.keySet()).as("its pipelines").containsExactlyInAnyOrder("events", "indexing");
        kinds.forEach((kind, pipeline) -> {
            final Map<String, Object> doc = (Map<String, Object>) pipeline.getOrDefault("documentation", Map.of());
            assertThat(doc.get("generated")).as("the %s pipeline's generated documentation, found by tag", kind)
                    .isEqualTo(true);
            assertThat(String.valueOf(doc.get("link"))).as("with its link").contains("action=open-doc");
        });
        assertThat(list(overview, "purpose")).as("the overview carries the events doc's Purpose and data")
                .anyMatch(purpose -> String.valueOf(purpose.get("text")).contains("translation e2e test"));
        final Map<String, Object> index = list(overview, "indexes").getFirst();
        assertThat(index).as("the index it is indexed into")
                .containsEntry("name", INDEX_NAME)
                .containsEntry("backend", "lucene");
        assertThat(number(index.get("fields"))).isGreaterThanOrEqualTo(5);

        // A field, by the user's name for it.
        final String userField = indexed.fields().stream()
                .filter(field -> "EventSource/User/Id".equals(field.get("source")))
                .map(field -> (String) field.get("name"))
                .findFirst()
                .orElseThrow();
        final Map<String, Object> about = map(client.json("describe_feed", args("feed", FEED,
                "field", userField.toLowerCase())), "field");
        dump("about " + userField, about);
        final Map<String, Object> hit = list(about, "in_indexes").getFirst();
        assertThat(hit).as("%s in the index, with how to search it", userField).containsEntry("field", userField);
        final List<String> advice = strings(hit, "how_to_search");
        assertThat(advice).isNotEmpty();
        assertThat(strings(about, "event_logging_paths")).as("traced to its event-logging path")
                .contains("EventSource/User/Id");
        final Set<String> docs = list(about, "documented").stream()
                .map(row -> String.valueOf(row.get("doc")))
                .collect(Collectors.toSet());
        assertThat(docs).as("its rows in both docs, the index's and the events pipeline's")
                .contains(csv.pipelineName(), indexed.pipelineName());
        assertThat(String.join(" ", advice))
                .as("a wildcard for ends-with, as Stroom finds nothing with ENDS_WITH on Lucene")
                .contains("EQUALS '*x'")
                .as("and not case-sensitive, as the index field says")
                .contains("not case-sensitive");

        // A field nothing names, and a feed that does not exist.
        final Map<String, Object> missing = client.json("describe_feed", args("feed", FEED, "field", "User.Nothing"));
        assertThat(map(missing, "field")).as("not found").containsEntry("found", false);
        assertThat(list(missing, "indexes").getFirst()).as("with the index's fields named like it")
                .containsKey("similar_fields");
        assertThat(McpTestClient.text(plainClient.call("describe_feed", args("feed", FEED + "-NOPE"))))
                .as("an unknown feed, with feeds named like it")
                .contains("No feed named", FEED);

        // A pipeline reading the feed through a filter naming its doc (IS_DOC_REF).
        final Map<String, Object> template = list(client.json("find_pipeline_templates", args("stage",
                "translation")), "candidates").stream()
                .filter(candidate -> "Event Data (Text)".equals(candidate.get("name")))
                .findFirst()
                .orElseThrow();
        final Map<String, Object> trial = agreed("create_pipeline", args("build", BUILD + "-v2",
                "name", FEED + "-Events-V2", "template_uuid", template.get("uuid"), "reuse_existing_docs", true,
                "set_properties", List.of(
                        args("element", "dsParser", "name", "textConverter", "doc_uuid", csv.converterUuid(),
                                "doc_type", TextConverterDoc.TYPE),
                        args("element", "translationFilter", "name", "xslt", "doc_uuid", csv.xsltUuid(),
                                "doc_type", XsltDoc.TYPE))));
        final String trialUuid = (String) trial.get("uuid");
        final DocRef feedDoc = new DocRef(FeedDoc.TYPE, csv.feedUuid(), FEED);
        asUser(admin, () -> processorFilterService.create(CreateProcessFilterRequest.builder()
                .processorType(ProcessorType.PIPELINE)
                .pipeline(new DocRef(PipelineDoc.TYPE, trialUuid))
                .queryData(QueryData.builder()
                        .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                        .expression(ExpressionOperator.builder()
                                .addTerm(ExpressionTerm.builder()
                                        .field(MetaFields.FEED.getFldName())
                                        .condition(Condition.IS_DOC_REF)
                                        .docRef(feedDoc)
                                        .build())
                                .addTerm(ExpressionTerm.builder()
                                        .field(MetaFields.TYPE.getFldName())
                                        .condition(Condition.EQUALS)
                                        .value("Raw Events")
                                        .build())
                                .build())
                        .build())
                .enabled(false)
                .priority(10)
                .maxProcessingTasks(1)
                .build()));
        final Map<String, Object> again = client.json("describe_feed", args("feed", FEED));
        assertThat(list(again, "pipelines")).as("found through its doc reference")
                .anyMatch(pipeline -> trialUuid.equals(pipeline.get("uuid")) && "events".equals(pipeline.get("kind")));
    }

    // The translation suite's CSV case (e2e_translation.onboard): its converter and XSLT written by hand, stepped,
    // processed, validated and documented.
    private CsvFeed onboardCsv() {
        final Map<String, Object> feed = agreed("create_feed", args("build", BUILD, "name", FEED));
        final long raw = upload(FEED, CSV_SAMPLE);
        final Map<String, Object> template = list(client.json("find_pipeline_templates", args("stage",
                "translation")), "candidates").stream()
                .filter(candidate -> "Event Data (Text)".equals(candidate.get("name")))
                .findFirst()
                .orElseThrow();
        final Map<String, Object> converter = agreed("save_text_converter", args("build", BUILD, "name", FEED,
                "converter_type", "DATA_SPLITTER", "code", CSV_SPLITTER));
        final Map<String, Object> xslt = agreed("save_xslt", args("build", BUILD, "name", FEED + "-Events",
                "code", CSV_XSLT));
        final String pipelineName = FEED + "-Events";
        final Map<String, Object> pipeline = agreed("create_pipeline", args("build", BUILD, "name", pipelineName,
                "template_uuid", template.get("uuid"), "set_properties", List.of(
                        args("element", "dsParser", "name", "textConverter", "doc_uuid", converter.get("uuid"),
                                "doc_type", TextConverterDoc.TYPE),
                        args("element", "translationFilter", "name", "xslt", "doc_uuid", xslt.get("uuid"),
                                "doc_type", XsltDoc.TYPE))));
        final String pipelineUuid = (String) pipeline.get("uuid");
        final Map<String, Object> sample = client.json("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw)));
        assertThat(sample).as("stepped clean: %s", sample.get("groups")).containsEntry("verdict", "clean");
        agreed("create_processor_filter", args("pipeline_uuid", pipelineUuid, "stream_ids", List.of(raw)));
        processAll();
        final Map<String, Object> gate = client.json("wait_for_processing", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw), "timeout_seconds", 10));
        assertThat(gate).as("one Events stream per raw stream").containsEntry("gate", "pass");
        agreed("write_documentation", args("build", BUILD, "pipeline_uuid", pipelineUuid,
                "markdown", "# " + pipelineName + "\n\n## Purpose and data\n\ncsv sample for the translation e2e "
                            + "test.\n\n## Field mapping\n\n| XPath | From |\n| --- | --- |\n"
                            + "| `EventSource/User/Id` | the user field |\n",
                "change", "Created"));
        return new CsvFeed((String) feed.get("uuid"), raw, pipelineUuid, pipelineName,
                (String) converter.get("uuid"), (String) xslt.get("uuid"));
    }

    // Stage 2 on Lucene, as far as describe_feed needs it (e2e_lucene_indexing.index_stage).
    private Indexed indexStage(final CsvFeed csv) {
        final Map<String, Object> processed = client.json("wait_for_processing", args(
                "pipeline_uuid", csv.pipelineUuid(), "stream_ids", List.of(csv.raw()), "timeout_seconds", 10));
        final List<?> events = (List<?>) list(processed, "streams").getFirst().get("events");
        final Map<String, Object> guidance = plainClient.json("get_field_conventions", args());
        assertThat(guidance).as("no convention chosen: the tool asks instead of assuming one")
                .containsEntry("status", "needs_guidance");
        assertThat(map(client.json("get_field_conventions", args("name", "stroom-flat")), "profile"))
                .as("user chose 'stroom-flat'")
                .containsKey("field_map");
        final Map<String, Object> template = list(client.json("find_pipeline_templates", args("stage",
                "indexing")), "candidates").stream()
                .filter(candidate -> "lucene".equals(candidate.get("backend")))
                .findFirst()
                .orElseThrow();
        assertThat(template).as("indexing template").containsEntry("name", "Indexing");
        final Map<String, Object> draft = client.json("draft_index_mapping", args("backend", "lucene",
                "index_name", INDEX_NAME, "convention", "stroom-flat", "events_stream_ids", events));
        assertThat(strings(draft, "problems")).as("field plan has StreamId, EventId and the time field").isEmpty();
        final Map<String, Object> plan = map(draft, "plan");
        final Map<String, Object> index = agreed("create_index_doc", args("build", BUILD, "backend", "lucene",
                "name", INDEX_NAME, "time_field", plan.get("time_field")));
        dump("index", index);
        final List<Map<String, Object>> fields = list(plan, "fields");
        assertThat(strings((Map<String, Object>) index.getOrDefault("fields", Map.of()), "added"))
                .as("index fields added from the session's plan")
                .containsExactlyInAnyOrderElementsOf(fields.stream().map(field -> (String) field.get("name"))
                        .toList());
        final Map<String, Object> xslt = agreed("save_xslt", args("build", BUILD, "name", INDEX_NAME + "-XSLT",
                "index_plan", plan));
        final String pipelineName = INDEX_NAME + " - Indexing";
        final Map<String, Object> pipeline = agreed("create_indexing_pipeline", args("build", BUILD,
                "name", pipelineName, "template_uuid", template.get("uuid"), "xslt_uuid", xslt.get("uuid"),
                "index_uuid", index.get("uuid")));
        final String pipelineUuid = (String) pipeline.get("uuid");
        assertThat(client.json("step_sample", args("pipeline_uuid", pipelineUuid, "stream_ids", events)))
                .as("stepped the events through the indexing pipeline clean")
                .containsEntry("verdict", "clean");
        agreed("create_processor_filter", args("pipeline_uuid", pipelineUuid, "stream_ids", events,
                "source_pipeline_uuid", csv.pipelineUuid()));
        processAll();
        final Map<String, Object> gate = client.json("wait_for_processing", args("pipeline_uuid", pipelineUuid,
                "stream_ids", events, "expect_events", false, "timeout_seconds", 10));
        assertThat(gate).as("indexing finished with no Error stream").containsEntry("gate", "pass");
        final Map<String, Object> verified = agreed("verify_index", args("build", BUILD,
                "index_uuid", index.get("uuid"), "backend", "lucene", "stream_ids", events,
                "expected_documents", 3, "fields", List.of("StreamId", "EventId", "EventTime", "UserId", "HostName"),
                "exact", List.of(Map.of("field", "UserId", "value", "bob"), Map.of("field", "HostName",
                        "value", "ws03")),
                "pipeline_uuid", pipelineUuid));
        assertThat(verified).as("verification searches pass").containsEntry("passed", true);
        agreed("write_documentation", args("build", BUILD, "pipeline_uuid", pipelineUuid,
                "markdown", "# " + pipelineName + "\n\n## Output\n\nLucene index " + INDEX_NAME + ".\n",
                "change", "Created", "stream_ids", events));
        return new Indexed(pipelineName, fields);
    }


    // --------------------------------------------------------------------------------


    /// The CSV feed onboarded by hand-written code.
    ///
    /// @param feedUuid      The feed.
    /// @param raw           Its sample stream.
    /// @param pipelineUuid  Its events pipeline.
    /// @param pipelineName  The pipeline's name.
    /// @param converterUuid Its Data Splitter.
    /// @param xsltUuid      Its translation.
    private record CsvFeed(String feedUuid,
                             long raw,
                             String pipelineUuid,
                             String pipelineName,
                             String converterUuid,
                             String xsltUuid) {

    }

    /// The feed's Events indexed on Lucene.
    ///
    /// @param pipelineName The indexing pipeline's name.
    /// @param fields       The index plan's fields.
    private record Indexed(String pipelineName, List<Map<String, Object>> fields) {

    }
}
