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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.draft.LocalCheck;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A time's zone read record by record from a field or the stream's meta (asked for by the user), its values in the
/// forms sources write: read as Stroom's stroom:format-date reads a zone, the plain offsets it can't read written as
/// it does, the rest mapped as the user says, the default where a record gives none.
class TestZoneSource extends XsltRuns {

    private static final Map<String, Object> SYDNEY = Map.of("AEST", "Australia/Sydney");

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "+5, +05:00",
            "+5:30, +05:30",
            "-3:30, -03:30",
            "+10.5, +10:30",
            "+5.75, +05:45",
            "UTC+5, UTC+05:00",
            "+10:00, +10:00",
            "+1000, +1000",
            "Australia/Sydney, Australia/Sydney",
            "AEST, AEST"})
    void plainOffsetsAreWrittenAsStroomReadsThem(final String given, final String written) {
        assertThat(ZoneSource.normalised(given)).isEqualTo(written);
    }

    @Test
    void zonesAreJudgedAsStroomReadsThem() {
        assertThat(List.of("Australia/Sydney", "UTC", "Z", "+10:00", "+1000", "UTC+10", "GMT/BST"))
                .noneMatch(ZoneSource::unreadable);
        // Abbreviations Stroom doesn't read, and spellings it can't: it would read the time as UTC.
        assertThat(List.of("AEST", "IST", "EST", "+5:30", "+10.5", "AUS Eastern Standard Time", "-300"))
                .allMatch(ZoneSource::unreadable);

        final ZoneSource source = new ZoneSource("tz", null, Map.of("AEST", "Australia/Sydney"), "UTC");
        assertThat(source.unreadable(List.of("AEST", "IST", "+5:30", "", "Australia/Sydney", "IST", "+10.5")))
                .containsExactly("IST");
    }

    @Test
    void aZoneSourceIsChecked() {
        assertThatThrownBy(() -> FieldMapping.fromJson(timeEntry(Map.of("field", "tz"))))
                .hasMessageContaining("needs default");
        assertThatThrownBy(() -> FieldMapping.fromJson(timeEntry(Map.of("field", "tz", "meta", "TimeZone",
                "default", "UTC")))).hasMessageContaining("one field or one meta attribute");
        assertThatThrownBy(() -> FieldMapping.fromJson(timeEntry(Map.of("field", "tz", "default", "UTC", "map",
                Map.of("IST", "India"))))).hasMessageContaining("'IST' is given as 'India'");
        final Map<String, Object> epoch = new LinkedHashMap<>(timeEntry(Map.of("field", "tz", "default", "UTC")));
        epoch.put("time_format", "epoch_ms");
        assertThatThrownBy(() -> FieldMapping.fromJson(epoch)).hasMessageContaining("not an epoch");
        // Kept as given, a fixed zone too.
        final FieldMapping read = FieldMapping.fromJson(timeEntry(Map.of("meta", "TimeZone", "default",
                "Australia/Sydney")));
        assertThat(read.zoneSource()).isEqualTo(new ZoneSource(null, "TimeZone", null, "Australia/Sydney"));
        assertThat(read.toJson(DumpMode.COMPACT)).extractingByKey("timezone")
                .isEqualTo(Map.of("meta", "TimeZone", "default", "Australia/Sydney"));
        assertThat(FieldMapping.fromJson(timeEntry("+10:00")).timezone()).isEqualTo("+10:00");
    }

    @Test
    void eachRecordsTimeIsReadInItsOwnZone() {
        final Map<String, Object> zone = new LinkedHashMap<>();
        zone.put("field", "tz");
        zone.put("map", SYDNEY);
        zone.put("default", "UTC");
        final XsltGenerator.Generated generated = XsltGenerator.generate(GenerationFixtures.mapping(
                GenerationFixtures.with("common", commonWith(timeEntry(zone)))), GenerationFixtures.SCHEMA, "4.1.0");
        assertThat(generated.problems()).isEmpty();
        assertThat(generated.xslt()).contains("mcp:zone(");

        final XmlElement events = transform(generated.xslt(), "<records xmlns=\"records:2\">"
                + record("Australia/Sydney") + record("+5:30") + record("AEST") + record("") + record("+10.5")
                + record("UTC+10") + "</records>");

        assertThat(invalid(events, GenerationFixtures.XSD)).isEmpty();
        // 2026-10-11 10:00:00 in each: Sydney is +11:00 then (daylight time), the blank one the default.
        assertThat(children(events, "Event").stream().map(event -> text(event, "EventTime/TimeCreated")))
                .containsExactly("2026-10-10T23:00:00.000Z", "2026-10-11T04:30:00.000Z", "2026-10-10T23:00:00.000Z",
                        "2026-10-11T10:00:00.000Z", "2026-10-10T23:30:00.000Z", "2026-10-11T00:00:00.000Z");
    }

    @Test
    void theSamplesZonesStroomDoesntReadAreNamedBeforeSaving() {
        final TranslationMapping mapping = GenerationFixtures.mapping(GenerationFixtures.with("common",
                commonWith(timeEntry(Map.of("field", "tz", "map", SYDNEY, "default", "UTC")))));
        final List<Object> records = new ArrayList<>();
        for (final String tz : List.of("Australia/Sydney", "AEST", "IST", "+5:30", "")) {
            records.add(Map.of("time", "2026-10-11 10:00:00", "tz", tz, "action", "login", "user", "dave",
                    "result", "ok"));
        }

        assertThat(String.valueOf(LocalCheck.checkMapping(mapping, records).get("problems")))
                .contains("'IST'", "doesn't read as zones", "map")
                .doesNotContain("'AEST'", "'+5:30'");
    }

    private static Map<String, Object> timeEntry(final Object timezone) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", "EventTime/TimeCreated");
        entry.put("field", "time");
        entry.put("time_format", "yyyy-MM-dd HH:mm:ss");
        entry.put("timezone", timezone);
        return entry;
    }

    // The base mapping's common fields, TimeCreated's entry replaced.
    private static List<Object> commonWith(final Map<String, Object> time) {
        final List<Object> common = new ArrayList<>(GenerationFixtures.BASE);
        common.set(0, time);
        return common;
    }

    private static String record(final String tz) {
        return "<record><data name=\"time\" value=\"2026-10-11 10:00:00\"/><data name=\"tz\" value=\"" + tz + "\"/>"
               + "<data name=\"action\" value=\"login\"/><data name=\"user\" value=\"dave\"/>"
               + "<data name=\"result\" value=\"ok\"/></record>";
    }
}
