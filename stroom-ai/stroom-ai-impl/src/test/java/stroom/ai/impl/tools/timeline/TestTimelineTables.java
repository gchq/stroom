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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Activity;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Characterised;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Item;
import stroom.ai.impl.tools.timeline.ActivityCharacterisation.Placement;
import stroom.ai.impl.tools.timeline.TimelineSpans.Kind;
import stroom.ai.impl.tools.timeline.TimelineSpans.Span;
import stroom.ai.impl.tools.timeline.TimelineTables.ActivityRow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestTimelineTables {

    private static final long CAP_MS = 30 * 60_000L;

    @Test
    void anActivityOfLooseItemsAloneHasARowSpanningItsItemsTimes() {
        // Eval case 115: a chat thread's messages placed in an activity of their own, in no span of it; beside an
        // activity of a focus session.
        final Activity chat = activity("a1", List.of());
        final Activity report = activity("a2", List.of("report.docx"));
        final List<Item> items = List.of(item("e1", "Can you cover the weekend on-call rota?", "10:00"),
                item("e2", "I'm on call Saturday already", "10:03"),
                item("e3", "Rota updated", "10:07"),
                // Past the idle cap: another interval of the same activity.
                item("e4", "Thanks, rota sorted", "12:00"));
        final Map<String, Placement> placed = new LinkedHashMap<>();
        items.forEach(item -> placed.put(item.id(), new Placement(item.id(), "a1", "model", "high", null)));
        final Characterised characterised = new Characterised(List.of(chat, report), Map.of("s1",
                new Placement("s1", "a2", ActivityCharacterisation.BY_ARTIFACT, null, null)), placed, items, Map.of(),
                4, 1, 0, 0, List.of(), null);
        final List<Span> spans = List.of(new Span(1, Kind.FOCUS, at("14:00"), at("15:00"), "report.docx",
                "ws-01", "ws-01", "Endpoint", List.of(), "test", "", false, Map.of(), "", null, null, null));

        final List<ActivityRow> rows = TimelineTables.rollUp(spans, characterised, Map.of("a1", "Weekend on-call rota",
                "a2", "Report"), CAP_MS, ZoneOffset.UTC);

        // Its two intervals, from the first item's time to the last's, labelled like the other activity, its
        // evidence its items' text where it has no artifacts.
        assertThat(rows).filteredOn(row -> "a1".equals(row.activity()))
                .extracting(ActivityRow::start, ActivityRow::end)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(at("10:00"), at("10:07")),
                        org.assertj.core.groups.Tuple.tuple(at("12:00"), at("12:00")));
        assertThat(rows).filteredOn(row -> "a1".equals(row.activity())).first().satisfies(row -> {
            assertThat(row.label()).isEqualTo("Weekend on-call rota");
            assertThat(row.barLabel()).isEqualTo("Weekend on-call rota");
            assertThat(row.marker()).isFalse();
            assertThat(row.spans()).isEmpty();
            assertThat(row.lanes()).containsExactly("ws-01");
            assertThat(row.evidence()).containsExactly("Can you cover the weekend on-call rota?",
                    "I'm on call Saturday already", "Rota updated");
        });
        // The other activity's row as it was.
        assertThat(rows).filteredOn(row -> "a2".equals(row.activity())).singleElement()
                .satisfies(row -> assertThat(row.spans()).containsExactly("s1"));
    }

    @Test
    void anActivityOfSpansTakesNoRowForItsLooseItems() {
        // An activity with a session and a message: its row is its span's, as before.
        final Activity report = activity("a1", List.of("report.docx"));
        final List<Item> items = List.of(item("e1", "sent the report", "16:30"));
        final Map<String, Placement> sessions = Map.of("s1", new Placement("s1", "a1",
                ActivityCharacterisation.BY_ARTIFACT, null, null));
        final Map<String, Placement> placed = Map.of("e1", new Placement("e1", "a1",
                ActivityCharacterisation.BY_TERMS, null, null));
        final Characterised characterised = new Characterised(List.of(report), sessions, placed, items, Map.of(), 0,
                0, 0, 0, List.of(), null);
        final List<Span> spans = List.of(new Span(1, Kind.FOCUS, at("14:00"), at("15:00"), "report.docx",
                "ws-01", "ws-01", "Endpoint", List.of(), "test", "", false, Map.of(), "", null, null, null));

        final List<ActivityRow> rows = TimelineTables.rollUp(spans, characterised, Map.of(), CAP_MS,
                ZoneOffset.UTC);

        assertThat(rows).singleElement().satisfies(row -> assertThat(row.spans()).containsExactly("s1"));
    }

    @Test
    void aLooseItemsIntervalOverMidnightIsARowADay() {
        final Activity chat = activity("a1", List.of());
        final List<Item> items = List.of(new Item("e1", "late", Instant.parse("2026-11-19T23:50:00Z").toEpochMilli(),
                        "ws-01", "Messages", null, null),
                new Item("e2", "later", Instant.parse("2026-11-20T00:10:00Z").toEpochMilli(), null, "Messages", null,
                        null));
        final Characterised characterised = new Characterised(List.of(chat), Map.of(), Map.of("e1",
                new Placement("e1", "a1", "model", null, null), "e2", new Placement("e2", "a1", "model", null, null)),
                items, Map.of(), 2, 1, 0, 0, List.of(), null);

        final List<ActivityRow> rows = TimelineTables.rollUp(List.of(), characterised, Map.of(), CAP_MS,
                ZoneOffset.UTC);

        assertThat(rows).extracting(row -> row.day().toString(), ActivityRow::lanes)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("2026-11-19", List.of("ws-01")),
                        org.assertj.core.groups.Tuple.tuple("2026-11-20", List.of("Messages")));
    }

    private static Activity activity(final String id, final List<String> artifacts) {
        return new Activity(id, id, artifacts, List.of(), List.of(), List.of(), 0, 0, 0, false, List.of());
    }

    private static Item item(final String id, final String text, final String time) {
        return new Item(id, text, at(time), "ws-01", "Messages", null, null);
    }

    private static long at(final String time) {
        return Instant.parse("2026-11-19T" + time + ":00Z").toEpochMilli();
    }
}
