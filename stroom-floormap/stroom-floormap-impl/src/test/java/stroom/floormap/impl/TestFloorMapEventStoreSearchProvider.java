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

package stroom.floormap.impl;

import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.planb.impl.dao.Db;
import stroom.planb.impl.dao.temporalstate.TemporalStateDb;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.query.api.Param;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The read contract: what a caller must say to get a snapshot, and where the expiry floor comes from.
///
/// This is the part of the design that replaced a read mode inferred from whether a time term
/// happened to be `<` rather than `>`. The rules are therefore worth pinning precisely —
/// an accidental relaxation here would put the guessing back.
class TestFloorMapEventStoreSearchProvider {

    private static final Instant AS_AT = Instant.parse("2026-01-01T12:00:00.000Z");

    private static List<Param> params(final String... keysAndValues) {
        final List<Param> list = new java.util.ArrayList<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            list.add(new Param(keysAndValues[i], keysAndValues[i + 1]));
        }
        return list;
    }

    private static FloorMapEventStoreDoc docWithExpiry(final SimpleDuration expiry) {
        return FloorMapEventStoreDoc.eventStoreBuilder()
                .uuid("uuid")
                .name("events")
                .eventExpiry(expiry)
                .build();
    }

    // ------------------------------------------------------------------
    // Neither half of the contract works alone.
    // ------------------------------------------------------------------

    @Test
    void noParametersMeansAnOrdinaryRangeRead() {
        assertThat(FloorMapEventStoreSearchProvider.readAsAt(params()))
                .as("the default is what every other data source gives")
                .isNull();
        assertThat(FloorMapEventStoreSearchProvider.readAsAt(null)).isNull();
    }

    @Test
    void bothParametersTogetherGiveASnapshot() {
        assertThat(FloorMapEventStoreSearchProvider.readAsAt(params(
                "readMode", "snapshot",
                "asAt", String.valueOf(AS_AT.toEpochMilli()))))
                .isEqualTo(AS_AT);
    }

    @Test
    void asAtWithoutAReadModeIsRefused() {
        // Refused rather than assumed: a caller who believes they asked for a snapshot must not
        // silently receive every row instead.
        assertThatThrownBy(() -> FloorMapEventStoreSearchProvider.readAsAt(
                params("asAt", String.valueOf(AS_AT.toEpochMilli()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readMode");
    }

    @Test
    void readModeWithoutAnAsAtIsRefused() {
        assertThatThrownBy(() -> FloorMapEventStoreSearchProvider.readAsAt(
                params("readMode", "snapshot")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asAt");
    }

    @Test
    void anUnknownReadModeIsNamedRatherThanIgnored() {
        assertThatThrownBy(() -> FloorMapEventStoreSearchProvider.readAsAt(
                params("readMode", "latest", "asAt", "1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latest");
    }

    @Test
    void theReadModeIsNotCaseSensitive() {
        assertThat(FloorMapEventStoreSearchProvider.readAsAt(params(
                "readMode", "SNAPSHOT",
                "asAt", String.valueOf(AS_AT.toEpochMilli()))))
                .isEqualTo(AS_AT);
    }

    // ------------------------------------------------------------------
    // asAt is epoch millis, and only that.
    // ------------------------------------------------------------------

    @Test
    void dateLiteralIsRefusedRatherThanParsed() {
        // The whole point: no date parser takes part in deciding what a query means, so the answer
        // cannot depend on how a literal is spelled or on the viewer's time zone.
        assertThatThrownBy(() -> FloorMapEventStoreSearchProvider.readAsAt(
                params("readMode", "snapshot", "asAt", "2026-01-01T12:00:00.000Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("epoch milliseconds");
    }

    @Test
    void surroundingWhitespaceIsTolerated() {
        assertThat(FloorMapEventStoreSearchProvider.readAsAt(params(
                "readMode", "snapshot",
                "asAt", "  " + AS_AT.toEpochMilli() + " ")))
                .isEqualTo(AS_AT);
    }

    @Test
    void anInstantTheStoreCannotRepresentIsNamedHere() {
        // Rather than surfacing from inside the key encoding as "Negative values are not permitted",
        // several frames from anything the caller wrote.
        assertThatThrownBy(() -> FloorMapEventStoreSearchProvider.readAsAt(
                params("readMode", "snapshot", "asAt", "-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the range");

        assertThatThrownBy(() -> FloorMapEventStoreSearchProvider.readAsAt(
                params("readMode", "snapshot", "asAt", String.valueOf(Long.MAX_VALUE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the range");
    }

    // ------------------------------------------------------------------
    // The expiry floor belongs to the store.
    // ------------------------------------------------------------------

    @Test
    void anEventStoreIsAccepted() {
        final FloorMapEventStoreDoc doc = docWithExpiry(null);
        assertThat(FloorMapEventStoreSearchProvider.requireEventStore(doc)).isSameAs(doc);
    }

    @Test
    void theFloorIsAsAtMinusTheStoresExpiry() {
        final SimpleDuration twoHours = SimpleDuration.builder()
                .time(2)
                .timeUnit(TimeUnit.HOURS)
                .build();

        assertThat(FloorMapEventStoreSearchProvider.expiryFloor(docWithExpiry(twoHours), AS_AT))
                .isEqualTo(AS_AT.minusSeconds(2 * 3600L));
    }

    @Test
    void anUnsetExpiryUsesTheStoresDefault() {
        assertThat(FloorMapEventStoreSearchProvider.expiryFloor(docWithExpiry(null), AS_AT))
                .as("the documented default is 24 hours")
                .isEqualTo(AS_AT.minusSeconds(24 * 3600L));
    }

    /// A store of another type is refused where the document is resolved.
    ///
    /// `PlanBDocCache` resolves by name across every registered Plan B type, so a name
    /// belonging to another type resolves to that type's document. Checked once, at resolution,
    /// rather than on the snapshot path alone — the range read would otherwise serve another store's
    /// rows quite happily, and the snapshot read would seek over an encoding that is not prefix-free
    /// and drop keys in silence.
    @Test
    void storeOfAnotherTypeIsRefused() {
        final PlanBDoc otherType = PlanBDoc.builder()
                .uuid("uuid")
                .name("events")
                .stateType(StateType.TEMPORAL_STATE)
                .build();

        assertThatThrownBy(() -> FloorMapEventStoreSearchProvider.requireEventStore(otherType))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FloorMapEventStoreDoc.TYPE);
    }

    // ------------------------------------------------------------------
    // The dispatch: which read actually runs.
    // ------------------------------------------------------------------

    /// A shard manager that hands out one reader and remembers nothing else.
    ///
    /// A Mockito mock with a custom answer rather than a plain stub, because `get` takes a
    /// `Function` and runs it — the behaviour under test is which method that function calls,
    /// so the double has to actually invoke it. A stubbed `get` would return without running
    /// anything, and the test would pass against a provider that did nothing at all.
    private static ShardManager shardManagerServing(final Db<?, ?> reader) {
        final ShardManager shardManager = Mockito.mock(ShardManager.class);
        Mockito.when(shardManager.get(Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    final Function<Db<?, ?>, Object> function = invocation.getArgument(1);
                    return function.apply(reader);
                });
        return shardManager;
    }

    @Test
    void withoutAnAsAtTheOrdinaryReadRuns() {
        try (final TemporalStateDb reader = Mockito.mock(TemporalStateDb.class)) {
            FloorMapEventStoreSearchProvider.readThrough(
                    shardManagerServing(reader), "events", null, null, null, null, null, null, null);

            Mockito.verify(reader).search(Mockito.any(), Mockito.any(), Mockito.any(),
                    Mockito.any(), Mockito.any());
            Mockito.verify(reader, Mockito.never()).searchSnapshot(
                    Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                    Mockito.any(), Mockito.any());
        }
    }

    @Test
    void withAnAsAtTheSnapshotRunsAndCarriesTheFloor() {
        final Instant floor = AS_AT.minusSeconds(3600L);

        try (final TemporalStateDb reader = Mockito.mock(TemporalStateDb.class)) {
            FloorMapEventStoreSearchProvider.readThrough(
                    shardManagerServing(reader), "events", null, null, null, null, null, AS_AT, floor);

            Mockito.verify(reader).searchSnapshot(
                    Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                    Mockito.eq(AS_AT), Mockito.eq(floor));
            Mockito.verify(reader, Mockito.never()).search(
                    Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        }
    }

    /// A store that is not a temporal state store cannot serve a snapshot.
    ///
    /// Holds by construction — the document type fixes `stateType` — so this is here for the
    /// case where it somehow does not, to fail with something a person can act on rather than by
    /// reading an encoding that is not prefix-free.
    @Test
    void snapshotOverTheWrongKindOfStoreIsRefused() {
        try (final Db<?, ?> notTemporal = Mockito.mock(Db.class)) {
            assertThatThrownBy(() -> FloorMapEventStoreSearchProvider.readThrough(
                    shardManagerServing(notTemporal), "events", null, null, null, null, null, AS_AT, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("temporal state store");
        }
    }
}
