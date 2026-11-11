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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.ToolRefusal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSurveyDoc {

    private static Map<String, Object> shape(final int count, final long stream, final String text,
                                             final int record) {
        final List<Object> examples = new ArrayList<>();
        examples.add(SampleProfiler.map("text", text, "location",
                SampleProfiler.map("stream", stream, "part", 0, "record", record)));
        return SampleProfiler.map("count", count, "streams", new ArrayList<>(List.of(stream)), "examples", examples);
    }

    private static Map<String, Object> result(final List<Long> streams, final int records, final boolean saturated) {
        return SampleProfiler.map("feed", "SRC", "format", "json array", "streams_read", streams,
                "records_read", records, "saturated", saturated, "new_shapes", 1,
                "time_range", SampleProfiler.map("oldest", "t0", "newest", "t1"));
    }

    @Test
    void streamsGoneSinceTheSurveyAreDroppedWithTheShapesSeenOnlyInThem() {
        // Seen in testing: a feed's streams deleted part-way through a build; the survey kept pointing at them.
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("streams_read", List.of(1L, 2L));
        state.put("saturated", true);
        state.put("shapes", new LinkedHashMap<>(Map.of(
                "only-in-1", Map.of("count", 3L, "streams", List.of(1L), "examples",
                        List.of(Map.of("text", "a", "location", Map.of("stream", 1L, "part", 0L, "record", 0L)))),
                "in-both", Map.of("count", 4L, "streams", List.of(1L, 2L), "examples", List.of(
                        Map.of("text", "b", "location", Map.of("stream", 1L, "part", 0L, "record", 1L)),
                        Map.of("text", "c", "location", Map.of("stream", 2L, "part", 0L, "record", 5L)))))));

        assertThat(SurveyDoc.streams(state)).containsExactly(1L, 2L);
        assertThat(SurveyDoc.withoutStreams(state, Set.of(3L))).isSameAs(state);

        final Map<String, Object> kept = SurveyDoc.withoutStreams(state, Set.of(1L));

        assertThat(kept.get("streams_read")).isEqualTo(List.of(2L));
        assertThat(kept.get("saturated")).isEqualTo(false);
        assertThat(List.copyOf(((Map<?, ?>) kept.get("shapes")).keySet())).isEqualTo(List.of("in-both"));
        final Map<?, ?> both = (Map<?, ?>) ((Map<?, ?>) kept.get("shapes")).get("in-both");
        assertThat(both.get("streams")).isEqualTo(List.of(2L));
        assertThat((List<?>) both.get("examples")).hasSize(1);
        // The state read is left as it was.
        assertThat(state.get("streams_read")).isEqualTo(List.of(1L, 2L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void surveyDocRoundTripsAndMerges() {
        final Map<String, Map<String, Object>> firstShapes = new LinkedHashMap<>();
        firstShapes.put("fields:action | action=login", shape(5, 4, "{\"action\": \"login\", \"user\": \"a|b\"}", 0));

        final Map<String, Object> state = SurveyDoc.merge(null, result(List.of(4L, 1L), 5, false), firstShapes, 2,
                "now");
        final String markdown = SurveyDoc.render(state);

        assertThat(markdown).contains("| 1 | fields:action \\| action=login | 5 | 100.0% | 1 |")
                .contains("Stream 4, part 0, record 0:").contains("{\"action\": \"login\", \"user\": \"a|b\"}");
        final Map<String, Object> again = SurveyDoc.readState(markdown);
        assertThat(ToolJson.dumps(again)).isEqualTo(ToolJson.dumps(state));
        final Map<String, Map<String, Object>> more = new LinkedHashMap<>();
        more.put("fields:action | action=login", shape(2, 2, "{\"action\": \"login\", \"user\": \"c\"}", 1));
        more.put("fields:action | action=logout", shape(1, 2, "{\"action\": \"logout\"}", 2));
        final Map<String, Object> merged = SurveyDoc.merge(again, result(List.of(2L), 3, true), more, 2, "later");
        final Map<String, Object> login = (Map<String, Object>) ((Map<String, Object>) merged.get("shapes"))
                .get("fields:action | action=login");
        assertThat(login).containsEntry("count", 7L).containsEntry("streams", List.of(2L, 4L));
        assertThat((List<?>) login.get("examples")).hasSize(2);
        assertThat(merged).containsEntry("streams_read", List.of(1L, 2L, 4L)).containsEntry("saturated", true);
        assertThat((List<?>) merged.get("surveys")).hasSize(2);
        assertThat(SurveyDoc.render(merged)).contains("the feed looks covered");
        assertThat(SurveyDoc.readState("no state here")).isNull();
    }

    @Test
    void kindsLeftUntranslatedAreRecordedAndShown() {
        final Map<String, Object> shapes = new LinkedHashMap<>();
        shapes.put("fields:a | loggerName=org.jgroups.TCP", SampleProfiler.map("count", 3, "streams", List.of(1),
                "examples", List.of()));
        shapes.put("fields:a | loggerName=org.keycloak.events", SampleProfiler.map("count", 1, "streams", List.of(1),
                "examples", List.of()));
        final Map<String, Object> state = SampleProfiler.map("feed", "F", "format", "json array", "streams_read",
                List.of(1), "surveys", new ArrayList<>(), "saturated", true, "shapes", shapes);

        final List<String> signatures = SurveyDoc.resolve(state, List.of("org.jgroups"));

        assertThat(signatures).containsExactly("fields:a | loggerName=org.jgroups.TCP");
        assertThatThrownBy(() -> SurveyDoc.resolve(state, List.of("loggerName=")))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("'loggerName=' matches 2 kinds of event in the survey; give the exact signature");
        SurveyDoc.setHandling(state, signatures, "drop", "cluster chatter", "now");
        assertThat(SurveyDoc.dropped(state)).isEqualTo(Map.of("fields:a | loggerName=org.jgroups.TCP",
                "cluster chatter"));
        final String markdown = SurveyDoc.render(state);
        assertThat(markdown).contains("left untranslated: cluster chatter")
                .contains("1 kind(s) are left untranslated");
        // Survives the round trip through the doc.
        assertThat(SurveyDoc.dropped(SurveyDoc.readState(markdown))).isEqualTo(SurveyDoc.dropped(state));
        SurveyDoc.setHandling(state, signatures, "translate", "", "later");
        assertThat(SurveyDoc.dropped(state)).isEmpty();
    }

    @Test
    void aRecordInTheStoredFormatReadsAndRendersTheSame() {
        // A record in the established layout (JSON indented by one): it must read, and be written back alike.
        final String python = """
                # SRC - Survey

                ## Survey state

                ```json survey-state
                {
                 "feed": "SRC",
                 "format": "json array",
                 "streams_read": [
                  1,
                  4
                 ],
                 "surveys": [],
                 "shapes": {
                  "fields:action | action=login": {
                   "count": 5,
                   "streams": [
                    4
                   ],
                   "examples": [
                    {
                     "text": "{\\"action\\": \\"login\\", \\"user\\": \\"caf\\u00e9\\"}",
                     "location": {
                      "stream": 4,
                      "part": 0,
                      "record": 0
                     }
                    }
                   ],
                   "first_seen": "2026-10-01T10:00:00Z"
                  }
                 },
                 "saturated": false,
                 "time_range": {}
                }
                ```
                """;

        final Map<String, Object> state = SurveyDoc.readState(python);

        assertThat(state).containsEntry("feed", "SRC");
        assertThat(SurveyDoc.render(state)).contains(python.substring(python.indexOf("```json survey-state")))
                .contains("{\"action\": \"login\", \"user\": \"café\"}");
    }

    @Test
    void longExamplesAreKeptCut() {
        final Map<String, Map<String, Object>> shapes = new LinkedHashMap<>();
        shapes.put("fields:a", shape(1, 1, "x".repeat(5000), 0));

        final Map<String, Object> state = SurveyDoc.merge(null, result(List.of(1L), 1, false), shapes, 2, "now");

        assertThat(ToolJson.dumps(state)).contains("x".repeat(SurveyDoc.MAX_EXAMPLE_CHARS) + "\"")
                .doesNotContain("x".repeat(SurveyDoc.MAX_EXAMPLE_CHARS + 1));
        assertThat(SurveyDoc.docName("ACME")).isEqualTo("ACME - Survey");
    }
}
