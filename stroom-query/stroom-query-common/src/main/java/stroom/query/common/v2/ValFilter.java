package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import com.google.common.base.Predicates;

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

    public static Predicate<Val[]> create(final ExpressionOperator rowExpression,
                                          final CompiledColumns compiledColumns,
                                          final DateTimeSettings dateTimeSettings,
                                          final Map<String, String> paramMap,
                                          final Consumer<Throwable> throwableConsumer) {
        final Optional<RowExpressionMatcher> optionalRowExpressionMatcher =
                RowExpressionMatcher.create(compiledColumns.getColumns(), dateTimeSettings, rowExpression);

        // See if any of the columns need mapping for the row filter.
        final boolean needObjectMap = optionalRowExpressionMatcher.map(rowExpressionMatcher -> {
            for (final CompiledColumn compiledColumn : compiledColumns.getCompiledColumns()) {
                if (rowExpressionMatcher.isRequiredColumn(compiledColumn.getColumn().getName())) {
                    return true;
                }
            }
            return false;
        }).orElse(false);

        final List<UsedColumn> usedColumns = new ArrayList<>();
        for (final CompiledColumn compiledColumn : compiledColumns.getCompiledColumns()) {
            final boolean needsMapping = optionalRowExpressionMatcher
                    .map(matcher -> matcher.isRequiredColumn(compiledColumn.getColumn().getName()))
                    .orElse(false);
            final Optional<Predicate<String>> optionalColumnIncludeExcludePredicate =
                    CompiledIncludeExcludeFilter.create(
                            compiledColumn.getColumn().getFilter(),
                            paramMap);

            if (optionalColumnIncludeExcludePredicate.isPresent() || needsMapping) {
                final Generator generator = compiledColumn.getGenerator();
                if (generator != null) {
                    final Predicate<String> columnIncludeExcludePredicate =
                            optionalColumnIncludeExcludePredicate.orElse(Predicates.alwaysTrue());
                    final BiConsumer<Map<String, Object>, String> mapConsumer;
                    if (needsMapping) {
                        mapConsumer = (columnNameToValueMap, s) ->
                                columnNameToValueMap.put(compiledColumn.getColumn().getName(), s);
                    } else {
                        mapConsumer = (columnNameToValueMap, s) -> {
                        };
                    }

                    usedColumns.add(new UsedColumn(generator, columnIncludeExcludePredicate, mapConsumer));
                }
            }
        }

        // Create a predicate to test the row values.
        final Predicate<Map<String, Object>> rowPredicate = optionalRowExpressionMatcher
                .map(rowExpressionMatcher -> (Predicate<Map<String, Object>>) columnNameToValueMap -> {
                    try {
                        // Test the row value map.
                        return rowExpressionMatcher.test(columnNameToValueMap);
                    } catch (final RuntimeException e) {
                        throwableConsumer.accept(e);
                        return false;
                    }
                })
                .orElse(Predicates.alwaysTrue());

        // If we need mappings then we need a map to receive them.
        final Supplier<Map<String, Object>> mapSupplier;
        if (needObjectMap) {
            mapSupplier = HashMap::new;
        } else {
            mapSupplier = Collections::emptyMap;
        }

        // If we need column mappings then create a predicate that will use them.
        if (!usedColumns.isEmpty()) {
            final ValueReferenceIndex valueReferenceIndex = compiledColumns.getValueReferenceIndex();
            return values -> {
                final StoredValues storedValues = valueReferenceIndex.createStoredValues();
                final Map<String, Object> columnNameToValueMap = mapSupplier.get();

                for (final UsedColumn usedColumn : usedColumns) {
                    final Generator generator = usedColumn.generator;
                    generator.set(values, storedValues);
                    final String value = generator.eval(storedValues, null).toString();
                    // As soon as we fail a predicate test for a column then return false.
                    if (!usedColumn.columnIncludeExcludePredicate.test(value)) {
                        return false;
                    }
                    usedColumn.mapConsumer.accept(columnNameToValueMap, value);
                }

                // Test the row value map.
                return rowPredicate.test(columnNameToValueMap);
            };
        } else if (optionalRowExpressionMatcher.isPresent()) {
            return values -> {
                // Test the row value map.
                return rowPredicate.test(Collections.emptyMap());
            };
        } else {
            return Predicates.alwaysTrue();
        }
    }

    private record UsedColumn(Generator generator,
                              Predicate<String> columnIncludeExcludePredicate,
                              BiConsumer<Map<String, Object>, String> mapConsumer) {

    }
}
