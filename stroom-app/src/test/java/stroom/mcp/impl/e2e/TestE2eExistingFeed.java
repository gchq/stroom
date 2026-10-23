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

import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.shared.Meta;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_existing_feed.py`, ported: building a pipeline from a feed that already holds
/// data, through the REAL tools on a real Stroom. A source feed gets four streams in which the kinds of event are
/// spread out (every stream has logins, the oldest a file read and a logout, the second oldest a password change),
/// and the test plays the agent and the user as the script does.
///
/// Covered, as the script numbers them: 1 (a survey of two streams, the newest and the oldest, confirmed as fewer
/// than the default, kept in the build's survey doc), 2 (the translation generated from a mapping steps clean at
/// the survey's locations, each the record it names), 3 (the next survey finds the password change, flagged as
/// unmatched), 4 (a rule for it, every location clean), 5 (the feed covered, the survey doc complete), 6 (the broad
/// check of each stream's head), 6b (a kind left untranslated on purpose), 7 (a ~2.6 MB single-line stream surveyed
/// from its head only), "stepping copied nothing; generated docs are tagged", 8 (the feed's streams processed where
/// they are, into a test feed the user names) and 9 (promotion takes the test feed off, and its filter leaves none of
/// the sample out), and the script's `text_locations` (survey locations in a CSV feed with a header and an RFC 5424
/// syslog feed are the lines they name).
///
/// Left out: the script's check of `_free_feed_name` (an internal helper, not a tool: the next free name for the
/// server's own test feed); the user naming the test feed instead, which the script goes on to, is covered.
@SuppressWarnings("unchecked")
class TestE2eExistingFeed extends AbstractE2eTest {

    private static final String SOURCE = "SRC-APP";
    private static final String SOURCE_BUILD = "src";
    private static final String BUILD = "existing";
    private static final String PIPELINE = SOURCE + "-Events";
    private static final String TEST_FEED = SOURCE + "-OUT";
    private static final List<Map<String, Object>> COMMON = List.of(
            entry("EventTime/TimeCreated", "field", "ts", "time_format", "yyyy-MM-dd'T'HH:mm:ssX"),
            entry("EventSource/System/Name", "value", "App"),
            entry("EventSource/System/Environment", "value", "Dev"),
            entry("EventSource/Generator", "value", "app"),
            entry("EventSource/Device/HostName", "field", "host"),
            entry("EventSource/User/Id", "field", "user"),
            entry("EventDetail/TypeId", "field", "action"));
    private static final List<Map<String, Object>> V1 = List.of(
            rule("login", "login", authenticate("Logon")),
            rule("logout", "logout", authenticate("Logoff")),
            rule("file read", "file_read", List.of(entry("EventDetail/View/File/Path", "field", "path"))));
    private static final List<Map<String, Object>> V2 = concat(V1,
            rule("password change", "passwd_change", authenticate("ChangePassword")));
    // The user chooses to leave file reads untranslated.
    private static final List<Map<String, Object>> V3 = concat(V2.stream()
                    .filter(r -> !"file read".equals(r.get("name")))
                    .toList(),
            Map.of("name", "file read", "drop", true, "when", List.of(Map.of("field", "action", "equals",
                    "file_read"))));

    @Test
    void aPipelineIsBuiltFromAFeedThatAlreadyHoldsDataByStepping() {
        // A source feed that already holds data, uploaded oldest first, so stream ids rise towards the newest.
        createFeed(SOURCE_BUILD, SOURCE);
        upload(SOURCE, events(0, List.of(List.of("alice", "login", ""), List.of("alice", "file_read", "/data/a.csv"),
                List.of("alice", "logout", ""))));
        upload(SOURCE, events(10, List.of(List.of("bob", "login", ""), List.of("bob", "passwd_change", ""))));
        upload(SOURCE, events(20, List.of(List.of("carol", "login", ""), List.of("dave", "login", ""))));
        upload(SOURCE, events(30, List.of(List.of("erin", "login", ""), List.of("frank", "login", ""),
                List.of("erin", "login", ""))));
        final List<Long> ids = streamIdsOf(SOURCE);
        assertThat(ids).hasSize(4);

        // 1. Two streams, spread over the feed, recorded in the build. Fewer streams than the default is the user's
        // to confirm (seen: an agent surveying a live feed 3 at a time): here in a form.
        final Map<String, Object> first = json("survey_feed", args("feed", SOURCE, "max_streams", 2,
                "build", BUILD));
        assertThat(formMessages()).anySatisfy(message -> assertThat(message).contains("only 2 stream(s) a call"));
        assertThat(map(first, "survey_doc")).containsEntry("name", SOURCE + " - Survey");
        assertThat(ids(first, "streams_read")).containsExactly(ids.get(3), ids.get(0));
        assertThat(kinds(first)).containsExactly("file_read", "login", "logout");
        assertThat(first).containsEntry("saturated", false);
        // The plan goes on from the survey: no feed to make, nothing to upload, the translation next.
        final Map<String, Object> surveyed = json("build_status", args("build", BUILD));
        dump("build_status after the survey", surveyed.get("next"));
        assertThat(nextTool(surveyed)).isEqualTo("draft_translation_mapping");

        // 2. A pipeline for those shapes, stepped on the feed's own records.
        final String templateUuid = templateUuid("translation", "Event Data (JSON)");
        final Map<String, Object> xslt = json("save_xslt", args("build", BUILD, "name", PIPELINE,
                "code", xsltFor(V1)));
        final String xsltUuid = (String) xslt.get("uuid");
        final Map<String, Object> pipeline = json("create_pipeline", args("build", BUILD, "name", PIPELINE,
                "template_uuid", templateUuid, "set_properties", List.of(
                        docProperty("translationFilter", "xslt", xsltUuid, "XSLT"),
                        valueProperty("jsonParser", "addRootObject", false))));
        final String pipelineUuid = (String) pipeline.get("uuid");
        // Next: the survey's locations, stepped where they are.
        assertThat(nextTool(pipeline)).isEqualTo("step_records");
        final List<Map<String, Object>> firstLocations = list(first, "locations");
        final Map<String, Object> stepped = json("step_records", args("pipeline_uuid", pipelineUuid,
                "locations", firstLocations));
        assertThat(stepped).containsEntry("verdict", "clean");
        assertThat(number(stepped.get("records_stepped"))).isEqualTo(firstLocations.size());
        for (final Map<String, Object> location : firstLocations) {
            final String action = String.valueOf(location.get("shape")).replaceAll(".*action=", "");
            assertThat(translationOutput(pipelineUuid, number(location.get("stream")),
                    (int) number(location.get("record")), (int) number(location.get("part"))))
                    .as("location %s is a %s record", location, action)
                    .contains("<TypeId>" + action + "</TypeId>");
        }

        // 3. The streams not read yet, carrying on from the survey doc.
        final Map<String, Object> second = json("survey_feed", args("feed", SOURCE, "build", BUILD));
        assertThat(ids(second, "streams_read")).containsExactlyInAnyOrder(ids.get(1), ids.get(2));
        assertThat(((List<Map<String, Object>>) second.get("shapes")).stream()
                .filter(shape -> Boolean.TRUE.equals(shape.get("new")))
                .map(shape -> String.valueOf(shape.get("signature")).replaceAll(".*action=", ""))
                .toList()).containsExactly("passwd_change");
        final List<Map<String, Object>> secondLocations = list(second, "locations");
        final Map<String, Object> gaps = json("step_records", args("pipeline_uuid", pipelineUuid,
                "locations", secondLocations));
        final List<Map<String, Object>> unmatched = ((List<Map<String, Object>>) gaps.get("groups")).stream()
                .filter(group -> JsonUtil.writeValueAsString(group, false).contains("No event mapping matched"))
                .toList();
        assertThat(unmatched).as("the current translation flags it: %s", gaps.get("groups")).isNotEmpty();
        assertThat(number(unmatched.getFirst().get("count"))).isOne();
        assertThat(list(gaps, "shapes_not_clean")).hasSize(1);

        // 4. The mapping extended, and every location stepped.
        json("save_xslt", args("uuid", xsltUuid, "code", xsltFor(V2)));
        final List<Map<String, Object>> every = new ArrayList<>(firstLocations);
        every.addAll(secondLocations);
        final Map<String, Object> both = json("step_records", args("pipeline_uuid", pipelineUuid,
                "locations", every));
        assertThat(both).containsEntry("verdict", "clean");
        assertThat(list(both, "shapes_not_clean")).isEmpty();
        assertThat(number(both.get("records_stepped"))).isEqualTo(every.size());

        // 5. Surveyed again: every stream read.
        final Map<String, Object> third = json("survey_feed", args("feed", SOURCE, "build", BUILD));
        assertThat(third).containsEntry("saturated", true);
        assertThat(number(third.get("new_shapes"))).isZero();
        assertThat(list(third, "streams_read")).isEmpty();
        final String surveyUuid = (String) map(third, "survey_doc").get("uuid");
        String record = documentation(surveyUuid);
        final Map<String, Object> state = SurveyDoc.readState(record);
        assertThat(((List<Number>) state.get("streams_read")).stream().map(Number::longValue).sorted().toList())
                .isEqualTo(ids);
        assertThat((Map<String, Object>) state.get("shapes")).hasSize(4);
        assertThat(state).containsEntry("saturated", true);
        assertThat(record).contains("## Kinds of event", "\"action\": \"passwd_change\"", "Stream ");

        // 6. The broad check: the head of each stream.
        final Map<String, Object> broad = json("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", ids, "records_per_stream", 2));
        assertThat(broad).containsEntry("verdict", "clean");
        assertThat(number(broad.get("records_stepped"))).isEqualTo(2L * ids.size());

        // 6b. The user leaves one kind of event untranslated.
        final Map<String, Object> marked = json("set_shape_handling", args("build", BUILD, "feed", SOURCE,
                "shapes", List.of("action=file_read"), "handling", "drop",
                "reason", "file reads are audited elsewhere"));
        final List<Map<String, Object>> dropLocations = list(marked, "locations");
        // Stepped clean and covered: processing is next.
        assertThat(nextTool(marked)).isEqualTo("create_processor_filter");
        assertThat(list(marked, "shapes")).hasSize(1);
        assertThat(dropLocations).isNotEmpty().allSatisfy(location ->
                assertThat(location).containsEntry("expect", "none"));
        final Map<String, Object> still = json("step_records", args("pipeline_uuid", pipelineUuid,
                "locations", dropLocations));
        assertThat(list(still, "shapes_not_clean")).isEqualTo(list(marked, "shapes"));
        assertThat(JsonUtil.writeValueAsString(still.get("groups"), false)).contains("left untranslated");
        json("save_xslt", args("uuid", xsltUuid, "code", xsltFor(V3)));
        final List<Map<String, Object>> rest = new ArrayList<>(every.stream()
                .filter(location -> !list(marked, "shapes").contains(location.get("shape")))
                .toList());
        rest.addAll(dropLocations);
        final Map<String, Object> after = json("step_records", args("pipeline_uuid", pipelineUuid,
                "locations", rest));
        assertThat(after).containsEntry("verdict", "clean");
        assertThat(list(after, "shapes_not_clean")).isEmpty();
        assertThat(number(after.get("left_untranslated_as_intended"))).isEqualTo(dropLocations.size());
        record = documentation(surveyUuid);
        assertThat(record).contains("left untranslated: file reads are audited elsewhere");

        // Stepping copied nothing; generated docs are tagged.
        assertThat(streamsOf(SOURCE)).hasSize(4).allSatisfy(meta ->
                assertThat(meta.getTypeName()).isEqualTo(StreamTypeNames.RAW_EVENTS));
        assertThat(tags(pipelineUuid)).contains("mcp-generated", "mcp-managed");

        // 8. Processed where it is: the Events go to a test feed in the build. Asked for by the user: no feed made
        // for raw samples, and nothing uploaded.
        final String samplesFeed = refusal("create_feed", args("build", BUILD, "name", SOURCE + "-SAMPLES"));
        assertThat(samplesFeed).contains("works from the existing feed", "user_samples=", "test_feed=true");
        // Seen in Ask Stroom AI: an earlier build's test feed held the server's name; the user names it instead.
        createFeed(SOURCE_BUILD, SOURCE + "-MCP-TEST");
        final Map<String, Object> named = json("create_feed", args("build", BUILD, "name", TEST_FEED,
                "test_feed", true));
        assertThat(named).containsEntry("test_feed", true).containsEntry("name", TEST_FEED);
        final Map<String, Object> started = json("create_processor_filter", args("pipeline_uuid", pipelineUuid,
                "stream_ids", ids));
        dump("create_processor_filter", started);
        assertThat(started).containsEntry("output_feed", TEST_FEED);
        assertThat(String.valueOf(started.get("output_note"))).contains("not into the production feed");
        assertThat(nextTool(started)).isEqualTo("wait_for_processing");
        assertThat(feedProperties(pipelineUuid)).singleElement().satisfies(property ->
                assertThat(feedNamed(property)).isEqualTo(TEST_FEED));
        assertThat(refusal("upload_sample", args("feed", TEST_FEED, "sample", "{\"ts\": \"2026-09-28T10:00:00Z\"}")))
                .contains("upload nothing");
        processAll();
        final Map<String, Object> done = json("wait_for_processing", args("pipeline_uuid", pipelineUuid,
                "stream_ids", ids, "timeout_seconds", 10));
        dump("wait_for_processing", done);
        assertThat(done).containsEntry("finished", true);
        assertThat(nextTool(done)).isEqualTo("check_events");
        final List<Meta> outputs = ids.stream().flatMap(id -> childrenOf(id).stream()).toList();
        final List<Meta> events = outputs.stream()
                .filter(meta -> StreamTypeNames.EVENTS.equals(meta.getTypeName()))
                .toList();
        assertThat(events).as("one Events stream per raw stream, all in %s", TEST_FEED).hasSize(ids.size())
                .allSatisfy(meta -> assertThat(meta.getFeedName()).isEqualTo(TEST_FEED));
        assertThat(streamsOf(SOURCE)).noneSatisfy(meta ->
                assertThat(meta.getTypeName()).isEqualTo(StreamTypeNames.EVENTS));
        final Map<String, Object> status = json("build_status", args("build", BUILD));
        dump("build_status", status);
        assertThat(next(status).get("step")).isNotEqualTo("feed");
        assertThat(JsonUtil.writeValueAsString(status.get("next"), false)).doesNotContain("upload_sample");

        // 9. Promoted: the pipeline writes into its input feed again, and its filter processes the sample again.
        writeDocumentation(pipelineUuid, ids.subList(0, 1));
        final String folder = "System/E2E Production/existing";
        final Map<String, Object> destinations = Map.of("Pipeline", folder, "XSLT", folder, "Documentation",
                folder);
        final Map<String, Object> review = json("promote_build", args("build", BUILD,
                "destinations", destinations));
        assertThat(review).containsEntry("status", "needs_review");
        final Map<String, Object> promoted = json("promote_build", args("build", BUILD,
                "destinations", destinations, "reviewed", review.get("review_id")));
        dump("promote_build", promoted);
        assertThat(feedProperties(pipelineUuid)).as("the StreamAppender's feed property is taken off").isEmpty();
        final List<String> said = strings(promoted, "promoted");
        assertThat(said).anySatisfy(line -> assertThat(line).contains("writes its Events into its input"));
        assertThat(said).anySatisfy(line -> assertThat(line).contains(TEST_FEED, "left"));
        final List<Map<String, Object>> made = list(promoted, "processing_filters");
        assertThat(made).singleElement().satisfies(filter -> {
            assertThat(filter).containsEntry("feed", SOURCE);
            assertThat(filter.get("except_processed_in_build")).as("none of the sample left out").isNull();
        });
    }

    /// Beyond the script, the user's stalled session as a test: an existing feed of XML fragments, onboarded by
    /// following each result's `next` (and build_status's when a tool gives none), as an agent would, with the
    /// decisions the plan leaves to it made the way its `then` says. Every call `next` names must go through, and
    /// the plan must reach documentation without the same step coming back again and again.
    @Test
    void anExistingFeedOfXmlFragmentsIsBuiltByFollowingNext() {
        final String source = "FRAG-SRC";
        final String build = "walk";
        createFeed(SOURCE_BUILD, source);
        final List<String> lines = TestE2eFragments.SAMPLE.lines().toList();
        upload(source, String.join("\n", lines.subList(0, 2)) + "\n");
        upload(source, String.join("\n", lines.subList(2, lines.size())) + "\n");
        final Map<String, Object> started = json("start_build", args("build", build, "feeds", List.of(source)));
        dump("start_build", started);
        final Map<String, Object> surveyed = json("survey_feed", args("feed", source, "build", build));
        dump("survey_feed", surveyed);

        final List<String> walked = new ArrayList<>();
        Map<String, Object> mapping = null;
        Object splitter = null;
        Map<String, Object> last = json("build_status", args("build", build));
        for (int turn = 0; turn < 20; turn++) {
            final Map<String, Object> next = next(last.containsKey("next")
                    ? last
                    : json("build_status", args("build", build)));
            final String step = String.valueOf(next.get("step"));
            final Map<String, Object> call = (Map<String, Object>) next.get("call");
            final String tool = (String) call.get("tool");
            final Map<String, Object> arguments = new LinkedHashMap<>((Map<String, Object>) call.get("arguments"));
            walked.add(step + ": " + tool);
            dump("next " + turn, next);
            assertThat(walked.stream().filter((step + ": " + tool)::equals).count())
                    .as("the same step over and over: %s", walked)
                    .isLessThanOrEqualTo(2);
            if ("index".equals(step) || "promoted".equals(step)) {
                break;
            }
            switch (tool) {
                case "build_data_splitter" -> {
                    arguments.put("save_as", source + "-Converter");
                    last = json(tool, arguments);
                    splitter = last.get("spec");
                }
                case "draft_translation_mapping" -> {
                    final Map<String, Object> draft = json(tool, arguments);
                    dump("draft_translation_mapping", draft);
                    // As the Stroom MCP server drafts it from more than one stream, an XML fragments mapping has no
                    // record element (utils/profile.py profile_many keeps record_element per file only, and
                    // draftmap.py reads it from the top level), so its own schema check fails ("xml_fragments
                    // input needs record"): the agent must add it. Reported, not changed.
                    assertThat(map(draft, "mapping").get("record")).isNull();
                    assertThat(map(draft, "schema_check")).containsEntry("ok", false);
                    // As its notes and schema problems ask: the system's name and environment, the record, the
                    // time (read from the message), the logons' user, an action for the update.
                    mapping = settled(map(draft, "mapping"));
                    last = saveTranslation(build, source, mapping, draft.get("schema_version"), arguments.get(
                            "stream_ids"), splitter);
                }
                case "find_pipeline_templates" -> {
                    json(tool, arguments);
                    // As its then says: create_pipeline from the best candidate. For XML fragments (the parser
                    // build_status's sample names), the agent takes the XML template with replace_parser, as
                    // profile_sample says: nothing refuses another template here (see the test after this one).
                    final Map<String, Object> sample = map(json("build_status", args("build", build)), "sample");
                    assertThat(String.valueOf(sample.get("suggested_parser"))).startsWith("XMLFragmentParser");
                    last = json("create_pipeline", args("build", build, "name", source + "-Events",
                            "template_uuid", templateUuid("translation", "Event Data (XML)"),
                            "replace_parser", "XMLFragmentParser"));
                }
                case "write_documentation" -> {
                    arguments.put("markdown", "## Purpose and data\n\nThe application's audit events.\n");
                    arguments.put("change", "Created");
                    last = json(tool, arguments);
                    if ("needs_descriptions".equals(last.get("status"))) {
                        arguments.put("field_descriptions", described(list(last, "fields")));
                        last = json(tool, arguments);
                    }
                }
                default -> {
                    assertThat(JsonUtil.writeValueAsString(arguments, false))
                            .as("%s's arguments are all given by the plan", tool)
                            .doesNotContain("\"<");
                    last = json(tool, arguments);
                    if ("create_processor_filter".equals(tool)) {
                        processAll();
                    }
                }
            }
        }
        dump("walked", walked);
        assertThat(walked).as("the plan reached documentation and on")
                .anySatisfy(step -> assertThat(step).startsWith("documented"));
    }

    // build_translation_xslt with the mapping, saved in the build, a review of records kept as Unknown agreed.
    private Map<String, Object> saveTranslation(final String build,
                                                final String source,
                                                final Map<String, Object> mapping,
                                                final Object schemaVersion,
                                                final Object streamIds,
                                                final Object splitter) {
        final Map<String, Object> arguments = args("mapping", mapping, "schema_version", schemaVersion,
                "stream_ids", streamIds, "splitter", splitter, "build", build, "name", source + "-Events");
        Map<String, Object> saved = json("build_translation_xslt", arguments);
        while ("needs_review".equals(saved.get("status"))) {
            arguments.put("reviewed", saved.get("review_id"));
            saved = json("build_translation_xslt", arguments);
        }
        dump("build_translation_xslt", saved);
        assertThat(saved).as("the drafted mapping saves: %s", saved.get("problems")).containsEntry("ok", true);
        return saved;
    }

    /// As the Stroom MCP server behaves (reported, not changed): create_pipeline checks a template's parser against
    /// the build's own sample streams only (its `_parser_reads_sample`, through `sample_format`), so in a build on
    /// an existing feed, which has none, a template whose parser can't read the feed is taken without a word; the
    /// first candidate find_pipeline_templates offers is Event Data (JSON), and every record then fails fatally.
    @Test
    void aTemplateThatCannotReadAnExistingFeedIsNotRefused() {
        final String source = "FRAG-SRC";
        createFeed(SOURCE_BUILD, source);
        upload(source, TestE2eFragments.SAMPLE);
        json("start_build", args("build", BUILD, "feeds", List.of(source)));
        final Map<String, Object> surveyed = json("survey_feed", args("feed", source, "build", BUILD));
        final Map<String, Object> pipeline = json("create_pipeline", args("build", BUILD, "name", source + "-Events",
                "template_uuid", templateUuid("translation", "Event Data (JSON)")));
        assertThat(pipeline).containsEntry("template", "Event Data (JSON)");
        final Map<String, Object> stepped = json("step_records", args("pipeline_uuid", pipeline.get("uuid"),
                "locations", surveyed.get("locations")));
        assertThat(stepped).containsEntry("verdict", "blocking");
        assertThat(JsonUtil.writeValueAsString(stepped.get("groups"), false)).contains("jsonParser");
    }

    // The draft as the user settles what its notes ask: the system's name and environment, a generator.
    private static Map<String, Object> settled(final Map<String, Object> draft) {
        final Map<String, Object> mapping = new LinkedHashMap<>(draft);
        final List<Map<String, Object>> common = new ArrayList<>();
        for (final Map<String, Object> entry : (List<Map<String, Object>>) draft.get("common")) {
            final Map<String, Object> settledEntry = new LinkedHashMap<>(entry);
            switch (String.valueOf(entry.get("path"))) {
                case "EventSource/System/Name" -> settledEntry.put("value", "AppAudit");
                case "EventSource/System/Environment" -> settledEntry.put("value", "Test");
                case "EventSource/Generator" -> settledEntry.putIfAbsent("value", "app-audit");
                default -> {
                }
            }
            common.add(settledEntry);
        }
        common.add(entry("EventTime/TimeCreated", "field", "when", "time_format", "MMM dd yyyy HH:mm:ss"));
        mapping.put("common", common);
        mapping.put("record", "Event");
        mapping.put("extract", List.of(Map.of("xpath", "EventData/Data", "regex", "^(\\S+ \\d+ \\d+ \\S+) ",
                "names", List.of("when"))));
        final List<Map<String, Object>> rules = new ArrayList<>();
        for (final Map<String, Object> rule : (List<Map<String, Object>>) draft.get("events")) {
            final Map<String, Object> decided = new LinkedHashMap<>(rule);
            final List<Map<String, Object>> fields = new ArrayList<>(
                    (List<Map<String, Object>>) rule.getOrDefault("fields", List.of()));
            if (fields.stream().anyMatch(field -> String.valueOf(field.get("path")).contains("Authenticate"))) {
                fields.add(entry("EventDetail/Authenticate/User/Id", "field", "by_user"));
            } else if (!"other".equals(rule.get("name"))) {
                // The one Update record: the item it changed.
                fields.clear();
                fields.add(entry("EventDetail/Update/After/Object/Id", "field", "item_id"));
                decided.remove("data");
            }
            decided.put("fields", fields);
            rules.add(decided);
        }
        mapping.put("events", rules);
        return mapping;
    }

    @Test
    void aBigSingleLineStreamIsReadFromItsHeadOnly() {
        // 7. A big single-line stream: only its head is read, complete records only, and its locations step clean.
        createFeed(SOURCE_BUILD, SOURCE);
        upload(SOURCE, events(0, List.of(List.of("alice", "login", ""), List.of("alice", "logout", ""))));
        final Map<String, Object> surveyed = json("survey_feed", args("feed", SOURCE, "build", BUILD));
        final String pipelineUuid = jsonPipeline(V1);
        assertThat(json("step_records", args("pipeline_uuid", pipelineUuid, "locations",
                surveyed.get("locations")))).containsEntry("verdict", "clean");

        final String bigFeed = "SRC-BIG";
        createFeed(SOURCE_BUILD, bigFeed);
        final String big = IntStream.range(0, 30_000)
                .mapToObj(i -> "{\"ts\": \"2026-09-28T10:00:00Z\", \"host\": \"app02\", \"user\": \"user" + i
                               + "\", \"action\": \"" + (i % 50 == 7
                        ? "logout"
                        : "login") + "\"}")
                .collect(Collectors.joining(", ", "[", "]"));
        assertThat(big.length()).isGreaterThan(2_500_000);
        upload(bigFeed, big);
        final Map<String, Object> head = json("survey_feed", args("feed", bigFeed, "max_chars_per_stream", 100_000));
        final Map<String, Object> stream = ((List<Map<String, Object>>) head.get("per_stream")).getFirst();
        dump("per_stream", stream);
        assertThat(stream).containsEntry("head_only", true);
        assertThat(number(head.get("records_read"))).isBetween(1L, 1999L);
        assertThat(kinds(head)).containsExactly("login", "logout");
        final List<Map<String, Object>> locations = list(head, "locations");
        final Map<String, Object> located = json("step_records", args("pipeline_uuid", pipelineUuid,
                "locations", locations));
        assertThat(located).containsEntry("verdict", "clean");
        assertThat(number(located.get("records_stepped"))).isEqualTo(locations.size());
    }

    @Test
    void surveyLocationsPointAtTheRightRecordInTextFormats() {
        // The script's text_locations: a CSV feed with a header, and an RFC 5424 syslog feed.
        final Map<String, Object> cases = jsonResource("eval_cases.json");
        for (final String caseName : List.of("01_csv_vpn", "06_syslog5424_ssh")) {
            final Map<String, Object> evalCase = (Map<String, Object>) cases.get(caseName);
            final String sample = (String) evalCase.get("sample");
            final Map<String, Object> reference = (Map<String, Object>) evalCase.get("reference");
            final String build = "loc-" + caseName.substring(0, 2);
            final String feed = "LOC-" + caseName.substring(0, 2);
            createFeed(build, feed);
            upload(feed, sample);
            final Map<String, Object> survey = json("survey_feed", args("feed", feed, "examples_per_shape", 5));
            final Map<String, Object> generated = json("build_translation_xslt", args("mapping",
                    reference.get("mapping"), "schema_version", "4.1.0"));
            final String converterCode = "csv_header".equals(reference.get("converter"))
                    ? CSV_SPLITTER
                    : (String) reference.get("converter");
            final Map<String, Object> converter = json("save_text_converter", args("build", build, "name", feed,
                    "converter_type", "DATA_SPLITTER", "code", converterCode));
            final Map<String, Object> xslt = json("save_xslt", args("build", build, "name", feed + "-Events",
                    "code", generated.get("xslt")));
            final Map<String, Object> pipeline = json("create_pipeline", args("build", build,
                    "name", feed + "-Events", "template_uuid", templateUuid("translation", "Event Data (Text)"),
                    "set_properties", List.of(
                            docProperty("dsParser", "textConverter", (String) converter.get("uuid"), "TextConverter"),
                            docProperty("translationFilter", "xslt", (String) xslt.get("uuid"), "XSLT"))));
            final List<String> lines = sample.strip().lines().toList();
            int checked = 0;
            for (final Map<String, Object> shape : (List<Map<String, Object>>) survey.get("shapes")) {
                for (final Map<String, Object> location : (List<Map<String, Object>>) shape.get("locations")) {
                    final int recordNo = (int) number(location.get("record"));
                    final String output = translationOutput((String) pipeline.get("uuid"),
                            number(location.get("stream")), recordNo, (int) number(location.get("part")));
                    final String user = output.contains("<User><Id>")
                            ? output.split("<User><Id>")[1].split("<")[0]
                            : null;
                    final String line = lines.get(recordNo + ("csv_header".equals(reference.get("converter"))
                            ? 1
                            : 0));
                    assertThat(user).as("location %s of %s steps the line it names", location, caseName)
                            .isNotNull();
                    assertThat(line).contains(user);
                    checked++;
                }
            }
            assertThat(checked).as("every record of %s located", caseName).isEqualTo(3);
        }
    }

    // A pipeline in the build reading the source's JSON arrays, translated by a mapping's XSLT.
    private String jsonPipeline(final List<Map<String, Object>> rules) {
        final Map<String, Object> xslt = json("save_xslt", args("build", BUILD, "name", PIPELINE,
                "code", xsltFor(rules)));
        final Map<String, Object> pipeline = json("create_pipeline", args("build", BUILD, "name", PIPELINE,
                "template_uuid", templateUuid("translation", "Event Data (JSON)"), "set_properties", List.of(
                        docProperty("translationFilter", "xslt", (String) xslt.get("uuid"), "XSLT"),
                        valueProperty("jsonParser", "addRootObject", false))));
        return (String) pipeline.get("uuid");
    }

    private String xsltFor(final List<Map<String, Object>> rules) {
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("input", "json");
        mapping.put("common", COMMON);
        mapping.put("events", rules);
        final Map<String, Object> result = json("build_translation_xslt", args("mapping", mapping,
                "schema_version", "4.1.0"));
        assertThat(result).as("the mapping generates").containsEntry("ok", true);
        return (String) result.get("xslt");
    }

    private void writeDocumentation(final String pipelineUuid, final List<Long> streamIds) {
        final Map<String, Object> arguments = new LinkedHashMap<>(args("build", BUILD,
                "pipeline_uuid", pipelineUuid, "change", "Created", "stream_ids", streamIds,
                "markdown", "## Purpose and data\n\nApplication audit events.\n\n## Field mapping\n\n"
                            + "| Field | Path |\n| --- | --- |\n| user | User/Id |\n"));
        Map<String, Object> written = json("write_documentation", arguments);
        if ("needs_descriptions".equals(written.get("status"))) {
            arguments.put("field_descriptions", described(list(written, "fields")));
            written = json("write_documentation", arguments);
        }
        assertThat(written.get("uuid")).as("documentation written: %s", written).isNotNull();
    }

    private List<Map<String, Object>> feedProperties(final String pipelineUuid) {
        return addedProperties(pipelineUuid).stream()
                .filter(property -> "feed".equals(property.get("name")))
                .toList();
    }

    private static String feedNamed(final Map<String, Object> property) {
        return String.valueOf(((Map<String, Object>) ((Map<String, Object>) property.get("value")).get("entity"))
                .get("name"));
    }

    private static List<String> kinds(final Map<String, Object> survey) {
        return ((List<Map<String, Object>>) survey.get("shapes")).stream()
                .map(shape -> String.valueOf(shape.get("signature")).replaceAll(".*action=", ""))
                .sorted()
                .toList();
    }

    // A stream's text, as the script's json.dumps writes it: a JSON array of events a minute apart.
    private static String events(final int minute, final List<List<String>> rows) {
        final List<String> records = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            final List<String> row = rows.get(i);
            records.add("{\"ts\": \"2026-09-28T09:" + String.format("%02d", minute + i) + ":00Z\", \"host\": "
                        + "\"app01\", \"user\": \"" + row.get(0) + "\", \"action\": \"" + row.get(1) + "\""
                        + (row.get(2).isEmpty()
                    ? ""
                    : ", \"path\": \"" + row.get(2) + "\"") + "}");
        }
        return "[" + String.join(", ", records) + "]";
    }

    private static Map<String, Object> rule(final String name,
                                            final String action,
                                            final List<Map<String, Object>> fields) {
        return Map.of("name", name, "when", List.of(Map.of("field", "action", "equals", action)), "fields", fields);
    }

    private static List<Map<String, Object>> authenticate(final String verb) {
        return List.of(entry("EventDetail/Authenticate/Action", "value", verb),
                entry("EventDetail/Authenticate/User/Id", "field", "user"));
    }

    private static Map<String, Object> entry(final String path, final Object... pairs) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", path);
        for (int i = 0; i < pairs.length; i += 2) {
            entry.put((String) pairs[i], pairs[i + 1]);
        }
        return entry;
    }

    private static List<Map<String, Object>> concat(final List<Map<String, Object>> rules,
                                                    final Map<String, Object> more) {
        final List<Map<String, Object>> all = new ArrayList<>(rules);
        all.add(more);
        return List.copyOf(all);
    }
}
