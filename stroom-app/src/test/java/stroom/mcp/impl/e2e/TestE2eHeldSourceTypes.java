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

import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.security.api.SecurityContext;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// An aggregated feed whose source type is held inside a string field, through the REAL tools on a real Stroom (seen
/// in external testing, 2026-11-17): a wrapper's `event` holds escaped JSON naming its logger (`logger_name`) in some
/// records, key=value pairs (`logger_name=`) in others, XML naming it in an attribute (`<Log logger=...>`) in others,
/// and plain text in the rest. The survey offers the reading joining the formats (`event>logger_name |
/// event>xml:@logger`) and the user takes it; record_source_types writes a row a logger and the `(no value)` row
/// for the text records; a source type group of JSON, key=value, XML and text records alike is built, and its
/// pipeline, processed, translates exactly its source types' records, the text ones among them, passing the rest
/// over.
@SuppressWarnings("unchecked")
class TestE2eHeldSourceTypes extends AbstractE2eTest {

    private static final String FEED = "E2E-HELD";
    private static final String FOLDER = "E2E Feeds/Held";
    private static final String BUILD = "held-1";
    private static final String GROUP = "Held core";
    private static final String READING = "event>logger_name | event>xml:@logger";
    private static final int RECORDS = 180;
    private static final String WRAPPER = """
            [{"path": "EventTime/TimeCreated", "field": "timestamp"},
             {"path": "EventSource/System/Name", "value": "Held"},
             {"path": "EventSource/System/Environment", "value": "Production"},
             {"path": "EventSource/Generator", "value": "Held"},
             {"path": "EventSource/Device/HostName", "field": "host"}]""";

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    @Test
    void aSourceTypeHeldInsideAFieldIsReadAlikeByTheSurveyTheRegistryAndTheGroupsPipeline() {
        createFeed("held-src", FEED);
        final String text = lines();
        final long stream = upload(FEED, text);
        promote("held-src", Map.of("Feed", FOLDER));
        final String template = templateUuid("translation", "Event Data (JSON)");
        final List<String> chosen = List.of("com.x.Db", "com.x.Xml", SourceTypeRows.NO_VALUE);
        final long expected = text.lines().filter(line -> line.contains("com.x.Db") || line.contains("com.x.Xml")
                                                          || line.contains("logged in from")).count();

        // The survey offers the reading into the held field first, joining its formats; the user takes it.
        json("start_build", args("build", BUILD, "feeds", List.of(FEED)));
        final int formsBefore = client.forms().size();
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(chosen, GROUP));
        final Map<String, Object> surveyed = json("survey_feed", args("feed", FEED, "build", BUILD));
        dump("survey_feed", surveyed);
        final ElicitFormRequest asked = formsSince(formsBefore).stream()
                .filter(form -> form.message().contains("aggregates several sources"))
                .findFirst()
                .orElseThrow();
        assertThat(asked.message()).contains(READING, "read inside a held field", "(no value)");
        final Map<String, Object> sources = map(surveyed, "source_types");
        assertThat(sources).containsEntry("field", READING);
        assertThat((Map<String, Object>) sources.get("records_read_by_value")).containsKeys("com.x.Auth",
                "com.x.Db", "com.x.Mail", "com.x.Xml");

        // The registry: a row a logger, and the (no value) row for the plain-text records.
        final Map<String, Object> shown = json("record_source_types", args("build", BUILD, "feed", FEED,
                "patterns", "exact"));
        final Map<String, Object> scoped = json("record_source_types", args("build", BUILD, "feed", FEED,
                "patterns", "exact", "reviewed", shown.get("review_id"), "group", GROUP));
        dump("record_source_types", scoped);
        assertThat(scoped).as("scoped: %s", scoped).containsEntry("group", GROUP);
        assertThat(strings(scoped, "scope")).containsExactlyInAnyOrderElementsOf(chosen);

        // The group: its source types' records told by the reading, JSON, key=value, XML and text alike.
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", BUILD, "feeds",
                List.of(FEED), "schema_version", VERSION, "stream_ids", List.of(stream), "mapping", mapping()));
        dump("build_translation_xslt " + GROUP, saved);
        assertThat(saved).as("the group saved: %s", saved.get("problems")).containsEntry("ok", true);
        final Map<String, Object> group = map(saved, "group");
        assertThat(strings(group, "source_types")).containsExactlyInAnyOrderElementsOf(chosen);
        final String groupXslt = readXslt(uuidOf(map(group, "xslt"))).getData();
        assertThat(groupXslt).contains("json-to-xml", "parse-xml", "reading");
        final Map<String, Object> registry = SourceTypeRegistry.read(documentation(uuidOf(map(group, "registry"))));
        assertThat(registry).containsEntry("field", READING);
        final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        list(registry, "rows").forEach(row -> rows.put((String) row.get("name"), row));
        assertThat(rows).containsOnlyKeys("com.x.Auth", "com.x.Db", "com.x.Mail", "com.x.Xml",
                SourceTypeRows.NO_VALUE);
        assertThat(rows.get(SourceTypeRows.NO_VALUE)).containsEntry("match", SourceTypeRows.NONE);

        // Its pipeline, stepped clean and processed: exactly its source types' records translated, the rest passed
        // over.
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", BUILD, "group", GROUP,
                "template_uuid", template)));
        final Map<String, Object> stepped = stepSample(pipeline, List.of(stream));
        assertThat(stepped).as("stepped clean: %s", stepped).containsEntry("verdict", "clean");
        final Map<String, Object> done = list(process(pipeline, List.of(stream)), "streams").getFirst();
        dump("processed", done);
        final List<Long> events = ids(done, "events");
        assertThat(events).hasSize(1);
        final String written = streamText(events.getFirst());
        assertThat(TestE2eSourceTypeGroups.count(written, "<Event>")).isEqualTo(expected);
        assertThat(written).contains("logger_name=com.x.Db", "com.x.Xml", "logged in from")
                .doesNotContain("com.x.Auth", "com.x.Mail");
        assertThat(passedOverInProcessing(pipeline)).isEqualTo(RECORDS - expected);
    }

    // The mapping an agent would send for the group: the wrapper's entries, and the held value kept whole.
    private static Map<String, Object> mapping() {
        return (Map<String, Object>) ToolJson.loads("""
                {"input": "json", "json_layout": "lines",
                 "common": %s,
                 "ignored_fields": ["system"],
                 "events": [{"name": "line", "allow_unknown": "a test of which records the group takes",
                             "fields": [{"path": "EventDetail/TypeId", "value": "HeldLine"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "event",
                                         "field": "event"}]}]}""".formatted(WRAPPER));
    }

    // The sample: a sixth each of JSON from com.x.Auth twice, JSON from com.x.Db, key=value from com.x.Db or
    // com.x.Mail, XML from com.x.Xml, and plain text.
    private static String lines() {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < RECORDS; i++) {
            final boolean even = i / 6 % 2 == 0;
            final String event = switch (i % 6) {
                case 0, 1 -> even
                        ? "{\"logger_name\": \"com.x.Auth\", \"action\": \"login\", \"user\": \"u" + i
                          + "\", \"client\": \"10.0.0." + i % 9 + "\"}"
                        : "{\"logger_name\": \"com.x.Auth\", \"action\": \"logout\", \"user\": \"u" + i
                          + "\", \"session\": \"s" + i + "\"}";
                case 2 -> even
                        ? "{\"logger_name\": \"com.x.Db\", \"query\": \"select " + i + "\", \"rows\": " + i + "}"
                        : "{\"logger_name\": \"com.x.Db\", \"query\": \"update " + i + "\", \"table\": \"t" + i % 4
                          + "\"}";
                case 3 -> even
                        ? "logger_name=com.x.Db query=\"select " + i + "\" rows=" + i
                        : "logger_name=com.x.Mail from=a" + i + "@x.org to=b@x.org size=" + i;
                case 4 -> even
                        ? "<Log logger=\"com.x.Xml\"><Op>read</Op><Path>/data/" + i + "</Path></Log>"
                        : "<Log logger=\"com.x.Xml\"><Op>write</Op><Size>" + i + "</Size></Log>";
                default -> "User u" + i + " logged in from the portal after " + i + " tries";
            };
            final Map<String, Object> record = new LinkedHashMap<>();
            record.put("timestamp", "2026-11-17T10:" + String.format("%02d:%02d", i / 60, i % 60) + ".000Z");
            record.put("host", "app0" + i % 3);
            record.put("system", "Teams");
            record.put("event", event);
            out.append(ToolJson.dumps(record)).append('\n');
        }
        return out.toString();
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

    // A stream's text, as Stroom stores it.
    private String streamText(final long id) {
        return securityContext.asProcessingUserResult(() -> {
            try (final Source source = streamStore.openSource(id)) {
                return SourceUtil.readString(source);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
