/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.shared;

import stroom.planb.shared.KeyType;
import stroom.planb.shared.StateType;
import stroom.planb.shared.StateValueType;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalStateKeySchema;
import stroom.planb.shared.TemporalStateSettings;
import stroom.util.json.JsonUtil;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The type's guarantees, which stand in place of a runtime guard.
///
/// The Map tab's read seeks to each entity's answer rather than scanning, and that is only correct
/// over a prefix-free key encoding. Rather than check the encoding on every read and throw, the
/// document type makes a store that would need a scan inexpressible. These tests are what hold that
/// claim up — if one of them fails, the seek has become unsafe.
class TestFloorMapEventStoreDoc {

    private static FloorMapEventStoreDoc doc(final TemporalStateSettings settings) {
        return new FloorMapEventStoreDoc(
                "uuid", "events", null, null, null, null, null, null,
                null, settings, null);
    }

    @Test
    void theKeyEncodingIsFixedWhenNoSettingsAreGiven() {
        final TemporalStateSettings settings = (TemporalStateSettings) doc(null).getSettings();

        assertThat(settings.getKeySchema().getKeyType())
                .as("the seek is only valid over a prefix-free encoding")
                .isEqualTo(KeyType.TERMINATED_STRING);
    }

    /// The point of fixing it in the constructor rather than hiding a form field.
    ///
    /// A hidden dropdown still leaves the value settable by import, by the REST API, or by editing
    /// an exported document. Overwriting it here is what makes the guarantee hold for every route in.
    @Test
    void keyEncodingSuppliedFromOutsideIsOverwritten() {
        final TemporalStateSettings hostile = new TemporalStateSettings.Builder()
                .keySchema(new TemporalStateKeySchema(
                        KeyType.VARIABLE,
                        null,
                        TemporalPrecision.SECOND))
                .build();

        final TemporalStateSettings settings = (TemporalStateSettings) doc(hostile).getSettings();

        assertThat(settings.getKeySchema().getKeyType()).isEqualTo(KeyType.TERMINATED_STRING);
        assertThat(settings.getKeySchema().getTemporalPrecision())
                .as("a coarser precision would silently drop events sharing a tick")
                .isEqualTo(TemporalPrecision.MILLISECOND);
    }

    @Test
    void theValueEncodingIsFixedToTheOneThatDeduplicates() {
        final TemporalStateSettings settings = (TemporalStateSettings) doc(null).getSettings();

        assertThat(settings.getValueSchema().getStateValueType())
                .as("repeated values are most of the volume, and VARIABLE dedupes them")
                .isEqualTo(StateValueType.VARIABLE);
    }

    @Test
    void theStateTypeIsAlwaysTemporalState() {
        // Whatever is passed: this is what makes ingest, merge, condense, retention and shard
        // deletion Plan B's rather than ours.
        final FloorMapEventStoreDoc withWrongType = new FloorMapEventStoreDoc(
                "uuid", "events", null, null, null, null, null, null,
                StateType.SESSION, null, null);

        assertThat(withWrongType.getStateType()).isEqualTo(StateType.TEMPORAL_STATE);
    }

    @Test
    void settingsTheUserMayChangeAreCarriedThrough() {
        // Everything that is not a schema stays the user's to set, because none of it is immutable
        // once data exists.
        final TemporalStateSettings given = new TemporalStateSettings.Builder()
                .maxStoreSize(1234L)
                .build();

        final TemporalStateSettings settings = (TemporalStateSettings) doc(given).getSettings();

        assertThat(settings.getMaxStoreSize()).isEqualTo(1234L);
    }

    @Test
    void expiryDefaultsToADayRatherThanToOff() {
        assertThat(doc(null).getEventExpiry())
                .as("raw, so the editor can tell unset from set-to-24-hours")
                .isNull();
        assertThat(doc(null).getEventExpiryOrDefault())
                .isEqualTo(FloorMapEventStoreDoc.DEFAULT_EVENT_EXPIRY);
        assertThat(FloorMapEventStoreDoc.DEFAULT_EVENT_EXPIRY.getTimeUnit())
                .isEqualTo(TimeUnit.HOURS);
        assertThat(FloorMapEventStoreDoc.DEFAULT_EVENT_EXPIRY.getTime()).isEqualTo(24);
    }

    @Test
    void anExplicitExpiryIsKept() {
        final SimpleDuration tenMinutes = SimpleDuration.builder()
                .time(10)
                .timeUnit(TimeUnit.MINUTES)
                .build();

        final FloorMapEventStoreDoc doc = FloorMapEventStoreDoc.eventStoreBuilder()
                .uuid("uuid")
                .name("events")
                .eventExpiry(tenMinutes)
                .build();

        assertThat(doc.getEventExpiry()).isEqualTo(tenMinutes);
        assertThat(doc.getEventExpiryOrDefault()).isEqualTo(tenMinutes);
    }

    /// The import route, which is the one a hidden form field would not have covered.
    @Test
    void documentDeserialisedWithAForeignKeySchemaStillReadsBackFixed() {
        final String json = JsonUtil.writeValueAsString(doc(new TemporalStateSettings.Builder()
                .keySchema(new TemporalStateKeySchema(KeyType.VARIABLE, null, null))
                .build()));

        final FloorMapEventStoreDoc read = JsonUtil.readValue(json, FloorMapEventStoreDoc.class);
        final TemporalStateSettings settings = (TemporalStateSettings) read.getSettings();

        assertThat(settings.getKeySchema().getKeyType()).isEqualTo(KeyType.TERMINATED_STRING);
    }

    @Test
    void roundTripsThroughJson() {
        final FloorMapEventStoreDoc original = FloorMapEventStoreDoc.eventStoreBuilder()
                .uuid("uuid")
                .name("events")
                .eventExpiry(SimpleDuration.builder().time(2).timeUnit(TimeUnit.HOURS).build())
                .build();

        final String json = JsonUtil.writeValueAsString(original);
        final FloorMapEventStoreDoc read = JsonUtil.readValue(json, FloorMapEventStoreDoc.class);

        assertThat(read).isEqualTo(original);
    }

    @Test
    void copyingKeepsTheExpiryAndTheFixedSchema() {
        final FloorMapEventStoreDoc original = FloorMapEventStoreDoc.eventStoreBuilder()
                .uuid("uuid")
                .name("events")
                .eventExpiry(SimpleDuration.builder().time(5).timeUnit(TimeUnit.MINUTES).build())
                .build();

        final FloorMapEventStoreDoc copy = original.copyEventStore().name("other").build();

        assertThat(copy.getEventExpiry()).isEqualTo(original.getEventExpiry());
        assertThat(((TemporalStateSettings) copy.getSettings()).getKeySchema().getKeyType())
                .isEqualTo(KeyType.TERMINATED_STRING);
    }
}
