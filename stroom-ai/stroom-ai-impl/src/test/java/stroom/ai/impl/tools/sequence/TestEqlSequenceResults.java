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

package stroom.ai.impl.tools.sequence;

import stroom.ai.api.ElasticEql.Event;
import stroom.ai.api.ElasticEql.Sequence;
import stroom.ai.impl.tools.sequence.EqlQueryWriter.Step;
import stroom.ai.impl.tools.sequence.EqlSequenceResults.Entity;
import stroom.ai.impl.tools.sequence.EqlSequenceResults.Review;
import stroom.ai.impl.tools.sequence.EqlSequenceResults.Row;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestEqlSequenceResults {

    private static final String TIME = "@timestamp";
    private static final long T0 = 1_767_225_600_000L;
    private static final DocRef DEVICES = new DocRef(ElasticIndexDoc.TYPE, "devices-uuid", "Devices");
    private static final DocRef SYSMON = new DocRef(ElasticIndexDoc.TYPE, "sysmon-uuid", "Sysmon");

    @Test
    void eachStepsEventsAreRead() {
        final List<Step> steps = List.of(
                new Step(1, List.of(), List.of("user"), false, 2, List.of("action"), Map.of()),
                new Step(2, List.of(), List.of("SubjectUserName"), true, 1, List.of(), Map.of()),
                new Step(2, List.of(), List.of("SubjectUserName"), false, 1, List.of("Image"), Map.of()));
        final Sequence sequence = new Sequence(List.of("alice"), List.of(
                event(T0, 1, 1, Map.of("action", "Copy")),
                event(T0 + 30_000, 1, 2, Map.of("action", "Paste")),
                new Event("", "", true, Map.of()),
                event(T0 + 90_500, 2, 7, Map.of("Image", "powershell.exe"))));

        final List<Row> rows = EqlSequenceResults.rows(List.of(sequence), steps, TIME);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.byStep().get(0)).hasSize(2);
            assertThat(row.byStep().get(1)).isEmpty();
            assertThat(row.byStep().get(2)).singleElement().satisfies(hit -> {
                assertThat(hit.streamId()).isEqualTo("2");
                assertThat(hit.eventId()).isEqualTo("7");
            });
        });
        assertThat(EqlSequenceResults.columns(steps)).containsExactly("user", "s1_time", "s1_last_time", "s1_count",
                "s1_action", "s1_StreamId", "s1_EventId", "gap_1_3_s", "s3_time", "s3_Image", "s3_StreamId",
                "s3_EventId", "span_s");
        assertThat(EqlSequenceResults.cells(rows.getFirst(), steps)).containsExactly("alice",
                "2026-01-01T00:00:00.000Z", "2026-01-01T00:00:30.000Z", "2", "Copy, Paste", "1, 1", "1, 2", "60.500",
                "2026-01-01T00:01:30.500Z", "powershell.exe", "2", "7", "90.500");
    }

    @Test
    void entitiesAreSummarised() {
        final List<Step> steps = twoSteps();
        final List<Row> rows = EqlSequenceResults.rows(List.of(
                pair("alice", T0, 60_000, 1),
                pair("bob", T0, 5_000, 3),
                pair("alice", T0 + 3_600_000, 10_000, 5)), steps, TIME);

        final List<Entity> entities = EqlSequenceResults.entities(rows);

        assertThat(entities).hasSize(2);
        assertThat(entities.getFirst().keys()).containsExactly("alice");
        assertThat(entities.getFirst().sequences()).isEqualTo(2);
        assertThat(EqlSequenceResults.iso(entities.getFirst().first())).isEqualTo("2026-01-01T00:00:00.000Z");
        assertThat(EqlSequenceResults.iso(entities.getFirst().last())).isEqualTo("2026-01-01T01:00:10.000Z");
        assertThat(entities.getFirst().shortestSpan()).isEqualTo(10_000L);
        assertThat(entities.get(1).keys()).containsExactly("bob");
    }

    @Test
    void anEntityWhoseFirstSequenceHasNoTimeIsSummarisedFromItsTimedOnes() {
        // Before, the summary's null-safe min and max unboxed two nulls and threw a NullPointerException, failing
        // the whole call (seen when Elasticsearch left the events' fields out).
        final List<Step> steps = twoSteps();
        final List<Row> rows = EqlSequenceResults.rows(List.of(
                untimedPair("alice", 1),
                pair("alice", T0 + 3_600_000, 10_000, 3),
                pair("alice", T0, 60_000, 5)), steps, TIME);

        final List<Entity> entities = EqlSequenceResults.entities(rows);

        assertThat(entities).singleElement().satisfies(entity -> {
            assertThat(entity.sequences()).isEqualTo(3);
            assertThat(EqlSequenceResults.iso(entity.first())).isEqualTo("2026-01-01T00:00:00.000Z");
            assertThat(EqlSequenceResults.iso(entity.last())).isEqualTo("2026-01-01T01:00:10.000Z");
            assertThat(entity.shortestSpan()).isEqualTo(10_000L);
        });
        assertThat(EqlSequenceResults.untimed(rows)).isEqualTo(1);
    }

    @Test
    void anEntityWithNoTimeAtAllHasEmptyTimes() {
        final List<Step> steps = twoSteps();
        final List<Row> rows = EqlSequenceResults.rows(List.of(untimedPair("bob", 1), untimedPair("bob", 3)), steps,
                TIME);

        final List<Entity> entities = EqlSequenceResults.entities(rows);

        assertThat(entities).singleElement().satisfies(entity -> {
            assertThat(entity.sequences()).isEqualTo(2);
            assertThat(entity.first()).isNull();
            assertThat(entity.last()).isNull();
            assertThat(entity.shortestSpan()).isNull();
        });
        assertThat(EqlSequenceResults.cells(rows.getFirst(), steps)).containsExactly("bob", "", "1", "1", "", "",
                "2", "2", "");
        assertThat(EqlSequenceResults.untimed(rows)).isEqualTo(2);
    }

    @Test
    void eventTimesAreReadInEachFormElasticsearchGives() {
        assertThat(EqlSequenceResults.epochMillis("1768471440000")).isEqualTo(1_768_471_440_000L);
        assertThat(EqlSequenceResults.epochMillis(" 1768471440000 ")).isEqualTo(1_768_471_440_000L);
        assertThat(EqlSequenceResults.epochMillis(1_768_471_440_000L)).isEqualTo(1_768_471_440_000L);
        assertThat(EqlSequenceResults.epochMillis(1_000)).isEqualTo(1_000L);
        // A date_nanos field's millis have a fraction, which is dropped.
        assertThat(EqlSequenceResults.epochMillis("1768471560123.456")).isEqualTo(1_768_471_560_123L);
        assertThat(EqlSequenceResults.epochMillis("1768471440000.0")).isEqualTo(1_768_471_440_000L);
        assertThat(EqlSequenceResults.epochMillis(1.76847144E12)).isEqualTo(1_768_471_440_000L);
        assertThat(EqlSequenceResults.epochMillis("1.76847144E12")).isEqualTo(1_768_471_440_000L);
        assertThat(EqlSequenceResults.epochMillis(new BigDecimal("1768471440000.999")))
                .isEqualTo(1_768_471_440_000L);
    }

    @Test
    void anEventTimeThatIsntANumberIsNoTime() {
        assertThat(EqlSequenceResults.epochMillis(null)).isNull();
        assertThat(EqlSequenceResults.epochMillis("")).isNull();
        assertThat(EqlSequenceResults.epochMillis("2026-01-15T10:04:00Z")).isNull();
        assertThat(EqlSequenceResults.epochMillis("1e400")).isNull();
        assertThat(EqlSequenceResults.epochMillis(Double.NaN)).isNull();
        assertThat(EqlSequenceResults.epochMillis(true)).isNull();
    }

    @Test
    void theHitsAreNamedExactlyGroupedByStream() {
        final List<Step> steps = twoSteps();
        final List<Row> rows = EqlSequenceResults.rows(List.of(pair("alice", T0, 60_000, 1),
                pair("bob", T0 + 1_000, 60_000, 3)), steps, TIME);

        final Review devices = EqlSequenceResults.review(DEVICES, TIME, true, rows, steps, 1, 10_000);
        final Review sysmon = EqlSequenceResults.review(SYSMON, TIME, true, rows, steps, 2, 10_000);

        assertThat(devices.exact()).isTrue();
        assertThat(devices.why()).isNull();
        assertThat(devices.terms()).isEqualTo(2);
        // StroomQL takes sort before select.
        assertThat(devices.query()).isEqualTo("""
                from "Devices"
                where (StreamId = 1 and EventId in (1, 3))
                sort by @timestamp
                select StreamId, EventId, @timestamp, user""");
        assertThat(sysmon.query()).contains("where (StreamId = 2 and EventId in (2, 4))")
                .contains("select StreamId, EventId, @timestamp, SubjectUserName");
        final List<Row> one = EqlSequenceResults.rows(List.of(pair("alice", T0, 60_000, 1)), steps, TIME);
        assertThat(EqlSequenceResults.review(DEVICES, TIME, true, one, steps, 1, 10_000).query())
                .contains("where (StreamId = 1 and EventId = 1)\n");
    }

    @Test
    void theExactFormIsUsedOnlyWhileItFitsUnderTheLimit() {
        final List<Step> steps = twoSteps();
        final List<Sequence> sequences = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            sequences.add(pair("user" + (i % 3), T0 + i * 1_000L, 500, i * 10L));
        }
        final List<Row> rows = EqlSequenceResults.rows(sequences, steps, TIME);
        final int length = EqlSequenceResults.review(DEVICES, TIME, true, rows, steps, 1, 100_000).query().length();

        final Review fits = EqlSequenceResults.review(DEVICES, TIME, true, rows, steps, 1,
                length + EqlSequenceResults.REVIEW_MARGIN);
        assertThat(fits.exact()).isTrue();
        assertThat(fits.terms()).isEqualTo(40);

        final Review tooLong = EqlSequenceResults.review(DEVICES, TIME, true, rows, steps, 1,
                length + EqlSequenceResults.REVIEW_MARGIN - 1);
        assertThat(tooLong.exact()).isFalse();
        assertThat(tooLong.entities()).isTrue();
        assertThat(tooLong.why()).isEqualTo(EqlSequenceResults.TOO_LONG);
        assertThat(tooLong.terms()).isEqualTo(3);
        assertThat(tooLong.query()).isEqualTo("""
                from "Devices"
                where @timestamp >= 2026-01-01T00:00:00.000Z and @timestamp <= 2026-01-01T00:00:39.000Z
                and ((user = "user0")
                or (user = "user1")
                or (user = "user2"))
                sort by @timestamp
                select StreamId, EventId, @timestamp, user""");
    }

    @Test
    void anIndexWithoutIdsIsTheEntitiesInTheWindow() {
        final List<Step> steps = twoSteps();
        final List<Row> rows = EqlSequenceResults.rows(List.of(
                pair("alice", T0, 60_000, 1),
                pair("bob \"b\"", T0 + 120_000, 60_000, 3)), steps, TIME);

        final Review review = EqlSequenceResults.review(SYSMON, TIME, false, rows, steps, 2, 10_000);

        assertThat(review.exact()).isFalse();
        assertThat(review.entities()).isTrue();
        assertThat(review.why()).isEqualTo(EqlSequenceResults.NO_IDS);
        assertThat(review.query()).isEqualTo("""
                from "Sysmon"
                where @timestamp >= 2026-01-01T00:01:00.000Z and @timestamp <= 2026-01-01T00:03:00.000Z
                and ((SubjectUserName = "alice")
                or (SubjectUserName = "bob \\"b\\""))
                sort by @timestamp
                select @timestamp, SubjectUserName""");
    }

    @Test
    void tooManyEntitiesIsTheWindowAlone() {
        final List<Step> steps = twoSteps();
        final List<Sequence> sequences = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            sequences.add(pair("user" + i, T0 + i * 1_000L, 500, i * 10L));
        }
        final List<Row> rows = EqlSequenceResults.rows(sequences, steps, TIME);

        final Review window = EqlSequenceResults.review(DEVICES, TIME, true, rows, steps, 1, 1_000);
        assertThat(window.exact()).isFalse();
        assertThat(window.entities()).isFalse();
        assertThat(window.query()).doesNotContain("user0").contains("@timestamp >= ");
        assertThat(EqlSequenceResults.eventCount(rows, steps, 0).events()).isEqualTo(300);
    }

    @Test
    void secondsAndTimes() {
        assertThat(EqlSequenceResults.seconds(1_500L)).isEqualTo("1.500");
        assertThat(EqlSequenceResults.seconds(120_000L)).isEqualTo("120");
        assertThat(EqlSequenceResults.seconds(null)).isEmpty();
        assertThat(EqlSequenceResults.iso(null)).isEmpty();
        assertThat(EqlSequenceResults.text(3.0)).isEqualTo("3");
    }

    private static List<Step> twoSteps() {
        return List.of(new Step(1, List.of(), List.of("user"), false, 1, List.of(), Map.of()),
                new Step(2, List.of(), List.of("SubjectUserName"), false, 1, List.of(), Map.of()));
    }

    // A sequence of a device event then a sysmon event, gapMs apart, with ids from the given event id.
    private static Sequence pair(final String user, final long time, final long gapMs, final long eventId) {
        return new Sequence(List.of(user), List.of(event(time, 1, eventId, Map.of()),
                event(time + gapMs, 2, eventId + 1, Map.of())));
    }

    // A sequence like [#pair], but its events have no time field.
    private static Sequence untimedPair(final String user, final long eventId) {
        return new Sequence(List.of(user), List.of(untimed(1, eventId), untimed(2, eventId + 1)));
    }

    private static Event untimed(final long streamId, final long eventId) {
        return new Event("index", "id-" + eventId, false, Map.of("StreamId", List.of(streamId), "EventId",
                List.of(eventId)));
    }

    private static Event event(final long time, final long streamId, final long eventId,
                               final Map<String, Object> fields) {
        final Map<String, List<Object>> all = new HashMap<>();
        all.put(TIME, List.of(time));
        all.put("StreamId", List.of(streamId));
        all.put("EventId", List.of(eventId));
        fields.forEach((name, value) -> all.put(name, List.of(value)));
        return new Event("index", "id-" + eventId, false, all);
    }
}
