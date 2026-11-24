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

package stroom.dashboard.impl;

import stroom.dashboard.shared.ColumnValue;
import stroom.dashboard.shared.ColumnValues;
import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.query.api.ColumnValueSelection;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ResultRequest;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.Item;
import stroom.query.common.v2.ItemMapper;
import stroom.query.common.v2.Key;
import stroom.query.common.v2.KeyFactory;
import stroom.query.common.v2.ValPredicateFactory;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.util.shared.PageRequest;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class TestColumnValuesFinder {

    private static final String COLOUR = "colour";
    private static final String SHAPE = "shape";

    // Each row is {colour, shape}.
    private static final List<String[]> ROWS = List.of(
            new String[]{"red", "circle"},
            new String[]{"red", "square"},
            new String[]{"green", "square"},
            new String[]{"blue", "triangle"},
            new String[]{"yellow", "circle"});

    private final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();
    private final ColumnValuesFinder finder = new ColumnValuesFinder(
            expressionPredicateFactory,
            new ValPredicateFactory(expressionPredicateFactory));

    @Test
    void allValuesAvailableWithNoFilters() {
        final ColumnValues result = find(column(COLOUR), column(SHAPE));

        assertThat(values(result)).containsExactly("blue", "green", "red", "yellow");
        assertThat(availability(result)).containsOnly(true);
        assertThat(result.getAvailableCount()).isEqualTo(4);
    }

    @Test
    void otherColumnTicksPutValuesBelowTheLine() {
        // Only circles are ticked in the shape column.
        final ColumnValues result = find(
                column(COLOUR),
                column(SHAPE, ticked("circle")));

        assertThat(values(result)).containsExactly("red", "yellow", "blue", "green");
        assertThat(availability(result)).containsExactly(true, true, false, false);
        assertThat(result.getAvailableCount()).isEqualTo(2);
    }

    @Test
    void ownTicksNeverHideOwnValues() {
        // Red is unticked in the colour column itself.
        final ColumnValues result = find(
                column(COLOUR, unticked("red")),
                column(SHAPE));

        assertThat(values(result)).containsExactly("blue", "green", "red", "yellow");
        assertThat(availability(result)).containsOnly(true);
    }

    @Test
    void valueIsAvailableIfAnyOfItsRowsPasses() {
        // Red has a circle row that passes and a square row that does not.
        final ColumnValues result = find(
                column(COLOUR),
                column(SHAPE, unticked("square")));

        assertThat(values(result)).containsExactly("blue", "red", "yellow", "green");
        assertThat(availability(result)).containsExactly(true, true, true, false);
    }

    @Test
    void otherColumnWithNothingTickedMakesEveryValueUnavailable() {
        final ColumnValues result = find(
                column(COLOUR),
                column(SHAPE, ColumnValueSelection.builder().invert(false).build()));

        assertThat(values(result)).containsExactly("blue", "green", "red", "yellow");
        assertThat(availability(result)).containsOnly(false);
        assertThat(result.getAvailableCount()).isZero();
    }

    @Test
    void otherColumnTextFilterNarrows() {
        final ColumnValues result = find(
                column(COLOUR),
                column(SHAPE).copy().columnFilter(new ColumnFilter("tri", true)).build());

        assertThat(values(result)).containsExactly("blue", "green", "red", "yellow");
        assertThat(availability(result)).containsExactly(true, false, false, false);
    }

    @Test
    void ownTextFilterNarrows() {
        final ColumnValues result = find(
                column(COLOUR).copy().columnFilter(new ColumnFilter("ree", true)).build(),
                column(SHAPE));

        assertThat(values(result)).containsExactly("green", "blue", "red", "yellow");
        assertThat(availability(result)).containsExactly(true, false, false, false);
    }

    @Test
    void disabledTextFilterIsIgnored() {
        final ColumnValues result = find(
                column(COLOUR),
                column(SHAPE).copy().columnFilter(new ColumnFilter("tri", false)).build());

        assertThat(availability(result)).containsOnly(true);
    }

    @Test
    void selectionFilterFromOtherComponentsNarrows() {
        final ExpressionOperator aggregateFilter = ExpressionOperator.builder()
                .addTerm(SHAPE, Condition.EQUALS, "square")
                .build();
        final TableSettings tableSettings = TableSettings.builder()
                .columns(List.of(column(COLOUR), column(SHAPE)))
                .aggregateFilter(aggregateFilter)
                .build();

        final ColumnValues result = find(tableSettings, null, PageRequest.unlimited());

        assertThat(values(result)).containsExactly("green", "red", "blue", "yellow");
        assertThat(availability(result)).containsExactly(true, true, false, false);
    }

    @Test
    void quickFilterLeavesValuesOutAltogether() {
        final ColumnValues result = find(
                tableSettings(column(COLOUR), column(SHAPE, ticked("circle"))),
                "e",
                PageRequest.unlimited());

        assertThat(values(result)).containsExactly("red", "yellow", "blue", "green");
        assertThat(result.getPageResponse().getTotal()).isEqualTo(4);

        final ColumnValues narrowed = find(
                tableSettings(column(COLOUR), column(SHAPE, ticked("circle"))),
                "low",
                PageRequest.unlimited());

        assertThat(values(narrowed)).containsExactly("yellow");
        assertThat(narrowed.getAvailableCount()).isEqualTo(1);
    }

    @Test
    void availableCountCoversAllPages() {
        final ColumnValues result = find(
                tableSettings(column(COLOUR), column(SHAPE, ticked("circle"))),
                null,
                new PageRequest(1, 2));

        assertThat(values(result)).containsExactly("yellow", "blue");
        assertThat(availability(result)).containsExactly(true, false);
        assertThat(result.getAvailableCount()).isEqualTo(2);
        assertThat(result.getPageResponse().getOffset()).isEqualTo(1);
        assertThat(result.getPageResponse().getTotal()).isEqualTo(4);
    }

    @Test
    void columnNotInTableGivesNoValues() {
        final ColumnValues result = finder.find(
                dataStore(),
                resultRequest(tableSettings(column(COLOUR), column(SHAPE))),
                column("size"),
                null,
                null,
                PageRequest.unlimited(),
                DateTimeSettings.builder().build());

        assertThat(result.getValues()).isEmpty();
        assertThat(result.getAvailableCount()).isZero();
    }

    @Test
    void noTableSettingsUsesDataStoreColumns() {
        final ColumnValues result = finder.find(
                dataStore(),
                ResultRequest.builder().componentId("table").build(),
                column(COLOUR),
                null,
                null,
                PageRequest.unlimited(),
                DateTimeSettings.builder().build());

        assertThat(values(result)).containsExactly("blue", "green", "red", "yellow");
        assertThat(availability(result)).containsOnly(true);
    }

    @Test
    void matchingConditionalFormattingRuleIsKept() {
        final ConditionalFormattingRule rule = ConditionalFormattingRule.builder()
                .id("redRule")
                .enabled(true)
                .expression(ExpressionOperator.builder()
                        .addTerm(COLOUR, Condition.EQUALS, "red")
                        .build())
                .build();

        final ColumnValues result = finder.find(
                dataStore(),
                resultRequest(tableSettings(column(COLOUR), column(SHAPE, ticked("triangle")))),
                column(COLOUR),
                null,
                List.of(rule),
                PageRequest.unlimited(),
                DateTimeSettings.builder().build());

        final ColumnValue red = result.getValues()
                .stream()
                .filter(columnValue -> columnValue.getValue().equals("red"))
                .findFirst()
                .orElseThrow();
        assertThat(red.getMatchingRule()).isEqualTo("redRule");
        assertThat(red.isAvailable()).isFalse();
    }

    private ColumnValues find(final Column... columns) {
        return find(tableSettings(columns), null, PageRequest.unlimited());
    }

    private ColumnValues find(final TableSettings tableSettings,
                              final String nameFilter,
                              final PageRequest pageRequest) {
        return finder.find(
                dataStore(),
                resultRequest(tableSettings),
                column(COLOUR),
                nameFilter,
                null,
                pageRequest,
                DateTimeSettings.builder().build());
    }

    private static TableSettings tableSettings(final Column... columns) {
        return TableSettings.builder()
                .columns(Arrays.asList(columns))
                .build();
    }

    private static ResultRequest resultRequest(final TableSettings tableSettings) {
        return ResultRequest.builder()
                .componentId("table")
                .addMappings(tableSettings)
                .build();
    }

    private static Column column(final String name) {
        return Column.builder()
                .id(name)
                .name(name)
                .expression("${" + name + "}")
                .build();
    }

    private static Column column(final String name, final ColumnValueSelection selection) {
        return column(name).copy().columnValueSelection(selection).build();
    }

    private static ColumnValueSelection ticked(final String... values) {
        return ColumnValueSelection.builder()
                .values(Set.of(values))
                .invert(false)
                .build();
    }

    private static ColumnValueSelection unticked(final String... values) {
        return ColumnValueSelection.builder()
                .values(Set.of(values))
                .invert(true)
                .build();
    }

    private static DataStore dataStore() {
        final DataStore dataStore = Mockito.mock(DataStore.class);
        Mockito.when(dataStore.getColumns()).thenReturn(List.of(column(COLOUR), column(SHAPE)));
        Mockito.when(dataStore.getKeyFactory()).thenReturn(Mockito.mock(KeyFactory.class));
        Mockito.doAnswer(invocation -> {
            final ItemMapper mapper = invocation.getArgument(4);
            for (final String[] row : ROWS) {
                mapper.create(new TestItem(row)).forEach(item -> {
                });
            }
            return null;
        }).when(dataStore).fetch(any(), any(), any(), any(), any(), any(), any());
        return dataStore;
    }

    private static List<String> values(final ColumnValues columnValues) {
        return columnValues.getValues()
                .stream()
                .map(ColumnValue::getValue)
                .toList();
    }

    private static List<Boolean> availability(final ColumnValues columnValues) {
        final List<Boolean> list = new ArrayList<>();
        for (final ColumnValue columnValue : columnValues.getValues()) {
            list.add(columnValue.isAvailable());
        }
        return list;
    }

    private record TestItem(String[] row) implements Item {

        @Override
        public Key getKey() {
            return null;
        }

        @Override
        public Val getValue(final int index) {
            return ValString.create(row[index]);
        }

        @Override
        public int size() {
            return row.length;
        }

        @Override
        public Val[] toArray() {
            final Val[] vals = new Val[row.length];
            for (int i = 0; i < row.length; i++) {
                vals[i] = getValue(i);
            }
            return vals;
        }
    }
}
