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

package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.planb.impl.dao.temporalstate.TemporalStateDb;
import stroom.planb.impl.dao.temporalstate.TemporalStateFields;
import stroom.planb.impl.data.value.TemporalState;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.planb.shared.KeyType;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TemporalStateKeySchema;
import stroom.planb.shared.TemporalStateSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TemporalStateDb#searchSnapshot} — the explicit point-in-time read behind the FloorMap
 * Event Store.
 *
 * <p>Separate from {@code TestTemporalStateDb} deliberately: that file is byte-identical to
 * {@code origin/master} and worth keeping so, since this fork's only change to the class is an
 * additive method that upstream knows nothing about.</p>
 *
 * <p>Every store here uses {@link KeyType#TERMINATED_STRING}, because that is the encoding the
 * FloorMap Event Store fixes and the only one the seek is valid over. A test on the default
 * {@code VARIABLE} encoding would be testing a configuration the document type cannot produce.</p>
 */
class TestTemporalStateDbSnapshot {

    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00.000Z");

    private static PlanBDoc doc() {
        return PlanBDoc
                .builder()
                .uuid(UUID.randomUUID().toString())
                .name("events")
                .stateType(StateType.TEMPORAL_STATE)
                .settings(new TemporalStateSettings.Builder()
                        .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
                        .keySchema(new TemporalStateKeySchema.Builder()
                                .keyType(KeyType.TERMINATED_STRING)
                                .build())
                        .build())
                .build();
    }

    private static FieldIndex fieldIndex() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create(TemporalStateFields.KEY);
        fieldIndex.create(TemporalStateFields.EFFECTIVE_TIME);
        fieldIndex.create(TemporalStateFields.VALUE);
        return fieldIndex;
    }

    private static void insert(final TemporalStateDb db, final String key, final long hour) {
        db.write(writer -> db.insert(writer, new TemporalState(
                TemporalKey.builder()
                        .prefix(KeyPrefix.create(key))
                        .time(T0.plusSeconds(hour * 3600L))
                        .build(),
                ValString.create(key + "@" + hour))));
    }

    /** Two keys, three entries each, one hour apart. */
    private static void insertFixture(final TemporalStateDb db) {
        for (final String key : List.of("alpha", "beta")) {
            for (int hour = 0; hour < 3; hour++) {
                insert(db, key, hour);
            }
        }
    }

    private static List<Val[]> snapshot(final TemporalStateDb db,
                                        final Instant asAt,
                                        final Instant notBefore) {
        return snapshot(db, asAt, notBefore, ExpressionOperator.builder().build());
    }

    private static List<Val[]> snapshot(final TemporalStateDb db,
                                        final Instant asAt,
                                        final Instant notBefore,
                                        final ExpressionOperator expression) {
        final List<Val[]> results = new ArrayList<>();
        db.searchSnapshot(
                new ExpressionCriteria(expression),
                fieldIndex(),
                null,
                new ExpressionPredicateFactory(),
                results::add,
                asAt,
                notBefore);
        return results;
    }

    private static List<String> keysOf(final List<Val[]> rows) {
        return rows.stream().map(row -> row[0].toString()).toList();
    }

    private static List<String> valuesOf(final List<Val[]> rows) {
        return rows.stream().map(row -> row[2].toString()).toList();
    }

    // ------------------------------------------------------------------
    // The property the whole encoding exists for.
    // ------------------------------------------------------------------

    /**
     * A key whose encoded bytes would extend another's must still be emitted.
     *
     * <p>Under an unterminated encoding the seek steps past {@code door1} by jumping beyond
     * {@code door1 + 0xFF...}, which clears every {@code door10} and {@code door11} entry too, so
     * those entities silently vanish from the map. The terminator is what prevents it, and this is
     * the test that would catch its loss.</p>
     */
    @Test
    void keysWhoseBytesExtendAnotherAreNotSkipped(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            for (final String name : List.of("door1", "door10", "door11", "door2", "door")) {
                insert(db, name, 1);
            }

            final List<Val[]> results = snapshot(db, T0.plusSeconds(2 * 3600L), null);

            assertThat(keysOf(results))
                    .containsExactlyInAnyOrder("door1", "door10", "door11", "door2", "door");
        }
    }

    // ------------------------------------------------------------------
    // Snapshot semantics.
    // ------------------------------------------------------------------

    @Test
    void returnsTheNewestEntryPerKey(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            insertFixture(db);

            // As at 02:30 - after the third entry of each key.
            final List<Val[]> results = snapshot(db, T0.plusSeconds(2 * 3600L + 1800L), null);

            assertThat(results).hasSize(2);
            assertThat(valuesOf(results)).containsExactly("alpha@2", "beta@2");
        }
    }

    @Test
    void picksTheEntryInForceNotTheNewest(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            insertFixture(db);

            // As at 00:30 - only the first entry of each key is in force.
            final List<Val[]> results = snapshot(db, T0.plusSeconds(1800L), null);

            assertThat(valuesOf(results)).containsExactly("alpha@0", "beta@0");
        }
    }

    @Test
    void includesAnEntryExactlyOnTheBoundary(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            insertFixture(db);

            final List<Val[]> results = snapshot(db, T0.plusSeconds(3600L), null);

            assertThat(valuesOf(results)).containsExactly("alpha@1", "beta@1");
        }
    }

    @Test
    void omitsKeysWithNoEntryYet(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            insert(db, "alpha", 0);
            // beta only comes into existence an hour later.
            insert(db, "beta", 1);

            final List<Val[]> results = snapshot(db, T0.plusSeconds(1800L), null);

            assertThat(keysOf(results)).containsExactly("alpha");
        }
    }

    @Test
    void returnsNothingForAnEmptyStore(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            assertThat(snapshot(db, T0, null)).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // The floor: expiry as a property of the read.
    // ------------------------------------------------------------------

    @Test
    void omitsAKeyWhoseNewestEntryPredatesTheFloor(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            insert(db, "stale", 0);
            insert(db, "fresh", 2);

            // As at 03:00, ignoring anything last seen before 01:00.
            final List<Val[]> results = snapshot(
                    db,
                    T0.plusSeconds(3 * 3600L),
                    T0.plusSeconds(3600L));

            assertThat(keysOf(results))
                    .as("stale was last seen at 00:00, before the floor, so it has nothing in scope")
                    .containsExactly("fresh");
        }
    }

    @Test
    void floorEqualToTheEntryTimeStillIncludesIt(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            insert(db, "alpha", 1);

            final List<Val[]> results = snapshot(
                    db,
                    T0.plusSeconds(3 * 3600L),
                    T0.plusSeconds(3600L));

            assertThat(keysOf(results)).containsExactly("alpha");
        }
    }

    @Test
    void noFloorMeansAKeyIsInScopeHoweverOldItIs(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            insert(db, "ancient", 0);

            final List<Val[]> results = snapshot(db, T0.plusSeconds(100 * 3600L), null);

            assertThat(keysOf(results)).containsExactly("ancient");
        }
    }

    // ------------------------------------------------------------------
    // Condense, which the store's settings tab warns about.
    // ------------------------------------------------------------------

    /**
     * Condense makes a stationary entity expire while it is still emitting.
     *
     * <p>This is the warning the FloorMap Event Store's settings tab carries — "This will cause
     * repeating events to disappear from the map" — and this is the mechanism behind it.
     * {@code condense} collapses a run of identical values to the run's <b>earliest</b> row, so an
     * entity that keeps re-reporting the same position has its newest row deleted and its "last
     * seen" time rewritten backwards. Under an expiry floor it then falls out of scope.</p>
     *
     * <p>Worth pinning rather than only documenting: nothing about either setting hints at the
     * other, and the symptom is an entity quietly missing from the map.</p>
     */
    @Test
    void condenseMovesAStationaryEntityBehindTheFloor(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            // One entity, same value at 00:00, 01:00 and 02:00 - a stationary entity still emitting.
            db.write(writer -> {
                for (int hour = 0; hour < 3; hour++) {
                    db.insert(writer, new TemporalState(
                            TemporalKey.builder()
                                    .prefix(KeyPrefix.create("parked"))
                                    .time(T0.plusSeconds(hour * 3600L))
                                    .build(),
                            ValString.create("bay7")));
                }
            });

            final Instant asAt = T0.plusSeconds(3 * 3600L);
            final Instant floor = T0.plusSeconds(90 * 60L);

            assertThat(keysOf(snapshot(db, asAt, floor)))
                    .as("before condense the newest row is at 02:00, after the floor")
                    .containsExactly("parked");

            db.condense(asAt);

            assertThat(keysOf(snapshot(db, asAt, floor)))
                    .as("condense keeps only the 00:00 row, which is before the floor, so the entity "
                        + "expires despite having emitted at 02:00")
                    .isEmpty();
        }
    }

    /** Without a floor, condense changes which row is returned but not whether the entity is. */
    @Test
    void condenseWithoutAFloorStillReturnsTheEntity(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            db.write(writer -> {
                for (int hour = 0; hour < 3; hour++) {
                    db.insert(writer, new TemporalState(
                            TemporalKey.builder()
                                    .prefix(KeyPrefix.create("parked"))
                                    .time(T0.plusSeconds(hour * 3600L))
                                    .build(),
                            ValString.create("bay7")));
                }
            });

            final Instant asAt = T0.plusSeconds(3 * 3600L);
            db.condense(asAt);

            assertThat(keysOf(snapshot(db, asAt, null)))
                    .as("the seek still finds the surviving row, whatever time it carries")
                    .containsExactly("parked");
        }
    }

    // ------------------------------------------------------------------
    // The expression is applied whole.
    // ------------------------------------------------------------------

    @Test
    void honoursANonTimeTerm(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            insertFixture(db);

            final List<Val[]> results = snapshot(
                    db,
                    T0.plusSeconds(3 * 3600L),
                    null,
                    ExpressionOperator.builder()
                            .addTextTerm(TemporalStateFields.KEY_FIELD, Condition.EQUALS, "beta")
                            .build());

            assertThat(keysOf(results)).containsExactly("beta");
        }
    }

    /**
     * A user's own time term is a filter, not something to strip.
     *
     * <p>The inferred path this replaces called {@code removeTimeTerms}, which was right when the
     * framework injected a zero-width range and wrong for anything the user wrote. With the mode
     * explicit there is nothing injected, so the term survives — and the walk keeps stepping back
     * until it finds a row that satisfies it.</p>
     */
    @Test
    void honoursATimeTermWrittenByTheCaller(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            insertFixture(db);

            final List<Val[]> results = snapshot(
                    db,
                    T0.plusSeconds(3 * 3600L),
                    null,
                    ExpressionOperator.builder()
                            .addDateTerm(TemporalStateFields.EFFECTIVE_TIME_FIELD,
                                    Condition.LESS_THAN,
                                    String.valueOf(T0.plusSeconds(2 * 3600L).toEpochMilli()))
                            .build());

            assertThat(valuesOf(results))
                    .as("the newest row satisfying the term, not the newest row")
                    .containsExactly("alpha@1", "beta@1");
        }
    }

    // ------------------------------------------------------------------
    // The ordinary read is untouched.
    // ------------------------------------------------------------------

    @Test
    void theOrdinarySearchStillReturnsAllHistory(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            insertFixture(db);

            final List<Val[]> results = new ArrayList<>();
            db.search(
                    new ExpressionCriteria(ExpressionOperator.builder().build()),
                    fieldIndex(),
                    null,
                    new ExpressionPredicateFactory(),
                    results::add);

            assertThat(results)
                    .as("search() is untouched: no mode inference, every row returned")
                    .hasSize(6);
        }
    }

    /**
     * The shape that used to flip the store into a snapshot behind the caller's back.
     *
     * <p>A zero-width {@code TimeRange} reaches the store as {@code >= T AND < T}, which no row
     * satisfies. It now returns nothing, which is what the expression says — the caller that wants a
     * snapshot asks for one.</p>
     */
    @Test
    void zeroWidthRangeIsNoLongerReadAsASnapshot(@TempDir final Path tempDir) {
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, doc(), false)) {
            insertFixture(db);

            final String millis = String.valueOf(T0.plusSeconds(2 * 3600L).toEpochMilli());
            final List<Val[]> results = new ArrayList<>();
            db.search(
                    new ExpressionCriteria(ExpressionOperator.builder()
                            .addDateTerm(TemporalStateFields.EFFECTIVE_TIME_FIELD,
                                    Condition.GREATER_THAN_OR_EQUAL_TO, millis)
                            .addDateTerm(TemporalStateFields.EFFECTIVE_TIME_FIELD,
                                    Condition.LESS_THAN, millis)
                            .build()),
                    fieldIndex(),
                    null,
                    new ExpressionPredicateFactory(),
                    results::add);

            assertThat(results).isEmpty();
        }
    }
}
