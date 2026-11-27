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

import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.workspace.EventsFile;
import stroom.docref.DocRef;
import stroom.mcp.impl.McpTerminal;
import stroom.mcp.impl.McpTestClient;
import stroom.mcp.impl.e2e.AwkwardValues.Value;
import stroom.search.elastic.ElasticTestNode;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/// The awkward values of [AwkwardValues] through the main paths as an agent drives them, on a real Stroom (asked for
/// by the user, 2026-11-18, after chat 181 found check_events and fetch_events reading Stroom's XML 1.1 Events as
/// XML 1.0: no sample had held an awkward character). Each format a source sends them in (JSON lines, XML 1.1,
/// key=value, CSV, bytes that aren't UTF-8) is surveyed, its translation drafted and built, stepped, processed,
/// checked (check_events passes where Stroom's own output is valid) and fetched (the events file parses and holds the
/// values as Stroom stored them); the JSON Events are indexed in Lucene and in Elasticsearch, verified and searched;
/// and an aggregated feed's source type held inside a JSON string is read alike by the tools and its group's
/// pipeline.
///
/// What Stroom itself does to the values (its core, not the tools) is asserted as it is, each with why:
/// - its Data Splitter drops the control characters but tab and new line (CR among them) as it reads
///   (`DS3Reader`), and trims a value's white space (`DS3Parser.normaliseBuffer`);
/// - its jsonParser cuts a string past its `stringTruncateLength` (10,000 by default), with a Warning (triaged for
///   review, with the property to raise);
/// - its XML writer drops a NUL, and takes one for its switch of escaping (`MyXmlEmitter`, known since chat 177);
/// - its search results cut a string past `maxStringFieldLength` (1,000) with a Warning.
///
/// The Elasticsearch node is [ElasticTestNode]'s (`STROOM_TEST_ELASTIC_URL`): skipped without one unless
/// `STROOM_TEST_ELASTIC_REQUIRED=true`. What the test made there is removed after it.
@SuppressWarnings("unchecked")
class TestE2eAwkwardCharacters extends AbstractE2eTest {

    // Times in the past, so searches up to today find them.
    private static final String TS = "2026-11-10T10:%02d:00.000Z";
    private static final String JSON = "Event Data (JSON)";
    private static final String XML = "Event Data (XML)";
    private static final String TEXT = "Event Data (Text)";
    private static final String STAMP = Long.toString(System.currentTimeMillis(), 36);
    // Searched for in the indexes: a control character, astral characters, XML's specials, the long value, text
    // like an entity.
    private static final List<String> SEARCHED = List.of("form_feed", "astral", "xml_special", "long", "entity_like");

    @Inject
    private Injector injector;

    private final List<String> indices = new ArrayList<>();
    private final List<String> indexTemplates = new ArrayList<>();

    @AfterEach
    void removeWhatWasIndexed() {
        if (indices.isEmpty() && indexTemplates.isEmpty()) {
            return;
        }
        final ElasticTestNode node = ElasticTestNode.getOrSkip();
        for (final String index : indices) {
            node.request("DELETE", "/_data_stream/" + index, null);
            node.deleteIndices(index + "*");
        }
        indexTemplates.forEach(node::deleteIndexTemplate);
    }

    @Test
    void jsonLinesAreTranslatedCheckedFetchedIndexedAndSearchedAsStroomStoresThem() {
        final List<Value> values = AwkwardValues.without(AwkwardValues.ALL, "nul");
        final Walked walked = walk("aw-json", JSON, jsonLines(values), values, null, this::wholeStrings);

        // The long value is cut by Stroom's jsonParser (its default 10,000): the draft said so before processing,
        // stepping gave the Warning for review (it had been benign: "clean", the value cut), with the property to
        // raise; raised as it says, the value is whole.
        assertThat(String.valueOf(walked.draft().get("notes"))).contains("stringTruncateLength");
        assertThat(walked.stored()).as("each value as Stroom stores it").isEqualTo(expected(values, text -> text));

        lucene(walked);
        elastic(walked);
    }

    @Test
    void aNulInJsonNeedsNothingAsStroomsWriterRemovesIt() {
        final List<Value> values = List.of(AwkwardValues.named("nul"), AwkwardValues.named("xml_special"));
        final String build = "aw-nul";
        final String feed = "AW-NUL";
        createFeed(build, feed);
        final long raw = upload(feed, jsonLines(values));
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw)));
        // Since stroom#6036 (in 7.14) Stroom's XML writer removes a NUL and keeps escaping on: the draft asks for
        // nothing (chats 182 and 183: its old note had the agent add strip_control).
        assertThat(String.valueOf(draft.get("notes"))).doesNotContain("NUL", "strip_control");
        // With no transform, the Events are valid, the NUL gone and the rest kept, escaped.
        final Walked walked = translate(build, feed, raw, JSON, mapping(draft, null, null), null, values);
        assertThat(walked.stored()).containsEntry(values.getFirst().user(), "beforeafter <b> & c")
                .containsEntry(values.get(1).user(), "a < b && c > d");
    }

    @Test
    void longValuesTheUserChoseToCutStepCleanAreCutAndDocumented() {
        // Seen in user test chat 182: the user said "Cut them", nothing kept it, and the parser's Warning kept every
        // step from being clean, so the build couldn't be processed. Recorded with the draft, it is benign.
        final List<Value> values = List.of(AwkwardValues.named("long"), AwkwardValues.named("xml_special"));
        final String build = "aw-cut";
        final String feed = "AW-CUT";
        createFeed(build, feed);
        final long raw = upload(feed, jsonLines(values));
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "build", build, "long_values", "cut"));
        assertThat(String.valueOf(draft.get("notes"))).contains("The user chose to cut them");

        final Walked walked = translate(build, feed, raw, JSON, mapping(draft, null, null), null, values);

        final Map<String, Object> cut = list(walked.stepped(), "groups").stream()
                .filter(group -> "jsonParser".equals(group.get("element"))).findFirst().orElseThrow();
        assertThat(cut).containsEntry("class", "benign").containsEntry("accepted", true);
        assertThat(String.valueOf(cut.get("reason"))).contains("the user chose to cut string values");
        assertThat(walked.stored().get(values.getFirst().user())).as("cut by Stroom's jsonParser")
                .hasSize(10_000);
        final Map<String, Object> documented = agreed("write_documentation", args("build", build, "pipeline_uuid",
                walked.pipeline(), "markdown", "## Purpose and data\n\nLong values.\n", "change", "Created",
                "stream_ids", List.of(raw)));
        assertThat(documentation(uuidOf(documented))).contains("the user chose to cut string values",
                "<!-- stroom-mcp accepted errors");
        assertThat(String.valueOf(documented.get("note"))).contains("Say so in Open items");
    }

    @Test
    void longValuesTheUserChoseToKeepWholeAreKeptByThePipelinesParser() {
        final List<Value> values = List.of(AwkwardValues.named("long"), AwkwardValues.named("xml_special"));
        final String build = "aw-whole";
        final String feed = "AW-WHOLE";
        createFeed(build, feed);
        final long raw = upload(feed, jsonLines(values));
        // The form's first answer, as a user taking it: kept whole.
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "build", build));
        assertThat(formMessages()).anyMatch(message -> message.startsWith("Some values are longer than Stroom's "
                                                                          + "JSON parser keeps"));
        assertThat(String.valueOf(draft.get("notes"))).contains("The user chose to keep them whole",
                "jsonParser.stringTruncateLength to 30000");

        // create_pipeline sets the parser's limit: clean at once, and the value whole.
        final Walked walked = translate(build, feed, raw, JSON, mapping(draft, null, null), null, values);

        assertThat(walked.stored()).as("each value as Stroom stores it").isEqualTo(expected(values, text -> text));
    }

    @Test
    void xml11IsTranslatedCheckedAndFetchedWithItsCharacterReferences() {
        final List<Value> values = AwkwardValues.xmlSafe();
        final StringBuilder sample = new StringBuilder("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<records>\n");
        for (int i = 0; i < values.size(); i++) {
            final Value value = values.get(i);
            sample.append("<record><ts>").append(TS.formatted(i)).append("</ts><user>").append(value.user())
                    .append("</user><host>ws01</host><msg>").append(xml11(value.text())).append("</msg></record>\n");
        }
        sample.append("</records>\n");

        final Walked walked = walk("aw-xml", XML, sample.toString(), values, null);

        // The sample check reads the XML 1.1 sample (it had read it as XML 1.0: "not XML a parser could read").
        assertThat(map(walked.built(), "sample_check")).containsEntry("records", values.size())
                .doesNotContainKey("note");
        assertThat(walked.stored()).isEqualTo(expected(values, text -> text));
    }

    @Test
    void keyValueValuesAreReadAsTheDataSplitterReadsThemTheirEscapesUndone() {
        final List<Value> values = AwkwardValues.lineSafe();
        final StringBuilder sample = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            final Value value = values.get(i);
            sample.append("ts=").append(TS.formatted(i)).append(" user=").append(value.user())
                    .append(" host=ws01 msg=\"").append(value.text().replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\"\n");
        }

        final Walked walked = walk("aw-kv", TEXT, sample.toString(), values, "key_value");

        // The escapes are the source's: the draft reads them with unescape_backslashes.
        assertThat(entry(map(walked.draft(), "mapping"), "msg")).containsEntry("transform", "unescape_backslashes");
        assertThat(walked.stored()).isEqualTo(expected(values, TestE2eAwkwardCharacters::asTheDataSplitterReads));
    }

    @Test
    void csvFieldsHoldingDelimitersQuotesAndLineEndsAreReadWhole() {
        final List<Value> values = AwkwardValues.xmlSafe();
        final StringBuilder sample = new StringBuilder("ts,user,host,msg\n");
        for (int i = 0; i < values.size(); i++) {
            final Value value = values.get(i);
            sample.append(TS.formatted(i)).append(',').append(value.user()).append(",ws01,\"")
                    .append(value.text().replace("\"", "\"\"")).append("\"\n");
        }

        final Walked walked = walk("aw-csv", TEXT, sample.toString(), values, "delimited");

        assertThat(map(walked.splitter(), "spec")).containsEntry("quoted_newlines", true);
        assertThat(entry(map(walked.draft(), "mapping"), "msg")).containsEntry("transform", "unescape_quotes");
        assertThat(walked.stored()).isEqualTo(expected(values, TestE2eAwkwardCharacters::asTheDataSplitterReads));
    }

    @Test
    void bytesThatArentUtf8AreSaidWithHowTheyReadAsWindows1252() {
        final String build = "aw-bytes";
        final String feed = "AW-BYTES";
        createFeed(build, feed);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.writeBytes("ts=2026-11-10T10:58:00.000Z user=aw-plain msg=\"plain\"\n".getBytes(StandardCharsets.UTF_8));
        bytes.writeBytes(AwkwardValues.notUtf8Line("aw-not-utf8"));
        final long raw = uploadBytes(feed, "/home/user/awkward.log", bytes.toByteArray());

        final Map<String, Object> surveyed = json("survey_feed", args("stream_ids", List.of(raw), "build", build));
        assertThat(map(list(surveyed, "per_stream").getFirst(), "undecodable_bytes"))
                .containsEntry("undecodable_bytes", 6);
        final Map<String, Object> fetched = json("fetch_events", args("streamId", raw, "firstRecord", 0,
                "recordCount", 2));
        final Map<String, Object> undecodable = map(fetched, "undecodable_bytes");
        assertThat(undecodable).containsEntry("encoding", "UTF-8");
        assertThat(map(undecodable, "as_windows_1252")).containsEntry("93", "“").containsEntry("94", "”")
                .containsEntry("E9", "é");
        assertThat(String.valueOf(undecodable.get("advice"))).contains("windows-1252");

        // Processed as the feed's UTF-8: each byte replaced, and the events still valid.
        final Map<String, Object> splitter = json("build_data_splitter", args("stream_ids", List.of(raw),
                "save_as", feed + "-DS", "build", build));
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Awkward", "system_name", "Awkward", "environment", "Test",
                "splitter", splitter.get("spec")));
        final Walked walked = translate(build, feed, raw, TEXT, mapping(draft, null, null), splitter.get("spec"),
                List.of(), this::acceptUndecodable);
        assertThat(walked.stored()).containsEntry("aw-plain", "plain")
                .containsEntry("aw-not-utf8", "�hi� caf� � �( �");
    }

    @Test
    void aSourceTypeHeldInAJsonStringHoldingAwkwardValuesIsReadAlikeByTheToolsAndTheGroupsPipeline() {
        final String feed = "AW-HELD";
        final String build = "aw-held-1";
        final String group = "Awkward held";
        createFeed("aw-held-src", feed);
        // The loggers: plain, one holding a form feed (as chat 181's Teams message), one astral; each record's held
        // JSON also holds an awkward value of its own.
        final List<String> loggers = List.of("com.x.Plain", "com.x.Ctl\fFeed", "com.x.Rocket🚀",
                "com.x.Other");
        final List<Value> values = AwkwardValues.without(AwkwardValues.ALL, "nul", "long");
        final StringBuilder sample = new StringBuilder();
        int n = 0;
        for (int i = 0; i < 2; i++) {
            for (final String logger : loggers) {
                for (final Value value : values) {
                    // Each logger's own shape, as different sources write different fields.
                    final Map<String, Object> event = new LinkedHashMap<>();
                    event.put("logger_name", logger);
                    event.put("msg", value.text());
                    event.put("field_of_" + loggers.indexOf(logger), n);
                    final Map<String, Object> record = new LinkedHashMap<>();
                    record.put("timestamp", "2026-11-10T" + String.format("%02d:%02d:%02d", 10 + n / 3600,
                            n / 60 % 60, n % 60) + ".000Z");
                    record.put("host", "app0" + n % 3);
                    record.put("event", ToolJson.dumps(event));
                    sample.append(ToolJson.dumps(record)).append('\n');
                    n++;
                }
            }
        }
        final long stream = upload(feed, sample.toString());
        promote("aw-held-src", Map.of("Feed", "E2E Feeds/Awkward"));
        // The tools' source types, as read: the form feed taken out (no group XSLT can hold it), the rocket kept.
        final List<String> chosen = List.of("com.x.Plain", "com.x.CtlFeed", "com.x.Rocket🚀");

        json("start_build", args("build", build, "feeds", List.of(feed)));
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(chosen, group));
        final Map<String, Object> surveyed = json("survey_feed", args("feed", feed, "build", build));
        dump("survey_feed held", surveyed);
        final Map<String, Object> sources = map(surveyed, "source_types");
        assertThat(String.valueOf(sources.get("field"))).startsWith("event>logger_name");
        assertThat((Map<String, Object>) sources.get("records_read_by_value")).containsKeys("com.x.Plain",
                "com.x.CtlFeed", "com.x.Rocket🚀", "com.x.Other");

        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed,
                "patterns", "exact"));
        final Map<String, Object> scoped = json("record_source_types", args("build", build, "feed", feed,
                "patterns", "exact", "reviewed", shown.get("review_id"), "group", group));
        assertThat(strings(scoped, "scope")).containsExactlyInAnyOrderElementsOf(chosen);
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", build, "feeds",
                List.of(feed), "schema_version", VERSION, "stream_ids", List.of(stream), "mapping",
                heldMapping()));
        assertThat(saved).as("the group saved: %s", saved.get("problems")).containsEntry("ok", true);
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", build, "group", group,
                "template_uuid", templateUuid("translation", JSON))));
        final Map<String, Object> stepped = stepSample(pipeline, List.of(stream));
        assertThat(stepped).as("stepped clean: %s", stepped).containsEntry("verdict", "clean");
        final Map<String, Object> done = list(process(pipeline, List.of(stream)), "streams").getFirst();
        final List<Long> events = ids(done, "events");
        assertThat(events).hasSize(1);
        assertThat(json("check_events", args("stream_ids", events))).containsEntry("ok", true);
        // Exactly the chosen source types' records, as the tools read them: all but com.x.Other's.
        final long expected = 2L * 3 * values.size();
        assertThat(TestE2eSourceTypeGroups.count(storedText(events.getFirst()), "<Event>")).isEqualTo(expected);
        assertThat(passedOverInProcessing(pipeline)).isEqualTo(n - expected);
    }

    // --- the walk ---

    // An agent's walk of a format: feed, sample, survey, the converter (text), the draft and its build, a pipeline,
    // stepping, processing, check_events and fetch_events.
    private Walked walk(final String build,
                        final String template,
                        final String sample,
                        final List<Value> values,
                        final String splitterKind) {
        return walk(build, template, sample, values, splitterKind, null);
    }

    // As walk, with what the agent does about a first step that isn't clean (null: it must be clean).
    private Walked walk(final String build,
                        final String template,
                        final String sample,
                        final List<Value> values,
                        final String splitterKind,
                        final Fix fix) {
        final String feed = build.toUpperCase();
        createFeed(build, feed);
        final long raw = upload(feed, sample);
        final Map<String, Object> surveyed = json("survey_feed", args("stream_ids", List.of(raw), "build", build));
        assertThat(number(surveyed.get("records_read"))).as("surveyed: %s", surveyed.get("per_stream"))
                .isEqualTo(values.size());
        Map<String, Object> splitter = null;
        if (splitterKind != null) {
            splitter = json("build_data_splitter", args("stream_ids", List.of(raw), "save_as", feed + "-DS",
                    "build", build));
            assertThat(map(splitter, "spec")).containsEntry("kind", splitterKind);
            assertThat(number(splitter.get("records"))).as("every record parsed").isEqualTo(values.size());
        }
        final Object spec = splitter == null
                ? null
                : splitter.get("spec");
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Awkward", "system_name", "Awkward", "environment", "Test", "splitter", spec));
        final Walked walked = translate(build, feed, raw, template, mapping(draft, null, null), spec, values, fix);
        return new Walked(walked.build(), walked.raw(), walked.pipeline(), walked.events(), splitter, draft,
                walked.built(), walked.stepped(), walked.processed(), walked.stored());
    }

    // From the mapping to fetched Events: build_translation_xslt, create_pipeline, step_sample, processing,
    // check_events (which must pass: Stroom's own output is valid) and fetch_events (whose events file must parse).
    private Walked translate(final String build,
                             final String feed,
                             final long raw,
                             final String template,
                             final Map<String, Object> mapping,
                             final Object spec,
                             final List<Value> values) {
        return translate(build, feed, raw, template, mapping, spec, values, null);
    }

    // As translate, with what the agent does about a first step that isn't clean (null: it must be clean).
    private Walked translate(final String build,
                             final String feed,
                             final long raw,
                             final String template,
                             final Map<String, Object> mapping,
                             final Object spec,
                             final List<Value> values,
                             final Fix fix) {
        final Map<String, Object> built = agreed("build_translation_xslt", args("mapping", mapping,
                "schema_version", VERSION, "stream_ids", List.of(raw), "build", build, "name", feed + "-Events",
                "splitter", spec));
        assertThat(built).as("built: %s", built.get("problems")).containsEntry("ok", true);
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", templateUuid("translation", template))));
        Map<String, Object> stepped = json("step_sample", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(raw)));
        assertThat(map(stepped, "groups_by_class")).as("stepped: %s", stepped.get("groups"))
                .containsEntry("blocking", 0);
        if (fix != null) {
            assertThat(stepped.get("verdict")).as("a first step for the agent to act on").isNotEqualTo("clean");
            fix.apply(build, pipeline, raw, list(stepped, "groups"));
            stepped = json("step_sample", args("pipeline_uuid", pipeline, "stream_ids", List.of(raw)));
        }
        assertThat(stepped).as("stepped: %s", stepped.get("groups")).containsEntry("verdict", "clean");
        final Map<String, Object> processed = process(pipeline, List.of(raw));
        assertThat(processed).as("processed: %s", processed.get("streams")).containsEntry("gate", "pass");
        final long events = number(((List<?>) list(processed, "streams").getFirst().get("events")).getFirst());
        final Map<String, Object> checked = json("check_events", args("stream_ids", List.of(events)));
        assertThat(checked).as("check_events on Stroom's valid Events: %s", checked).containsEntry("ok", true);
        return new Walked(build, raw, pipeline, events, null, null, built, stepped, processed,
                fetched(events, Math.max(values.size(), 2)));
    }

    // The agent's fix for the jsonParser's cut, as the review's reason says: its stringTruncateLength raised.
    private void wholeStrings(final String build,
                              final String pipeline,
                              final long raw,
                              final List<Map<String, Object>> groups) {
        final Map<String, Object> cut = groups.stream().filter(group -> "jsonParser".equals(group.get("element")))
                .findFirst().orElseThrow();
        assertThat(cut).containsEntry("class", "review");
        assertThat(String.valueOf(cut.get("reason"))).contains("stringTruncateLength", "set_properties");
        agreed("update_pipeline", args("pipeline_uuid", pipeline, "set_properties", List.of(Map.of(
                "element", "jsonParser", "name", "stringTruncateLength", "value", 20_000))));
    }

    // The user accepts the replaced bytes as the source's (a fix of the feed's encoding or the source is theirs):
    // the review recorded as accepted, as triage says to ask.
    private void acceptUndecodable(final String build,
                                   final String pipeline,
                                   final long raw,
                                   final List<Map<String, Object>> groups) {
        final List<Map<String, Object>> bytes = groups.stream().filter(group -> String.valueOf(group.get("reason"))
                .contains("character set")).toList();
        assertThat(bytes).as("un-decodable bytes: %s", groups).isNotEmpty()
                .allSatisfy(group -> assertThat(group).containsEntry("class", "review"));
        final List<Map<String, Object>> accepted = bytes.stream().map(group -> Map.<String, Object>of(
                "element", group.get("element"),
                "example", list(group, "examples").getFirst().get("message"),
                "reason", "The test's bytes aren't UTF-8 on purpose")).toList();
        agreed("write_documentation", args("build", build, "pipeline_uuid", pipeline, "markdown",
                "## Purpose and data\n\nAwkward bytes.\n", "change", "Created", "stream_ids", List.of(raw),
                "accept_errors", accepted));
    }

    // fetch_events' records of an Events stream, written to an events file as fetch_events' out writes them (the
    // file is chat only, so not callable over /mcp), parsed: each user's Description.
    private Map<String, String> fetched(final long events, final int count) {
        final Map<String, Object> page = json("fetch_events", args("streamId", events, "firstRecord", 0,
                "recordCount", count));
        final List<String> records = ((List<?>) page.get("records")).stream()
                .map(record -> record instanceof final Map<?, ?> fields
                        ? String.valueOf(fields.get("data"))
                        : String.valueOf(record))
                .toList();
        final StringBuilder file = new StringBuilder(EventsFile.start());
        for (int i = 0; i < records.size(); i++) {
            file.append(EventsFile.element(events, i + 1, records.get(i)));
        }
        file.append(EventsFile.end());
        assertThat(file.toString()).as("each event kept as XML, not escaped text").doesNotContain("format=\"text\"");
        final Map<String, String> stored = new LinkedHashMap<>();
        for (final Element event : descendants(parse(file.toString()).getDocumentElement(), "Event")) {
            stored.put(textAt(event, "EventSource/User/Id"), descendants(event, "Description").stream()
                    .findFirst()
                    .map(Element::getTextContent)
                    .orElse(null));
        }
        return stored;
    }

    // --- indexing ---

    private void lucene(final Walked walked) {
        final String build = walked.build();
        final List<Long> events = List.of(walked.events());
        client.answeringWith(choosing("Stroom flat convention"));
        client.json("get_field_conventions", Map.of());
        client.answeringWith(McpTestClient::agree);
        final String indexName = build.toUpperCase() + "-INDEX";
        final Map<String, Object> plan = map(client.json("draft_index_mapping", Map.of("backend", "lucene",
                "index_name", indexName, "convention", "stroom-flat", "events_stream_ids", events)), "plan");
        final String index = uuidOf(agreed("create_index_doc", Map.of("build", build, "backend", "lucene",
                "name", indexName, "time_field", plan.get("time_field"))));
        final Map<String, Object> template = templates("indexing").stream()
                .filter(candidate -> "lucene".equals(candidate.get("backend"))).findFirst().orElseThrow();
        final String xslt = uuidOf(client.json("save_xslt", Map.of("build", build, "name", indexName + "-XSLT",
                "index_plan", plan)));
        final String pipeline = uuidOf(agreed("create_indexing_pipeline", Map.of("build", build,
                "name", indexName + " - Indexing", "template_uuid", template.get("uuid"), "xslt_uuid", xslt,
                "index_uuid", index)));
        assertThat(stepSample(pipeline, events)).containsEntry("verdict", "clean");
        agreed("create_processor_filter", Map.of("pipeline_uuid", pipeline, "stream_ids", events,
                "source_pipeline_uuid", walked.pipeline()));
        processAll();
        flushIndexShards();
        assertThat(json("wait_for_processing", Map.of("pipeline_uuid", pipeline, "stream_ids", events,
                "expect_events", false, "timeout_seconds", 10))).containsEntry("gate", "pass");
        verified(build, index, "lucene", walked, plan, pipeline);
        searched(index);
    }

    private void elastic(final Walked walked) {
        final ElasticTestNode node = ElasticTestNode.getOrSkip();
        final String build = walked.build();
        final List<Long> events = List.of(walked.events());
        final String indexName = "e2e-awkward-" + STAMP + "-v1";
        indices.add(indexName);
        final DocRef cluster = liveElasticCluster(node, "E2E Live Elasticsearch (awkward)");
        final String template = elasticEventsTemplate("E2E Events to Elasticsearch (awkward)",
                "Fixture for TestE2eAwkwardCharacters");
        final Map<String, Object> plan = map(client.json("draft_index_mapping", Map.of("backend", "elasticsearch",
                "index_name", indexName, "convention", "stroom-flat", "events_stream_ids", events)), "plan");
        final String xslt = uuidOf(client.json("save_xslt", args("build", build, "name", indexName + "-XSLT",
                "index_plan", plan)));
        final String pipeline = uuidOf(agreed("create_indexing_pipeline", args("build", build,
                "name", indexName + " - Indexing", "template_uuid", template, "xslt_uuid", xslt,
                "index_name", indexName, "cluster_uuid", cluster.getUuid())));
        assertThat(stepSample(pipeline, events)).containsEntry("verdict", "clean");
        final Map<String, Object> agreedTemplate = agreed("propose_index_template", args("pipeline_uuid", pipeline,
                "plan", plan, "events_stream_ids", events, "without_example", true));
        dump("propose_index_template", agreedTemplate);
        final String indexTemplate = applyDevTools(node, (String) agreedTemplate.get("dev_tools"));
        if (indexTemplate != null) {
            indexTemplates.add(indexTemplate);
        }
        final Map<String, Object> started = agreed("create_processor_filter", args("pipeline_uuid", pipeline,
                "stream_ids", events, "source_pipeline_uuid", walked.pipeline()));
        processAll();
        assertThat(json("wait_for_processing", args("pipeline_uuid", pipeline, "stream_ids", events,
                "expect_events", false, "filter_id", started.get("filter_id"), "timeout_seconds", 10)))
                .containsEntry("gate", "pass");
        node.refresh(indexName);
        assertThat(node.count(indexName, null)).isEqualTo(walked.stored().size());
        final String index = uuidOf(agreed("create_index_doc", args("build", build, "backend", "elasticsearch",
                "name", indexName, "time_field", plan.get("time_field"), "index_name", indexName,
                "cluster_uuid", cluster.getUuid())));
        verified(build, index, "elasticsearch", walked, plan, pipeline);
        searched(index);
    }

    // verify_index over every planned field: each document found, and the first hit traced to its event (on
    // Elasticsearch, that had failed: its XSLT's output, holding &#xc;, was read as XML 1.0).
    private void verified(final String build,
                          final String index,
                          final String backend,
                          final Walked walked,
                          final Map<String, Object> plan,
                          final String pipeline) {
        final List<String> fields = ((List<Map<String, Object>>) plan.get("fields")).stream()
                .map(field -> (String) field.get("name")).toList();
        final Map<String, Object> verified = agreed("verify_index", args("build", build, "index_uuid", index,
                "backend", backend, "stream_ids", List.of(walked.events()), "expected_documents",
                walked.stored().size(), "fields", fields, "pipeline_uuid", pipeline));
        final List<Map<String, Object>> checks = list(verified, "checks");
        assertThat(verified).as("verified on %s: %s", backend, checks).containsEntry("passed", true);
        // Stroom's search results cut a string past 1,000 characters (its maxStringFieldLength), with a Warning:
        // the long value's, said with the check.
        assertThat(String.valueOf(checks.getFirst().get("errors"))).contains("Truncating string to 1000 characters");
        assertThat(map(checks.getFirst(), "trace")).containsEntry("traced", true);
    }

    // search_events finds each searched value's event by its user.
    private void searched(final String index) {
        for (final String name : SEARCHED) {
            final String user = AwkwardValues.named(name).user();
            final Map<String, Object> found = json("search_events", args("index", index, "where",
                    List.of(Map.of("field", "UserId", "value", user)), "limit", 3, "allStreams", true,
                    "timeFrom", "2000-01-01T00:00:00.000Z"));
            assertThat(number(found.get("total"))).as("%s found: %s", user, found).isEqualTo(1);
        }
    }

    // --- the samples and mappings ---

    private static String jsonLines(final List<Value> values) {
        final StringBuilder sample = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            final Value value = values.get(i);
            sample.append("{\"ts\": \"").append(TS.formatted(i)).append("\", \"user\": \"").append(value.user())
                    .append("\", \"host\": \"ws01\", \"msg\": ").append(value.json()).append("}\n");
        }
        return sample.toString();
    }

    // XML 1.1 text: the specials escaped, and each character XML 1.1 holds only as a reference (a control, CR, the
    // line ends it would normalise) as one.
    private static String xml11(final String text) {
        final StringBuilder out = new StringBuilder();
        text.codePoints().forEach(cp -> {
            switch (cp) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> {
                    if ((cp < 0x20 && cp != '\t' && cp != '\n') || (cp >= 0x7f && cp <= 0x9f) || cp == 0x2028) {
                        out.append("&#x").append(Integer.toHexString(cp)).append(';');
                    } else {
                        out.appendCodePoint(cp);
                    }
                }
            }
        });
        return out.toString();
    }

    // The draft, as the agent settles it: its common entries kept (the time with its format, the user, the host,
    // the message with any transform the draft gave it, or the one given), and one rule of the agent's own.
    private static Map<String, Object> mapping(final Map<String, Object> draft,
                                               final String field,
                                               final String transform) {
        final Map<String, Object> mapping = new LinkedHashMap<>(map(draft, "mapping"));
        final List<Map<String, Object>> common = new ArrayList<>();
        for (final Map<String, Object> entry : (List<Map<String, Object>>) mapping.get("common")) {
            final Map<String, Object> settled = new LinkedHashMap<>(entry);
            switch (String.valueOf(entry.get("path"))) {
                case "EventSource/System/Name" -> settled.put("value", "Awkward");
                case "EventSource/System/Environment" -> settled.put("value", "Test");
                case "EventSource/Generator" -> settled.put("value", "awkward");
                default -> {
                }
            }
            if (field != null && field.equals(entry.get("field"))) {
                settled.put("transform", transform);
            }
            common.add(settled);
        }
        if (common.stream().noneMatch(entry -> String.valueOf(entry.get("path")).startsWith("EventSource/Device"))) {
            common.add(Map.of("path", "EventSource/Device/HostName", "value", "ws01"));
        }
        mapping.put("common", common);
        mapping.put("events", List.of(Map.of("name", "message", "fields", List.of(
                Map.of("path", "EventDetail/TypeId", "value", "Message"),
                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user")))));
        return mapping;
    }

    private static Map<String, Object> entry(final Map<String, Object> mapping, final String field) {
        return ((List<Map<String, Object>>) mapping.get("common")).stream()
                .filter(entry -> field.equals(entry.get("field"))).findFirst().orElseThrow();
    }

    // The group's mapping: the wrapper's entries, and the held value kept whole as the description.
    private static Map<String, Object> heldMapping() {
        return (Map<String, Object>) ToolJson.loads("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventTime/TimeCreated", "field": "timestamp"},
                            {"path": "EventSource/System/Name", "value": "Awkward"},
                            {"path": "EventSource/System/Environment", "value": "Test"},
                            {"path": "EventSource/Generator", "value": "Awkward"},
                            {"path": "EventSource/Device/HostName", "field": "host"}],
                 "events": [{"name": "line",
                             "fields": [{"path": "EventDetail/TypeId", "value": "HeldLine"},
                                        {"path": "EventDetail/Description", "field": "event"},
                                        {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                        {"path": "EventDetail/Authenticate/User/Id", "value": "held"}]}]}""");
    }

    // Stroom's Data Splitter's reading of a value (core): the control characters but tab and new line dropped as
    // it reads (DS3Reader, CR among them), then white space trimmed from each end (DS3Parser.normaliseBuffer).
    private static String asTheDataSplitterReads(final String text) {
        final StringBuilder read = new StringBuilder();
        text.chars().filter(c -> c >= ' ' || c == '\n' || c == '\t').forEach(c -> read.append((char) c));
        int start = 0;
        int end = read.length();
        while (start < end && read.charAt(start) <= ' ') {
            start++;
        }
        while (end > start && read.charAt(end - 1) <= ' ') {
            end--;
        }
        return read.substring(start, end);
    }

    private static Map<String, String> expected(final List<Value> values, final UnaryOperator<String> asStored) {
        final Map<String, String> expected = new LinkedHashMap<>();
        values.forEach(value -> expected.put(value.user(), asStored.apply(value.text())));
        return expected;
    }

    // --- Stroom ---

    // upload_sample files=: the file's bytes posted by the command it gives, as the user's terminal would.
    private long uploadBytes(final String feed, final String path, final byte[] content) {
        final Map<String, Object> given = json("upload_sample", args("feed", feed, "files", List.of(path)));
        final Map<String, Object> command = list(given, "commands").getFirst();
        final McpTerminal.Posted posted = McpTerminal.run(injector, admin, (String) command.get("bash"), path,
                content);
        assertThat(posted.status()).as("posted: %s", posted.body()).isEqualTo(200);
        return number(posted.body().get("stream_id"));
    }

    // promote_build: the layout shown first (needs_review), then promoted once the user agrees in the form.
    private void promote(final String build, final Map<String, Object> destinations) {
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                destinations));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        final Map<String, Object> promoted = json("promote_build", args("build", build, "destinations",
                destinations, "reviewed", review.get("review_id")));
        assertThat(promoted).as("promoted: %s", promoted).containsKey("promoted");
    }


    // --------------------------------------------------------------------------------


    /// What the agent does about a first step that isn't clean.
    @FunctionalInterface
    private interface Fix {

        /// @param build    The build.
        /// @param pipeline The pipeline.
        /// @param raw      The sample's raw stream.
        /// @param groups   The step's groups.
        void apply(String build, String pipeline, long raw, List<Map<String, Object>> groups);
    }


    /// What a walk made and read.
    ///
    /// @param build     The build.
    /// @param raw       The sample's raw stream.
    /// @param pipeline  The translation pipeline.
    /// @param events    The Events stream processing made.
    /// @param splitter  build_data_splitter's answer, or null.
    /// @param draft     draft_translation_mapping's answer, or null.
    /// @param built     build_translation_xslt's answer.
    /// @param stepped   step_sample's answer.
    /// @param processed wait_for_processing's answer.
    /// @param stored    Each user's Description, as fetch_events read the Events.
    private record Walked(String build,
                          long raw,
                          String pipeline,
                          long events,
                          Map<String, Object> splitter,
                          Map<String, Object> draft,
                          Map<String, Object> built,
                          Map<String, Object> stepped,
                          Map<String, Object> processed,
                          Map<String, String> stored) {

    }
}
