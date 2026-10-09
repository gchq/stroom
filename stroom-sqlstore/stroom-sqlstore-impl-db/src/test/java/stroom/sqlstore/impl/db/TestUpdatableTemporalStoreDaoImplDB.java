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

package stroom.sqlstore.impl.db;

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.sqlstore.api.UpdatableTemporalStore;
import stroom.sqlstore.impl.UpdatableTemporalStoreDao;
import stroom.sqlstore.shared.ChangeOperation;
import stroom.sqlstore.shared.TemporalStoreTimeRange;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-database tests for {@link UpdatableTemporalStoreDaoImpl}.
 *
 * <p>These exercise behaviour that the mocked unit test
 * ({@code TestUpdatableTemporalStoreDaoImpl}) cannot: actual CRUD round-trips,
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} upsert semantics, the temporal
 * "valid at or before" resolution, and the transactional rollback of
 * {@link UpdatableTemporalStoreDao#applyChanges}.</p>
 *
 * <p>Requires a reachable MySQL (dev default {@code localhost:3307}); the
 * {@link DbTestModule} gives each Gradle fork its own uniquely-named schema.
 * Each test uses a fresh, unique map name so the tests are independent without
 * needing to clear shared state.</p>
 */
class TestUpdatableTemporalStoreDaoImplDB {

    // Three ascending effective times, expressed as ISO-8601 so the query-time
    // strings below read clearly. The store persists the epoch-milli values.
    private static final String T1_ISO = "2020-01-01T00:00:00.000Z";
    private static final String T2_ISO = "2021-01-01T00:00:00.000Z";
    private static final String T3_ISO = "2022-01-01T00:00:00.000Z";
    private static final long T1 = Instant.parse(T1_ISO).toEpochMilli();
    private static final long T2 = Instant.parse(T2_ISO).toEpochMilli();
    private static final long T3 = Instant.parse(T3_ISO).toEpochMilli();

    private static final AtomicInteger SEQ = new AtomicInteger();

    private static UpdatableTemporalStoreDao dao;

    /**
     * A store document: the UUID everything is scoped by, and the name, which is only a
     * denormalised label on each row.
     */
    private record Store(String uuid, String name) {

    }

    @BeforeAll
    static void beforeAll() {
        final Injector injector = Guice.createInjector(new TestModule());
        dao = injector.getInstance(UpdatableTemporalStoreDao.class);
    }

    // -----------------------------------------------------------------------
    // Scoping is by document UUID, not by name
    // -----------------------------------------------------------------------

    /**
     * Two store documents that happen to share a name must not share data.
     *
     * <p>Regression test for the original design, which keyed rows on {@code map_name}: two
     * same-named documents addressed the same rows, so either owner could read or
     * {@code clear()} the other's data.</p>
     */
    @Test
    void testTwoStoresSharingANameKeepSeparateData() {
        final String sharedName = "locations_" + SEQ.incrementAndGet();
        final Store a = new Store(uuid(), sharedName);
        final Store b = new Store(uuid(), sharedName);

        dao.create(a.uuid(), entry(a.name(), "desk", T1, "{\"owner\":\"A\"}"));
        dao.create(b.uuid(), entry(b.name(), "desk", T1, "{\"owner\":\"B\"}"));

        assertThat(dao.count(a.uuid())).isEqualTo(1);
        assertThat(dao.count(b.uuid())).isEqualTo(1);
        assertThat(dao.fetch(a.uuid(), id(a.name(), "desk")))
                .map(TemporalEntry::getValue)
                .hasValue("{\"owner\":\"A\"}");
        assertThat(dao.fetch(b.uuid(), id(b.name(), "desk")))
                .map(TemporalEntry::getValue)
                .hasValue("{\"owner\":\"B\"}");

        // Clearing one must leave the other untouched.
        dao.clear(a.uuid());
        assertThat(dao.count(a.uuid())).isZero();
        assertThat(dao.count(b.uuid())).isEqualTo(1);
    }

    /**
     * Renaming the owning document must not orphan its data.
     *
     * <p>Regression test for the headline bug: rows were keyed on {@code map_name}, so a rename
     * left every row addressed by a name nothing resolved to any more and the store read as
     * empty, with no error and no way back through the UI. Here the same UUID is used with a
     * new name, which is exactly what a rename looks like to this layer.</p>
     */
    @Test
    void testRenamingTheStoreKeepsItsData() {
        final Store before = uniqueStore();
        dao.create(before.uuid(), entry(before.name(), "gate", T1, "{\"v\":1}"));

        final Store after = new Store(before.uuid(), before.name() + "_renamed");

        assertThat(dao.count(after.uuid())).isEqualTo(1);
        assertThat(dao.fetchAll(after.uuid())).hasSize(1);
        assertThat(dao.fetch(after.uuid(), id(after.name(), "gate")))
                .map(TemporalEntry::getValue)
                .hasValue("{\"v\":1}");
        // And a temporal query still resolves, even though the criteria name has changed.
        assertThat(dao.find(after.uuid(), criteriaAt(after.name(), T3_ISO)).getValues())
                .hasSize(1);
    }

    // -----------------------------------------------------------------------
    // CRUD + count
    // -----------------------------------------------------------------------

    @Test
    void testCreateAndFetch() {
        final Store store = uniqueStore();
        dao.create(store.uuid(), entry(store.name(), "gate1", T1, "{\"name\":\"A\"}"));

        final Optional<TemporalEntry> fetched = dao.fetch(store.uuid(), id(store.name(), "gate1"));

        assertThat(fetched).isPresent();
        assertThat(fetched.get().getValue()).isEqualTo("{\"name\":\"A\"}");
        assertThat(dao.count(store.uuid())).isEqualTo(1);
    }

    @Test
    void testFetchMissingReturnsEmpty() {
        final Store store = uniqueStore();
        assertThat(dao.fetch(store.uuid(), id(store.name(), "nope"))).isEmpty();
    }

    /**
     * Two writes to the same natural key must upsert (overwrite value), not
     * insert a duplicate row.
     */
    @Test
    void testUpdateOverwritesValueForSameNaturalKey() {
        final Store store = uniqueStore();
        dao.create(store.uuid(), entry(store.name(), "gate1", T1, "{\"name\":\"A\"}"));
        dao.update(store.uuid(), entry(store.name(), "gate1", T1, "{\"name\":\"B\"}"));

        assertThat(dao.count(store.uuid())).isEqualTo(1);
        assertThat(dao.fetch(store.uuid(), id(store.name(), "gate1")))
                .map(TemporalEntry::getValue)
                .hasValue("{\"name\":\"B\"}");
    }

    /**
     * The same key at a different effective time is a distinct row, not an
     * overwrite.
     */
    @Test
    void testSameKeyDifferentTimeIsSeparateRow() {
        final Store store = uniqueStore();
        dao.create(store.uuid(), entry(store.name(), "gate1", T1, "{\"v\":1}"));
        dao.create(store.uuid(), entry(store.name(), "gate1", T2, "{\"v\":2}"));

        assertThat(dao.count(store.uuid())).isEqualTo(2);
    }

    @Test
    void testDelete() {
        final Store store = uniqueStore();
        dao.create(store.uuid(), entry(store.name(), "gate1", T1, "{}"));

        assertThat(dao.delete(store.uuid(), id(store.name(), "gate1"))).isTrue();
        assertThat(dao.fetch(store.uuid(), id(store.name(), "gate1"))).isEmpty();
        // Deleting again reports nothing removed.
        assertThat(dao.delete(store.uuid(), id(store.name(), "gate1"))).isFalse();
    }

    @Test
    void testClearRemovesOnlyThatStore() {
        final Store a = uniqueStore();
        final Store b = uniqueStore();
        dao.create(a.uuid(), entry(a.name(), "k1", T1, "{}"));
        dao.create(a.uuid(), entry(a.name(), "k2", T1, "{}"));
        dao.create(b.uuid(), entry(b.name(), "k1", T1, "{}"));

        dao.clear(a.uuid());

        assertThat(dao.count(a.uuid())).isZero();
        assertThat(dao.count(b.uuid())).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // getTimeRange
    // -----------------------------------------------------------------------

    @Test
    void testGetTimeRange() {
        final Store store = uniqueStore();
        dao.create(store.uuid(), entry(store.name(), "k1", T1, "{}"));
        dao.create(store.uuid(), entry(store.name(), "k1", T3, "{}"));
        dao.create(store.uuid(), entry(store.name(), "k2", T2, "{}"));

        final TemporalStoreTimeRange range = dao.getTimeRange(store.uuid());

        assertThat(range.getMinEffectiveTimeMs()).isEqualTo(T1);
        assertThat(range.getMaxEffectiveTimeMs()).isEqualTo(T3);
    }

    @Test
    void testGetTimeRangeEmptyStore() {
        final TemporalStoreTimeRange range = dao.getTimeRange(uniqueStore().uuid());
        assertThat(range.getMinEffectiveTimeMs()).isNull();
        assertThat(range.getMaxEffectiveTimeMs()).isNull();
    }

    // -----------------------------------------------------------------------
    // fetchAll - latest version per key
    // -----------------------------------------------------------------------

    @Test
    void testFetchAllReturnsLatestVersionPerKeySortedByKey() {
        final Store store = uniqueStore();
        dao.create(store.uuid(), entry(store.name(), "gate", T1, "{\"v\":1}"));
        dao.create(store.uuid(), entry(store.name(), "gate", T2, "{\"v\":2}"));
        dao.create(store.uuid(), entry(store.name(), "gate", T3, "{\"v\":3}"));
        dao.create(store.uuid(), entry(store.name(), "computer", T1, "{\"v\":9}"));

        final List<TemporalEntry> all = dao.fetchAll(store.uuid());

        assertThat(all).hasSize(2);
        // Sorted by key ascending: "computer" before "gate".
        assertThat(all.get(0).getKey()).isEqualTo("computer");
        assertThat(all.get(1).getKey()).isEqualTo("gate");
        // "gate" resolves to its latest version (T3).
        assertThat(all.get(1).getEffectiveTimeMs()).isEqualTo(T3);
        assertThat(all.get(1).getValue()).isEqualTo("{\"v\":3}");
    }

    // -----------------------------------------------------------------------
    // find - temporal "valid at or before" resolution
    // -----------------------------------------------------------------------

    /**
     * The core temporal contract: a query at a given time returns, for each key,
     * the most recent version whose effective time is at or before that time.
     */
    @Test
    void testFindResolvesVersionValidAtOrBeforeQueryTime() {
        final Store store = uniqueStore();
        dao.create(store.uuid(), entry(store.name(), "gate", T1, "{\"v\":1}"));
        dao.create(store.uuid(), entry(store.name(), "gate", T2, "{\"v\":2}"));
        dao.create(store.uuid(), entry(store.name(), "gate", T3, "{\"v\":3}"));

        // Query strictly between T2 and T3 -> resolves to the T2 version.
        final String betweenT2AndT3 = "2021-06-01T00:00:00.000Z";
        final ResultPage<TemporalEntry> result =
                dao.find(store.uuid(), criteriaAt(store.name(), betweenT2AndT3));

        assertThat(result.getValues()).hasSize(1);
        assertThat(result.getValues().get(0).getEffectiveTimeMs()).isEqualTo(T2);
        assertThat(result.getValues().get(0).getValue()).isEqualTo("{\"v\":2}");
    }

    @Test
    void testFindAtExactEffectiveTimeIsInclusive() {
        final Store store = uniqueStore();
        dao.create(store.uuid(), entry(store.name(), "gate", T1, "{\"v\":1}"));
        dao.create(store.uuid(), entry(store.name(), "gate", T2, "{\"v\":2}"));

        final ResultPage<TemporalEntry> result =
                dao.find(store.uuid(), criteriaAt(store.name(), T2_ISO));

        assertThat(result.getValues()).hasSize(1);
        assertThat(result.getValues().get(0).getValue()).isEqualTo("{\"v\":2}");
    }

    @Test
    void testFindBeforeAnyVersionReturnsNothing() {
        final Store store = uniqueStore();
        dao.create(store.uuid(), entry(store.name(), "gate", T2, "{\"v\":2}"));

        final ResultPage<TemporalEntry> result =
                dao.find(store.uuid(), criteriaAt(store.name(), T1_ISO));

        assertThat(result.getValues()).isEmpty();
    }

    @Test
    void testFindIsolatesByStore() {
        final Store a = uniqueStore();
        final Store b = uniqueStore();
        dao.create(a.uuid(), entry(a.name(), "gate", T1, "{\"store\":\"A\"}"));
        dao.create(b.uuid(), entry(b.name(), "gate", T1, "{\"store\":\"B\"}"));

        final ResultPage<TemporalEntry> result =
                dao.find(a.uuid(), criteriaAt(a.name(), T3_ISO));

        assertThat(result.getValues()).hasSize(1);
        assertThat(result.getValues().get(0).getValue()).isEqualTo("{\"store\":\"A\"}");
    }

    /**
     * A stale map name in the criteria must not hide the data. The {@code map_name} column is a
     * label, so terms on it are stripped before the query runs - honouring them would make a
     * renamed document look empty.
     */
    @Test
    void testFindIgnoresTheMapNameTerm() {
        final Store store = uniqueStore();
        dao.create(store.uuid(), entry(store.name(), "gate", T1, "{\"v\":1}"));

        final ResultPage<TemporalEntry> result =
                dao.find(store.uuid(), criteriaAt("a-name-that-was-never-used", T3_ISO));

        assertThat(result.getValues()).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // search - value column pruning
    // -----------------------------------------------------------------------

    /**
     * {@code includeValue = false} must skip the value column, not merely blank it afterwards.
     *
     * <p>The value is a {@code longtext}. A caller that only wants keys and times - the timeline
     * histogram - would otherwise have every row's payload read out of the database and thrown
     * away.</p>
     */
    @Test
    void testSearchCanOmitTheValueColumn() {
        final Store store = uniqueStore();
        dao.create(store.uuid(), entry(store.name(), "gate", T1, "{\"v\":1}"));

        final List<TemporalEntry> withValue = new java.util.ArrayList<>();
        dao.search(store.uuid(), criteriaAt(store.name(), T3_ISO), true, withValue::add);
        final List<TemporalEntry> withoutValue = new java.util.ArrayList<>();
        dao.search(store.uuid(), criteriaAt(store.name(), T3_ISO), false, withoutValue::add);

        assertThat(withValue).hasSize(1);
        assertThat(withValue.get(0).getValue()).isEqualTo("{\"v\":1}");

        // Same rows, same keys and times - only the payload is absent.
        assertThat(withoutValue).hasSize(1);
        assertThat(withoutValue.get(0).getKey()).isEqualTo("gate");
        assertThat(withoutValue.get(0).getEffectiveTimeMs()).isEqualTo(T1);
        assertThat(withoutValue.get(0).getValue()).isNull();
    }

    // -----------------------------------------------------------------------
    // applyChanges - atomic batch
    // -----------------------------------------------------------------------

    @Test
    void testApplyChangesAppliesUpsertsAndDeletes() {
        final Store store = uniqueStore();
        dao.create(store.uuid(), entry(store.name(), "stale", T1, "{}"));

        dao.applyChanges(store.uuid(), List.of(
                ChangeOperation.upsert(entry(store.name(), "gate", T1, "{\"v\":1}")),
                ChangeOperation.upsert(entry(store.name(), "gate", T1, "{\"v\":2}")), // overwrites
                ChangeOperation.delete(id(store.name(), "stale"))));

        assertThat(dao.count(store.uuid())).isEqualTo(1);
        assertThat(dao.fetch(store.uuid(), id(store.name(), "gate")))
                .map(TemporalEntry::getValue)
                .hasValue("{\"v\":2}");
    }

    /**
     * An operation carrying a different map name cannot reach another store's data - the batch
     * is scoped by the UUID passed to applyChanges, not by anything in the operations.
     */
    @Test
    void testApplyChangesCannotEscapeItsStoreViaTheMapName() {
        final Store target = uniqueStore();
        final Store other = uniqueStore();
        dao.create(other.uuid(), entry(other.name(), "victim", T1, "{\"owner\":\"other\"}"));

        // Name the other store in the operations; the write must still land on target.
        dao.applyChanges(target.uuid(), List.of(
                ChangeOperation.upsert(entry(other.name(), "victim", T1, "{\"owner\":\"attacker\"}")),
                ChangeOperation.delete(id(other.name(), "victim"))));

        // The other store is untouched.
        assertThat(dao.count(other.uuid())).isEqualTo(1);
        assertThat(dao.fetch(other.uuid(), id(other.name(), "victim")))
                .map(TemporalEntry::getValue)
                .hasValue("{\"owner\":\"other\"}");
        // The upsert and the delete both applied within the target store, netting zero rows.
        assertThat(dao.count(target.uuid())).isZero();
    }

    /**
     * Interleaved upserts and deletes must still apply in list order.
     *
     * <p>Consecutive operations of the same type are sent to the database as one JDBC batch, so
     * order is only preserved if a run is flushed the moment the type changes. Batching the whole
     * list by type instead would reorder these three into upsert, upsert, delete and leave the row
     * gone rather than present.</p>
     */
    @Test
    void testInterleavedUpsertsAndDeletesApplyInOrder() {
        final Store store = uniqueStore();

        dao.applyChanges(store.uuid(), List.of(
                ChangeOperation.upsert(entry(store.name(), "gate", T1, "{\"v\":1}")),
                ChangeOperation.delete(id(store.name(), "gate")),
                ChangeOperation.upsert(entry(store.name(), "gate", T1, "{\"v\":2}"))));

        // The final upsert wins: the row exists, with the last value written.
        assertThat(dao.count(store.uuid())).isEqualTo(1);
        assertThat(dao.fetch(store.uuid(), id(store.name(), "gate")))
                .map(TemporalEntry::getValue)
                .hasValue("{\"v\":2}");
    }

    /** And the reverse order leaves the row deleted. */
    @Test
    void testInterleavedEndingInDeleteLeavesNothing() {
        final Store store = uniqueStore();

        dao.applyChanges(store.uuid(), List.of(
                ChangeOperation.upsert(entry(store.name(), "gate", T1, "{\"v\":1}")),
                ChangeOperation.delete(id(store.name(), "gate"))));

        assertThat(dao.count(store.uuid())).isZero();
    }

    /** A large batch round-trips intact - the bind count per row has to match the template. */
    @Test
    void testLargeBatchWritesEveryRow() {
        final Store store = uniqueStore();
        final List<ChangeOperation> ops = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) {
            ops.add(ChangeOperation.upsert(entry(store.name(), "k" + i, T1, "{\"i\":" + i + "}")));
        }

        dao.applyChanges(store.uuid(), ops);

        assertThat(dao.count(store.uuid())).isEqualTo(500);
        assertThat(dao.fetch(store.uuid(), id(store.name(), "k499")))
                .map(TemporalEntry::getValue)
                .hasValue("{\"i\":499}");
    }

    /**
     * If any operation in the batch fails, the whole transaction must roll back
     * - no partial writes are visible.
     */
    @Test
    void testApplyChangesRollsBackOnError() {
        final Store store = uniqueStore();

        assertThatThrownBy(() -> dao.applyChanges(store.uuid(), List.of(
                ChangeOperation.upsert(entry(store.name(), "good", T1, "{}")),
                // UPSERT with a null entry trips the guard mid-transaction.
                new ChangeOperation(ChangeOperation.Type.UPSERT, null, null))))
                .isInstanceOf(IllegalArgumentException.class);

        // The earlier valid upsert must have been rolled back.
        assertThat(dao.count(store.uuid())).isZero();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Store uniqueStore() {
        final int n = SEQ.incrementAndGet();
        return new Store(uuid(), "testStore_" + n);
    }

    private static String uuid() {
        return java.util.UUID.randomUUID().toString();
    }

    private static TemporalEntry entry(final String map,
                                       final String key,
                                       final long time,
                                       final String value) {
        return new TemporalEntry(map, key, time, value);
    }

    private static TemporalEntryId id(final String map, final String key) {
        return new TemporalEntryId(map, key, TestUpdatableTemporalStoreDaoImplDB.T1);
    }

    /**
     * Builds a find criteria that pins the map name and a temporal lookup time,
     * mirroring the StroomQL the UI issues at a playback timeline position. The map term is
     * stripped by the DAO; scoping comes from the UUID argument.
     */
    private static ExpressionCriteria criteriaAt(final String map, final String isoTime) {
        return new ExpressionCriteria(ExpressionOperator.builder()
                .addTerm(UpdatableTemporalStore.MAP_FIELD.getFldName(),
                        ExpressionTerm.Condition.EQUALS, map)
                .addTerm(UpdatableTemporalStore.TIME_FIELD.getFldName(),
                        ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO, isoTime)
                .build());
    }
}
