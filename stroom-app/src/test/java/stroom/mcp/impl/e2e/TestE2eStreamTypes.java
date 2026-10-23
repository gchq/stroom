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
import stroom.docref.DocRef;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.xslt.XsltStore;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_stream_types.py`: stream types beyond Raw Events and Events, and templates found
/// by what they are, through the REAL tools on a real Stroom. Templates here are fixtures with names no standard
/// template has, in a folder of their own (System/E2E Bases), with no children: found only by what they are.
///
/// 1. Templates: a JSON translation template ('json-in v3') and a records template ('csv records v1') are found for
///    their stage; start_onboarding names 'json-in v3' for a JSON sample; a source is onboarded through it.
/// 2. Raw Reference: the reference-data template and the loaders are found by what they are; a Raw Reference feed's
///    whole-feed filter takes the feed's stream type; the wait takes the pipeline's output type (Reference); an
///    events pipeline's reference names no loader, and the lookup fills the events. Its mapping lost,
///    rebuild_mapping reads the lookup back from the XSLT as the entry that wrote it, and the events are filled as
///    before. Processed, every event of the Events stream is filled. A pipeline made without the reference finds
///    nothing; update_pipeline attaches it to the translation step, and the lookup fills the event. The events
///    pipeline's next version, its translation written anew: the draft names the current XSLT's lookups, a save
///    without them warns, the plan's pipeline step is create_pipeline version_of=, which asks whether to keep the
///    reference data; the new version is a child of the same template with the previous converter, its lookup
///    filled when they are kept and empty when not.
/// 3. Records, up to indexing: a pipeline writing Records, from 'csv records v1'; its wait takes Records from the
///    pipeline; the Records stream is refused as Events to plan an index from, drafted as XML it is refused for
///    delimited, and drafted as delimited it gives a plan.
///
/// The next version (the end of part 2) is ported but @Disabled: the Java port has none of what it checks.
/// Left out of part 3: the Elasticsearch indexing of the Records (the indexing pipeline on a cluster, its index
/// template applied, the documents counted in Elasticsearch), which needs an Elasticsearch cluster.
@SuppressWarnings("unchecked")
class TestE2eStreamTypes extends AbstractE2eTest {

    private static final String STAMP = "1";
    private static final String BASES = "System/E2E Bases";
    private static final String JSON_TEMPLATE = "json-in v3";
    private static final String RECORDS_TEMPLATE = "csv records v1";
    private static final String RECORDS_DATA = """
            {"elements": {"add": [{"id": "dsParser", "type": "DSParser"}, {"id": "xmlWriter", "type": "XMLWriter"},
                                  {"id": "streamAppender", "type": "StreamAppender"}]},
             "links": {"add": [{"from": "dsParser", "to": "xmlWriter"}, {"from": "xmlWriter", "to": "streamAppender"}]},
             "properties": {"add": [{"element": "streamAppender", "name": "streamType",
                                     "value": {"string": "Records"}}]}}
            """;
    // The format suite's samples (dev/format_samples.py).
    private static final String JSON_LINES = """
            {"ts": "2026-10-01T08:00:00Z", "user": {"name": "alice"}, "src": "10.0.0.5", "event": "login"}
            {"ts": "2026-10-01T08:05:00Z", "user": {"name": "bob"}, "src": "10.0.0.6", "event": "logout"}
            {"ts": "2026-10-01T08:07:00Z", "user": {"name": "carol"}, "src": "10.0.0.7", "event": "login"}
            """;
    private static final String CSV_QUOTED = """
            time,user,src_ip,action,message
            2026-10-01T08:00:00Z,alice,10.0.0.5,login,"Logged in, from the VPN"
            2026-10-01T08:05:00Z,"o'brien, pat",10.0.0.6,logout,"Said ""bye"" and left"
            2026-10-01T08:07:00Z,carol,10.0.0.7,login,Plain message
            """;
    private static final String DIRECTORY = """
            user,name,department,site
            alice,Alice Anderson,Operations,HQ
            bob,Bob Brown,Finance,HQ
            carol,Carol Clark,Engineering,Annex
            """;
    private static final String BADGES = """
            time,badge_user,door,result
            2026-10-01T07:58:00Z,ALICE,Main entrance,granted
            2026-10-01T08:02:00Z,BOB,Server room,denied
            2026-10-01T08:10:00Z,ALICE,Server room,granted
            """;
    private static final String LOOKUP = "lower-case(data[@name='badge_user']/@value)";

    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private XsltStore xsltStore;

    @Override
    boolean enablesDisabledFilters() {
        return true;
    }

    @Test
    void part1TemplatesAreFoundByWhatTheyAreAndASourceIsOnboardedThroughOne() {
        final Map<String, Object> standardJson = list(client.json("find_pipeline_templates",
                args("stage", "translation")), "candidates").stream()
                .filter(candidate -> "JSONParser".equals(candidate.get("parser")))
                .findFirst()
                .orElseThrow();
        final PipelineData jsonData = asUser(admin, () -> pipelineStore.readDocument(
                new DocRef(PipelineDoc.TYPE, (String) standardJson.get("uuid"))).getPipelineData());
        final DocRef mine = fixture(JSON_TEMPLATE, jsonData);
        final DocRef records = fixture(RECORDS_TEMPLATE, JsonUtil.readValue(RECORDS_DATA, PipelineData.class));

        final Map<String, Object> found = list(client.json("find_pipeline_templates", args("stage", "translation")),
                "candidates").stream()
                .filter(candidate -> mine.getUuid().equals(candidate.get("uuid")))
                .findFirst()
                .orElse(Map.of());
        dump("found " + JSON_TEMPLATE, found);
        // Neither configured nor in Stroom's standard folder: what it is (or, after a run, its children) finds it.
        assertThat(found.get("source")).as("'%s' found as a translation template by what it is", JSON_TEMPLATE)
                .isIn("template_like", "inherited_by_others");
        assertThat(found).containsEntry("path", BASES).containsEntry("parser", "JSONParser");
        assertThat(list(client.json("find_pipeline_templates", args("stage", "records")), "candidates"))
                .as("'%s' found as a records template", RECORDS_TEMPLATE)
                .anyMatch(candidate -> records.getUuid().equals(candidate.get("uuid")));

        final String build = "st-json-" + STAMP;
        final String feed = "ST-JSON-" + STAMP;
        final Map<String, Object> started = client.json("start_onboarding", args("source_name", "Acme JSON app",
                "samples", Map.of("app.jsonl", JSON_LINES), "build", build));
        assertThat((String) started.get("template")).as("start_onboarding names the templates there are")
                .contains(JSON_TEMPLATE);
        agreed("create_feed", args("build", build, "name", feed));
        final long raw = upload(feed, JSON_LINES);
        final Map<String, Object> draft = client.json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Acme", "system_name", "Acme", "environment", "Test"));
        final Map<String, Object> saved = agreed("build_translation_xslt", args(
                "mapping", decide(map(draft, "mapping"), "user.name"),
                "stream_ids", List.of(raw), "build", build, "name", feed + "-Events"));
        assertThat(saved).as("saved: %s", saved.get("problems")).containsEntry("ok", true);
        assertThat(saved.get("saved")).isNotNull();
        final Map<String, Object> pipeline = agreed("create_pipeline", args("build", build,
                "name", feed + "-Events", "template_uuid", mine.getUuid()));
        final Map<String, Object> stepped = client.json("step_sample", args(
                "pipeline_uuid", pipeline.get("uuid"), "stream_ids", List.of(raw)));
        dump("step_sample", stepped);
        assertThat(stepped).as("a source onboarded through '%s'", JSON_TEMPLATE).containsEntry("verdict", "clean");
    }

    @Test
    void part2RawReferenceIsLoadedAndLookedUp() {
        referenceData();
    }

    @Test
    @Disabled("Not ported: the next version of a pipeline whose translation is written anew (Python 0.18.43's "
              + "create_pipeline version_of=/keep_references=, draft_translation_mapping's current_translation, "
              + "build_translation_xslt's warning that a mapping keeps none of the current lookups, and the plan's "
              + "create_pipeline version_of= step; tests/test_new_version.py). None of it is in the Java port.")
    void part2TheNextVersionKeepsTheReferenceDataWhenTheUserSays() {
        final Badge badge = referenceData();
        newVersion(badge.eventsUuid(), badge.textUuid(), badge.refFeed(), badge.eventsFeed(), badge.eventsRaw());
    }

    // Part 2 up to the next version: the reference loaded, looked up, its mapping rebuilt, processed, and attached
    // later to a pipeline made without it.
    private Badge referenceData() {
        final Map<String, Object> found = client.json("find_reference_data", args());
        dump("find_reference_data", found);
        final Map<String, Object> template = map(found, "reference_data_template");
        assertThat(list(found, "loaders")).as("the reference-data template and the loaders, by what they are")
                .isNotEmpty();
        final String build = "st-ref-" + STAMP;
        final String feed = "ST-USERS-" + STAMP;
        agreed("create_feed", args("build", build, "name", feed, "stream_type", "Raw Reference"));
        final long raw = number(client.json("upload_sample", args("feed", feed, "sample", DIRECTORY,
                "stream_type", "Raw Reference", "effective_time", "2000-01-01T00:00:00.000Z")).get("stream_id"));
        client.json("build_data_splitter", args("stream_ids", List.of(raw), "save_as", feed, "build", build));
        final Map<String, Object> code = client.json("build_reference_xslt", args("mapping", referenceMapping()));
        assertThat(code).as("reference XSLT: %s", code.get("problems")).containsEntry("ok", true);
        client.json("save_xslt", args("build", build, "name", feed + "-Reference", "code", code.get("xslt")));
        final Map<String, Object> loader = agreed("create_pipeline", args("build", build,
                "name", feed + "-Reference", "template_uuid", template.get("uuid")));
        final String loaderUuid = (String) loader.get("uuid");
        assertThat(client.json("step_sample", args("pipeline_uuid", loaderUuid, "stream_ids", List.of(raw))))
                .as("the reference pipeline steps clean")
                .containsEntry("verdict", "clean");
        final Map<String, Object> started = agreed("create_processor_filter", args("pipeline_uuid", loaderUuid,
                "feed", feed, "created_after", "2000-01-01T00:00:00.000Z"));
        dump("reference filter", started);
        assertThat(String.valueOf(started.get("scope"))).as("the whole-feed filter takes the feed's stream type")
                .contains("(Raw Reference)");
        processAll();
        final Map<String, Object> done = client.json("wait_for_processing", args("pipeline_uuid", loaderUuid,
                "stream_ids", List.of(raw), "timeout_seconds", 10));
        dump("reference wait", done);
        assertThat(done).containsEntry("gate", "pass");
        assertThat(list(done, "streams").getFirst()).as("the wait takes the pipeline's output type")
                .containsEntry("output_type", "Reference");

        final String eventsBuild = "st-badge-" + STAMP;
        final String eventsFeed = "ST-BADGE-" + STAMP;
        agreed("create_feed", args("build", eventsBuild, "name", eventsFeed));
        final long eventsRaw = upload(eventsFeed, BADGES);
        client.json("build_data_splitter", args("stream_ids", List.of(eventsRaw), "save_as", eventsFeed,
                "build", eventsBuild));
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping", badgeMapping(true),
                "stream_ids", List.of(eventsRaw), "build", eventsBuild, "name", eventsFeed + "-Events"));
        assertThat(saved).as("the events mapping with its lookup: %s", saved.get("problems"))
                .containsEntry("ok", true);
        final Map<String, Object> text = list(client.json("find_pipeline_templates", args("stage", "translation")),
                "candidates").stream()
                .filter(candidate -> "DSParser".equals(candidate.get("parser")))
                .findFirst()
                .orElseThrow();
        final Map<String, Object> events = agreed("create_pipeline", args("build", eventsBuild,
                "name", eventsFeed + "-Events", "template_uuid", text.get("uuid"),
                "references", List.of(Map.of("feed", feed))));
        final String eventsUuid = (String) events.get("uuid");
        assertThat(userName(stepOutput(eventsUuid, eventsRaw, 0, "translationFilter")))
                .as("the lookup, through the loader resolved with none named, fills the event")
                .isEqualTo("Alice Anderson");

        // Its mapping lost: the lookup read back from the XSLT.
        final DocRef xsltRef = new DocRef(XsltDoc.TYPE, (String) map(saved, "saved").get("uuid"));
        asUser(admin, () -> xsltStore.writeDocument(xsltStore.readDocument(xsltRef).copy().description("").build()));
        final Map<String, Object> restored = agreed("rebuild_mapping", args("uuid", xsltRef.getUuid()));
        dump("rebuild_mapping", restored);
        final Map<String, Object> back = (Map<String, Object>) KeptMapping.readMapping(asUser(admin, () ->
                        xsltStore.readDocument(xsltRef).getDescription()))
                .orElseThrow()
                .payload()
                .get("mapping");
        assertThat(restored.get("saved")).isNotNull();
        assertThat(restored).doesNotContainKey("differences");
        assertThat(lookups(back)).as("its mapping lost, rebuild_mapping reads the lookup back as written")
                .containsExactly(lookup());
        assertThat(userName(stepOutput(eventsUuid, eventsRaw, 0, "translationFilter")))
                .as("the XSLT saved from the rebuilt mapping fills the event the same")
                .isEqualTo("Alice Anderson");

        // Processed, not only stepped: every event of the Events stream filled by its own lookup.
        final Map<String, Object> sampled = client.json("step_sample", args("pipeline_uuid", eventsUuid,
                "stream_ids", List.of(eventsRaw)));
        assertThat(sampled).as("the events pipeline steps the sample clean: %s", sampled.get("groups"))
                .containsEntry("verdict", "clean");
        agreed("create_processor_filter", args("pipeline_uuid", eventsUuid, "stream_ids", List.of(eventsRaw)));
        processAll();
        final Map<String, Object> processed = client.json("wait_for_processing", args("pipeline_uuid", eventsUuid,
                "stream_ids", List.of(eventsRaw), "timeout_seconds", 10));
        assertThat(processed).as("the events processed").containsEntry("gate", "pass");
        final long eventsId = number(((List<?>) list(processed, "streams").getFirst().get("events")).getFirst());
        final Map<String, Object> read = client.json("fetch_events", args("streamId", eventsId,
                "firstRecord", 0, "recordCount", 10));
        dump("fetch_events", read);
        final List<String> names = ((List<?>) read.get("records")).stream()
                .map(record -> userName(record instanceof final Map<?, ?> fields
                        ? String.valueOf(fields.get("data"))
                        : String.valueOf(record)))
                .toList();
        assertThat(names).as("processing fills each event's lookup, as stepping does")
                .containsExactly("Alice Anderson", "Bob Brown", "Alice Anderson");

        // A pipeline made without the reference, which update_pipeline then attaches (an agent that left it out).
        final Map<String, Object> late = agreed("create_pipeline", args("build", eventsBuild,
                "name", eventsFeed + "-Events-Late", "template_uuid", text.get("uuid")));
        assertThat(strings(late, "still_to_set")).as("the later pipeline takes the build's XSLT").isEmpty();
        final String lateUuid = (String) late.get("uuid");
        assertThat(userName(stepOutput(lateUuid, eventsRaw, 0, "translationFilter")))
                .as("with no reference, the lookup finds nothing")
                .isNull();
        final Map<String, Object> updated = agreed("update_pipeline", args("pipeline_uuid", lateUuid,
                "references", List.of(Map.of("feed", feed))));
        dump("update_pipeline", updated);
        assertThat(strings(updated, "reference_data")).as("update_pipeline attaches the reference to the "
                                                          + "translation step")
                .singleElement()
                .satisfies(reference -> assertThat(reference).contains(feed).endsWith("on translationFilter"));
        assertThat(userName(stepOutput(lateUuid, eventsRaw, 1, "translationFilter")))
                .as("the reference attached later fills the event")
                .isEqualTo("Bob Brown");
        return new Badge(eventsUuid, (String) text.get("uuid"), feed, eventsFeed, eventsRaw);
    }

    // The events pipeline's next version, its translation written anew: the draft and the save name what the
    // current XSLT looks up; the new version is a child of the same template with the new XSLT, the previous
    // version's converter, and its reference data when the user keeps them.
    private void newVersion(final String eventsUuid,
                            final String textUuid,
                            final String refFeed,
                            final String eventsFeed,
                            final long eventsRaw) {
        final String build = "st-badge2-" + STAMP;
        agreed("start_build", args("build", build, "feeds", List.of(eventsFeed)));
        final Map<String, Object> draft = client.json("draft_translation_mapping", args(
                "stream_ids", List.of(eventsRaw), "build", build, "source_name", "Door",
                "system_name", "Door Control", "environment", "Test"));
        final Map<String, Object> current = (Map<String, Object>) draft.getOrDefault("current_translation",
                Map.of());
        dump("current_translation", current);
        assertThat(map(current, "pipeline")).containsEntry("uuid", eventsUuid);
        assertThat(strings(current, "lookup_maps")).containsExactly("USER_DIRECTORY");
        assertThat(strings(current, "reference_data")).anyMatch(reference -> reference.contains(refFeed));
        assertThat(strings(draft, "notes")).as("the draft names the current translation's lookups")
                .anyMatch(note -> note.contains("Ask the user whether the new version keeps these lookups"));

        final Map<String, Object> without = agreed("build_translation_xslt", args("mapping", badgeMapping(false),
                "stream_ids", List.of(eventsRaw), "build", build, "name", eventsFeed + "-Events-V2"));
        assertThat(without).containsEntry("ok", true);
        assertThat(strings(without, "warnings")).as("a mapping without the lookups is saved with a warning naming them")
                .anyMatch(warning -> warning.contains("keeps none of the current translation's lookups"));
        final Map<String, Object> kept = agreed("build_translation_xslt", args("mapping", badgeMapping(true),
                "stream_ids", List.of(eventsRaw), "uuid", map(without, "saved").get("uuid")));
        assertThat(kept).as("with the lookup kept: %s", kept.get("problems")).containsEntry("ok", true);
        final Map<String, Object> call = map(map(client.json("build_status", args("build", build)), "next"), "call");
        assertThat(call).as("the plan's pipeline step makes the next version of the current pipeline")
                .containsEntry("tool", "create_pipeline");
        assertThat(map(call, "arguments")).containsEntry("version_of", eventsUuid);
        final String asked = replyText("create_pipeline", args("build", build, "name", eventsFeed + "-Events-V2",
                "version_of", eventsUuid));
        assertThat(asked).as("whether the new version keeps the reference data is asked")
                .contains("loads reference data for its lookups", refFeed);

        final Map<String, Object> keeping = agreed("create_pipeline", args("build", build,
                "name", eventsFeed + "-Events-V2", "version_of", eventsUuid, "keep_references", true));
        dump("version kept", keeping);
        final PipelineDoc doc = asUser(admin, () -> pipelineStore.readDocument(
                new DocRef(PipelineDoc.TYPE, (String) keeping.get("uuid"))));
        assertThat(doc.getParentPipeline().getUuid()).as("a child of the same template").isEqualTo(textUuid);
        assertThat(strings(keeping, "reference_data")).hasSize(1);
        assertThat(strings(keeping, "filled_from_build")).as("with its converter")
                .anyMatch(filled -> filled.contains("as ") && filled.contains("textConverter"));
        assertThat(userName(stepOutput((String) keeping.get("uuid"), eventsRaw, 1, "translationFilter")))
                .as("the new version's lookup fills the event")
                .isEqualTo("Bob Brown");
        assertThat(map(client.json("build_status", args("build", build)), "next"))
                .as("the version recorded, it is stepped next")
                .containsEntry("step", "stepped");

        final Map<String, Object> dropped = agreed("create_pipeline", args("build", build,
                "name", eventsFeed + "-Events-V2-NoRef", "version_of", eventsUuid, "keep_references", false));
        assertThat(strings(dropped, "reference_data")).isEmpty();
        assertThat(userName(stepOutput((String) dropped.get("uuid"), eventsRaw, 1, "translationFilter")))
                .as("its reference data left out, as the user chose")
                .isNull();
    }

    @Test
    void part3RecordsAreWrittenAndRefusedAsEventsToPlanAnIndexFrom() {
        final DocRef template = fixture(RECORDS_TEMPLATE, JsonUtil.readValue(RECORDS_DATA, PipelineData.class));
        final String build = "st-rec-" + STAMP;
        final String feed = "ST-REC-" + STAMP;
        agreed("create_feed", args("build", build, "name", feed));
        final long raw = upload(feed, CSV_QUOTED);
        client.json("build_data_splitter", args("stream_ids", List.of(raw), "save_as", feed, "build", build));
        final Map<String, Object> writer = agreed("create_pipeline", args("build", build, "name", feed + "-Records",
                "template_uuid", template.getUuid()));
        final String writerUuid = (String) writer.get("uuid");
        final Map<String, Object> stepped = client.json("step_sample", args("pipeline_uuid", writerUuid,
                "stream_ids", List.of(raw)));
        assertThat(stepped).as("the records pipeline steps clean: %s", stepped.get("groups"))
                .containsEntry("verdict", "clean");
        agreed("create_processor_filter", args("pipeline_uuid", writerUuid, "stream_ids", List.of(raw)));
        processAll();
        final Map<String, Object> done = client.json("wait_for_processing", args("pipeline_uuid", writerUuid,
                "stream_ids", List.of(raw), "timeout_seconds", 10));
        dump("records wait", done);
        assertThat(done).containsEntry("gate", "pass");
        assertThat(list(done, "streams").getFirst()).as("the wait takes Records from the pipeline")
                .containsEntry("output_type", "Records");
        final List<?> produced = (List<?>) list(done, "streams").getFirst().get("events");

        final String index = "st-records-" + STAMP;
        assertThat(replyText("draft_index_mapping", args("backend", "elasticsearch", "index_name", index,
                "events_stream_ids", produced, "convention", "ecs")))
                .as("the Records stream is refused as Events to plan from")
                .contains("is a Records stream");
        assertThat(replyText("draft_index_mapping", args("backend", "elasticsearch", "index_name", index,
                "events_stream_ids", produced,
                "discovery", Map.of("input", "xml", "record", "record", "timestamp_field", "time"))))
                .as("the Records drafted as XML are refused for delimited")
                .contains("'input': 'delimited'");
        // Its time field is read by data name, as the delimited input indexes it.
        final Map<String, Object> plan = map(client.json("draft_index_mapping", args("backend", "elasticsearch",
                "index_name", index, "events_stream_ids", produced,
                "discovery", Map.of("input", "delimited", "timestamp_field", "time"))), "plan");
        dump("records plan", plan);
        final Map<String, Object> xslt = client.json("save_xslt", args("build", build, "name", index + "-XSLT",
                "index_plan", plan));
        assertThat(xslt.get("uuid")).as("the indexing XSLT saved from the plan").isNotNull();
    }

    // A template of our own, named as no standard template is, in a folder of its own, with no children.
    private DocRef fixture(final String name, final PipelineData data) {
        final DocRef ref = create(PipelineDoc.TYPE, name, folder(BASES)).getDocRef();
        asUser(admin, () -> pipelineStore.writeDocument(pipelineStore.readDocument(ref).copy()
                .pipelineData(data)
                .description("Fixture for dev/e2e_stream_types.py")
                .build()));
        return ref;
    }

    // The agent's decisions beyond the draft (e2e_formats.decide): a device where the draft has none, and the user.
    private static Map<String, Object> decide(final Map<String, Object> mapping, final String user) {
        final Map<String, Object> decided = JsonUtil.readValue(JsonUtil.writeValueAsString(mapping, false),
                Map.class);
        final List<Map<String, Object>> common = (List<Map<String, Object>>) decided.computeIfAbsent("common",
                key -> new ArrayList<>());
        final List<String> paths = common.stream().map(entry -> String.valueOf(entry.get("path"))).toList();
        if (paths.stream().noneMatch(path -> path.startsWith("EventSource/Device/"))) {
            common.add(new LinkedHashMap<>(Map.of("path", "EventSource/Device/HostName", "value", "acme-gw01")));
        }
        if (!paths.contains("EventSource/User/Id")) {
            common.add(new LinkedHashMap<>(Map.of("path", "EventSource/User/Id", "field", user)));
        }
        return decided;
    }

    private static Map<String, Object> referenceMapping() {
        return args("input", "data_splitter", "maps", List.of(args("name", "USER_DIRECTORY", "key", "user",
                "values", List.of(Map.of("element", "name", "field", "name"),
                        Map.of("element", "department", "field", "department")))));
    }

    private static Map<String, Object> lookup() {
        return args("map", "USER_DIRECTORY", "xpath", LOOKUP, "path", "name");
    }

    // The badge controller's mapping, its user's name looked up in the directory (or not).
    private static Map<String, Object> badgeMapping(final boolean withLookup) {
        final List<Object> common = new ArrayList<>(List.of(
                Map.of("path", "EventTime/TimeCreated", "field", "time", "time_format", "yyyy-MM-dd'T'HH:mm:ssX"),
                Map.of("path", "EventSource/System/Name", "value", "Door Control"),
                Map.of("path", "EventSource/System/Environment", "value", "Test"),
                Map.of("path", "EventSource/Generator", "value", "badge-controller"),
                Map.of("path", "EventSource/Device/Name", "field", "door"),
                Map.of("path", "EventSource/User/Id", "field", "badge_user", "transform", "lower")));
        if (withLookup) {
            common.add(Map.of("path", "EventSource/User/Name", "lookup", lookup()));
        }
        return args("input", "data_splitter", "common", common, "events", List.of(args("name", "badge",
                "fields", List.of(
                        Map.of("path", "EventDetail/TypeId", "value", "Badge-Access"),
                        Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                        Map.of("path", "EventDetail/Authenticate/User/Id", "field", "badge_user", "transform",
                                "lower"),
                        Map.of("path", "EventDetail/Authenticate/Outcome/Success", "field", "result",
                                "map", Map.of("granted", "true", "denied", "false"))))));
    }

    private static List<Object> lookups(final Map<String, Object> mapping) {
        final List<Map<String, Object>> fields = new ArrayList<>((List<Map<String, Object>>)
                mapping.getOrDefault("common", List.of()));
        for (final Map<String, Object> rule : (List<Map<String, Object>>) mapping.get("events")) {
            fields.addAll((List<Map<String, Object>>) rule.getOrDefault("fields", List.of()));
        }
        return fields.stream()
                .filter(field -> field.get("lookup") != null)
                .map(field -> field.get("lookup"))
                .toList();
    }

    // EventSource/User/Name of the (first) event in Events XML.
    private static String userName(final String events) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            final Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(events)));
            final Node name = (Node) XPathFactory.newInstance().newXPath().evaluate(
                    "(//*[local-name()='EventSource']/*[local-name()='User']/*[local-name()='Name'])[1]",
                    document, XPathConstants.NODE);
            return name == null
                    ? null
                    : name.getTextContent();
        } catch (final Exception e) {
            throw new AssertionError("Not Events XML: " + events, e);
        }
    }


    // --------------------------------------------------------------------------------


    /// The badge controller's events pipeline, looking its users up in the directory fed as reference data.
    ///
    /// @param eventsUuid The events pipeline.
    /// @param textUuid   The template it is a child of.
    /// @param refFeed    The directory's Raw Reference feed.
    /// @param eventsFeed The badge controller's feed.
    /// @param eventsRaw  Its sample stream.
    private record Badge(String eventsUuid, String textUuid, String refFeed, String eventsFeed, long eventsRaw) {

    }
}
