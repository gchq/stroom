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

import stroom.ai.impl.tools.JsonValues;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// What the survey learns of each source type: counted per call, kept across calls, summed by the registry's rows.
class TestSourceTypeProfiles {

    private static final List<SourceTypeRows.Row> ROWS = List.of(
            new SourceTypeRows.Row("Security", SourceTypeRows.VALUE, "Security"),
            new SourceTypeRows.Row("Database", SourceTypeRows.VALUE, "Database"),
            new SourceTypeRows.Row("Integration", SourceTypeRows.VALUE, "Integration"),
            new SourceTypeRows.Row("Named only", SourceTypeRows.VALUE, "Nowhere"));

    @Test
    void eachSourceTypeIsProfiledFromTheRecordsRead() {
        final Map<String, Object> state = counted(TestSourceTypeFields.wrapped(60), 1);

        final Map<String, Map<String, Object>> profiles = SourceTypeProfiles.summarise(state, ROWS);

        assertThat(profiles.get("Security")).containsEntry("records_read", 20L)
                .containsEntry("share_percent", 33.3)
                .containsEntry("suggested_category", SourceTypeProfiles.SECURITY_AUDIT)
                .containsEntry("kind_field", "message.eventType")
                .containsEntry("formats", Map.of("json", 20L));
        assertThat(JsonValues.maps(profiles.get("Security").get("signals"))).extracting(signal -> signal.get("signal"))
                .contains("actor", "outcome", "access_words");
        assertThat(profiles.get("Database")).containsEntry("suggested_category", SourceTypeProfiles.OPERATIONAL);
        assertThat(JsonValues.maps(profiles.get("Database").get("signals"))).extracting(signal -> signal.get("signal"))
                .contains("no_actor", "timings");
        assertThat(profiles.get("Integration")).containsEntry("formats", Map.of("xml Envelope", 20L));
        // The wrapper's fields, every record's, are left out of each source type's.
        assertThat(JsonValues.maps(profiles.get("Database").get("fields"))).isEmpty();
        assertThat(JsonValues.listOrEmpty(profiles.get("Security").get("examples"))).hasSize(3);
        // A row the survey met no record of says so.
        assertThat(profiles.get("Named only")).containsEntry("records_read", 0L)
                .containsEntry("rests_on", "not seen in the 60 records read");
    }

    @Test
    void countsAddUpCallByCallAndTheEstimateScalesToTheStreamsRead() {
        final Map<String, Object> first = counted(TestSourceTypeFields.wrapped(30), 1);
        final Map<String, Object> both = SourceTypeProfiles.merge(first, counted(TestSourceTypeFields.wrapped(30),
                2));

        assertThat(SourceTypeProfiles.valueCounts(both)).containsEntry("Security", 20L)
                .containsEntry("Database", 20L);
        assertThat(both).containsEntry("records", 60L);
        final Map<String, Map<String, Object>> profiles = SourceTypeProfiles.summarise(both, ROWS);
        assertThat(profiles.get("Security")).containsEntry("streams_seen", 2);
        // Each stream's records read are a tenth of its size (see counted).
        assertThat((Long) profiles.get("Security").get("records_estimate")).isGreaterThan(150L);
    }

    @Test
    void aThinlySeenSourceTypeSaysWhatItRestsOn() {
        final Map<String, Object> state = counted(TestSourceTypeFields.wrapped(30).subList(0, 15), 1);

        assertThat(SourceTypeProfiles.summarise(state, ROWS).get("Security"))
                .containsEntry("rests_on", "from only 5 of the 15 records read");
    }

    @Test
    void heartbeatsAreDiagnosticNoise() {
        final SourceTypeProfiles.Category category = SourceTypeProfiles.category(Map.of("heartbeats", 95L), 100,
                Map.of("k", 100L), Map.of());
        assertThat(category).isEqualTo(new SourceTypeProfiles.Category(SourceTypeProfiles.DIAGNOSTIC_NOISE, "high"));
        assertThat(SourceTypeProfiles.category(Map.of(), 100, Map.of(), Map.of("DEBUG", 90L)).name())
                .isEqualTo(SourceTypeProfiles.DIAGNOSTIC_NOISE);
    }

    // The records counted as one stream's, its size ten times the bytes of the records read.
    private static Map<String, Object> counted(final List<Map<String, Object>> records, final long stream) {
        final SourceTypeProfiles profiles = new SourceTypeProfiles("source");
        profiles.streamCreated(stream, 1_762_732_800_000L);
        long bytes = 0;
        for (int i = 0; i < records.size(); i++) {
            final String text = ToolJson.dumps(records.get(i));
            bytes += text.length() + 1;
            profiles.add(records.get(i), text, stream, 0, i);
        }
        return SourceTypeProfiles.merge(null, profiles.toState(Map.of(stream + ":0", bytes * 10)));
    }
}
