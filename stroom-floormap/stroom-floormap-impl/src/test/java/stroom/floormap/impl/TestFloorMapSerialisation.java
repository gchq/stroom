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

package stroom.floormap.impl;

import stroom.docref.DocRef;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapGroup;
import stroom.floormap.shared.FloorMapMeasurementUnits;
import stroom.floormap.shared.FloorMapMeasurementUnits.Unit;
import stroom.query.api.TimeRange;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Verifies JSON serialisation and deserialisation of [FloorMapDoc],
/// including round-trip fidelity and backward compatibility with legacy JSON
/// that may contain removed fields.
class TestFloorMapSerialisation {

    @Test
    void testSerializationRoundTrip() {
        final DocRef storeRef = DocRef.builder()
                .type("SqlTemporalStore")
                .uuid("store-uuid-123")
                .name("StoreName")
                .build();

        final TimeRange timeRange = new TimeRange("LAST_24_HOURS", null, null);

        final FloorMapDoc original = FloorMapDoc.builder()
                .uuid("map-uuid-456")
                .name("MyFloorMap")
                .description("Floor map description")
                .factsStoreRef(storeRef)
                .eventsQuery("from StoreName select events")
                .eventsQueryTimeRange(timeRange)
                .build();

        // Serialize
        final String json = JsonUtil.writeValueAsString(original);
        assertThat(json).isNotNull();

        // Deserialize
        final FloorMapDoc deserialized = JsonUtil.readValue(json, FloorMapDoc.class);
        assertThat(deserialized).isNotNull();

        // Assert new fields
        assertThat(deserialized.getFactsStoreRef()).isEqualTo(storeRef);
        assertThat(deserialized.getEventsQuery()).isEqualTo("from StoreName select events");
        assertThat(deserialized.getEventsQueryTimeRange()).isEqualTo(timeRange);
    }

    @Test
    void testLegacyJsonIgnored() {
        // Old FloorMapDoc JSON that contains the removed 'query' / 'queryTimeRange' fields.
        // Jackson should silently ignore unknown fields; deserialisation must not throw.
        final String oldJson = "{"
                               + "\"uuid\":\"map-uuid-456\","
                               + "\"name\":\"MyFloorMap\","
                               + "\"description\":\"Floor map description\","
                               + "\"query\":\"from StoreName select old_query\","
                               + "\"queryTimeRange\":{\"name\":\"LAST_24_HOURS\"}"
                               + "}";

        final FloorMapDoc deserialized = JsonUtil.readValue(oldJson, FloorMapDoc.class);
        assertThat(deserialized).isNotNull();

        // The legacy 'query' field is not migrated, so eventsQuery stays null.
        assertThat(deserialized.getEventsQuery()).isNull();
        assertThat(deserialized.getFactsStoreRef()).isNull();
        // A document written before groups existed simply has none.
        assertThat(deserialized.getGroups()).isNull();
        // Likewise units: an uncalibrated map has no scale, and that is normal.
        assertThat(deserialized.getMeasurementUnits()).isNull();
    }

    /// A document written before `temporalStoreRef` was renamed must still find
    /// its facts store.
    ///
    /// The rename is carried by a single `@JsonAlias("temporalStoreRef")` on
    /// the `factsStoreRef` constructor parameter. Nothing else migrates the field
    /// and nothing else was pinning it, so deleting that one annotation — or renaming
    /// the parameter without moving the alias with it — would silently detach every
    /// pre-rename document from its store. The map would then open with no facts and no
    /// error, which reads as "the data is gone" rather than "the document did not
    /// load".
    @Test
    void testLegacyTemporalStoreRefMigratesToFactsStoreRef() {
        final String legacyJson = "{"
                                  + "\"uuid\":\"map-uuid-456\","
                                  + "\"name\":\"MyFloorMap\","
                                  + "\"temporalStoreRef\":{"
                                  + "\"type\":\"SqlTemporalStore\","
                                  + "\"uuid\":\"store-uuid-123\","
                                  + "\"name\":\"StoreName\"}"
                                  + "}";

        final FloorMapDoc deserialized = JsonUtil.readValue(legacyJson, FloorMapDoc.class);

        assertThat(deserialized.getFactsStoreRef()).isNotNull();
        assertThat(deserialized.getFactsStoreRef().getUuid()).isEqualTo("store-uuid-123");
        assertThat(deserialized.getFactsStoreRef().getType()).isEqualTo("SqlTemporalStore");
        assertThat(deserialized.getFactsStoreRef().getName()).isEqualTo("StoreName");
        // The alias feeds the facts store only — the events store has no alias and a
        // legacy document never named one.
        assertThat(deserialized.getEventsStoreRef()).isNull();
    }

    /// The migration completes on the next save: the document is rewritten under the
    /// new name, and reading it back a second time still resolves the store.
    ///
    /// This is the half that makes the rename one-way. If the field were written
    /// back under the alias, the old name would live on in newly-saved documents for
    /// ever and the alias could never be retired.
    @Test
    void testMigratedFactsStoreRefIsRewrittenUnderTheNewName() {
        final String legacyJson = "{"
                                  + "\"uuid\":\"map-uuid-456\","
                                  + "\"name\":\"MyFloorMap\","
                                  + "\"temporalStoreRef\":{"
                                  + "\"type\":\"SqlTemporalStore\","
                                  + "\"uuid\":\"store-uuid-123\","
                                  + "\"name\":\"StoreName\"}"
                                  + "}";

        final FloorMapDoc migrated = JsonUtil.readValue(legacyJson, FloorMapDoc.class);
        final String rewritten = JsonUtil.writeValueAsString(migrated);

        assertThat(rewritten).contains("factsStoreRef");
        assertThat(rewritten).doesNotContain("temporalStoreRef");

        // And it survives the second read, which is what the user actually depends on.
        final FloorMapDoc reread = JsonUtil.readValue(rewritten, FloorMapDoc.class);
        assertThat(reread.getFactsStoreRef()).isEqualTo(migrated.getFactsStoreRef());
        assertThat(reread.getFactsStoreRef().getUuid()).isEqualTo("store-uuid-123");
    }

    /// Groups are configuration stored on the document, so they must survive a
    /// write/read cycle intact — ids included, since a lost id would orphan the
    /// group's identity.
    @Test
    void testGroupsRoundTrip() {
        final FloorMapGroup maintenance = new FloorMapGroup(
                "group-40213", "Maintenance", "#8e24aa",
                List.of("bob@x.com", "gate-3"));
        final FloorMapGroup security = new FloorMapGroup(
                "group-11902", "Security", null, List.of());

        final FloorMapDoc original = FloorMapDoc.builder()
                .uuid("map-uuid-456")
                .name("MyFloorMap")
                .groups(List.of(maintenance, security))
                .build();

        final FloorMapDoc deserialized = JsonUtil.readValue(
                JsonUtil.writeValueAsString(original), FloorMapDoc.class);

        assertThat(deserialized.getGroups()).containsExactly(maintenance, security);
        //noinspection SequencedCollectionMethodCanBeUsed
        final FloorMapGroup readBack = deserialized.getGroups().get(0);
        assertThat(readBack.getId()).isEqualTo("group-40213");
        assertThat(readBack.getName()).isEqualTo("Maintenance");
        assertThat(readBack.getColour()).isEqualTo("#8e24aa");
        assertThat(readBack.getMemberIds()).containsExactly("bob@x.com", "gate-3");
        // A colourless group still renders: the default fills in at read time.
        assertThat(deserialized.getGroups().get(1).findColourOrDefault())
                .isEqualTo(FloorMapGroup.DEFAULT_COLOUR);
    }

    /// A calibrated scale must survive a write/read cycle: losing it would silently
    /// revert every size on the map to "map units".
    @Test
    void testMeasurementUnitsRoundTrip() {
        final FloorMapMeasurementUnits units =
                new FloorMapMeasurementUnits(Unit.METRE, 0.187);

        final FloorMapDoc original = FloorMapDoc.builder()
                .uuid("map-uuid-456")
                .name("MyFloorMap")
                .measurementUnits(units)
                .build();

        final FloorMapDoc deserialized = JsonUtil.readValue(
                JsonUtil.writeValueAsString(original), FloorMapDoc.class);

        assertThat(deserialized.getMeasurementUnits()).isEqualTo(units);
        assertThat(deserialized.getMeasurementUnits().getUnit()).isEqualTo(Unit.METRE);
        assertThat(deserialized.getMeasurementUnits().getUnitsPerMapUnit()).isEqualTo(0.187);
    }

    /// Every tab's `onWrite` returns `doc.copy()…build()`, so a field
    /// missing from the copy constructor is silently deleted whenever the user
    /// saves from a tab that does not itself write it.
    @Test
    void testMeasurementUnitsSurviveACopy() {
        final FloorMapMeasurementUnits units =
                new FloorMapMeasurementUnits(Unit.FOOT, 2.5);

        final FloorMapDoc original = FloorMapDoc.builder()
                .uuid("map-uuid-456")
                .name("MyFloorMap")
                .measurementUnits(units)
                .build();

        // A copy that changes something else entirely — as the Settings,
        // Query and Documentation tabs all do.
        final FloorMapDoc copied = original.copy().description("edited elsewhere").build();

        assertThat(copied.getMeasurementUnits()).isEqualTo(units);
    }

    /// The client decides the document is dirty by diffing the written doc against
    /// the read one, so calibrating the map must change equality — otherwise the
    /// save button never lights up and the user's work is lost on close.
    @Test
    void testMeasurementUnitsAffectEquality() {
        final FloorMapDoc uncalibrated = FloorMapDoc.builder()
                .uuid("map-uuid-456")
                .name("MyFloorMap")
                .build();
        final FloorMapDoc calibrated = uncalibrated.copy()
                .measurementUnits(new FloorMapMeasurementUnits(Unit.METRE, 0.187))
                .build();
        final FloorMapDoc rescaled = calibrated.copy()
                .measurementUnits(new FloorMapMeasurementUnits(Unit.METRE, 0.25))
                .build();

        assertThat(calibrated).isNotEqualTo(uncalibrated);
        assertThat(rescaled).isNotEqualTo(calibrated);
        assertThat(calibrated.hashCode()).isNotEqualTo(uncalibrated.hashCode());
        assertThat(calibrated).isEqualTo(uncalibrated.copy()
                .measurementUnits(new FloorMapMeasurementUnits(Unit.METRE, 0.187))
                .build());
    }
}
