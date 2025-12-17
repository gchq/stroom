/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.common.v2;

import stroom.dictionary.api.WordListProvider;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class ValFilter {

    public static Predicate<Val[]> create(final ExpressionOperator rowExpression,
                                          final CompiledColumns compiledColumns,
                                          final DateTimeSettings dateTimeSettings,
                                          final ExpressionPredicateFactory expressionPredicateFactory,
                                          final Map<String, String> paramMap,
                                          final WordListProvider wordListProvider) {
        final ValueFunctionFactories<Values> queryFieldIndex = RowUtil
                .createColumnNameValExtractor(compiledColumns.getColumns());
        final Optional<Predicate<Values>> optionalRowExpressionMatcher =
                expressionPredicateFactory.createOptional(rowExpression, queryFieldIndex, dateTimeSettings);

        final Set<String> fieldsUsed = new HashSet<>(ExpressionUtil.fields(rowExpression));
        final List<UsedColumn> usedColumns = new ArrayList<>();
        for (final CompiledColumn compiledColumn : compiledColumns.getCompiledColumns()) {
            final Column column = compiledColumn.getColumn();
            final boolean needsMapping = fieldsUsed.contains(column.getName());
            final Optional<Predicate<String>> optionalColumnIncludeExcludePredicate =
                    CompiledIncludeExcludeFilter.create(column.getFilter(), paramMap, wordListProvider);

            Generator generator = null;
            boolean required = false;
            Predicate<Val> columnIncludeExcludePredicate = val -> true;
            if (optionalColumnIncludeExcludePredicate.isPresent() || needsMapping) {
                generator = compiledColumn.getGenerator();
                if (generator != null) {
                    required = true;

                    if (optionalColumnIncludeExcludePredicate.isPresent()) {
                        final Predicate<String> predicate = optionalColumnIncludeExcludePredicate.get();
                        columnIncludeExcludePredicate = val -> {
                            final String text = NullSafe.getOrElse(val, Val::toString, "");
                            return predicate.test(text);
                        };
                    }
                }
            }
            usedColumns.add(new UsedColumn(required, generator, columnIncludeExcludePredicate));
        }

        // If we need column mappings then create a predicate that will use them.
        if (!usedColumns.isEmpty()) {
            final ValueReferenceIndex valueReferenceIndex = compiledColumns.getValueReferenceIndex();
            final Predicate<Values> rowPredicate = optionalRowExpressionMatcher.orElse(values -> true);

            return values -> {
                final StoredValues storedValues = valueReferenceIndex.createStoredValues();
                final Val[] vals = new Val[usedColumns.size()];
                for (int i = 0; i < usedColumns.size(); i++) {
                    Val val = ValNull.INSTANCE;
                    final UsedColumn usedColumn = usedColumns.get(i);
                    if (usedColumn.required) {
                        final Generator generator = usedColumn.generator;
                        generator.set(values, storedValues);
                        val = generator.eval(storedValues, null);

                        // As soon as we fail a predicate test for a column then return false.
                        if (!usedColumn.columnIncludeExcludePredicate.test(val)) {
                            return false;
                        }
                    }
                    vals[i] = val;
                }

                // Test the row value map.
                return rowPredicate.test(Values.of(vals));
            };
        } else if (optionalRowExpressionMatcher.isPresent()) {
            return values -> false;
        } else {
            return values -> true;
        }
    }

    private record UsedColumn(boolean required,
                              Generator generator,
                              Predicate<Val> columnIncludeExcludePredicate) {

    }
}
