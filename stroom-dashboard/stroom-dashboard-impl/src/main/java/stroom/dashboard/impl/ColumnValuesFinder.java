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
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.OffsetRange;
import stroom.query.api.ResultRequest;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.ConditionalFormattingMapper.RuleAndMatcher;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.FilteredMapper;
import stroom.query.common.v2.Item;
import stroom.query.common.v2.ItemMapper;
import stroom.query.common.v2.OpenGroups;
import stroom.query.common.v2.OpenGroupsImpl;
import stroom.query.common.v2.RowUtil;
import stroom.query.common.v2.RowValueFilter;
import stroom.query.common.v2.SimpleMapper;
import stroom.query.common.v2.ValPredicateFactory;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.Values;
import stroom.util.collections.TrimmedSortedList;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/// Finds the distinct values of a table column for the column's value filter (the tick list).
///
/// Each value is marked as available if at least one row with that value passes all of the table's
/// filters: the column text filters, the ticks in every other column and the filter from selections in other
/// components. A column's own ticks are ignored so that unticking a value never hides it from its own list.
/// Available values are sorted before all other values.
public class ColumnValuesFinder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ColumnValuesFinder.class);

    private static final Comparator<ColumnValue> AVAILABLE_FIRST = Comparator
            .comparing((final ColumnValue columnValue) -> !columnValue.isAvailable())
            .thenComparing(new ColumnValueComparator());

    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final ValPredicateFactory valPredicateFactory;

    @Inject
    public ColumnValuesFinder(final ExpressionPredicateFactory expressionPredicateFactory,
                              final ValPredicateFactory valPredicateFactory) {
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.valPredicateFactory = valPredicateFactory;
    }

    /// Finds one page of the distinct values of a column, available values first.
    ///
    /// @param dataStore                  The table's data store.
    /// @param resultRequest              The table's result request. Its first mapping holds the table settings
    ///                                   whose filters decide which values are available.
    /// @param column                     The column to find values for.
    /// @param nameFilter                 Text typed into the tick list's quick filter, or null. Values that do
    ///                                   not match are left out altogether.
    /// @param conditionalFormattingRules The table's conditional formatting rules, used to style each value.
    /// @param pageRequest                The page of values to return.
    /// @param dateTimeSettings           The date time settings to use when evaluating filters.
    /// @return The requested page of values and the number of available values across all pages.
    public ColumnValues find(final DataStore dataStore,
                             final ResultRequest resultRequest,
                             final Column column,
                             final String nameFilter,
                             final List<ConditionalFormattingRule> conditionalFormattingRules,
                             final PageRequest pageRequest,
                             final DateTimeSettings dateTimeSettings) {
        final TableSettings tableSettings = NullSafe.first(resultRequest.getMappings());
        final List<Column> columns = getTableColumns(dataStore, tableSettings);
        final int columnIndex = indexOf(columns, column.getId());
        final Map<String, ColumnValue> values = new HashMap<>();

        if (columnIndex != -1) {
            final Predicate<Val> nameFilterPredicate = valPredicateFactory.createValPredicate(
                    column,
                    nameFilter,
                    dateTimeSettings);
            final Predicate<Values> availablePredicate = createAvailablePredicate(
                    columns,
                    columnIndex,
                    NullSafe.get(tableSettings, TableSettings::getAggregateFilter),
                    dateTimeSettings);
            final List<RuleAndMatcher> ruleAndMatchers = getRules(
                    column,
                    dateTimeSettings,
                    conditionalFormattingRules);
            final ItemMapper mapper = SimpleMapper.create(dataStore.getColumns(), columns);
            final OpenGroups openGroups = OpenGroupsImpl.fromGroupSelection(
                    resultRequest.getGroupSelection(), dataStore.getKeyFactory());

            //noinspection unused row, count
            dataStore.fetch(
                    dataStore.getColumns(),
                    OffsetRange.UNBOUNDED,
                    openGroups,
                    null,
                    item -> {
                        mapper.create(item).forEach(row -> addValue(
                                values,
                                row,
                                columnIndex,
                                nameFilterPredicate,
                                availablePredicate,
                                ruleAndMatchers));
                        return Stream.empty();
                    },
                    row -> {

                    },
                    count -> {

                    });
        }

        return createPage(values, pageRequest);
    }

    private List<Column> getTableColumns(final DataStore dataStore, final TableSettings tableSettings) {
        final List<Column> columns = NullSafe.get(tableSettings, TableSettings::getColumns);
        if (NullSafe.isEmptyCollection(columns)) {
            return dataStore.getColumns();
        }
        return columns;
    }

    private int indexOf(final List<Column> columns, final String columnId) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getId().equals(columnId)) {
                return i;
            }
        }
        return -1;
    }

    private Predicate<Values> createAvailablePredicate(final List<Column> columns,
                                                       final int columnIndex,
                                                       final ExpressionOperator aggregateFilter,
                                                       final DateTimeSettings dateTimeSettings) {
        // A column's own ticks never hide its own values.
        final List<Column> filterColumns = new ArrayList<>(columns);
        filterColumns.set(columnIndex, columns.get(columnIndex).copy().columnValueSelection(null).build());

        // Another column with nothing ticked hides every row.
        if (!RowValueFilter.matches(filterColumns)) {
            return row -> false;
        }

        return FilteredMapper
                .createValuesPredicate(filterColumns, aggregateFilter, dateTimeSettings, expressionPredicateFactory)
                .orElse(row -> true);
    }

    private void addValue(final Map<String, ColumnValue> values,
                          final Item row,
                          final int columnIndex,
                          final Predicate<Val> nameFilterPredicate,
                          final Predicate<Values> availablePredicate,
                          final List<RuleAndMatcher> ruleAndMatchers) {
        final Val val = row.getValue(columnIndex);
        if (val == null || !nameFilterPredicate.test(val)) {
            return;
        }
        final String value = val.toString();
        if (value == null) {
            return;
        }

        final ColumnValue existing = values.get(value);
        if (existing == null) {
            values.put(value, new ColumnValue(
                    value,
                    getMatchingRuleId(val, ruleAndMatchers),
                    isAvailable(row, availablePredicate)));
        } else if (!existing.isAvailable() && isAvailable(row, availablePredicate)) {
            values.put(value, new ColumnValue(value, existing.getMatchingRule(), true));
        }
    }

    private boolean isAvailable(final Item row, final Predicate<Values> availablePredicate) {
        try {
            return availablePredicate.test(row);
        } catch (final RuntimeException e) {
            // The table hides rows that fail to filter, so do the same here.
            LOGGER.debug(e::getMessage, e);
            return false;
        }
    }

    private String getMatchingRuleId(final Val val, final List<RuleAndMatcher> ruleAndMatchers) {
        return ruleAndMatchers
                .stream()
                .filter(ruleAndMatcher -> ruleAndMatcher.matcher().test(Values.of(val)))
                .findFirst()
                .map(RuleAndMatcher::rule)
                .map(ConditionalFormattingRule::getId)
                .orElse(null);
    }

    private ColumnValues createPage(final Map<String, ColumnValue> values, final PageRequest pageRequest) {
        final TrimmedSortedList<ColumnValue> list = new TrimmedSortedList<>(pageRequest, AVAILABLE_FIRST);
        long availableCount = 0;
        for (final ColumnValue columnValue : values.values()) {
            list.add(columnValue);
            if (columnValue.isAvailable()) {
                availableCount++;
            }
        }
        final ResultPage<ColumnValue> resultPage = list.getResultPage();
        return new ColumnValues(resultPage.getValues(), resultPage.getPageResponse(), availableCount);
    }

    private List<RuleAndMatcher> getRules(final Column column,
                                          final DateTimeSettings dateTimeSettings,
                                          final List<ConditionalFormattingRule> rules) {
        final List<ConditionalFormattingRule> activeRules = NullSafe.list(rules)
                .stream()
                .filter(ConditionalFormattingRule::isEnabled)
                .toList();
        final List<RuleAndMatcher> ruleAndMatchers = new ArrayList<>();
        if (!activeRules.isEmpty()) {
            final ValueFunctionFactories<Values> queryFieldIndex = RowUtil
                    .createColumnNameValExtractor(Collections.singletonList(column));
            for (final ConditionalFormattingRule rule : activeRules) {
                try {
                    final Optional<Predicate<Values>> optionalValuesPredicate =
                            expressionPredicateFactory.createOptional(
                                    rule.getExpression(),
                                    queryFieldIndex,
                                    dateTimeSettings);
                    final Predicate<Values> conditionalFormattingPredicate =
                            optionalValuesPredicate.orElse(t -> true);
                    ruleAndMatchers.add(new RuleAndMatcher(rule, conditionalFormattingPredicate));
                } catch (final RuntimeException e) {
                    throw new RuntimeException("Error evaluating conditional formatting rule: " +
                                               rule.getExpression() +
                                               " (" +
                                               e.getMessage() +
                                               ")", e);
                }
            }
        }

        return ruleAndMatchers;
    }
}
