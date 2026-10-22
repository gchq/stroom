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

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.sqlstore.api.UpdatableTemporalStore;
import stroom.util.shared.PageRequest;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TestUpdatableTemporalStoreDaoImpl {

    @Mock
    private SqlStoreDbConnProvider sqlStoreDbConnProvider;

    @Mock
    private ExpressionMapperFactory expressionMapperFactory;

    @Mock
    private ExpressionMapper expressionMapper;

    private UpdatableTemporalStoreDaoImpl dao;

    @BeforeEach
    void setUp() {
        Mockito.when(expressionMapperFactory.create()).thenReturn(expressionMapper);
        dao = new UpdatableTemporalStoreDaoImpl(sqlStoreDbConnProvider, expressionMapperFactory);
    }

    private static final String UUID = "11111111-2222-3333-4444-555555555555";

    @Test
    void testCreateWithNullDocUuid() {
        final TemporalEntry entry = new TemporalEntry("map1", "key1", 1000L, "val");
        assertThatThrownBy(() -> dao.create(null, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    @Test
    void testCreateWithBlankDocUuid() {
        final TemporalEntry entry = new TemporalEntry("map1", "key1", 1000L, "val");
        assertThatThrownBy(() -> dao.create("  ", entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    @Test
    void testCreateWithNullEntry() {
        assertThatThrownBy(() -> dao.create(UUID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporal entry: there is no entry.");
    }

    @Test
    void testCreateWithNullMap() {
        final TemporalEntry entry = new TemporalEntry(null, "key1", 1000L, "val");
        assertThatThrownBy(() -> dao.create(UUID, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporal entry: the map name must not be blank.");
    }

    @Test
    void testCreateWithEmptyMap() {
        final TemporalEntry entry = new TemporalEntry("  ", "key1", 1000L, "val");
        assertThatThrownBy(() -> dao.create(UUID, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporal entry: the map name must not be blank.");
    }

    @Test
    void testCreateWithNullKey() {
        final TemporalEntry entry = new TemporalEntry("map1", null, 1000L, "val");
        assertThatThrownBy(() -> dao.create(UUID, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporal entry: the key must not be blank.");
    }

    @Test
    void testCreateWithEmptyKey() {
        final TemporalEntry entry = new TemporalEntry("map1", "", 1000L, "val");
        assertThatThrownBy(() -> dao.create(UUID, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporal entry: the key must not be blank.");
    }

    @Test
    void testCreateWithNullEffectiveTime() {
        final TemporalEntry entry = new TemporalEntry("map1", "key1", null, "val");
        assertThatThrownBy(() -> dao.create(UUID, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporal entry: the effective time must be set.");
    }

    /// The DAO applies the same key rules as every other write path, so an over-long key is refused
    /// with a message rather than failing in the database.
    @Test
    void testCreateWithOverLongKey() {
        final TemporalEntry entry = new TemporalEntry(
                "map1", "k".repeat(UpdatableTemporalStore.MAX_KEY_LENGTH + 1), 1000L, "val");
        assertThatThrownBy(() -> dao.create(UUID, entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid temporal entry: the key is "
                                        + (UpdatableTemporalStore.MAX_KEY_LENGTH + 1) + " characters long");
        Mockito.verifyNoInteractions(sqlStoreDbConnProvider);
    }

    /// Regression test: the page length was used as given, so a request for a huge page read the
    /// whole store into memory. It is refused before any database work.
    @Test
    void testFindRefusesAPageLongerThanTheMaximum() {
        final ExpressionCriteria criteria = new ExpressionCriteria(
                new PageRequest(0, UpdatableTemporalStoreDaoImpl.MAX_PAGE_LENGTH + 1),
                null,
                ExpressionOperator.builder()
                        .addTerm(UpdatableTemporalStore.MAP_FIELD.getFldName(), Condition.EQUALS, "map1")
                        .build());

        assertThatThrownBy(() -> dao.find(UUID, criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(UpdatableTemporalStoreDaoImpl.MAX_PAGE_LENGTH));
        Mockito.verifyNoInteractions(sqlStoreDbConnProvider);
    }

    @Test
    void testFetchWithNullDocUuid() {
        final TemporalEntryId id = new TemporalEntryId("map1", "key1", 1000L);
        assertThatThrownBy(() -> dao.fetch(null, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    @Test
    void testFetchWithNullId() {
        assertThatThrownBy(() -> dao.fetch(UUID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporal entry: there is no entry id.");
    }

    /**
     * A null map name on an id is no longer an error. Scoping is by document UUID, so the map
     * name on an id is not consulted at all - see UpdatableTemporalStoreDaoImpl's class doc.
     */
    @Test
    void testFetchWithNullMapIsAccepted() {
        final TemporalEntryId id = new TemporalEntryId(null, "key1", 1000L);
        assertThatThrownBy(() -> dao.fetch(UUID, id))
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testFetchWithNullKey() {
        final TemporalEntryId id = new TemporalEntryId("map1", null, 1000L);
        assertThatThrownBy(() -> dao.fetch(UUID, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporal entry: the key must not be blank.");
    }

    @Test
    void testFetchWithNullEffectiveTime() {
        final TemporalEntryId id = new TemporalEntryId("map1", "key1", null);
        assertThatThrownBy(() -> dao.fetch(UUID, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporal entry: the effective time must be set.");
    }

    @Test
    void testDeleteWithNullDocUuid() {
        final TemporalEntryId id = new TemporalEntryId("map1", "key1", 1000L);
        assertThatThrownBy(() -> dao.delete(null, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    @Test
    void testDeleteWithNullId() {
        assertThatThrownBy(() -> dao.delete(UUID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporal entry: there is no entry id.");
    }

    @Test
    void testDeleteWithNullKey() {
        final TemporalEntryId id = new TemporalEntryId("map1", null, 1000L);
        assertThatThrownBy(() -> dao.delete(UUID, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporal entry: the key must not be blank.");
    }

    @Test
    void testDeleteWithNullEffectiveTime() {
        final TemporalEntryId id = new TemporalEntryId("map1", "key1", null);
        assertThatThrownBy(() -> dao.delete(UUID, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporal entry: the effective time must be set.");
    }

    @Test
    void testClearWithNullDocUuid() {
        assertThatThrownBy(() -> dao.clear(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    @Test
    void testFetchAllWithNullDocUuid() {
        assertThatThrownBy(() -> dao.fetchAll(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    @Test
    void testGetTimeRangeWithNullDocUuid() {
        assertThatThrownBy(() -> dao.getTimeRange(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Store document UUID must be defined and not empty.");
    }

    // -----------------------------------------------------------------------
    // findSnapshotTerm - which time term, if any, makes a read a snapshot
    // -----------------------------------------------------------------------

    @Test
    void testSnapshotTermIsATopLevelUpperBound() {
        final ExpressionTerm upper = timeTerm(Condition.LESS_THAN_OR_EQUAL_TO, "2021-01-01T00:00:00.000Z");
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(UpdatableTemporalStore.MAP_FIELD.getFldName(), Condition.EQUALS, "map1")
                .addTerm(upper)
                .build();

        assertThat(UpdatableTemporalStoreDaoImpl.findSnapshotTerm(new ExpressionCriteria(expression)))
                .isSameAs(upper);
    }

    /** The framework wraps the user's expression in further ANDs; a conjunct is still a conjunct. */
    @Test
    void testSnapshotTermIsFoundThroughNestedAnds() {
        final ExpressionTerm upper = timeTerm(Condition.LESS_THAN, "2021-01-01T00:00:00.000Z");
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder()
                        .addTerm(timeTerm(Condition.GREATER_THAN_OR_EQUAL_TO, "2020-01-01T00:00:00.000Z"))
                        .addTerm(upper)
                        .build())
                .build();

        assertThat(UpdatableTemporalStoreDaoImpl.findSnapshotTerm(new ExpressionCriteria(expression)))
                .isSameAs(upper);
    }

    /**
     * Regression test: time terms were collected from anywhere in the tree, so one inside a NOT or
     * an OR became the snapshot instant for the whole query, changing what it meant.
     */
    @Test
    void testTimeTermsInsideNotOrOrAreNotSnapshotTerms() {
        final ExpressionOperator not = ExpressionOperator.builder()
                .op(Op.NOT)
                .addTerm(timeTerm(Condition.LESS_THAN_OR_EQUAL_TO, "2021-01-01T00:00:00.000Z"))
                .build();
        final ExpressionOperator or = ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(timeTerm(Condition.LESS_THAN_OR_EQUAL_TO, "2021-01-01T00:00:00.000Z"))
                .addTerm(UpdatableTemporalStore.KEY_FIELD.getFldName(), Condition.EQUALS, "gate")
                .build();

        assertThat(UpdatableTemporalStoreDaoImpl.findSnapshotTerm(new ExpressionCriteria(
                ExpressionOperator.builder().addOperator(not).build())))
                .isNull();
        assertThat(UpdatableTemporalStoreDaoImpl.findSnapshotTerm(new ExpressionCriteria(
                ExpressionOperator.builder().addOperator(or).build())))
                .isNull();
    }

    @Test
    void testLowerBoundsAndDisabledOrUnparseableTermsAreNotSnapshotTerms() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(timeTerm(Condition.GREATER_THAN_OR_EQUAL_TO, "2020-01-01T00:00:00.000Z"))
                .addTerm(ExpressionTerm.builder()
                        .field(UpdatableTemporalStore.TIME_FIELD.getFldName())
                        .condition(Condition.LESS_THAN_OR_EQUAL_TO)
                        .value("2021-01-01T00:00:00.000Z")
                        .enabled(false)
                        .build())
                .addTerm(timeTerm(Condition.LESS_THAN_OR_EQUAL_TO, "not a date"))
                .build();

        assertThat(UpdatableTemporalStoreDaoImpl.findSnapshotTerm(new ExpressionCriteria(expression)))
                .isNull();
    }

    private static ExpressionTerm timeTerm(final Condition condition, final String value) {
        return ExpressionTerm.builder()
                .field(UpdatableTemporalStore.TIME_FIELD.getFldName())
                .condition(condition)
                .value(value)
                .build();
    }
}
