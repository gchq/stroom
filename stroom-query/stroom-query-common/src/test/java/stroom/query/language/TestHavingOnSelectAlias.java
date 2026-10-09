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

package stroom.query.language;

import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FilteredMapper;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.Values;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Whether a {@code having} clause can filter on a column alias that the {@code select} defines.
 *
 * <p>This is the premise the Floor Map's event-expiry design rests on
 * (<i>docs/floormap-event-expiry-plan.md</i>, assumption A1). The intended query is</p>
 *
 * <pre>
 * from param('EventStore')
 * having "Effective Time" &gt; param('ExpiryFloor')
 * select EffectiveTime as "Effective Time", ...
 * </pre>
 *
 * <p>which drops any entity whose latest event predates the floor. The clause names the alias, and
 * the alias is defined further down in the {@code select}.</p>
 */
class TestHavingOnSelectAlias {

    private static final String QUERY = """
            from "events"
            having "Effective Time" > param('ExpiryFloor')
            select EffectiveTime as "Effective Time", Key as "Entity ID"
            """;

    private static final long FLOOR_MS = 1_757_000_000_000L;

    /** The shape that works: the {@code having} field and the column name are the same string. */
    private static final String WORKING_QUERY = """
            from "events"
            having EffectiveTime > param('ExpiryFloor')
            select EffectiveTime, Key as "Entity ID"
            """;

    /** The clause parses, and the parameter is substituted into the term. */
    @Test
    void theClauseParsesAndTheFloorIsSubstituted() {
        final TableSettings tableSettings = tableSettings(QUERY, FLOOR_MS);

        assertThat(tableSettings.getAggregateFilter()).isNotNull();
        assertThat(tableSettings.getAggregateFilter().getChildren()).hasSize(1);
        assertThat(tableSettings.getAggregateFilter().getChildren().getFirst().toString())
                .contains("Effective Time")
                .contains(String.valueOf(FLOOR_MS));
    }

    /**
     * <b>The clause adds a second column with the same name, even though the alias is selected.</b>
     *
     * <p>{@code SearchRequestFactory} collects having-referenced fields and adds a column for any
     * it does not already hold, which is what lets a {@code having} name a column the
     * {@code select} omits. Its record of what it already holds is keyed on the source field
     * ({@code EffectiveTime}), not the alias — so naming the alias looks new, and a column is added
     * whose expression is the alias <em>as a string literal</em> rather than the value.</p>
     */
    @Test
    void theClauseAddsADuplicateColumnWhoseExpressionIsALiteral() {
        final List<Column> columns = tableSettings(QUERY, FLOOR_MS).getColumns();

        final List<Column> named = columns.stream()
                .filter(c -> "Effective Time".equals(c.getName()))
                .toList();

        assertThat(named).hasSize(2);
        assertThat(named.getFirst().getExpression()).isEqualTo("${EffectiveTime}");
        assertThat(named.getFirst().isVisible()).isTrue();
        assertThat(named.getLast().getExpression()).isEqualTo("'Effective Time'");
        assertThat(named.getLast().isVisible()).isFalse();
    }

    /**
     * <b>And the duplicate is the one the filter binds to, so the filter rejects every row.</b>
     *
     * <p>{@code RowUtil.createColumnNameValExtractor} builds a {@code HashMap} keyed by column
     * name, so where two columns share a name the last one wins — and the literal is last. Every
     * row therefore presents the constant text {@code "Effective Time"} where a date is wanted, no
     * row can be after the floor, and the map goes empty.</p>
     *
     * <p>This test asserts the broken behaviour so the defect is recorded rather than rediscovered.
     * When it is fixed — by keying on the source field, by not adding a column whose alias is
     * already present, or by making the added column carry the value rather than its name — this
     * test should be rewritten to assert that the recent row passes, not deleted.</p>
     */
    @Test
    void theFilterBindsToTheLiteralAndSoRejectsEveryRow() {
        final TableSettings tableSettings = tableSettings(QUERY, FLOOR_MS);
        final Predicate<Values> predicate = predicateFor(tableSettings);

        // Column order, from the parse: the selected pair, three hidden id columns, then the
        // duplicate the having clause added.
        final Values wellAfterTheFloor = values(
                ValDate.create(FLOOR_MS + 60_000L),
                ValString.create("alice@example.org"),
                ValString.EMPTY, ValString.EMPTY, ValString.EMPTY,
                ValString.create("Effective Time"));

        assertThat(predicate.test(wellAfterTheFloor))
                .describedAs("a row an hour newer than the floor is still rejected, because the "
                             + "filter is reading the literal column the clause added")
                .isFalse();
    }

    /** The same query with the floor at epoch still rejects everything, for the same reason. */
    @Test
    void zeroFloorDoesNotRescueIt() {
        final Predicate<Values> predicate = predicateFor(tableSettings(QUERY, 0L));

        final Values anyRow = values(
                ValDate.create(FLOOR_MS),
                ValString.create("alice@example.org"),
                ValString.EMPTY, ValString.EMPTY, ValString.EMPTY,
                ValString.create("Effective Time"));

        assertThat(predicate.test(anyRow)).isFalse();
    }

    /**
     * Naming the <b>source field</b> instead does not rescue it either — nothing is called that.
     *
     * <p>The column took the alias as its name, so {@code EffectiveTime} matches no column and the
     * predicate cannot be built at all. In the real pipeline that throws during mapper construction,
     * before any row is fetched, so it surfaces as zero rows plus an error rather than as a filter
     * that does nothing.</p>
     */
    @Test
    void namingTheSourceFieldFindsNoColumn() {
        final String query = """
                from "events"
                having EffectiveTime > param('ExpiryFloor')
                select EffectiveTime as "Effective Time", Key as "Entity ID"
                """;
        final TableSettings tableSettings = tableSettings(query, FLOOR_MS);

        assertThatThrownBy(() -> predicateFor(tableSettings))
                .hasMessageContaining("Field not found: EffectiveTime");
    }

    /**
     * <b>The working shape: the {@code having} field and the column name must be the same string.</b>
     *
     * <p>When they agree, the parser sees the field as already held and adds no duplicate, so the
     * filter binds to the real column and compares dates. This is what the three cases above are
     * each missing, and it is the constraint any query relying on a {@code having} filter has to
     * satisfy.</p>
     */
    @Test
    void havingFieldMatchingTheColumnNameFiltersCorrectly() {
        final String query = """
                from "events"
                having EffectiveTime > param('ExpiryFloor')
                select EffectiveTime as EffectiveTime, Key as "Entity ID"
                """;
        final TableSettings tableSettings = tableSettings(query, FLOOR_MS);
        final Predicate<Values> predicate = predicateFor(tableSettings);

        assertThat(tableSettings.getColumns().stream()
                .filter(c -> "EffectiveTime".equals(c.getName()))
                .count())
                .describedAs("no duplicate column is added when the names agree")
                .isEqualTo(1);

        assertThat(predicate.test(rowWithTimeAt(tableSettings, 0, FLOOR_MS + 60_000L)))
                .describedAs("newer than the floor").isTrue();
        assertThat(predicate.test(rowWithTimeAt(tableSettings, 0, FLOOR_MS - 60_000L)))
                .describedAs("older than the floor").isFalse();
    }

    /** The same holds with no alias at all, which is the same thing spelled shorter. */
    @Test
    void anUnaliasedColumnFiltersCorrectly() {
        final String query = """
                from "events"
                having EffectiveTime > param('ExpiryFloor')
                select EffectiveTime, Key as "Entity ID"
                """;
        final TableSettings tableSettings = tableSettings(query, FLOOR_MS);
        final Predicate<Values> predicate = predicateFor(tableSettings);

        assertThat(predicate.test(rowWithTimeAt(tableSettings, 0, FLOOR_MS + 60_000L))).isTrue();
        assertThat(predicate.test(rowWithTimeAt(tableSettings, 0, FLOOR_MS - 60_000L))).isFalse();
    }

    /**
     * <b>Zero is not a neutral floor.</b>
     *
     * <p>The Floor Map's expiry design has executions that must not filter — the timeline histogram
     * and the Events Query tab — bind a floor that passes everything rather than removing the
     * clause. Epoch was the obvious candidate and is wrong: a store holding anything from before
     * 1970 would have it silently dropped from exactly the reads that are supposed to show
     * everything.</p>
     */
    @Test
    void zeroIsNotANeutralFloorBecauseItRejectsPre1970Rows() {
        final TableSettings tableSettings = tableSettings(WORKING_QUERY, 0L);
        final Predicate<Values> predicate = predicateFor(tableSettings);

        final long apollo11 = -14_182_940_000L;   // 1969-07-20, comfortably before epoch
        assertThat(predicate.test(rowWithTimeAt(tableSettings, 0, apollo11)))
                .describedAs("a pre-1970 row is dropped by a zero floor")
                .isFalse();
    }

    /**
     * <b>{@code Long.MIN_VALUE} is a neutral floor, and parses.</b>
     *
     * <p>Worth asserting rather than assuming: the value travels as a bare number and
     * {@code DateExpressionParser} turns it into an {@code Instant}, which has a narrower range
     * than {@code long} milliseconds might suggest. It is in range, and it passes a date no
     * plausible store holds anything before.</p>
     */
    @Test
    void longMinValueIsANeutralFloor() {
        final TableSettings tableSettings = tableSettings(WORKING_QUERY, Long.MIN_VALUE);
        final Predicate<Values> predicate = predicateFor(tableSettings);

        final long apollo11 = -14_182_940_000L;
        assertThat(predicate.test(rowWithTimeAt(tableSettings, 0, apollo11)))
                .describedAs("a pre-1970 row passes").isTrue();
        assertThat(predicate.test(rowWithTimeAt(tableSettings, 0, FLOOR_MS)))
                .describedAs("a modern row passes").isTrue();
    }

    // -----------------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------------

    private static TableSettings tableSettings(final String query, final long floorMs) {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(0L).build();
        final SearchRequest in = new SearchRequest(
                null,
                new QueryKey("test"),
                Query.builder()
                        .params(List.of(new Param("ExpiryFloor", String.valueOf(floorMs))))
                        .build(),
                new ArrayList<ResultRequest>(0),
                dateTimeSettings,
                false);
        final ExpressionContext expressionContext = ExpressionContext
                .builder()
                .dateTimeSettings(dateTimeSettings)
                .maxStringLength(100)
                .build();
        final SearchRequest out = new SearchRequestFactory(
                (keywordGroup, parentTableSettings) -> null,
                MockDataSourceResolver.getInstance(),
                () -> criteria -> null,
                MockSecurityContext.getInstance())
                .create(query, in, expressionContext);
        return out.getResultRequests().getFirst().getMappings().getFirst();
    }

    private static Predicate<Values> predicateFor(final TableSettings tableSettings) {
        final Optional<Predicate<Values>> predicate = FilteredMapper.createValuesPredicate(
                tableSettings.getColumns(),
                tableSettings.getAggregateFilter(),
                DateTimeSettings.builder().referenceTime(0L).build(),
                new ExpressionPredicateFactory());
        assertThat(predicate).describedAs("a having clause should produce a predicate").isPresent();
        return predicate.get();
    }

    private static int indexOfColumnNamed(final TableSettings tableSettings, final String name) {
        final List<Column> columns = tableSettings.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (name.equals(columns.get(i).getName())) {
                return i;
            }
        }
        return -1;
    }

    private static Values rowWithTimeAt(final TableSettings tableSettings,
                                        final int timeIndex,
                                        final long timeMs) {
        final int size = tableSettings.getColumns().size();
        final Val[] vals = new Val[size];
        for (int i = 0; i < size; i++) {
            vals[i] = ValString.EMPTY;
        }
        vals[timeIndex] = ValDate.create(timeMs);
        return values(vals);
    }

    private static Values values(final Val... vals) {
        return new Values() {
            @Override
            public Val getValue(final int index) {
                return index < vals.length
                        ? vals[index]
                        : ValString.EMPTY;
            }

            @Override
            public int size() {
                return vals.length;
            }

            @Override
            public Val[] toArray() {
                return vals;
            }
        };
    }
}
