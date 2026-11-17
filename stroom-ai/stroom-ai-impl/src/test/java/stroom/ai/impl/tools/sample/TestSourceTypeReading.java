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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A source type reading: a path to a value that may step into held values (`event>payload.logger_name`), readings
/// tried in turn (` | `), its syntax checked, and a field alone read as a field always was.
class TestSourceTypeReading {

    @Test
    void aFieldAloneIsAReadingOfThatFieldAsBefore() {
        final SourceTypeReading reading = SourceTypeReading.of("sourcetype");

        assertThat(SourceTypeReading.isReading("sourcetype")).isFalse();
        assertThat(SourceTypeReading.isReading("event.Workload")).isFalse();
        assertThat(SourceTypeReading.isReading("meta:Feed")).isFalse();
        assertThat(reading.held()).isFalse();
        assertThat(reading.spec()).isEqualTo("sourcetype");
        assertThat(reading.valueOf(Map.of("sourcetype", " PegaDB "))).isEqualTo("PegaDB");
        assertThat(reading.valueOf(Map.of("sourcetype", "none"))).isNull();
        assertThat(reading.valueOf(Map.of("other", "x"))).isNull();
        assertThat(reading.valueOf(null)).isNull();
    }

    @Test
    void anObjectDeeperThanTheFlatteningIsReadByItsPath() {
        // Flattened four levels deep, `a.b.c.d` holds the object with `e` in it.
        final Map<String, Object> record = SampleProfiler.flatten(ToolJson.loads(
                "{\"a\": {\"b\": {\"c\": {\"d\": {\"e\": {\"f\": \"deep\"}}}}}}"));

        assertThat(record).containsKey("a.b.c.d");
        assertThat(SourceTypeFields.valueOf(record, "a.b.c.d.e.f")).isEqualTo("deep");
        assertThat(SourceTypeFields.valueOf(record, "a.b.c.d.e")).isNull();
        assertThat(SourceTypeFields.valueOf(record, "a.b.c.d.x")).isNull();
    }

    @Test
    void readingsAreTriedInTurnTheFirstWithAValueGivingIt() {
        final SourceTypeReading reading = SourceTypeReading.of("event>logger_name | event>xml:@logger | other");

        assertThat(reading.paths()).hasSize(3);
        assertThat(reading.valueOf(record("{\"logger_name\": \"com.x.Json\"}"))).isEqualTo("com.x.Json");
        assertThat(reading.valueOf(record("<Log logger=\"com.x.Xml\"/>"))).isEqualTo("com.x.Xml");
        assertThat(reading.valueOf(record("logger_name=com.x.Kv a=b"))).isEqualTo("com.x.Kv");
        // Plain text holds no value; the last reading, a field of the record, then gives one.
        final Map<String, Object> text = new LinkedHashMap<>(record("User bob logged in"));
        assertThat(reading.valueOf(text)).isNull();
        text.put("other", "fallback");
        assertThat(reading.valueOf(text)).isEqualTo("fallback");
        // A held field missing gives no value.
        assertThat(reading.valueOf(Map.of("system", "Teams"))).isNull();
        assertThat(reading.heldFormats(Map.of("system", "Teams"))).containsEntry("event", "missing");
        assertThat(reading.heldFormats(record("User bob"))).containsEntry("event", HeldText.TEXT);
    }

    @Test
    void heldValuesAreSteppedIntoUpToThreeDeep() {
        final Map<String, Object> record = record(ToolJson.dumps(Map.of("payload", ToolJson.dumps(Map.of("inner",
                "k=v logger=deep")))));

        assertThat(SourceTypeReading.of("event>payload>inner>logger").valueOf(record)).isEqualTo("deep");
        assertThat(SourceTypeReading.of("event>payload>inner>kv:logger").valueOf(record)).isEqualTo("deep");
        assertThat(SourceTypeReading.of("event>payload>inner>json:logger").valueOf(record)).isNull();
        assertThat(SourceTypeReading.of("event>payload>#format").valueOf(record)).isEqualTo(HeldText.JSON);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', quoteCharacter = '"', value = {
            "event>; an empty step",
            ">logger; starts with no field",
            "event>>logger; an empty step",
            "a>b>c>d>e; steps into 4 held values",
            "event>xml:a..b; an empty name",
            "event>@a.b; the step's last name",
            "event>json:@a; only XML has attributes",
            "event>kv:not a key; can't be a key",
            "event>syslog:pid; a syslog header's fields",
            "event>json:#root; only xml: has one",
            "event>#root>x; nothing steps into it",
            "event>#nope; is no step",
            "meta:Feed>x; read whole",
            "a>x | ; an empty reading",
            "a>1 | a>2 | a>3 | a>4 | a>5 | a>6 | a>7; at most 6"})
    void aReadingThatIsNoneIsRefusedSayingWhy(final String spec, final String why) {
        assertThat(SourceTypeReading.problem(spec)).hasValueSatisfying(problem -> assertThat(problem).contains(why));
        assertThatThrownBy(() -> SourceTypeReading.of(spec)).isInstanceOf(IllegalArgumentException.class);
        assertThat(SourceTypeReading.cached(spec)).isNull();
        assertThat(SourceTypeFields.valueOf(record("{\"x\": \"v\"}"), spec)).isNull();
    }

    @Test
    void aReadingIsWrittenOneWayWhateverItsSpacing() {
        assertThat(SourceTypeReading.of(" event > XML:Header.@id |  event>logger ").spec())
                .isEqualTo("event>xml:Header.@id | event>logger");
        assertThat(SourceTypeReading.problem("event>logger_name | event>xml:@logger")).isEmpty();
        assertThat(SourceTypeReading.of("event>#format").byFormat()).isTrue();
        assertThat(SourceTypeReading.of("event>logger").byFormat()).isFalse();
    }

    @Test
    void theLastNameReadSaysWhatTheValueIs() {
        assertThat(SourceTypeReading.lastName("event>payload.logger_name")).isEqualTo("logger_name");
        assertThat(SourceTypeReading.lastName("event>xml:Header.@logger | x")).isEqualTo("logger");
        assertThat(SourceTypeReading.lastName("event.Workload")).isEqualTo("Workload");
        assertThat(SourceTypeReading.lastName("sourcetype")).isEqualTo("sourcetype");
    }

    @Test
    void aReadingIsSpeltAsTheRecordsSpellTheirFields() {
        final List<Map<String, Object>> records = List.of(record("{\"a\": 1}"));

        assertThat(SourceTypeReading.asRecordsSpellIt("EVENT>logger | Event>xml:@l", records))
                .hasValue("event>logger | event>xml:@l");
        assertThat(SourceTypeReading.asRecordsSpellIt("nothing>logger", records)).isEmpty();
        assertThat(SourceTypeReading.asRecordsSpellIt("nothing>logger", List.of())).hasValue("nothing>logger");
    }

    @Test
    void aReadingIsSaidForPeople() {
        assertThat(SourceTypeReading.of("event>payload>logger_name | event>xml:#root").said())
                .contains("logger_name inside payload inside the held field event")
                .contains("the root element's name inside the held field event (when it holds xml)");
    }

    private static Map<String, Object> record(final String event) {
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("system", "Teams");
        record.put("event", event);
        return record;
    }
}
