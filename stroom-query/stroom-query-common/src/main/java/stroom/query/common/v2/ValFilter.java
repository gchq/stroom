/*
 * Copyright 2024 Crown Copyright
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

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;
import stroom.util.NullSafe;
import stroom.util.PredicateUtil;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ValFilter {

    private static final Predicate<Val[]> ALWAYS_TRUE_VAL_PREDICATE = valArr -> true;

    public static Predicate<Val[]> create(final ExpressionOperator rowExpression,
                                          final CompiledColumns compiledColumns,
                                          final DateTimeSettings dateTimeSettings,
                                          final Map<CIKey, String> paramMap,
                                          final Consumer<Throwable> throwableConsumer) {
        final Optional<RowExpressionMatcher> optRowExpressionMatcher =
                RowExpressionMatcher.create(compiledColumns.getColumns(), dateTimeSettings, rowExpression);

        // See if any of the columns need mapping for the row filter.
        final boolean needObjectMap = optRowExpressionMatcher.map(rowExpressionMatcher -> {
            for (final CompiledColumn compiledColumn : compiledColumns.getCompiledColumns()) {
                if (rowExpressionMatcher.isRequiredColumn(compiledColumn.getColumn().getNameAsCIKey())) {
                    return true;
                }
            }
            return false;
        }).orElse(false);

        final List<UsedColumn> usedColumns = new ArrayList<>();
        for (final CompiledColumn compiledColumn : compiledColumns.getCompiledColumns()) {
            final boolean needsMapping = optRowExpressionMatcher
                    .map(matcher -> matcher.isRequiredColumn(compiledColumn.getColumn().getNameAsCIKey()))
                    .orElse(false);
            final Optional<Predicate<String>> optColumnIncludeExcludePredicate =
                    CompiledIncludeExcludeFilter.create(
                            compiledColumn.getColumn().getFilter(),
                            paramMap);

            if (optColumnIncludeExcludePredicate.isPresent() || needsMapping) {
                final Generator generator = compiledColumn.getGenerator();
                if (generator != null) {
                    final Predicate<String> columnIncludeExcludePredicate = optColumnIncludeExcludePredicate
                            .orElse(PredicateUtil.ALWAYS_TRUE_STRING_PREDICATE);

                    final BiConsumer<Map<CIKey, Object>, String> mapConsumer;
                    if (needsMapping) {
                        mapConsumer = (columnNameToValueMap, s) ->
                                columnNameToValueMap.put(compiledColumn.getColumn().getNameAsCIKey(), s);
                    } else {
                        mapConsumer = (columnNameToValueMap, s) -> {
                        };
                    }

                    usedColumns.add(new UsedColumn(generator, columnIncludeExcludePredicate, mapConsumer));
                }
            }
        }

        // Create a predicate to test the row values.
        final Predicate<Map<CIKey, Object>> rowPredicate = optRowExpressionMatcher
                .map(rowExpressionMatcher -> (Predicate<Map<CIKey, Object>>) columnNameToValueMap -> {
                    try {
                        // Test the row value map.
                        return rowExpressionMatcher.test(columnNameToValueMap);
                    } catch (final RuntimeException e) {
                        throwableConsumer.accept(e);
                        return false;
                    }
                })
                .orElse(RowExpressionMatcher.ALWAYS_TRUE_PREDICATE);

        // If we need mappings then we need a map to receive them.
        final Supplier<Map<CIKey, Object>> mapSupplier = needObjectMap
                ? HashMap::new
                : Collections::emptyMap;

        // If we need column mappings then create a predicate that will use them.
        if (!usedColumns.isEmpty()) {
            final ValueReferenceIndex valueReferenceIndex = compiledColumns.getValueReferenceIndex();
            return values -> {
                final StoredValues storedValues = valueReferenceIndex.createStoredValues();
                final Map<CIKey, Object> columnNameToValueMap = mapSupplier.get();

                for (final UsedColumn usedColumn : usedColumns) {
                    final Generator generator = usedColumn.generator;
                    generator.set(values, storedValues);
                    final Val val = generator.eval(storedValues, null);
                    final String text = NullSafe.getOrElse(val, Val::toString, "");
                    // As soon as we fail a predicate test for a column then return false.
                    if (!usedColumn.columnIncludeExcludePredicate.test(text)) {
                        return false;
                    }
                    // TODO : Provide a map of Val to the row predicate as opposed to strings.
                    //  Fix the row expression matcher to deal with Vals.
                    usedColumn.mapConsumer.accept(columnNameToValueMap, val.toString());
                }

                // Test the row value map.
                return rowPredicate.test(columnNameToValueMap);
            };
        } else if (optRowExpressionMatcher.isPresent()) {
            return values -> {
                // Test the row value map.
                return rowPredicate.test(Collections.emptyMap());
            };
        } else {
            return ALWAYS_TRUE_VAL_PREDICATE;
        }
    }


    // --------------------------------------------------------------------------------


    private record UsedColumn(Generator generator,
                              Predicate<String> columnIncludeExcludePredicate,
                              BiConsumer<Map<CIKey, Object>, String> mapConsumer) {

    }
}
