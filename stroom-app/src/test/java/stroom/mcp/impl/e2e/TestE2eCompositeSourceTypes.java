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

/// An aggregated feed whose source is told only by two fields together, through the REAL tools on a real Stroom
/// (decided 2026-11-17): the wrapper's own `system` (Teams, Skype for Business) and, inside the escaped JSON its
/// `event` holds, `logger_name` (System, Application), each pair's events with keys of their own; and a few records
/// whose `event` is plain text (no logger: the `(no value)` row). The survey offers the pair `system +
/// event>logger_name` first, with the evidence that neither alone tells the shapes apart; record_source_types writes
/// a row a pair, each matching both parts, and the `(no value)` row; a source type group of two of the pairs is
/// built, and its pipeline, processed, translates exactly those pairs' records, passing the rest over.
@SuppressWarnings("unchecked")
class TestE2eCompositeSourceTypes extends AbstractE2eTest {

    private static final String FEED = "E2E-COMPOSITE";
    private static final String FOLDER = "E2E Feeds/Composite";
    private static final String BUILD = "composite-1";
    private static final String GROUP = "UC core";
    private static final String PAIR = "system + event>logger_name";
    private static final List<String> SYSTEMS = List.of("Teams", "Skype for Business");
    private static final List<String> LOGGERS = List.of("System", "Application");
    private static final int PER_PAIR = 40;
    private static final int PLAIN = 8;
    // Each pair's two kinds of event, by their keys.
    private static final String[][][] KEYS = {{{"cpu", "memory"}, {"call_id", "meeting_id"}},
            {{"disk", "network"}, {"codec", "jitter"}}};
    private static final String WRAPPER = """
            [{"path": "EventTime/TimeCreated", "field": "timestamp"},
             {"path": "EventSource/System/Name", "value": "UC"},
             {"path": "EventSource/System/Environment", "value": "Production"},
             {"path": "EventSource/Generator", "value": "UC"},
             {"path": "EventSource/Device/HostName", "field": "host"}]""";

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    @Test
    void aSourceToldByTwoFieldsTogetherIsReadAlikeByTheSurveyTheRegistryAndTheGroupsPipeline() {
        createFeed("composite-src", FEED);
        final String text = lines();
        final long stream = upload(FEED, text);
        promote("composite-src", Map.of("Feed", FOLDER));
        final String template = templateUuid("translation", "Event Data (JSON)");
        final List<String> chosen = List.of("Teams System", "Skype for Business Application");

        // The survey offers the pair first, with what it rests on; the user takes it.
        json("start_build", args("build", BUILD, "feeds", List.of(FEED)));
        final int formsBefore = client.forms().size();
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(chosen, GROUP));
        final Map<String, Object> surveyed = json("survey_feed", args("feed", FEED, "build", BUILD));
        dump("survey_feed", surveyed);
        final ElicitFormRequest asked = formsSince(formsBefore).stream()
                .filter(form -> form.message().contains("aggregates several sources"))
                .findFirst()
                .orElseThrow();
        assertThat(asked.message()).contains(PAIR, "pairs of values", "offered because", "within system = ",
                "neither alone tells the sources");
        final Map<String, Object> sources = map(surveyed, "source_types");
        assertThat(sources).containsEntry("field", PAIR);
        assertThat((Map<String, Object>) sources.get("records_read_by_value")).containsKeys("Teams ‖ System",
                "Teams ‖ Application", "Skype for Business ‖ System", "Skype for Business ‖ Application");

        // The registry: a row a pair, each part matched exactly, and the (no value) row for the plain text.
        final Map<String, Object> shown = json("record_source_types", args("build", BUILD, "feed", FEED));
        dump("record_source_types shown", shown);
        assertThat(strings(shown, "rows")).contains("Teams System = Teams + = System", SourceTypeRows.NO_VALUE);
        final Map<String, Object> scoped = json("record_source_types", args("build", BUILD, "feed", FEED,
                "reviewed", shown.get("review_id"), "group", GROUP));
        dump("record_source_types", scoped);
        assertThat(scoped).as("scoped: %s", scoped).containsEntry("group", GROUP);
        assertThat(strings(scoped, "scope")).containsExactlyInAnyOrderElementsOf(chosen);

        // The group: its pairs' records told by both parts.
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", BUILD, "feeds",
                List.of(FEED), "schema_version", VERSION, "stream_ids", List.of(stream), "mapping", mapping()));
        dump("build_translation_xslt " + GROUP, saved);
        assertThat(saved).as("the group saved: %s", saved.get("problems")).containsEntry("ok", true);
        final Map<String, Object> group = map(saved, "group");
        assertThat(strings(group, "source_types")).containsExactlyInAnyOrderElementsOf(chosen);
        final String groupXslt = readXslt(uuidOf(map(group, "xslt"))).getData();
        assertThat(groupXslt).contains("source_type_value_1", "source_type_value_2", "tokenize($value, ");
        final Map<String, Object> registry = SourceTypeRegistry.read(documentation(uuidOf(map(group, "registry"))));
        assertThat(registry).containsEntry("field", PAIR);
        final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        list(registry, "rows").forEach(row -> rows.put((String) row.get("name"), row));
        assertThat(rows).containsOnlyKeys("Teams System", "Teams Application", "Skype for Business System",
                "Skype for Business Application", SourceTypeRows.NO_VALUE);
        assertThat(rows.get("Teams System")).containsEntry("match", SourceTypeRows.TUPLE)
                .containsEntry("value", "= Teams + = System");
        assertThat(rows.get(SourceTypeRows.NO_VALUE)).containsEntry("match", SourceTypeRows.NONE);

        // Its pipeline, stepped clean and processed: exactly its pairs' records translated, the rest passed over.
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", BUILD, "group", GROUP,
                "template_uuid", template)));
        final Map<String, Object> stepped = stepSample(pipeline, List.of(stream));
        assertThat(stepped).as("stepped clean: %s", stepped).containsEntry("verdict", "clean");
        final Map<String, Object> done = list(process(pipeline, List.of(stream)), "streams").getFirst();
        dump("processed", done);
        final List<Long> events = ids(done, "events");
        assertThat(events).hasSize(1);
        final String written = streamText(events.getFirst());
        assertThat(TestE2eSourceTypeGroups.count(written, "<Event>")).isEqualTo(2L * PER_PAIR);
        // Each pair's events told by their keys: Teams' System's cpu_seq, Skype's Application's codec_seq.
        assertThat(TestE2eSourceTypeGroups.count(written, "cpu_seq")).isEqualTo(PER_PAIR);
        assertThat(TestE2eSourceTypeGroups.count(written, "codec_seq")).isEqualTo(PER_PAIR);
        assertThat(written).doesNotContain("call_id_seq", "disk_seq", "plain text");
        assertThat(passedOverInProcessing(pipeline)).isEqualTo(2L * PER_PAIR + PLAIN);
    }

    // The mapping an agent would send for the group: the wrapper's entries, and the held value kept whole.
    private static Map<String, Object> mapping() {
        return (Map<String, Object>) ToolJson.loads("""
                {"input": "json", "json_layout": "lines",
                 "common": %s,
                 "ignored_fields": ["system"],
                 "events": [{"name": "line", "allow_unknown": "a test of which records the group takes",
                             "fields": [{"path": "EventDetail/TypeId", "value": "UcLine"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "event",
                                         "field": "event"}]}]}""".formatted(WRAPPER));
    }

    // The sample: PER_PAIR records of each pair of system and logger, each pair's events with keys of their own (two
    // kinds, one key `<first key>_seq` naming the pair for the checks of what the group wrote), then PLAIN records of
    // Teams whose event is plain text.
    private static String lines() {
        final StringBuilder out = new StringBuilder();
        int n = 0;
        for (int s = 0; s < SYSTEMS.size(); s++) {
            for (int l = 0; l < LOGGERS.size(); l++) {
                for (int i = 0; i < PER_PAIR; i++) {
                    final String event = "{\"logger_name\": \"" + LOGGERS.get(l) + "\", \"" + KEYS[s][l][i % 2]
                                         + "\": \"v" + i + "\", \"" + KEYS[s][l][0] + "_seq\": " + i + "}";
                    out.append(line(n++, SYSTEMS.get(s), event));
                }
            }
        }
        for (int i = 0; i < PLAIN; i++) {
            out.append(line(n++, "Teams", "Teams client restarted: plain text " + i));
        }
        return out.toString();
    }

    private static String line(final int n, final String system, final String event) {
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("timestamp", "2026-11-18T10:" + String.format("%02d:%02d", n / 60, n % 60) + ".000Z");
        record.put("host", "uc0" + n % 3);
        record.put("system", system);
        record.put("event", event);
        return ToolJson.dumps(record) + "\n";
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
