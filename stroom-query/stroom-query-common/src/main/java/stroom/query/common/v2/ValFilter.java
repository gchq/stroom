package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;
import stroom.util.NullSafe;

import com.google.common.base.Predicates;

import java.util.ArrayList;
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
        final Map<String, Column> columnNameToColumnMap = new HashMap<>();
        final Optional<Predicate<RowValueMap>> optionalRowExpressionMatcher =
                RowFilter.create(
                        compiledColumns.getColumns(),
                        dateTimeSettings,
                        rowExpression,
                        columnNameToColumnMap);

        // See if any of the columns need mapping for the row filter.
        boolean needObjectMap = false;
        for (final CompiledColumn compiledColumn : compiledColumns.getCompiledColumns()) {
            if (columnNameToColumnMap.containsKey(compiledColumn.getColumn().getName())) {
                needObjectMap = true;
                break;
            }
        }

        final List<UsedColumn> usedColumns = new ArrayList<>();
        for (final CompiledColumn compiledColumn : compiledColumns.getCompiledColumns()) {
            final boolean needsMapping = columnNameToColumnMap.containsKey(compiledColumn.getColumn().getName());
            final Optional<Predicate<String>> optionalColumnIncludeExcludePredicate =
                    CompiledIncludeExcludeFilter.create(
                            compiledColumn.getColumn().getFilter(),
                            paramMap);

            if (optionalColumnIncludeExcludePredicate.isPresent() || needsMapping) {
                final Generator generator = compiledColumn.getGenerator();
                if (generator != null) {
                    final Predicate<String> columnIncludeExcludePredicate =
                            optionalColumnIncludeExcludePredicate.orElse(Predicates.alwaysTrue());
                    final BiConsumer<RowValueMap, String> mapConsumer;
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
        final Predicate<RowValueMap> rowPredicate = optionalRowExpressionMatcher
                .map(rowExpressionMatcher -> (Predicate<RowValueMap>) columnNameToValueMap -> {
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
        final Supplier<RowValueMap> mapSupplier;
        if (needObjectMap) {
            mapSupplier = RowValueMap::new;
        } else {
            mapSupplier = RowValueMap::empty;
        }

        // If we need column mappings then create a predicate that will use them.
        if (!usedColumns.isEmpty()) {
            final ValueReferenceIndex valueReferenceIndex = compiledColumns.getValueReferenceIndex();
            return values -> {
                final StoredValues storedValues = valueReferenceIndex.createStoredValues();
                final RowValueMap columnNameToValueMap = mapSupplier.get();

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
        } else if (optionalRowExpressionMatcher.isPresent()) {
            return values -> {
                // Test the row value map.
                return rowPredicate.test(RowValueMap.empty());
            };
        } else {
            return Predicates.alwaysTrue();
        }
    }

    private record UsedColumn(Generator generator,
                              Predicate<String> columnIncludeExcludePredicate,
                              BiConsumer<RowValueMap, String> mapConsumer) {

    }
}
