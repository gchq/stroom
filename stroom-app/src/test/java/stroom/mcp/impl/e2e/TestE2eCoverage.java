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

import stroom.ai.impl.tools.KeptMapping;
import stroom.data.shared.StreamTypeNames;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_coverage.py`: a kind of event the sample missed, found once the feed is
/// processed; the rule added; only the affected stream processed again; the pipelines that follow reviewed, driving
/// the real tools. Every numbered part is covered: 1 (an events pipeline from a sample of logons, processing the
/// whole feed), 2 (a CEF pipeline to Kafka following it: a KafkaConfig doc and the standard Kafka producer, as the
/// script's local stack has them, with no broker; stepping sends nothing), 3 (the whole feed reviewed: the views the
/// sample missed, in the later stream alone, and the CEF pipeline a follow-on), 4 (the rule added, only that stream
/// reprocessed, the follow-on reviewed against it and against the changed code stepped; the XSLT's pending
/// changes), 5 (health pings kept as Unknown; a larger feed reviewed in batches; a hand edit carried into the
/// mapping, proven on the original samples) and 6 (a Lucene indexing pipeline whose filter names the feed in a
/// Dictionary, found as a follow-on).
@SuppressWarnings("unchecked")
class TestE2eCoverage extends AbstractE2eTest {

    private static final String BUILD = "cov";
    private static final String FEED = "COV";
    private static final String HEADER = "time,user,host,action,resource\n";
    private static final String SAMPLE = HEADER + "2026-10-01T10:00:00,alice,ws01,login,\n"
                                         + "2026-10-01T10:05:00,bob,ws02,login,\n";
    private static final String LATER = HEADER + "2026-10-02T09:00:00,carol,ws03,login,\n"
                                        + "2026-10-02T09:01:00,carol,ws03,view,Payroll\n"
                                        + "2026-10-02T09:02:00,dave,ws04,view,Firewall\n";
    private static final Map<String, Object> MAPPING = Map.of(
            "input", "data_splitter",
            "unmatched", "warn",
            "common", List.of(
                    Map.of("path", "EventTime/TimeCreated", "field", "time", "time_format", "yyyy-MM-dd'T'HH:mm:ss"),
                    Map.of("path", "EventSource/System/Name", "value", "Coverage"),
                    Map.of("path", "EventSource/System/Environment", "value", "Test"),
                    Map.of("path", "EventSource/Generator", "value", "e2e"),
                    Map.of("path", "EventSource/Device/HostName", "field", "host"),
                    Map.of("path", "EventSource/User/Id", "field", "user")),
            "events", List.of(Map.of("name", "login", "when", List.of(Map.of("field", "action", "equals", "login")),
                    "fields", List.of(
                            Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                            Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                            Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user")))));
    private static final Map<String, Object> NOISE_RULE = Map.of("name", "noise",
            "when", List.of(Map.of("field", "action", "equals", "ping")),
            "keep_unknown", true,
            "allow_unknown", "Health pings: there is nothing in them to map",
            "fields", List.of(
                    Map.of("path", "EventDetail/TypeId", "value", "Ping"),
                    Map.of("path", "EventDetail/Unknown/Data", "data_name", "action", "field", "action")));
    private static final List<String> MORE = IntStream.range(0, 5)
            .mapToObj(n -> HEADER + "2026-10-0" + (3 + n) + "T08:00:00,erin,ws0" + n + ",login,\n"
                           + "2026-10-0" + (3 + n) + "T08:01:00,erin,ws0" + n + ",view,Vault\n"
                           + "2026-10-0" + (3 + n) + "T08:02:00,monitor,ws0" + n + ",ping,\n")
            .toList();
    private static final Map<String, Object> VIEW_RULE = Map.of("name", "view",
            "when", List.of(Map.of("field", "action", "equals", "view")),
            "fields", List.of(
                    Map.of("path", "EventDetail/TypeId", "value", "View"),
                    Map.of("path", "EventDetail/View/Resource/Type", "value", "Secret"),
                    Map.of("path", "EventDetail/View/Resource/Name", "field", "resource")));

    @Inject
    private ProcessorFilterService processorFilterService;
    @Inject
    private ProcessorService processorService;

    @Override
    boolean followsNext() {
        return true;
    }

    @Test
    void kindsTheSampleMissed() {
        // ### 1. an events pipeline built from a sample of logons, processing the whole feed
        createFeed(BUILD, FEED);
        final long first = upload(FEED, SAMPLE);
        final long later = upload(FEED, LATER);
        final Map<String, Object> converter = map(agreed("build_data_splitter", args("stream_ids", List.of(first),
                "save_as", FEED + "-CSV", "build", BUILD)), "saved");
        final Map<String, Object> built = agreed("build_translation_xslt", args("mapping", MAPPING, "stream_ids",
                List.of(first), "build", BUILD, "name", FEED + "-Events", "schema_version", VERSION));
        assertThat(built.get("ok")).as("XSLT saved: %s", built.get("problems")).isEqualTo(true);
        final String xslt = (String) map(built, "saved").get("uuid");
        final String pipeline = (String) createPipeline(BUILD, FEED + "-Events",
                templateUuid("translation", "Event Data (Text)"),
                docProperty("dsParser", "textConverter", (String) converter.get("uuid"), "TextConverter"),
                docProperty("translationFilter", "xslt", xslt, "XSLT")).get("uuid");
        assertThat(stepSample(pipeline, List.of(first)).get("verdict")).as("the sample steps clean")
                .isEqualTo("clean");
        final Map<String, Object> done = process(pipeline, List.of(first, later));
        final List<Long> eventsFirst = ids(list(done, "streams").getFirst(), "events");
        assertThat(eventsFirst).as("processed: %s", done.get("streams")).isNotEmpty();

        // ### 2. a CEF pipeline following it, from now on
        final Map<String, Object> saved = agreed("draft_cef_mapping", args("stream_ids", eventsFirst,
                "custom_keys", false, "topic", "arcsight", "build", BUILD, "name", FEED + "-CEF", "header",
                Map.of("vendor", "Acme")));
        dump("draft_cef_mapping", saved);
        assertThat(saved.get("saved")).as("CEF XSLT saved: %s", saved.get("problems")).isNotNull();
        final String kafka = create(KafkaConfigDoc.TYPE, "COV-KAFKA", "System/MCP Workspace/" + BUILD).getUuid();
        final String forwarding = (String) agreed("create_pipeline", args("build", BUILD, "name", FEED + "-CEF",
                "standalone", "kafka", "set_properties", List.of(
                        docProperty("xsltFilter", "xslt", (String) map(saved, "saved").get("uuid"), "XSLT"),
                        docProperty("standardKafkaProducer", "kafkaConfig", kafka, "KafkaConfig")))).get("uuid");
        final Map<String, Object> cefStep = stepSample(forwarding, eventsFirst);
        assertThat(cefStep.get("verdict")).as("the CEF pipeline steps clean (sending nothing): %s",
                said(list(cefStep, "groups"))).isEqualTo("clean");
        final String soon = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        agreed("create_processor_filter", args("pipeline_uuid", forwarding, "feed", FEED, "stream_type", "Events",
                "created_after", soon));
        // Not the script's: design.md (Validation and safety, Write scope) and FINDINGS ("A filter enabled, nothing
        // processed"): a CEF pipeline's filter is created disabled, for the user to enable, and the processor
        // Stroom made for it (which takes its first filter's flag) is enabled, so enabling the filter is enough.
        final List<ProcessorFilter> cefFilters = asUser(admin, () -> processorFilterService.find(
                new DocRef(PipelineDoc.TYPE, forwarding)).getValues());
        assertThat(cefFilters).as("the CEF pipeline's filter").singleElement().satisfies(filter -> {
            assertThat(filter.isEnabled()).as("created disabled, for the user").isFalse();
            assertThat(asUser(admin, () -> processorService.fetchByUuid(filter.getProcessor().getUuid()))
                    .orElseThrow().isEnabled()).as("its processor enabled").isTrue();
        });

        // ### 3. the whole feed reviewed: the views the sample missed, in the later stream alone
        final Map<String, Object> review = json("review_coverage", args("pipeline_uuid", pipeline));
        dump("review_coverage", review);
        final List<Map<String, Object>> missed = list(review, "missed").stream()
                .filter(group -> "no rule matched".equals(group.get("found_as")))
                .toList();
        assertThat(missed).as("one kind missed").hasSize(1);
        assertThat(missed.getFirst()).as("the views, 2 records in the later stream")
                .containsEntry("kind", Map.of("action", "view"))
                .containsEntry("records", 2);
        assertThat(ids(missed.getFirst(), "raw_streams")).containsExactly(later);
        assertThat(ids(review, "affected_raw_stream_ids")).containsExactly(later);
        assertThat(map(review, "progress")).containsEntry("done", true);
        assertThat((String) review.get("tell_user")).startsWith("Found untranslated events");
        assertThat(list(review, "follow_on")).as("the CEF pipeline, a follow-on")
                .anyMatch(follow -> forwarding.equals(follow.get("uuid")) && "cef".equals(follow.get("kind")));

        // ### 4. the rule added, only that stream processed again, the follow-on reviewed against it
        final Map<String, Object> fixed = agreed("build_translation_xslt", args("changes", Map.of("events",
                        List.of(VIEW_RULE)), "uuid", xslt, "stream_ids", List.of(first, later), "change",
                "Rule for view events, found once the whole feed was processed"));
        assertThat(fixed.get("ok")).as("%s", fixed.get("problems")).isEqualTo(true);
        assertThat(strings(fixed, "changes_applied")).containsExactly("rule 'view' added after the others");
        assertThat(stepSample(pipeline, List.of(first, later)).get("verdict")).as("steps clean").isEqualTo("clean");
        final Map<String, Object> redo = agreed("reprocess_streams", args("pipeline_uuid", pipeline, "stream_ids",
                List.of(later)));
        processAll();
        final Map<String, Object> again = json("wait_for_processing", args("pipeline_uuid", pipeline, "stream_ids",
                List.of(later), "filter_id", redo.get("filter_id"), "timeout_seconds", 10));
        final List<Long> newEvents = ids(list(again, "streams").getFirst(), "events");
        assertThat(newEvents).as("reprocessed: %s", again.get("streams")).isNotEmpty();
        final Map<String, Object> after = json("review_coverage", args("pipeline_uuid", pipeline,
                "events_stream_ids", newEvents));
        final Map<String, Object> cefReview = followOn(after, forwarding);
        assertThat(list(cefReview, "changes")).as("CEF changes for View events: %s", cefReview)
                .anyMatch(change -> "View".equals(change.get("event_type")));
        assertThat(strings(cefReview, "missing")).anyMatch(missing -> missing.contains("View events"));
        // A copy in a build can't process production streams: the changed code, stepped, gives the Events instead.
        final Map<String, Object> stepped = json("review_coverage", args("pipeline_uuid", pipeline,
                "changed_pipeline_uuid", pipeline, "raw_stream_ids", List.of(later)));
        assertThat((String) stepped.get("follow_on_events")).as("the follow-on checked against the stepped copy")
                .endsWith("the changed copy gives, stepped");
        assertThat(list(followOn(stepped, forwarding), "changes"))
                .anyMatch(change -> "View".equals(change.get("event_type")));
        final Map<String, Object> pending = KeptMapping.pendingOf(readXslt(xslt).getDescription()).orElseThrow();
        assertThat(list(pending, "entries")).as("the XSLT's changes pending, for one version line at promotion")
                .extracting(entry -> entry.get("change"))
                .containsExactly("Created from its mapping",
                        "Rule for view events, found once the whole feed was processed");
        // The script's 'stroom-mcp' (the server's name): the server is Stroom itself now, as its version history
        // names it.
        assertThat(String.valueOf(list(pending, "entries").getFirst().get("by"))).as("by the server")
                .startsWith("Stroom ");

        keptAndBatched(pipeline, xslt, List.of(first, later));
        indexedThroughADictionary(pipeline, eventsFirst, newEvents);
    }

    // ### 5. pings kept as Unknown; a larger feed reviewed in batches
    private void keptAndBatched(final String pipeline, final String xslt, final List<Long> samples) {
        final Map<String, Object> noisy = agreed("build_translation_xslt", args("changes", Map.of("events",
                List.of(NOISE_RULE)), "uuid", xslt, "stream_ids", samples, "change",
                "Health pings kept as Unknown, as agreed"));
        assertThat(noisy.get("ok")).as("the pings' rule agreed and saved: %s", noisy.get("problems"))
                .isEqualTo(true);
        assertThat(noisy.get("saved")).isNotNull();
        assertThat(stepSample(pipeline, samples).get("verdict")).as("steps clean").isEqualTo("clean");
        final List<Long> more = new ArrayList<>();
        for (final String text : MORE) {
            more.add(upload(FEED, text));
        }
        final Map<String, Object> done = process(pipeline, more);
        assertThat(list(done, "streams")).as("%s more streams processed", more.size())
                .allMatch(stream -> !ids(stream, "events").isEmpty());
        final List<Map<String, Object>> groups = new ArrayList<>();
        int calls = 0;
        Object continueFrom = null;
        while (true) {
            final Map<String, Object> part = json("review_coverage", args("pipeline_uuid", pipeline,
                    "continue_from", continueFrom, "max_streams", 2, "follow_on", false));
            calls++;
            groups.addAll(list(part, "missed"));
            final Map<String, Object> progress = map(part, "progress");
            continueFrom = progress.get("continue_from");
            if (Boolean.TRUE.equals(progress.get("done")) || calls > 10) {
                break;
            }
            assertThat((String) progress.get("note")).as("batch %s: %s", calls, progress)
                    .contains("call again with continue_from");
        }
        final List<Map<String, Object>> kept = groups.stream()
                .filter(group -> "kept as Unknown by rule 'noise'".equals(group.get("found_as")))
                .toList();
        final List<Map<String, Object>> unmatched = groups.stream()
                .filter(group -> "no rule matched".equals(group.get("found_as")))
                .toList();
        assertThat(calls).as("calls of two Error streams").isGreaterThanOrEqualTo(3);
        assertThat(kept.stream().mapToInt(group -> ((Number) group.get("records")).intValue()).sum())
                .as("pings kept as Unknown").isEqualTo(5);
        assertThat(unmatched).as("unmatched kinds").isEmpty();
        final Set<Long> pinged = new TreeSet<>();
        kept.forEach(group -> pinged.addAll(ids(group, "raw_streams")));
        assertThat(pinged).as("one ping in each of the five").isEqualTo(new TreeSet<>(more));
        // A hand edit carried into the mapping, proven on the pipeline's original samples without naming them.
        final String code = readXslt(xslt).getData();
        editXslt(xslt, doc -> doc.copy().data(code.replaceFirst("<TypeId>Ping</TypeId>",
                "<TypeId>HealthPing</TypeId>")).build());
        final Map<String, Object> redone = agreed("rebuild_mapping", args("uuid", xslt));
        dump("rebuild_mapping", redone);
        final Map<String, Object> provenOn = map(redone, "proven_on");
        final List<Long> all = new ArrayList<>(samples);
        all.addAll(more);
        assertThat(redone.get("saved")).as("rebuild_mapping saved it").isNotNull();
        assertThat((String) provenOn.get("which")).contains("original sample streams");
        assertThat(ids(provenOn, "streams")).as("proven on %s", provenOn.get("which")).containsAll(all);
        assertThat(strings(map(redone, "rebuilt"), "entries_new")).containsExactly("rule noise: EventDetail/TypeId");
    }

    // ### 6. an indexing pipeline whose filter names the feed in a Dictionary: a follow-on all the same
    private void indexedThroughADictionary(final String pipeline,
                                           final List<Long> eventsFirst,
                                           final List<Long> newEvents) {
        final String template = list(json("find_pipeline_templates", args("stage", "indexing")), "candidates")
                .stream()
                .filter(candidate -> "lucene".equals(candidate.get("backend")))
                .map(candidate -> (String) candidate.get("uuid"))
                .findFirst()
                .orElseThrow();
        final String indexName = "COV-INDEX";
        final Map<String, Object> drafted = agreed("draft_index_mapping", args("backend", "lucene", "index_name",
                indexName, "convention", "stroom-flat", "events_stream_ids", eventsFirst));
        final Map<String, Object> plan = map(drafted, "plan");
        final Map<String, Object> index = agreed("create_index_doc", args("build", BUILD, "backend", "lucene",
                "name", indexName, "time_field", plan.get("time_field"), "plan", plan));
        final String indexXslt = (String) agreed("save_xslt", args("build", BUILD, "name", indexName + "-XSLT",
                "index_plan", plan)).get("uuid");
        final Map<String, Object> indexer = agreed("create_indexing_pipeline", args("build", BUILD, "name",
                indexName + " - Indexing", "template_uuid", template, "xslt_uuid", indexXslt, "index_uuid",
                index.get("uuid")));
        final Map<String, Object> feeds = agreed("save_dictionary", args("build", BUILD, "name", "COV-Feeds",
                "text", "SOMETHING-ELSE\n" + FEED + "\n"));
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field(MetaFields.FEED)
                        .condition(Condition.IN_DICTIONARY)
                        .docRef(new DocRef(DictionaryDoc.TYPE, (String) feeds.get("uuid"), "COV-Feeds"))
                        .build())
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.EVENTS)
                .build();
        asUser(admin, () -> processorFilterService.create(CreateProcessFilterRequest.builder()
                .processorType(ProcessorType.PIPELINE)
                .pipeline(new DocRef(PipelineDoc.TYPE, (String) indexer.get("uuid")))
                .queryData(QueryData.builder().dataSource(MetaFields.STREAM_STORE_DOC_REF).expression(expression)
                        .build())
                .enabled(false)
                .priority(10)
                .maxProcessingTasks(1)
                .build()));
        final Map<String, Object> review = json("review_coverage", args("pipeline_uuid", pipeline,
                "events_stream_ids", newEvents));
        final Map<String, Object> found = followOn(review, (String) indexer.get("uuid"));
        assertThat(found).as("found through the Dictionary").containsEntry("kind", "index");
        assertThat(strings(found, "not_indexed")).as("its plan takes no View paths")
                .anyMatch(path -> path.contains("View"));
    }

    private static Map<String, Object> followOn(final Map<String, Object> review, final String uuid) {
        return list(review, "follow_on").stream()
                .filter(follow -> uuid.equals(follow.get("uuid")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Not a follow-on: " + uuid + " in " + review.get("follow_on")));
    }
}
