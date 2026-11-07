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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.TimePatterns;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/// The draft's choice of each event's time (the work package's matrix T1-T16): the record's own time before its
/// container's, a fallback record by record, times that are an object's rather than the event's left as data,
/// numeric times, and a choice put to the agent where two times could each be the event's. The samples are made by
/// `event-time/generate.py`, which says what each holds.
class TestDraftEventTime extends DraftFixtures {

    private static final String TIME_CREATED = "EventTime/TimeCreated";

    // T1.
    @Test
    void cefAfterAnIsoHeaderWithNoTimeOfItsOwnHasTheHeadersTime() {
        final Map<String, Object> time = time(draft("t01_cef_header_only"));
        assertThat(time.get("field")).isEqualTo("time");
        assertThat(time).doesNotContainKey("any_of");
    }

    // T2.
    @Test
    void cefsOwnTimeIsReadBeforeItsContainersWithOneFormatForBoth() {
        final Map<String, Object> draft = draft("t02_cef_rt_every");
        final Map<String, Object> time = time(draft);
        assertThat(time.get("any_of")).isEqualTo(List.of("rt", "time"));
        // One format reads both: each's own, as optional sections, checked on the sample's values of both.
        final String format = (String) time.get("time_format");
        assertThat(format).startsWith("[MMM dd yyyy HH:mm:ss][");
        assertThat(TimePatterns.checkTimeFormat(format, values("t02_cef_rt_every", "rt"))).isNull();
        assertThat(TimePatterns.checkTimeFormat(format, values("t02_cef_rt_every", "time"))).isNull();
        assertThat(timeNote(draft)).contains("'rt'", "'time'");
    }

    // T3.
    @Test
    void whereSomeRecordsHaveTheirOwnTimeTheRestHaveTheirContainersNotTheReceivedTime() {
        final Map<String, Object> draft = draft("t03_cef_rt_some");
        final Map<String, Object> time = time(draft);
        assertThat(time.get("any_of")).isEqualTo(List.of("rt", "time"));
        assertThat(time).doesNotContainKey("time_fallback");
        assertThat(timeNote(draft)).contains("'rt'", "40%");
    }

    // T4.
    @Test
    void anOwnTimeAsANumberBesideAContainersPatternLeavesTheContainersForAllWithANote() {
        final Map<String, Object> draft = draft("t04_cef_rt_epoch_some");
        final Map<String, Object> time = time(draft);
        assertThat(time.get("field")).isEqualTo("time");
        assertThat(time).doesNotContainKey("any_of");
        assertThat(timeNote(draft)).contains("'rt'", "epoch");
    }

    // T5.
    @Test
    void anObjectsCreationAndExpiryAreNotTheEventsTime() {
        final Map<String, Object> draft = draft("t05_json_object_times");
        assertThat(time(draft).get("field")).isEqualTo("event_time");
        assertThat(time(draft)).doesNotContainKey("choose_from");
        assertThat(timeNote(draft)).contains("'created_at'", "'expires'");
        assertThat(draft).doesNotContainKey("time_candidates");
    }

    // T6.
    @Test
    void aCreationTimeAloneIsTheEventsTimeWithANoteToCheck() {
        final Map<String, Object> draft = draft("t06_json_created_only");
        assertThat(time(draft).get("field")).isEqualTo("created_at");
        assertThat(timeNote(draft)).contains("'created_at'", "check");
    }

    // T7.
    @Test
    void aKindWithTwoTimesNeitherNamedTheEventsIsPutToTheAgent() {
        final Map<String, Object> draft = draft("t07_json_two_kinds");
        assertThat(time(draft).get("field")).isEqualTo("event_time");
        final List<Map<String, Object>> candidates = JsonValues.maps(draft.get("time_candidates"));
        assertThat(candidates).hasSize(1);
        final Map<String, Object> session = candidates.getFirst();
        assertThat(session.get("rule")).isEqualTo("session");
        assertThat(JsonValues.maps(session.get("fields")).stream().map(f -> f.get("field")).toList())
                .containsExactly("start", "end");
        // Each with what tells them apart: its share of the kind's records, its format and how far it is from the
        // other.
        assertThat(JsonValues.maps(session.get("fields")).getFirst())
                .containsKeys("share", "time_format", "rank", "from_others");
        // Drafted with the first, marked: build_translation_xslt refuses it until the agent chooses.
        final Map<String, Object> sessionTime = fields(rules(draft).get("session")).get(TIME_CREATED);
        assertThat(sessionTime.get("field")).isEqualTo("start");
        assertThat(sessionTime.get("choose_from")).isEqualTo(List.of("start", "end"));
        assertThat(fields(rules(draft).get("file_access"))).doesNotContainKey(TIME_CREATED);
    }

    // T8.
    @Test
    void aRecordsOwnDateAndTimeAreJoinedBeforeAHeaderWithoutAYear() {
        final Map<String, Object> time = time(draft("t08_kv_date_time_3164"));
        assertThat(String.valueOf(time.get("xpath"))).contains("concat(");
    }

    // T9.
    @Test
    void twoTimesEachNamedAsTheEventsArePutToTheAgent() {
        final Map<String, Object> draft = draft("t09_kv_two_event_times");
        assertThat(time(draft).get("field")).isEqualTo("ts");
        assertThat(time(draft).get("choose_from")).isEqualTo(List.of("ts", "logged_at"));
        final List<Map<String, Object>> candidates = JsonValues.maps(draft.get("time_candidates"));
        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst()).doesNotContainKey("rule");
        assertThat(JsonValues.maps(candidates.getFirst().get("fields")).stream().map(f -> f.get("field")).toList())
                .containsExactly("ts", "logged_at");
    }

    // T10.
    @Test
    void aTimeYearsBeforeTheStreamIsStillTheEventsTime() {
        final Map<String, Object> draft = draft("t10_json_backfilled");
        assertThat(time(draft).get("field")).isEqualTo("timestamp");
        assertThat(draft).doesNotContainKey("time_candidates");
    }

    // T11.
    @Test
    void eachFormatOfAMixedStreamHasItsOwnTime() {
        final Map<String, Object> draft = draft("t11_mixed_relay");
        assertThat(((Map<?, ?>) draft.get("splitter")).get("kind")).isEqualTo(SplitterSpec.ALTERNATIVES);
        final Map<String, Map<String, Object>> rules = rules(draft);
        final List<Object> times = rules.values().stream()
                .map(rule -> fields(rule).get(TIME_CREATED))
                .filter(Objects::nonNull)
                .map(time -> time.containsKey("any_of")
                        ? time.get("any_of")
                        : time.get("field"))
                .distinct()
                .toList();
        assertThat(times).contains(List.of("rt", "time"), "time");
    }

    // T12.
    @Test
    void noTimeAnywhereIsSaid() {
        final Map<String, Object> draft = draft("t12_no_time");
        assertThat(common(draft)).doesNotContainKey(TIME_CREATED);
        assertThat(notes(draft)).anyMatch(note -> note.startsWith("No timestamp field was recognised"));
    }

    // T13.
    @Test
    void cefsFileTimesAreTheFilesNotTheEvents() {
        final Map<String, Object> draft = draft("t13_cef_file_time");
        assertThat(time(draft).get("any_of")).isEqualTo(List.of("rt", "time"));
        assertThat(timeNote(draft)).contains("'fileCreateTime'");
    }

    // T15.
    @Test
    void aFiletimeOfAnAccountsLastLogonIsKeptAsData() {
        final Map<String, Object> draft = draft("t15_json_filetime_object");
        assertThat(time(draft).get("field")).isEqualTo("TimeCreated");
        assertThat(timeNote(draft)).contains("'lastLogon'", "windows_filetime");
    }

    // T16.
    @Test
    void aFiletimeThatIsTheEventsTimeIsConverted() {
        final Map<String, Object> time = time(draft("t16_json_filetime_event"));
        assertThat(time.get("field")).isEqualTo("EventTime");
        assertThat(time.get("time_format")).isEqualTo("windows_filetime");
    }

    private static Map<String, Object> draft(final String name) {
        return DraftMapping.draft(List.of(resource("event-time/" + name + ".log")), "", null, null, null, null,
                null);
    }

    private static Map<String, Object> time(final Map<String, Object> draft) {
        final Map<String, Object> time = common(draft).get(TIME_CREATED);
        assertThat(time).as("the common time").isNotNull();
        return time;
    }

    // The note that lists every timestamp field, its share, its rank and which was chosen.
    private static String timeNote(final Map<String, Object> draft) {
        return notes(draft).stream()
                .filter(note -> note.startsWith("Timestamp fields:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no note of the timestamp fields in " + notes(draft)));
    }

    // A field's values in a sample's records, as the draft reads them.
    private static List<String> values(final String name, final String field) {
        return DraftMapping.records(Map.of(name, resource("event-time/" + name + ".log")), null).records().stream()
                .map(record -> record.get(field))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }
}
